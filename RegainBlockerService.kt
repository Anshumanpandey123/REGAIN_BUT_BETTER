package com.regain.pro

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Calendar

class RegainBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastCheckTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        // 1. Zero-Lag 350ms Event Throttling for window content updates
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && (now - lastCheckTime) < 350) {
            return
        }
        lastCheckTime = now

        val pkg = event.packageName?.toString() ?: return

        // 2. O(1) Package Filtering - Ignore own app, SystemUI, and soft keyboards immediately
        if (pkg == packageName || pkg.contains("systemui") || pkg.contains("inputmethod")) {
            return
        }

        val focusActive = isFocusOrScheduleActive()
        val strictMode = prefs.getBoolean("strict_active", false) || focusActive

        // 3. PRO: Active Session Uninstall & Permission Revoke Prevention
        if (prefs.getBoolean("strict_uninstall", true) && strictMode) {
            if (pkg.contains("packageinstaller") || pkg.contains("settings")) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        if (containsText(root, "Regain Pro", 0)) {
                            triggerRedirect("Strict Mode Active: Uninstall & permission changes are locked!")
                            return
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
        }

        // 4. PRO: Split-Screen & Floating Window Interception
        if (strictMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (prefs.getBoolean("strict_split", true)) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        if (containsSplitOrFloatingDivider(root, 0)) {
                            triggerRedirect("Strict Mode Active: Split-screen & floating windows are disabled!")
                            return
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
        }

        // 5. Zero-Bypass Daily App Quotas (UsageStatsManager)
        if (checkAppDailyQuotaExceeded(pkg)) {
            triggerRedirect("Daily limit exceeded for this app. Stay focused!")
            return
        }

        // 6. Granular Feed & Social Blocker (YouTube Shorts, IG Reels, FB Reels, Snapchat Spotlight)
        handleGranularSocialInterception(pkg)

        // 7. Browser Domain Filtering & Whitelist
        if (isBrowserPackage(pkg)) {
            handleBrowserFiltering()
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    private fun isFocusOrScheduleActive(): Boolean {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)
        }
        val isTimerRunning = prefs.getBoolean("is_focus_running", false)
        if (isTimerRunning) return true

        // Check active schedule window
        val scheduleJson = prefs.getString("active_schedules_json", "[]") ?: "[]"
        if (scheduleJson.contains("true")) {
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

    private fun checkAppDailyQuotaExceeded(pkg: String): Boolean {
        val limitMinutes = prefs.getInt("limit_$pkg", -1)
        if (limitMinutes <= 0) return false

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
                    // Check creator whitelist first
                    if (isYouTubeAllowedChannel(root)) {
                        return
                    }
                    if (findNodeByIdSubstrings(root, listOf("reel_recycler", "reel_watch_fragment_root", "shorts_container", "shorts_player"), 0)) {
                        fireInterceptionAction(stricterInterception, "YouTube Shorts blocked. Back to learning!")
                    }
                }
                // Instagram Reels Interception
                pkg == "com.instagram.android" && blockIgReels -> {
                    if (findNodeByIdSubstrings(root, listOf("clips_viewer_view_pager", "clips_video_container", "reel_viewer"), 0)) {
                        fireInterceptionAction(stricterInterception, "Instagram Reels blocked. Keep your focus!")
                    }
                }
                // Facebook Reels
                pkg.contains("facebook") && blockFbReels -> {
                    if (findNodeByIdSubstrings(root, listOf("fb_shorts", "reels_tray", "reel_viewer"), 0)) {
                        fireInterceptionAction(stricterInterception, "Facebook Reels blocked!")
                    }
                }
                // Snapchat Spotlight
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

    private fun isBrowserPackage(pkg: String): Boolean {
        return pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
               pkg.contains("opera") || pkg.contains("brave") || pkg.contains("edge")
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
                    triggerRedirect("Domain $blocked is blocked during focus!")
                    return
                }
            }
        }
    }

    private fun fireInterceptionAction(stricter: Boolean, msg: String) {
        showToast(msg)
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (stricter) {
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 100)
        }
    }

    private fun triggerRedirect(message: String) {
        showToast(message)
        performGlobalAction(GLOBAL_ACTION_HOME)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("ALERT_MESSAGE", message)
        }
        if (launchIntent != null) {
            mainHandler.postDelayed({
                startActivity(launchIntent)
            }, 150)
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Explicit recursive traversal with depth limiting to preserve CPU and zero-lag performance
    private fun containsText(node: AccessibilityNodeInfo, query: String, depth: Int): Boolean {
        if (depth > 6) return false
        val text = node.text?.toString() ?: ""
        if (text.contains(query, ignoreCase = true)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (containsText(child, query, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun checkNodeMatchesAnyText(node: AccessibilityNodeInfo, allowed: List<String>, depth: Int): Boolean {
        if (depth > 6) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        for (a in allowed) {
            if (text.contains(a)) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (checkNodeMatchesAnyText(child, allowed, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun findNodeByIdSubstrings(node: AccessibilityNodeInfo, identifiers: List<String>, depth: Int): Boolean {
        if (depth > 6) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        for (id in identifiers) {
            if (viewId.contains(id)) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findNodeByIdSubstrings(child, identifiers, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun containsSplitOrFloatingDivider(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 4) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("docked_divider") || viewId.contains("pip_menu") || viewId.contains("split_screen_divider")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (containsSplitOrFloatingDivider(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }
}
