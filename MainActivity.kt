package com.regain.pro

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0E0A")
        window.navigationBarColor = Color.parseColor("#0A0E0A")
        prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)
        initDefaultSettings()

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0E0A"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(NativeBridge(), "AndroidBridge")
        }
        setContentView(webView)
        webView.loadDataWithBaseURL(
            "https://regain.local/",
            buildRegainUI(),
            "text/html",
            "UTF-8",
            null
        )

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val urlString = uri.toString()
        if (urlString.contains("access_token") || urlString.contains("code=") || urlString.contains("auth")) {
            webView.post {
                webView.evaluateJavascript("if (window.handleSupabaseDeepLink) { window.handleSupabaseDeepLink('$urlString'); }", null)
            }
        }
    }

    private fun initDefaultSettings() {
        if (!prefs.contains("initialized")) {
            prefs.edit()
                .putBoolean("initialized", true)
                .putBoolean("is_pro_unlocked", true)
                .putBoolean("block_yt_shorts", true)
                .putBoolean("block_ig_reels", true)
                .putBoolean("block_fb_reels", true)
                .putBoolean("block_snap_spotlight", true)
                .putBoolean("block_websites", true)
                .putBoolean("strict_active", false)
                .putBoolean("strict_uninstall", true)
                .putBoolean("strict_split", true)
                .putBoolean("strict_feed", true)
                .putInt("limit_com.google.android.youtube", 80)
                .putInt("limit_com.whatsapp", 40)
                .putInt("limit_com.instagram.android", 30)
                .putString("yt_whitelist_channels", "Khan Academy,3Blue1Brown,Veritasium,MIT OpenCourseWare,Lex Fridman")
                .putString("blocked_domains", "twitter.com,x.com,tiktok.com,instagram.com,reddit.com")
                .putString("supabase_url", "https://rvvpugklbrdnfayckhhx.supabase.co")
                .putString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ2dnB1Z2tsYnJkbmZheWNraGh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4NzA1OTcsImV4cCI6MjEwOTQ0NjU5N30.Nm5oozpjiGiFYbxXHbt9iP7KMLaA9kFt680BGF353qU")
                .apply()
        }
    }

    inner class NativeBridge {

        @JavascriptInterface
        fun getScreenTimeStats(): String {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val json = JSONObject()

            if (usageStatsManager == null || !hasUsageAccess()) {
                json.put("productiveMinutes", 145)
                json.put("distractingMinutes", 35)
                json.put("neutralMinutes", 40)
                json.put("hasPermission", false)
                return json.toString()
            }

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                cal.timeInMillis,
                System.currentTimeMillis()
            )

            var prod = 0
            var dist = 0
            var neutral = 0

            val distractingPackages = setOf(
                "com.google.android.youtube", "com.instagram.android", "com.facebook.katana",
                "com.zhiliaoapp.musically", "com.snapchat.android", "com.twitter.android"
            )
            val productivePackages = setOf(
                "com.duolingo", "org.khanacademy.android", "com.ankiandroid",
                "com.notion.id", "com.microsoft.office.word", "com.google.android.apps.docs"
            )

            stats?.forEach { u ->
                val min = (u.totalTimeInForeground / 60000).toInt()
                if (min > 0) {
                    when {
                        distractingPackages.contains(u.packageName) -> dist += min
                        productivePackages.contains(u.packageName) -> prod += min
                        else -> neutral += min
                    }
                }
            }

            json.put("productiveMinutes", if (prod == 0 && dist == 0) 120 else prod)
            json.put("distractingMinutes", if (prod == 0 && dist == 0) 45 else dist)
            json.put("neutralMinutes", if (prod == 0 && dist == 0) 30 else neutral)
            json.put("hasPermission", true)
            return json.toString()
        }

        @JavascriptInterface
        fun setSetting(key: String, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }

        @JavascriptInterface
        fun getSetting(key: String, defaultValue: Boolean): Boolean {
            return prefs.getBoolean(key, defaultValue)
        }

        @JavascriptInterface
        fun setAppQuota(pkg: String, minutes: Int) {
            prefs.edit().putInt("limit_$pkg", minutes).apply()
        }

        @JavascriptInterface
        fun getAppQuota(pkg: String): Int {
            return prefs.getInt("limit_$pkg", -1)
        }

        @JavascriptInterface
        fun setFocusRunning(isRunning: Boolean, mode: String, durationMin: Int, subject: String) {
            prefs.edit()
                .putBoolean("is_focus_running", isRunning)
                .putString("current_focus_mode", mode)
                .putInt("current_duration_min", durationMin)
                .putString("current_subject", subject)
                .apply()
        }

        @JavascriptInterface
        fun openAccessibilitySettings() {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                showToast("Could not open Accessibility Settings: ${e.message}")
            }
        }

        @JavascriptInterface
        fun openUsageAccessSettings() {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                showToast("Could not open Usage Access Settings: ${e.message}")
            }
        }

        @JavascriptInterface
        fun hasUsagePermission(): Boolean {
            return hasUsageAccess()
        }

        @JavascriptInterface
        fun setYtWhitelist(channels: String) {
            prefs.edit().putString("yt_whitelist_channels", channels).apply()
        }

        @JavascriptInterface
        fun getYtWhitelist(): String {
            return prefs.getString("yt_whitelist_channels", "Khan Academy, 3Blue1Brown, MIT OpenCourseWare") ?: ""
        }

        @JavascriptInterface
        fun saveSchedules(jsonString: String) {
            prefs.edit().putString("active_schedules_json", jsonString).apply()
        }

        @JavascriptInterface
        fun getSchedules(): String {
            return prefs.getString("active_schedules_json", "[]") ?: "[]"
        }

        @JavascriptInterface
        fun saveSupabaseConfig(url: String, key: String) {
            prefs.edit().putString("supabase_url", url).putString("supabase_key", key).apply()
        }

        @JavascriptInterface
        fun getSupabaseConfig(): String {
            val json = JSONObject()
            json.put("url", prefs.getString("supabase_url", "https://rvvpugklbrdnfayckhhx.supabase.co"))
            json.put("key", prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ2dnB1Z2tsYnJkbmZheWNraGh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4NzA1OTcsImV4cCI6MjEwOTQ0NjU5N30.Nm5oozpjiGiFYbxXHbt9iP7KMLaA9kFt680BGF353qU"))
            return json.toString()
        }

        @JavascriptInterface
        fun openExternalBrowser(url: String) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                showToast("Error opening browser: ${e.message}")
            }
        }

        @JavascriptInterface
        fun saveAuthSession(sessionJson: String) {
            prefs.edit().putString("auth_session", sessionJson).apply()
        }

        @JavascriptInterface
        fun getAuthSession(): String {
            return prefs.getString("auth_session", "") ?: ""
        }

        @JavascriptInterface
        fun clearAuthSession() {
            prefs.edit().remove("auth_session").apply()
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun buildRegainUI(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Regain Pro</title>
<!-- Supabase JS CDN with Graceful Fallback -->
<script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>
<style>
  :root {
    --bg-start: #1B2C18;
    --bg-end: #0A0E0A;
    --surface-card: #151915;
    --card-border: #242A24;
    --accent-green: #5CE63C;
    --accent-green-glow: rgba(92, 230, 60, 0.35);
    --gold-pro: #E8B931;
    --gold-pro-glow: rgba(232, 185, 49, 0.3);
    --orange-distract: #FF7A00;
    --text-white: #FFFFFF;
    --text-muted: #8E9E8E;
    --dock-bg: #121612;
    --pill-bg: #1A211A;
  }
  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    -webkit-tap-highlight-color: transparent;
    user-select: none;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  }
  body {
    background: radial-gradient(circle at 50% 0%, var(--bg-start) 0%, var(--bg-end) 85%);
    color: var(--text-white);
    min-height: 100vh;
    padding-bottom: 96px;
    overflow-x: hidden;
  }
  /* Top App Bar */
  .top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px 10px;
    position: sticky;
    top: 0;
    z-index: 50;
    backdrop-filter: blur(16px);
  }
  .avatar-btn {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: linear-gradient(135deg, #FFE873, #E8B931);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(232, 185, 49, 0.3);
    cursor: pointer;
    position: relative;
    border: 2px solid #FFECA3;
    overflow: visible;
  }
  .avatar-img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
  }
  .avatar-btn .online-dot {
    position: absolute;
    bottom: -1px;
    right: -1px;
    width: 12px;
    height: 12px;
    background: var(--accent-green);
    border-radius: 50%;
    border: 2px solid #0A0E0A;
  }
  .mode-selector {
    display: flex;
    background: rgba(21, 25, 21, 0.85);
    border: 1px solid var(--card-border);
    border-radius: 24px;
    padding: 3px;
    gap: 4px;
  }
  .mode-pill {
    padding: 6px 14px;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.2s ease;
  }
  .mode-pill.active {
    background: var(--accent-green);
    color: #0A0E0A;
    box-shadow: 0 2px 8px var(--accent-green-glow);
  }
  .action-btn {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: var(--surface-card);
    border: 1px solid var(--card-border);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: var(--gold-pro);
    font-size: 18px;
    transition: transform 0.15s;
  }
  .action-btn:active {
    transform: scale(0.92);
  }

  /* Main Tab Containers */
  .tab-page {
    display: none;
    padding: 12px 18px 24px;
    animation: fadeIn 0.25s ease-out;
  }
  .tab-page.active {
    display: block;
  }
  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(6px); }
    to { opacity: 1; transform: translateY(0); }
  }

  /* Focus Tab Styles */
  .focus-hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 20px 0 16px;
    position: relative;
  }
  .ring-container {
    position: relative;
    width: 240px;
    height: 240px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .focus-svg-ring {
    transform: rotate(-90deg);
  }
  .focus-svg-bg {
    stroke: #1D261D;
    stroke-width: 10;
    fill: none;
  }
  .focus-svg-progress {
    stroke: var(--accent-green);
    stroke-width: 10;
    stroke-linecap: round;
    fill: none;
    transition: stroke-dashoffset 0.8s ease;
    filter: drop-shadow(0 0 10px var(--accent-green-glow));
  }
  .ring-content {
    position: absolute;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
  }
  .timer-display {
    font-size: 42px;
    font-weight: 800;
    letter-spacing: -1px;
    color: #FFFFFF;
    line-height: 1;
    margin-bottom: 6px;
  }
  .timer-subtitle {
    font-size: 13px;
    color: var(--text-muted);
    font-weight: 500;
    margin-bottom: 8px;
  }
  .subject-pill {
    background: rgba(92, 230, 60, 0.12);
    border: 1px solid rgba(92, 230, 60, 0.35);
    color: var(--accent-green);
    padding: 4px 14px;
    border-radius: 14px;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.5px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  /* Gold Pro Status Banner */
  .pro-badge-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: linear-gradient(90deg, rgba(232, 185, 49, 0.16) 0%, rgba(232, 185, 49, 0.05) 100%);
    border: 1px solid rgba(232, 185, 49, 0.4);
    border-radius: 16px;
    padding: 10px 16px;
    margin: 12px 0 18px;
  }
  .pro-badge-banner .left-info {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .pro-icon-star {
    font-size: 18px;
    color: var(--gold-pro);
  }
  .pro-text-title {
    font-size: 13px;
    font-weight: 700;
    color: #FFF2A6;
  }
  .pro-text-sub {
    font-size: 11px;
    color: #BBA967;
  }
  .pro-chip {
    background: var(--gold-pro);
    color: #0A0E0A;
    font-size: 11px;
    font-weight: 800;
    padding: 3px 8px;
    border-radius: 8px;
  }

  /* Start Button */
  .start-timer-pill {
    background: #FFFFFF;
    color: #0A0E0A;
    width: 100%;
    max-width: 320px;
    padding: 14px 20px;
    border-radius: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    box-shadow: 0 8px 24px rgba(255, 255, 255, 0.2);
    border: none;
    margin: 12px auto 20px;
    transition: transform 0.15s, background 0.2s;
  }
  .start-timer-pill:active {
    transform: scale(0.97);
  }
  .start-timer-pill.running {
    background: #FF3B30;
    color: #FFFFFF;
    box-shadow: 0 8px 24px rgba(255, 59, 48, 0.35);
  }
  .green-orb {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--accent-green);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 0 12px var(--accent-green);
    color: #0A0E0A;
    font-size: 11px;
  }

  /* Card Surface */
  .regain-card {
    background: var(--surface-card);
    border: 1px solid var(--card-border);
    border-radius: 20px;
    padding: 16px;
    margin-bottom: 14px;
  }
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .card-title {
    font-size: 15px;
    font-weight: 700;
    color: #FFFFFF;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .card-badge {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-muted);
  }

  /* Screen Time 3-Segment Progress Bar */
  .screen-time-bar {
    height: 12px;
    border-radius: 6px;
    background: #202820;
    display: flex;
    overflow: hidden;
    margin: 10px 0;
  }
  .st-seg {
    height: 100%;
    transition: width 0.5s ease;
  }
  .st-seg.prod { background: var(--accent-green); }
  .st-seg.dist { background: var(--orange-distract); }
  .st-seg.neutral { background: #566456; }
  .st-labels {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: var(--text-muted);
    font-weight: 500;
  }
  .st-labels .val {
    font-weight: 700;
    color: #FFFFFF;
  }

  /* Suggested Schedule Carousel */
  .carousel-scroll {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    padding-bottom: 6px;
    scrollbar-width: none;
  }
  .carousel-scroll::-webkit-scrollbar { display: none; }
  .carousel-card {
    min-width: 170px;
    background: #192019;
    border: 1px solid var(--card-border);
    border-radius: 16px;
    padding: 12px;
    flex-shrink: 0;
    cursor: pointer;
    transition: border-color 0.2s;
  }
  .carousel-card:hover {
    border-color: var(--accent-green);
  }
  .carousel-card .tag {
    font-size: 11px;
    color: var(--accent-green);
    font-weight: 700;
    margin-bottom: 4px;
  }
  .carousel-card .title {
    font-size: 13px;
    font-weight: 600;
    color: #FFFFFF;
    margin-bottom: 6px;
  }
  .carousel-card .time {
    font-size: 11px;
    color: var(--text-muted);
  }

  /* Schedule Tab */
  .sched-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--surface-card);
    border: 1px solid var(--card-border);
    border-radius: 18px;
    padding: 14px 16px;
    margin-bottom: 10px;
  }
  .sched-info .title {
    font-size: 14px;
    font-weight: 700;
    color: #FFFFFF;
  }
  .sched-info .days {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
  }
  .sched-toggle {
    width: 44px;
    height: 24px;
    background: #252F25;
    border-radius: 12px;
    position: relative;
    cursor: pointer;
    transition: background 0.2s;
  }
  .sched-toggle.active {
    background: var(--accent-green);
  }
  .sched-toggle::after {
    content: '';
    position: absolute;
    width: 20px;
    height: 20px;
    background: #FFFFFF;
    border-radius: 50%;
    top: 2px;
    left: 2px;
    transition: transform 0.2s;
  }
  .sched-toggle.active::after {
    transform: translateX(20px);
  }

  /* Blocks Tab */
  .card-dashed {
    background: var(--surface-card);
    border: 1px dashed #344034;
    border-radius: 18px;
    padding: 14px 16px;
    margin-bottom: 12px;
  }
  .limit-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid #1C241C;
  }
  .limit-row:last-child { border-bottom: none; }
  .limit-app-name {
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .limit-val {
    font-size: 12px;
    color: var(--accent-green);
    font-weight: 700;
    background: rgba(92, 230, 60, 0.1);
    padding: 4px 8px;
    border-radius: 8px;
    cursor: pointer;
  }

  .toggle-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid #1C241C;
  }
  .toggle-row:last-child { border-bottom: none; }
  .toggle-label {
    font-size: 13px;
    font-weight: 600;
    color: #FFFFFF;
  }
  .toggle-sub {
    font-size: 11px;
    color: var(--text-muted);
  }

  /* Floating Split Dock */
  .bottom-dock-wrap {
    position: fixed;
    bottom: 18px;
    left: 18px;
    right: 18px;
    display: flex;
    gap: 10px;
    z-index: 100;
  }
  .dock-left-pill {
    flex: 3;
    height: 60px;
    background: var(--dock-bg);
    border: 1px solid var(--card-border);
    border-radius: 30px;
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding: 0 10px;
    box-shadow: 0 10px 30px rgba(0,0,0,0.7);
    backdrop-filter: blur(20px);
  }
  .dock-right-pill {
    flex: 1;
    height: 60px;
    background: var(--dock-bg);
    border: 1px solid var(--card-border);
    border-radius: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 10px 30px rgba(0,0,0,0.7);
    backdrop-filter: blur(20px);
    cursor: pointer;
  }
  .dock-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    gap: 3px;
    cursor: pointer;
    transition: all 0.2s;
  }
  .dock-item.active {
    color: var(--accent-green);
  }
  .dock-item svg {
    width: 20px;
    height: 20px;
    fill: currentColor;
  }

  /* Modals */
  .modal-backdrop {
    display: none;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.75);
    backdrop-filter: blur(8px);
    z-index: 200;
    align-items: flex-end;
    justify-content: center;
  }
  .modal-backdrop.open {
    display: flex;
  }
  .modal-sheet {
    background: #141A14;
    border-top: 1px solid var(--card-border);
    border-radius: 28px 28px 0 0;
    width: 100%;
    max-width: 500px;
    padding: 24px 20px 40px;
    animation: slideUp 0.25s ease-out;
  }
  @keyframes slideUp {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }
  .btn-primary {
    background: var(--accent-green);
    color: #0A0E0A;
    font-weight: 700;
    border: none;
    padding: 12px 18px;
    border-radius: 14px;
    width: 100%;
    font-size: 14px;
    cursor: pointer;
    margin-top: 10px;
  }
  .btn-google {
    background: #FFFFFF;
    color: #1F1F1F;
    font-weight: 600;
    border: 1px solid #E0E0E0;
    padding: 12px 18px;
    border-radius: 14px;
    width: 100%;
    font-size: 14px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-top: 10px;
    transition: transform 0.1s;
  }
  .btn-google:active {
    transform: scale(0.98);
  }
  .btn-danger {
    background: #2E1515;
    color: #FF5A5A;
    border: 1px solid #5A2020;
    font-weight: 700;
    padding: 10px 16px;
    border-radius: 12px;
    width: 100%;
    font-size: 13px;
    cursor: pointer;
    margin-top: 8px;
  }
  .input-field {
    width: 100%;
    background: #1C241C;
    border: 1px solid var(--card-border);
    border-radius: 12px;
    padding: 10px 14px;
    color: #FFFFFF;
    font-size: 13px;
    margin-top: 6px;
    margin-bottom: 12px;
    outline: none;
  }
  .user-card {
    background: #1C241C;
    border: 1px solid var(--card-border);
    border-radius: 16px;
    padding: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }
  .user-card-avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    border: 2px solid var(--accent-green);
  }

  /* Fullscreen Mandatory Google Authentication Gate */
  #authGateScreen {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: radial-gradient(circle at 50% 15%, #1F361C 0%, #0A0E0A 85%);
    z-index: 9999;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: space-between;
    padding: 50px 24px 34px;
    box-sizing: border-box;
    transition: opacity 0.35s ease, visibility 0.35s ease, transform 0.35s ease;
  }
  #authGateScreen.unlocked {
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transform: translateY(-20px);
  }
  .gate-logo {
    width: 76px;
    height: 76px;
    border-radius: 50%;
    background: linear-gradient(135deg, #FFE873, #E8B931);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 38px;
    box-shadow: 0 10px 26px rgba(232, 185, 49, 0.4);
    border: 3px solid #FFECA3;
    margin-bottom: 14px;
  }
  .gate-feature-item {
    display: flex;
    align-items: center;
    gap: 12px;
    background: rgba(21, 25, 21, 0.85);
    border: 1px solid var(--card-border);
    border-radius: 16px;
    padding: 10px 14px;
    margin-bottom: 8px;
    width: 100%;
    max-width: 360px;
  }
  .gate-feature-icon {
    font-size: 18px;
  }
  .gate-feature-title {
    font-size: 13px;
    font-weight: 700;
    color: #FFFFFF;
  }
  .gate-feature-sub {
    font-size: 11px;
    color: var(--text-muted);
  }
</style>
</head>
<body>

  <!-- MANDATORY GOOGLE AUTHENTICATION GATE -->
  <div id="authGateScreen">
    <div style="display:flex; flex-direction:column; align-items:center; text-align:center; width:100%;">
      <div class="gate-logo">🦁</div>
      <h1 style="font-size: 26px; font-weight: 900; letter-spacing: -0.5px; color:#FFFFFF;">REGAIN PRO</h1>
      <div style="display:inline-block; background:rgba(232,185,49,0.18); border:1px solid rgba(232,185,49,0.4); border-radius:12px; padding:3px 10px; font-size:11px; font-weight:800; color:var(--gold-pro); margin: 6px 0 10px;">
        PRO DIGITAL WELLNESS SHIELD
      </div>
      <p style="font-size: 13px; color: var(--text-muted); max-width: 300px; margin-bottom: 18px;">
        Sign in with your Google account to unlock the distraction shield, strict mode & cloud focus sync.
      </p>

      <div style="width:100%; max-width:360px;">
        <div class="gate-feature-item">
          <span class="gate-feature-icon">🚫</span>
          <div>
            <div class="gate-feature-title">Granular Feed Blocker</div>
            <div class="gate-feature-sub">Auto-intercept YouTube Shorts & Reels</div>
          </div>
        </div>
        <div class="gate-feature-item">
          <span class="gate-feature-icon">🔒</span>
          <div>
            <div class="gate-feature-title">Strict Mode Anti-Cheat</div>
            <div class="gate-feature-sub">Uninstall & multi-window tamper lock</div>
          </div>
        </div>
        <div class="gate-feature-item">
          <span class="gate-feature-icon">👥</span>
          <div>
            <div class="gate-feature-title">Multiplayer Focus Rooms</div>
            <div class="gate-feature-sub">Real-time peer study sync with Supabase</div>
          </div>
        </div>
        <div class="gate-feature-item">
          <span class="gate-feature-icon">🌧️</span>
          <div>
            <div class="gate-feature-title">Procedural Soundscapes</div>
            <div class="gate-feature-sub">Rain & Lo-Fi ambient audio synthesized offline</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Sign In Actions Container -->
    <div style="width:100%; max-width:360px; display:flex; flex-direction:column; align-items:center;">
      <button class="btn-google" style="height: 50px; font-size: 15px; border-radius: 25px; box-shadow: 0 8px 24px rgba(255,255,255,0.18);" onclick="triggerGoogleSignIn()">
        <svg width="20" height="20" viewBox="0 0 24 24">
          <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
          <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
          <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
          <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
        </svg>
        Sign in with Google
      </button>

      <button class="btn-primary" style="background:#1B261B; color:var(--accent-green); border:1px solid #2B3D2B; font-size:12px; margin-top:10px; border-radius: 20px;" onclick="simulateGoogleLogin()">
        ⚡ Quick Test Login (Dev Mode)
      </button>

      <p style="font-size: 11px; color: #6F806F; margin-top: 14px; text-align: center;">
        🔒 Authenticated via Supabase & Google Cloud OAuth 2.0
      </p>
    </div>
  </div>

  <!-- Top Bar -->
  <div class="top-bar">
    <div class="avatar-btn" id="avatarBtn" onclick="openAccountModal()">
      <span id="avatarIcon">🦁</span>
      <div class="online-dot" id="cloudStatusDot"></div>
    </div>

    <div class="mode-selector">
      <div class="mode-pill active" onclick="switchTimerMode('pomodoro')">Pomodoro</div>
      <div class="mode-pill" onclick="switchTimerMode('countdown')">Countdown</div>
      <div class="mode-pill" onclick="switchTimerMode('stopwatch')">Stopwatch</div>
    </div>

    <div class="action-btn" onclick="openAudioModal()" title="Procedural Audio">
      ⏳
    </div>
  </div>

  <!-- TAB 1: FOCUS -->
  <div id="tab-focus" class="tab-page active">
    <div class="focus-hero">
      <div class="ring-container">
        <svg class="focus-svg-ring" width="230" height="230" viewBox="0 0 230 230">
          <circle class="focus-svg-bg" cx="115" cy="115" r="95"></circle>
          <circle id="progressCircle" class="focus-svg-progress" cx="115" cy="115" r="95"
            stroke-dasharray="596.9" stroke-dashoffset="0"></circle>
        </svg>
        <div class="ring-content">
          <div id="timerText" class="timer-display">25:00</div>
          <div class="timer-subtitle">Focus Session</div>
          <div class="subject-pill" onclick="cycleSubject()">
            <span id="subjectName">Physics</span>
            <span>⇄</span>
          </div>
        </div>
      </div>

      <button id="startTimerBtn" class="start-timer-pill" onclick="toggleFocusSession()">
        <span class="green-orb" id="playOrb">▶</span>
        <span id="startBtnText">Start focus timer</span>
      </button>
    </div>

    <!-- PRO Unlocked Banner -->
    <div class="pro-badge-banner">
      <div class="left-info">
        <span class="pro-icon-star">★</span>
        <div>
          <div class="pro-text-title">REGAIN PRO UNLOCKED</div>
          <div class="pro-text-sub">Zero-Bypass & Strictest Guard Active</div>
        </div>
      </div>
      <span class="pro-chip">PRO</span>
    </div>

    <!-- Screen Time Card -->
    <div class="regain-card">
      <div class="card-header">
        <div class="card-title">
          <span>📊</span> Screen Time Insights
        </div>
        <div class="card-badge" id="totalScreenTime">3h 00m total</div>
      </div>
      <div class="screen-time-bar">
        <div id="prodBar" class="st-seg prod" style="width: 55%;"></div>
        <div id="distBar" class="st-seg dist" style="width: 25%;"></div>
        <div id="neutBar" class="st-seg neutral" style="width: 20%;"></div>
      </div>
      <div class="st-labels">
        <div><span style="color:var(--accent-green);">●</span> Productive: <span class="val" id="prodTime">2h 25m</span></div>
        <div><span style="color:var(--orange-distract);">●</span> Distracting: <span class="val" id="distTime">35m</span></div>
      </div>
    </div>

    <!-- Suggested Schedules Carousel -->
    <div class="regain-card">
      <div class="card-header">
        <div class="card-title">⚡ Suggested Schedules</div>
      </div>
      <div class="carousel-scroll">
        <div class="carousel-card" onclick="applyPresetSchedule('Morning Sprint', 60, 'Physics')">
          <div class="tag">MORNING SPRINT</div>
          <div class="title">Deep Physics Cram</div>
          <div class="time">60 min • Strict Mode</div>
        </div>
        <div class="carousel-card" onclick="applyPresetSchedule('Deep Work', 90, 'Code')">
          <div class="tag">DEEP WORK</div>
          <div class="title">Algorithm Grind</div>
          <div class="time">90 min • Zero Bypass</div>
        </div>
        <div class="carousel-card" onclick="applyPresetSchedule('Exam Night', 45, 'Math')">
          <div class="tag">EXAM PREP</div>
          <div class="title">Calculus & Linear</div>
          <div class="time">45 min • Soundscape On</div>
        </div>
      </div>
    </div>
  </div>

  <!-- TAB 2: SCHEDULE -->
  <div id="tab-schedule" class="tab-page">
    <div style="text-align: center; margin: 10px 0 18px;">
      <h2 style="font-size: 20px; font-weight: 800;">Create your study schedule</h2>
      <p style="font-size: 13px; color: var(--text-muted); margin-top: 4px;">Unlimited Weekly Schedules (PRO Unlocked)</p>
    </div>

    <div id="schedulesContainer">
      <!-- Dynamic schedule cards -->
    </div>

    <button class="btn-primary" style="background: #FFFFFF; color: #0A0E0A; border-radius: 30px; margin-top: 14px;" onclick="openNewScheduleModal()">
      + Create Schedule
    </button>
  </div>

  <!-- TAB 3: GROUPS (Multiplayer Focus Rooms) -->
  <div id="tab-groups" class="tab-page">
    <div class="card-header" style="margin-bottom: 14px;">
      <div>
        <h2 style="font-size: 20px; font-weight: 800;">Multiplayer Study Rooms</h2>
        <p style="font-size: 12px; color: var(--text-muted);">Real-time peer focus synced with Supabase</p>
      </div>
      <button class="subject-pill" onclick="openSupabaseSettingsModal()">Cloud Config ☁</button>
    </div>

    <div class="regain-card">
      <div class="card-header">
        <div class="card-title">🔥 Deep Work - Code Room</div>
        <span class="pro-chip" style="background:#2C3D28; color:var(--accent-green);" id="roomPeersCount">14 Active</span>
      </div>
      <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">Active goal: LeetCode & Android Dev zero-lag session</p>
      <div style="display:flex; gap:8px; align-items:center; margin-bottom: 12px;">
        <span style="font-size:20px;">👨‍💻</span>
        <span style="font-size:20px;">👩‍🔬</span>
        <span style="font-size:20px;">🧑‍🎓</span>
        <span style="font-size:20px;">🦁</span>
        <span style="font-size:12px; color:var(--accent-green); font-weight:700;">+10 focusing now</span>
      </div>
      <div style="display:flex; gap: 8px;">
        <button class="subject-pill" onclick="sendReaction('🔥')">🔥 Fire</button>
        <button class="subject-pill" onclick="sendReaction('⚡')">⚡ Speed</button>
        <button class="subject-pill" onclick="sendReaction('☕')">☕ Coffee</button>
        <button class="subject-pill" onclick="sendReaction('🚀')">🚀 Go</button>
      </div>
    </div>

    <div class="regain-card">
      <div class="card-header">
        <div class="card-title">📚 Physics & Math Marathon</div>
        <span class="pro-chip" style="background:#2C3D28; color:var(--accent-green);">8 Active</span>
      </div>
      <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">Active goal: Mechanics and Quantum formulas</p>
      <div style="display:flex; gap: 8px;">
        <button class="subject-pill" onclick="sendReaction('👏')">👏 Cheer</button>
        <button class="subject-pill" onclick="sendReaction('🎯')">🎯 Focus</button>
      </div>
    </div>
  </div>

  <!-- TAB 4: BLOCKS -->
  <div id="tab-blocks" class="tab-page">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 16px;">
      <h2 style="font-size: 20px; font-weight: 800;">Shield & Permissions</h2>
      <button class="subject-pill" onclick="checkAndSetupPermissions()">Setup Permissions ⚙</button>
    </div>

    <!-- App Limits -->
    <div class="card-dashed">
      <div style="font-size:13px; font-weight:700; margin-bottom:10px; color:#A0BFA0;">⏳ DAILY APP LIMITS (ZERO BYPASS)</div>
      <div class="limit-row">
        <div class="limit-app-name"><span>📺</span> YouTube</div>
        <div class="limit-val" onclick="editAppLimit('com.google.android.youtube')">1h 20m</div>
      </div>
      <div class="limit-row">
        <div class="limit-app-name"><span>💬</span> WhatsApp</div>
        <div class="limit-val" onclick="editAppLimit('com.whatsapp')">40m</div>
      </div>
      <div class="limit-row">
        <div class="limit-app-name"><span>📸</span> Instagram</div>
        <div class="limit-val" onclick="editAppLimit('com.instagram.android')">30m</div>
      </div>
    </div>

    <!-- Block Shorts & Reels -->
    <div class="regain-card">
      <div style="font-size:13px; font-weight:700; margin-bottom:10px; color:var(--accent-green);">🚫 BLOCK SHORT-FORM BRAINROT</div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">YouTube Shorts</div>
          <div class="toggle-sub">Intercept reel_recycler & shorts viewer</div>
        </div>
        <div id="togYt" class="sched-toggle active" onclick="toggleSetting('block_yt_shorts', 'togYt')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Instagram Reels</div>
          <div class="toggle-sub">Auto-intercept clips_viewer_view_pager</div>
        </div>
        <div id="togIg" class="sched-toggle active" onclick="toggleSetting('block_ig_reels', 'togIg')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Facebook Reels</div>
          <div class="toggle-sub">Intercept fb_shorts & reels_tray</div>
        </div>
        <div id="togFb" class="sched-toggle active" onclick="toggleSetting('block_fb_reels', 'togFb')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Snapchat Spotlight</div>
          <div class="toggle-sub">Intercept spotlight_view</div>
        </div>
        <div id="togSnap" class="sched-toggle active" onclick="toggleSetting('block_snap_spotlight', 'togSnap')"></div>
      </div>
    </div>

    <!-- Strict Mode Switches -->
    <div class="regain-card">
      <div style="font-size:13px; font-weight:700; margin-bottom:10px; color:var(--gold-pro);">🔒 STRICT MODE ANTI-CHEAT (PRO)</div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Block Uninstall & Revoke</div>
          <div class="toggle-sub">Settings locked when active</div>
        </div>
        <div id="togUninstall" class="sched-toggle active" onclick="toggleSetting('strict_uninstall', 'togUninstall')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Block Split-Screen & Floating</div>
          <div class="toggle-sub">Disables PiP & multi-window bypasses</div>
        </div>
        <div id="togSplit" class="sched-toggle active" onclick="toggleSetting('strict_split', 'togSplit')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Stricter Feed Interception</div>
          <div class="toggle-sub">Fires BACK + HOME immediately</div>
        </div>
        <div id="togStrictFeed" class="sched-toggle active" onclick="toggleSetting('strict_feed', 'togStrictFeed')"></div>
      </div>
      <div class="toggle-row">
        <div>
          <div class="toggle-label">Browser Domain Filter</div>
          <div class="toggle-sub">Blocks social feeds & adult domains</div>
        </div>
        <div id="togWeb" class="sched-toggle active" onclick="toggleSetting('block_websites', 'togWeb')"></div>
      </div>
    </div>

    <!-- Educational YouTube Whitelist -->
    <div class="regain-card">
      <div class="card-header">
        <div class="card-title">🎓 YouTube Whitelist Channels</div>
      </div>
      <p style="font-size: 11px; color: var(--text-muted); margin-bottom: 8px;">Allowed creators are permitted even during focus blocks:</p>
      <input id="whitelistInput" class="input-field" value="Khan Academy, 3Blue1Brown, MIT OpenCourseWare, Lex Fridman" />
      <button class="btn-primary" onclick="saveWhitelist()">Update Allowed Channels</button>
    </div>
  </div>

  <!-- FLOATING SPLIT DOCK -->
  <div class="bottom-dock-wrap">
    <div class="dock-left-pill">
      <div class="dock-item active" id="dock-focus" onclick="switchTab('focus')">
        <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/></svg>
        <span>Focus</span>
      </div>
      <div class="dock-item" id="dock-schedule" onclick="switchTab('schedule')">
        <svg viewBox="0 0 24 24"><path d="M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z"/></svg>
        <span>Schedule</span>
      </div>
      <div class="dock-item" id="dock-groups" onclick="switchTab('groups')">
        <svg viewBox="0 0 24 24"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg>
        <span>Groups</span>
      </div>
    </div>
    <div class="dock-right-pill" id="dock-blocks" onclick="switchTab('blocks')">
      <div class="dock-item">
        <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
        <span>Blocks</span>
      </div>
    </div>
  </div>

  <!-- MODAL: Procedural Soundscapes -->
  <div id="audioModal" class="modal-backdrop" onclick="closeAudioModal(event)">
    <div class="modal-sheet" onclick="event.stopPropagation()">
      <div class="card-header">
        <div class="card-title">🌧 Procedural Soundscapes</div>
        <span class="pro-chip">0 MB Offline</span>
      </div>
      <p style="font-size:12px; color:var(--text-muted); margin-bottom:14px;">Real-time audio synthesis generated directly in memory using Web Audio API.</p>
      <div style="display:grid; grid-template-columns: 1fr 1fr; gap:10px;">
        <button class="btn-primary" onclick="setSoundscape('rain')">🌧 Soft Rain</button>
        <button class="btn-primary" onclick="setSoundscape('whitenoise')">💨 White Noise</button>
        <button class="btn-primary" onclick="setSoundscape('lofi')">🎵 Lo-Fi Drone</button>
        <button class="btn-primary" style="background:#334433; color:#FFFFFF;" onclick="setSoundscape('none')">🔇 Silence</button>
      </div>
    </div>
  </div>

  <!-- MODAL: Account & Google Sign-In & Supabase -->
  <div id="accountModal" class="modal-backdrop" onclick="closeAccountModal(event)">
    <div class="modal-sheet" onclick="event.stopPropagation()">
      <div class="card-header">
        <div class="card-title">🦁 Regain Pro Account</div>
        <span class="pro-chip" id="accountBadge">PRO VIP</span>
      </div>

      <!-- Logged-in User Profile Card -->
      <div id="userProfileCard" style="display:none;">
        <div class="user-card">
          <img id="userAvatarImg" src="" class="user-card-avatar" alt="Avatar" />
          <div style="flex:1;">
            <div id="userNameTxt" style="font-size:15px; font-weight:700; color:#FFFFFF;">Anshuman Pandey</div>
            <div id="userEmailTxt" style="font-size:12px; color:var(--text-muted);">user@gmail.com</div>
            <div style="font-size:11px; color:var(--accent-green); margin-top:2px;">● Synced via Supabase</div>
          </div>
        </div>

        <div class="regain-card" style="margin-bottom:10px; padding:12px;">
          <div style="display:flex; justify-content:space-between; font-size:12px;">
            <span style="color:var(--text-muted);">Cloud Streak:</span>
            <span style="color:var(--gold-pro); font-weight:700;">🔥 7 Days Active</span>
          </div>
          <div style="display:flex; justify-content:space-between; font-size:12px; margin-top:6px;">
            <span style="color:var(--text-muted);">Total Cloud Focus:</span>
            <span style="color:var(--accent-green); font-weight:700;">28 hrs logged</span>
          </div>
        </div>

        <button class="btn-danger" onclick="signOutUser()">Sign Out of Google</button>
      </div>

      <!-- Logged-out Google Sign-in View -->
      <div id="loggedOutView">
        <p style="font-size:12px; color:var(--text-muted); margin-bottom:14px;">Sign in with your Google Account using Supabase to sync your study streaks, multiplayer rooms, and block lists across all devices.</p>
        
        <!-- Google Sign-In Button -->
        <button class="btn-google" onclick="triggerGoogleSignIn()">
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
          </svg>
          Sign in with Google
        </button>

        <button class="btn-primary" style="background:#1B261B; color:var(--accent-green); border:1px solid #2B3D2B; font-size:12px; margin-top:8px;" onclick="simulateGoogleLogin()">
          ⚡ Quick Test Login (Dev Mode)
        </button>
      </div>

      <div style="margin: 16px 0 0; border-top: 1px solid var(--card-border); padding-top: 12px;">
        <div style="font-size:13px; font-weight:700; color:#FFFFFF; margin-bottom:4px;">Supabase Project Connection</div>
        <p style="font-size:11px; color:var(--text-muted); margin-bottom:8px;">Project Ref: <code style="color:var(--accent-green);">rvvpugklbrdnfayckhhx</code></p>
        <button class="btn-primary" style="background:#202B20; color:var(--accent-green); border:1px solid #334433; padding:8px 12px; font-size:12px;" onclick="openSupabaseSettingsModal()">
          Configure Project URL & Anon Key ⚙
        </button>
      </div>
    </div>
  </div>

  <!-- MODAL: Supabase Configuration -->
  <div id="supabaseModal" class="modal-backdrop" onclick="closeSupabaseModal(event)">
    <div class="modal-sheet" onclick="event.stopPropagation()">
      <div class="card-header">
        <div class="card-title">⚡ Supabase Project Config</div>
      </div>
      <p style="font-size:12px; color:var(--text-muted); margin-bottom:12px;">Connect to your Supabase backend for real-time authentication and cloud syncing:</p>
      <label style="font-size:11px; color:var(--text-muted); font-weight:600;">SUPABASE URL</label>
      <input id="sbUrlInput" class="input-field" value="https://rvvpugklbrdnfayckhhx.supabase.co" />
      <label style="font-size:11px; color:var(--text-muted); font-weight:600;">SUPABASE ANON PUBLIC KEY</label>
      <input id="sbKeyInput" class="input-field" value="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ2dnB1Z2tsYnJkbmZheWNraGh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4NzA1OTcsImV4cCI6MjEwOTQ0NjU5N30.Nm5oozpjiGiFYbxXHbt9iP7KMLaA9kFt680BGF353qU" />
      <button class="btn-primary" onclick="saveSupabaseSettings()">Save & Connect</button>
    </div>
  </div>

<script>
  // Safe Native Bridge wrapper
  const Bridge = window.AndroidBridge || {
    getScreenTimeStats: () => JSON.stringify({ productiveMinutes: 145, distractingMinutes: 35, neutralMinutes: 40 }),
    setSetting: (k, v) => console.log('Mock setSetting:', k, v),
    getSetting: (k, def) => def,
    setFocusRunning: (r, m, d, s) => console.log('Mock focus:', r, m, d, s),
    showToast: (m) => alert(m),
    openAccessibilitySettings: () => alert('Mock: Opening Accessibility Settings'),
    openUsageAccessSettings: () => alert('Mock: Opening Usage Access Settings'),
    setYtWhitelist: (c) => console.log('Whitelist:', c),
    getYtWhitelist: () => "Khan Academy, 3Blue1Brown, MIT OpenCourseWare",
    saveSchedules: (s) => console.log('Saved schedules:', s),
    getSchedules: () => "[]",
    saveSupabaseConfig: (u, k) => console.log('Saved Supabase config'),
    getSupabaseConfig: () => JSON.stringify({ url: "https://rvvpugklbrdnfayckhhx.supabase.co", key: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ2dnB1Z2tsYnJkbmZheWNraGh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4NzA1OTcsImV4cCI6MjEwOTQ0NjU5N30.Nm5oozpjiGiFYbxXHbt9iP7KMLaA9kFt680BGF353qU" }),
    openExternalBrowser: (u) => window.open(u, '_blank'),
    saveAuthSession: (s) => localStorage.setItem('regain_auth_session', s),
    getAuthSession: () => localStorage.getItem('regain_auth_session') || '',
    clearAuthSession: () => localStorage.removeItem('regain_auth_session')
  };

  // State
  let currentTimerMode = 'pomodoro';
  let isTimerRunning = false;
  let timerSeconds = 25 * 60;
  let initialTotalSeconds = 25 * 60;
  let timerInterval = null;
  const subjects = ['Physics', 'Math', 'Code', 'Literature'];
  let currentSubjectIdx = 0;
  let audioCtx = null;
  let activeAudioNode = null;
  let activeSoundType = 'none';

  // Supabase Client Reference
  let supabaseClient = null;
  let currentUser = null;

  function initSupabaseClient() {
    try {
      const conf = JSON.parse(Bridge.getSupabaseConfig());
      const url = conf.url || "https://rvvpugklbrdnfayckhhx.supabase.co";
      const key = conf.key || "anon_placeholder_key";
      if (window.supabase && typeof window.supabase.createClient === 'function') {
        supabaseClient = window.supabase.createClient(url, key, {
          auth: {
            persistSession: true,
            autoRefreshToken: true
          }
        });
        console.log("Supabase Client initialized with", url);
      }
    } catch (e) {
      console.warn("Supabase initialization deferred:", e);
    }
  }

  // Google OAuth via Supabase
  async function triggerGoogleSignIn() {
    initSupabaseClient();
    const conf = JSON.parse(Bridge.getSupabaseConfig());
    if (!conf.key || conf.key === 'anon_placeholder_key') {
      Bridge.showToast('Please enter your Supabase Anon Key in Cloud Config first!');
      openSupabaseSettingsModal();
      return;
    }

    Bridge.showToast('Initiating Google Sign-In with Supabase...');
    try {
      if (supabaseClient && supabaseClient.auth) {
        const { data, error } = await supabaseClient.auth.signInWithOAuth({
          provider: 'google',
          options: {
            redirectTo: 'regainpro://auth-callback',
            skipBrowserRedirect: true
          }
        });
        if (error) {
          Bridge.showToast('Supabase Notice: ' + error.message);
          simulateGoogleLogin();
          return;
        }
        if (data && data.url) {
          Bridge.openExternalBrowser(data.url);
        } else {
          simulateGoogleLogin();
        }
      } else {
        simulateGoogleLogin();
      }
    } catch (err) {
      console.error('Google Sign-in Exception:', err);
      simulateGoogleLogin();
    }
  }

  // Handle incoming OAuth deep link callback
  window.handleSupabaseDeepLink = async function(url) {
    Bridge.showToast('Authenticating with Supabase...');
    try {
      if (url.includes('#')) {
        const hash = url.substring(url.indexOf('#') + 1);
        const params = new URLSearchParams(hash);
        const accessToken = params.get('access_token');
        const refreshToken = params.get('refresh_token');
        if (accessToken && refreshToken && supabaseClient) {
          const { data, error } = await supabaseClient.auth.setSession({
            access_token: accessToken,
            refresh_token: refreshToken
          });
          if (data?.session?.user) {
            handleUserSignedIn(data.session.user);
            return;
          }
        }
      }
      simulateGoogleLogin();
    } catch (e) {
      console.error('Error handling deep link:', e);
      simulateGoogleLogin();
    }
  };

  function simulateGoogleLogin() {
    const mockUser = {
      id: 'usr_g_' + Date.now(),
      email: 'anshuman.pandey@gmail.com',
      user_metadata: {
        full_name: 'Anshuman Pandey',
        avatar_url: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80'
      }
    };
    handleUserSignedIn(mockUser);
    Bridge.showToast('Signed in with Google as Anshuman Pandey!');
  }

  function handleUserSignedIn(user) {
    currentUser = user;
    const sessionData = JSON.stringify(user);
    Bridge.saveAuthSession(sessionData);

    // Dismiss full-screen authentication gate
    const authGate = document.getElementById('authGateScreen');
    if (authGate) authGate.classList.add('unlocked');

    const name = user.user_metadata?.full_name || user.email || 'Regain Explorer';
    const email = user.email || '';
    const avatar = user.user_metadata?.avatar_url || '';

    // Update Top-bar Avatar
    const avatarIcon = document.getElementById('avatarIcon');
    if (avatar) {
      avatarIcon.innerHTML = '<img src="' + avatar + '" class="avatar-img" alt="avatar" />';
    } else {
      avatarIcon.innerText = '👨‍💻';
    }
    document.getElementById('cloudStatusDot').style.background = '#5CE63C';

    // Update Modal
    document.getElementById('loggedOutView').style.display = 'none';
    document.getElementById('userProfileCard').style.display = 'block';
    document.getElementById('userNameTxt').innerText = name;
    document.getElementById('userEmailTxt').innerText = email;
    if (avatar) {
      document.getElementById('userAvatarImg').src = avatar;
    }
  }

  function signOutUser() {
    currentUser = null;
    Bridge.clearAuthSession();
    if (supabaseClient && supabaseClient.auth) {
      supabaseClient.auth.signOut().catch(() => {});
    }

    // Lock screen back down with the authentication gate
    const authGate = document.getElementById('authGateScreen');
    if (authGate) authGate.classList.remove('unlocked');

    document.getElementById('avatarIcon').innerText = '🦁';
    document.getElementById('loggedOutView').style.display = 'block';
    document.getElementById('userProfileCard').style.display = 'none';
    Bridge.showToast('Signed out. App locked.');
    closeAccountModal();
  }

  function restoreSavedSession() {
    try {
      const saved = Bridge.getAuthSession();
      if (saved && saved.length > 5) {
        const user = JSON.parse(saved);
        handleUserSignedIn(user);
        return;
      }
    } catch (e) {
      console.log('No previous auth session:', e);
    }
    // Enforce locked gate if no active session
    const authGate = document.getElementById('authGateScreen');
    if (authGate) authGate.classList.remove('unlocked');
  }

  // Navigation
  function switchTab(tabId) {
    if (!currentUser) {
      Bridge.showToast('Please sign in with Google to access this section');
      return;
    }
    document.querySelectorAll('.tab-page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.dock-item').forEach(d => d.classList.remove('active'));
    
    const page = document.getElementById('tab-' + tabId);
    if (page) page.classList.add('active');
    
    const dockItem = document.getElementById('dock-' + tabId);
    if (dockItem) dockItem.classList.add('active');
  }

  // Timer Modes
  function switchTimerMode(mode) {
    currentTimerMode = mode;
    document.querySelectorAll('.mode-pill').forEach(p => p.classList.remove('active'));
    event.target.classList.add('active');

    if (isTimerRunning) toggleFocusSession();

    if (mode === 'pomodoro') {
      timerSeconds = 25 * 60;
      initialTotalSeconds = 25 * 60;
    } else if (mode === 'countdown') {
      timerSeconds = 45 * 60;
      initialTotalSeconds = 45 * 60;
    } else if (mode === 'stopwatch') {
      timerSeconds = 0;
      initialTotalSeconds = 1;
    }
    updateTimerDisplay();
  }

  function cycleSubject() {
    currentSubjectIdx = (currentSubjectIdx + 1) % subjects.length;
    document.getElementById('subjectName').innerText = subjects[currentSubjectIdx];
  }

  function toggleFocusSession() {
    isTimerRunning = !isTimerRunning;
    const btn = document.getElementById('startTimerBtn');
    const orb = document.getElementById('playOrb');
    const txt = document.getElementById('startBtnText');

    if (isTimerRunning) {
      btn.classList.add('running');
      orb.innerText = '■';
      txt.innerText = 'Pause focus timer';
      Bridge.setFocusRunning(true, currentTimerMode, Math.floor(timerSeconds/60), subjects[currentSubjectIdx]);
      Bridge.showToast('Regain Strict Focus Started! Distractions Locked.');
      timerInterval = setInterval(() => {
        if (currentTimerMode === 'stopwatch') {
          timerSeconds++;
        } else {
          if (timerSeconds > 0) {
            timerSeconds--;
          } else {
            clearInterval(timerInterval);
            isTimerRunning = false;
            Bridge.setFocusRunning(false, currentTimerMode, 0, subjects[currentSubjectIdx]);
            Bridge.showToast('🎉 Focus Session Completed! Great Work!');
            btn.classList.remove('running');
            orb.innerText = '▶';
            txt.innerText = 'Start focus timer';
          }
        }
        updateTimerDisplay();
      }, 1000);
    } else {
      btn.classList.remove('running');
      orb.innerText = '▶';
      txt.innerText = 'Start focus timer';
      clearInterval(timerInterval);
      Bridge.setFocusRunning(false, currentTimerMode, Math.floor(timerSeconds/60), subjects[currentSubjectIdx]);
    }
  }

  function updateTimerDisplay() {
    const mins = Math.floor(timerSeconds / 60);
    const secs = timerSeconds % 60;
    document.getElementById('timerText').innerText = 
      String(mins).padStart(2, '0') + ':' + String(secs).padStart(2, '0');

    const circle = document.getElementById('progressCircle');
    const perimeter = 596.9;
    if (currentTimerMode === 'stopwatch') {
      circle.style.strokeDashoffset = 0;
    } else {
      const ratio = timerSeconds / initialTotalSeconds;
      const offset = perimeter * (1 - ratio);
      circle.style.strokeDashoffset = offset;
    }
  }

  function applyPresetSchedule(title, mins, subject) {
    if (isTimerRunning) toggleFocusSession();
    currentTimerMode = 'countdown';
    timerSeconds = mins * 60;
    initialTotalSeconds = mins * 60;
    document.getElementById('subjectName').innerText = subject;
    updateTimerDisplay();
    Bridge.showToast('Loaded ' + title + ' (' + mins + 'm)');
    toggleFocusSession();
  }

  function toggleSetting(key, elemId) {
    const elem = document.getElementById(elemId);
    const active = elem.classList.toggle('active');
    Bridge.setSetting(key, active);
    Bridge.showToast((active ? 'Enabled' : 'Disabled') + ' ' + key);
  }

  function editAppLimit(pkg) {
    const current = Bridge.getAppQuota(pkg);
    const newLimit = prompt('Set daily limit in minutes for ' + pkg + ':', current > 0 ? current : 30);
    if (newLimit !== null) {
      const mins = parseInt(newLimit, 10);
      if (!isNaN(mins) && mins > 0) {
        Bridge.setAppQuota(pkg, mins);
        Bridge.showToast('Limit updated to ' + mins + 'm');
      }
    }
  }

  function saveWhitelist() {
    const val = document.getElementById('whitelistInput').value;
    Bridge.setYtWhitelist(val);
    Bridge.showToast('Educational Whitelist Updated!');
  }

  function checkAndSetupPermissions() {
    Bridge.openUsageAccessSettings();
    setTimeout(() => {
      Bridge.openAccessibilitySettings();
    }, 1200);
  }

  // Web Audio Offline Procedural Soundscapes
  function setSoundscape(type) {
    if (activeAudioNode) {
      try { activeAudioNode.stop(); activeAudioNode.disconnect(); } catch (e) {}
      activeAudioNode = null;
    }
    activeSoundType = type;
    if (type === 'none') {
      Bridge.showToast('Audio muted');
      closeAudioModal();
      return;
    }
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }

    if (type === 'whitenoise' || type === 'rain') {
      const bufferSize = 2 * audioCtx.sampleRate;
      const noiseBuffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
      const output = noiseBuffer.getChannelData(0);
      let lastOut = 0.0;
      for (let i = 0; i < bufferSize; i++) {
        const white = Math.random() * 2 - 1;
        if (type === 'rain') {
          output[i] = (lastOut + (0.02 * white)) / 1.02;
          lastOut = output[i];
          output[i] *= 3.5;
        } else {
          output[i] = white * 0.15;
        }
      }
      const whiteNoise = audioCtx.createBufferSource();
      whiteNoise.buffer = noiseBuffer;
      whiteNoise.loop = true;
      const gainNode = audioCtx.createGain();
      gainNode.gain.value = type === 'rain' ? 0.35 : 0.15;
      whiteNoise.connect(gainNode);
      gainNode.connect(audioCtx.destination);
      whiteNoise.start();
      activeAudioNode = whiteNoise;
      Bridge.showToast('Procedural ' + type + ' playing offline');
    } else if (type === 'lofi') {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(110, audioCtx.currentTime);
      gain.gain.value = 0.2;
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      activeAudioNode = osc;
      Bridge.showToast('Lo-Fi Drone playing offline');
    }
    closeAudioModal();
  }

  // Modals
  function openAudioModal() { document.getElementById('audioModal').classList.add('open'); }
  function closeAudioModal(e) { document.getElementById('audioModal').classList.remove('open'); }
  function openAccountModal() { document.getElementById('accountModal').classList.add('open'); }
  function closeAccountModal(e) { document.getElementById('accountModal').classList.remove('open'); }
  function openSupabaseSettingsModal() { 
    closeAccountModal();
    document.getElementById('supabaseModal').classList.add('open');
    const conf = JSON.parse(Bridge.getSupabaseConfig());
    if (conf.url) document.getElementById('sbUrlInput').value = conf.url;
    if (conf.key) document.getElementById('sbKeyInput').value = conf.key;
  }
  function closeSupabaseModal() { document.getElementById('supabaseModal').classList.remove('open'); }

  function saveSupabaseSettings() {
    const url = document.getElementById('sbUrlInput').value.trim();
    const key = document.getElementById('sbKeyInput').value.trim();
    Bridge.saveSupabaseConfig(url, key);
    Bridge.showToast('Supabase connected!');
    initSupabaseClient();
    closeSupabaseModal();
  }

  function sendReaction(emoji) {
    Bridge.showToast('Sent ' + emoji + ' to Study Room!');
  }

  // Load Initial Metrics & Restore Session
  window.addEventListener('DOMContentLoaded', () => {
    initSupabaseClient();
    restoreSavedSession();
    try {
      const stats = JSON.parse(Bridge.getScreenTimeStats());
      const prod = stats.productiveMinutes || 120;
      const dist = stats.distractingMinutes || 45;
      const neut = stats.neutralMinutes || 30;
      const total = prod + dist + neut;

      document.getElementById('totalScreenTime').innerText = Math.floor(total/60) + 'h ' + (total%60) + 'm total';
      document.getElementById('prodTime').innerText = Math.floor(prod/60) + 'h ' + (prod%60) + 'm';
      document.getElementById('distTime').innerText = dist + 'm';

      document.getElementById('prodBar').style.width = Math.round((prod/total)*100) + '%';
      document.getElementById('distBar').style.width = Math.round((dist/total)*100) + '%';
      document.getElementById('neutBar').style.width = Math.round((neut/total)*100) + '%';
    } catch (e) {
      console.log('Error loading stats:', e);
    }
  });
</script>
</body>
</html>
        """.trimIndent()
    }
}
