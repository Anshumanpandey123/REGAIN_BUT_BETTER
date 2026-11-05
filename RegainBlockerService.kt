package com.regain.pro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Calendar

class RegainBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastContentCheckTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastBlockedPkg = ""
    private var lastBlockedTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)

        // Configure accessibility service dynamically to maintain clean Play Protect compliance
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT
                notificationTimeout = 100
            }
            serviceInfo = info
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return

        // 1. Strict Financial App Firewall: Instantly ignore banking, payment, wallet, crypto & authenticator apps
        if (isSensitiveFinancialPackage(pkg)) {
            return
        }

        // 2. Ignore Regain Pro itself, System UI, Soft Keyboards, and Home Screen Launchers
        if (pkg == packageName || pkg.contains("systemui") || pkg.contains("inputmethod") || isLauncherPackage(pkg)) {
            return
        }

        val now = System.currentTimeMillis()
        val isWindowState = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // 3. 350ms Event Throttling for content updates to ensure ZERO CPU lag and zero battery drain
        if (!isWindowState && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheckTime < 350) {
                return
            }
            lastContentCheckTime = now
        }

        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)
        }

        val focusActive = isFocusOrScheduleActive()
        val strictMode = prefs.getBoolean("strict_active", false) || focusActive

        // ====================================================================
        // 4. CORE FOCUS BLOCKER: Restrict all distracting apps when focus session is running
        // ====================================================================
        if (focusActive && isDistractingApp(pkg)) {
            // Prevent duplicate rapid-fire re-triggers for the same app within 800ms
            if (pkg != lastBlockedPkg || (now - lastBlockedTime) > 800) {
                lastBlockedPkg = pkg
                lastBlockedTime = now
                val appLabel = getAppLabel(pkg)
                triggerBlockPopup(pkg, appLabel, "Focus Session is in progress! $appLabel is blocked to keep your concentration.")
            }
            return
        }

        // ====================================================================
        // 5. DAILY APP LIMIT QUOTA CHECK: Restrict apps that exceeded user daily limit
        // ====================================================================
        val limitMinutes = prefs.getInt("limit_$pkg", -1)
        if (limitMinutes > 0 && checkAppDailyQuotaExceeded(pkg, limitMinutes)) {
            if (pkg != lastBlockedPkg || (now - lastBlockedTime) > 800) {
                lastBlockedPkg = pkg
                lastBlockedTime = now
                val appLabel = getAppLabel(pkg)
                triggerBlockPopup(pkg, appLabel, "Daily time limit of ${limitMinutes}m reached for $appLabel. Great discipline!")
            }
            return
        }

        // ====================================================================
        // 6. EXPLICIT INDIVIDUAL APP BLOCK: Apps manually blocked in Blocks Tab
        // ====================================================================
        if (prefs.getBoolean("block_app_$pkg", false)) {
            if (pkg != lastBlockedPkg || (now - lastBlockedTime) > 800) {
                lastBlockedPkg = pkg
                lastBlockedTime = now
                val appLabel = getAppLabel(pkg)
                triggerBlockPopup(pkg, appLabel, "$appLabel is locked in your Regain Pro Block List.")
            }
            return
        }

        // ====================================================================
        // 7. PRO: Strict Mode Protection (Block Uninstall & Settings Bypass)
        // ====================================================================
        if (prefs.getBoolean("strict_uninstall", true) && strictMode) {
            if (pkg.contains("packageinstaller") || pkg.contains("settings")) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        if (containsText(root, "Regain Pro", 0)) {
                            triggerBlockPopup(pkg, "Settings", "Strict Mode Active: Uninstalling Regain Pro is locked until session finishes!")
                            return
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
        }

        // ====================================================================
        // 8. PRO: Split-Screen & Floating Window Interception
        // ====================================================================
        if (strictMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (prefs.getBoolean("strict_split", true)) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        if (containsSplitOrFloatingDivider(root, 0)) {
                            triggerBlockPopup(pkg, "Split Screen", "Strict Mode Active: Split-screen & floating windows are disabled during focus!")
                            return
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
        }

        // ====================================================================
        // 9. GRANULAR FEED BLOCKER: YouTube Shorts, Instagram Reels, FB Reels, Snapchat
        // ====================================================================
        handleGranularSocialInterception(pkg)

        // ====================================================================
        // 10. BROWSER DOMAIN FILTERING: Block distracting websites in Chrome
        // ====================================================================
        if (isBrowserPackage(pkg)) {
            handleBrowserFiltering()
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    /**
     * Triggers the Block Action:
     * 1. Calls GLOBAL_ACTION_HOME immediately to kick the user out of the distracting app.
     * 2. Brings Regain Pro's Blocked App Popup to the foreground.
     */
    private fun triggerBlockPopup(pkg: String, appLabel: String, reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            action = "ACTION_SHOW_BLOCK_POPUP"
            putExtra("BLOCKED_PKG", pkg)
            putExtra("BLOCKED_APP_NAME", appLabel)
            putExtra("BLOCK_REASON", reason)
            putExtra("BLOCK_TIMESTAMP", System.currentTimeMillis())
        }

        mainHandler.postDelayed({
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 120)
    }

    private fun isFocusOrScheduleActive(): Boolean {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)
        }
        val isTimerRunning = prefs.getBoolean("is_focus_running", false)
        if (isTimerRunning) return true

        // Check active schedule time window
        val schedulesJson = prefs.getString("active_schedules_json", "[]") ?: "[]"
        if (schedulesJson.contains("\"active\":true") || schedulesJson.contains("true")) {
            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val scheduleStart = prefs.getInt("sched_start_min", -1)
            val scheduleEnd = prefs.getInt("sched_end_min", -1)
            if (scheduleStart in 0..scheduleEnd) {
                if (currentMinutes in scheduleStart..scheduleEnd) return true
            }
        }
        return false
    }

    /**
     * Checks whether an app is categorized as distracting.
     * Checks user custom classification first, then default distracting set, then custom list.
     */
    private fun isDistractingApp(pkg: String): Boolean {
        // 1. User manual classification takes absolute priority
        val userCategory = prefs.getString("app_category_$pkg", null)
        if (userCategory == "distracting") return true
        if (userCategory == "productive" || userCategory == "neutral") return false

        // 2. Default known distracting applications
        val defaultDistracting = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.twitter.android",
            "com.twitter.android.lite",
            "com.reddit.frontpage",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "tv.twitch.android.app",
            "com.discord",
            "com.pinterest",
            "com.whatsapp"
        )

        if (defaultDistracting.contains(pkg)) {
            return true
        }

        // 3. Custom distracting app list from SharedPreferences
        val customDistracting = prefs.getStringSet("custom_distracting_packages", emptySet()) ?: emptySet()
        if (customDistracting.contains(pkg)) {
            return true
        }

        return false
    }

    private fun checkAppDailyQuotaExceeded(pkg: String, limitMinutes: Int): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
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
        ) ?: return false

        for (u in stats) {
            if (u.packageName == pkg) {
                val usedMinutes = (u.totalTimeInForeground / 60000).toInt()
                if (usedMinutes >= limitMinutes) {
                    return true
                }
            }
        }
        return false
    }

    private fun handleGranularSocialInterception(pkg: String) {
        val blockYtShorts = prefs.getBoolean("block_yt_shorts", true)
        val blockIgReels = prefs.getBoolean("block_ig_reels", true)
        val blockFbReels = prefs.getBoolean("block_fb_reels", true)
        val blockSnapSpotlight = prefs.getBoolean("block_snap_spotlight", true)
        val stricterInterception = prefs.getBoolean("strict_feed", true)

        val root = rootInActiveWindow ?: return
        try {
            when {
                // YouTube Shorts Interception
                pkg == "com.google.android.youtube" && blockYtShorts -> {
                    if (isYouTubeAllowedChannel(root)) return
                    if (findNodeByIdSubstrings(root, listOf("reel_recycler", "reel_watch_fragment_root", "shorts_container", "shorts_player"), 0)) {
                        fireInterceptionAction(stricterInterception, "YouTube Shorts blocked. Stay focused on learning!")
                    }
                }
                // Instagram Reels Interception
                pkg == "com.instagram.android" && blockIgReels -> {
                    if (findNodeByIdSubstrings(root, listOf("clips_viewer_view_pager", "clips_video_container", "reel_viewer"), 0)) {
                        fireInterceptionAction(stricterInterception, "Instagram Reels blocked. Keep your study streak!")
                    }
                }
                // Facebook Reels Interception
                pkg.contains("facebook") && blockFbReels -> {
                    if (findNodeByIdSubstrings(root, listOf("fb_shorts", "reels_tray", "reel_viewer"), 0)) {
                        fireInterceptionAction(stricterInterception, "Facebook Reels blocked!")
                    }
                }
                // Snapchat Spotlight Interception
                pkg.contains("snapchat") && blockSnapSpotlight -> {
                    if (findNodeByIdSubstrings(root, listOf("spotlight_view", "spotlight_recycler"), 0)) {
                        fireInterceptionAction(stricterInterception, "Snapchat Spotlight blocked!")
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun isYouTubeAllowedChannel(root: AccessibilityNodeInfo): Boolean {
        val whitelistString = prefs.getString("yt_whitelist_channels", "") ?: ""
        if (whitelistString.isBlank()) return false

        val channels = whitelistString.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (channels.isEmpty()) return false

        return checkNodeMatchesAnyText(root, channels, 0)
    }

    private fun isSensitiveFinancialPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("bank") || lower.contains("pay") || lower.contains("wallet") ||
               lower.contains("money") || lower.contains("credit") || lower.contains("crypto") ||
               lower.contains("finance") || lower.contains("authenticator") || lower.contains("pass") ||
               lower.contains("security") || lower.contains("card") || lower.contains("invest") ||
               lower.contains("upi") || lower.contains("paypal")
    }

    private fun isBrowserPackage(pkg: String): Boolean {
        return pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
               pkg.contains("opera") || pkg.contains("brave") || pkg.contains("edge")
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.contains("home") || lower == "android" ||
               lower == "com.google.android.apps.nexuslauncher" || lower == "com.miui.home" ||
               lower == "com.sec.android.app.launcher" || lower == "com.oppo.launcher" ||
               lower == "com.oneplus.launcher" || lower == "com.huawei.android.launcher"
    }

    private fun handleBrowserFiltering() {
        val blockWebsites = prefs.getBoolean("block_websites", true)
        if (!blockWebsites) return

        val root = rootInActiveWindow ?: return
        try {
            val urlBarNodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            if (urlBarNodes.isEmpty()) {
                val fallbackList = root.findAccessibilityNodeInfosByViewId("url_bar")
                inspectUrlNodes(fallbackList)
            } else {
                inspectUrlNodes(urlBarNodes)
            }
        } finally {
            root.recycle()
        }
    }

    private fun inspectUrlNodes(nodes: List<AccessibilityNodeInfo>?) {
        if (nodes == null) return
        val blockedDomains = listOf("twitter.com", "x.com", "tiktok.com", "instagram.com", "reddit.com", "netflix.com", "facebook.com")
        for (node in nodes) {
            val text = node.text?.toString()?.lowercase() ?: ""
            node.recycle()
            for (blocked in blockedDomains) {
                if (text.contains(blocked)) {
                    triggerBlockPopup("com.android.chrome", "Chrome", "Domain $blocked is restricted during focus!")
                    return
                }
            }
        }
    }

    private fun fireInterceptionAction(stricter: Boolean, msg: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (stricter) {
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 100)
        }
        showToast(msg)
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("youtube") -> "YouTube"
                pkg.contains("instagram") -> "Instagram"
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("facebook") -> "Facebook"
                pkg.contains("snapchat") -> "Snapchat"
                pkg.contains("tiktok") -> "TikTok"
                pkg.contains("twitter") -> "Twitter / X"
                pkg.contains("reddit") -> "Reddit"
                pkg.contains("chrome") -> "Google Chrome"
                else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun containsText(node: AccessibilityNodeInfo?, target: String, depth: Int): Boolean {
        if (node == null || depth > 5) return false
        val text = node.text?.toString()
        if (text != null && text.contains(target, ignoreCase = true)) return true
        val desc = node.contentDescription?.toString()
        if (desc != null && desc.contains(target, ignoreCase = true)) return true

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val found = containsText(child, target, depth + 1)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    private fun checkNodeMatchesAnyText(node: AccessibilityNodeInfo?, list: List<String>, depth: Int): Boolean {
        if (node == null || depth > 4) return false
        val text = node.text?.toString()?.lowercase()
        if (text != null && list.any { text.contains(it) }) return true
        val desc = node.contentDescription?.toString()?.lowercase()
        if (desc != null && list.any { desc.contains(it) }) return true

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val matched = checkNodeMatchesAnyText(child, list, depth + 1)
                child.recycle()
                if (matched) return true
            }
        }
        return false
    }

    private fun findNodeByIdSubstrings(node: AccessibilityNodeInfo?, substrings: List<String>, depth: Int): Boolean {
        if (node == null || depth > 6) return false
        val viewId = node.viewIdResourceName?.lowercase()
        if (viewId != null && substrings.any { viewId.contains(it) }) return true

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByIdSubstrings(child, substrings, depth + 1)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    private fun containsSplitOrFloatingDivider(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 4) return false
        val viewId = node.viewIdResourceName?.lowercase()
        if (viewId != null && (viewId.contains("docked_divider") || viewId.contains("pip_menu") || viewId.contains("floating_overlay"))) {
            return true
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val found = containsSplitOrFloatingDivider(child, depth + 1)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
