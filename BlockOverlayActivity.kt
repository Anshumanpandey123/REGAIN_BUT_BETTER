package com.regainpro.personal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private lateinit var timerText: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("RegainProPrefs", Context.MODE_PRIVATE)

        // Make activity fullscreen, immersive, and stay on top
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
            statusBarColor = Color.parseColor("#0C0606")
            navigationBarColor = Color.parseColor("#0C0606")
        }

        val blockedPkg = intent.getStringExtra("BLOCKED_PKG") ?: ""
        val blockedAppName = intent.getStringExtra("BLOCKED_APP_NAME") ?: "This App"
        val blockReason = intent.getStringExtra("BLOCK_REASON") ?: "Focus session in progress"

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0E0B"))
            setPadding(48, 80, 48, 60)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Block Shield Orb
        val orb = TextView(this).apply {
            text = "🚫"
            textSize = 40f
            gravity = Gravity.CENTER
            val orbSize = (84 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(orbSize, orbSize).apply {
                bottomMargin = (20 * resources.displayMetrics.density).toInt()
            }
        }
        rootLayout.addView(orb)

        // 2. Alert Badge
        val badge = TextView(this).apply {
            text = "FOCUS RESTRICTION ACTIVE"
            textSize = 11f
            setTextColor(Color.parseColor("#FF5252"))
            paint.isFakeBoldText = true
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }
        rootLayout.addView(badge)

        // 3. App Title
        val title = TextView(this).apply {
            text = "$blockedAppName is Blocked"
            textSize = 24f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }
        rootLayout.addView(title)

        // 4. Subtitle / Reason
        val sub = TextView(this).apply {
            text = blockReason
            textSize = 13f
            setTextColor(Color.parseColor("#9EAFA1"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (32 * resources.displayMetrics.density).toInt()
            }
        }
        rootLayout.addView(sub)

        // 5. Timer Container
        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#151D15"))
            setPadding(40, 30, 40, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (40 * resources.displayMetrics.density).toInt()
            }
        }

        val timerLabel = TextView(this).apply {
            text = "TIME REMAINING IN FOCUS"
            textSize = 11f
            setTextColor(Color.parseColor("#8E9E8E"))
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        timerCard.addView(timerLabel)

        timerText = TextView(this).apply {
            text = "25:00"
            textSize = 42f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        timerCard.addView(timerText)

        val motivational = TextView(this).apply {
            text = "🔥 Stay disciplined. You can do this!"
            textSize = 12f
            setTextColor(Color.parseColor("#5CE63C"))
            gravity = Gravity.CENTER
        }
        timerCard.addView(motivational)

        rootLayout.addView(timerCard)

        // Spacer
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
            )
        }
        rootLayout.addView(spacer)

        // 6. Primary Action: Back to Home Screen
        val btnHome = Button(this).apply {
            text = "Go to Home Screen"
            textSize = 15f
            setTextColor(Color.parseColor("#081208"))
            setBackgroundColor(Color.parseColor("#5CE63C"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * resources.displayMetrics.density).toInt()
            ).apply {
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                goToHome()
            }
        }
        rootLayout.addView(btnHome)

        // 7. Secondary Action: Open Regain Pro
        val btnRegain = Button(this).apply {
            text = "Open Regain Pro Dashboard"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B261B"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            )
            setOnClickListener {
                val intent = Intent(this@BlockOverlayActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }
        rootLayout.addView(btnRegain)

        setContentView(rootLayout)

        startTimerUpdater()
    }

    private fun startTimerUpdater() {
        timerRunnable = object : Runnable {
            override fun run() {
                val isRunning = prefs.getBoolean("is_focus_running", false)
                if (!isRunning) {
                    finish()
                    return
                }

                val durationMins = prefs.getInt("focus_duration_mins", 25)
                val startTime = prefs.getLong("focus_start_time", System.currentTimeMillis())
                val totalMillis = durationMins * 60 * 1000L
                val elapsed = System.currentTimeMillis() - startTime
                val remainingMillis = totalMillis - elapsed

                if (remainingMillis <= 0) {
                    timerText.text = "00:00"
                    finish()
                } else {
                    val remainingSecs = remainingMillis / 1000
                    val mins = remainingSecs / 60
                    val secs = remainingSecs % 60
                    timerText.text = String.format("%02d:%02d", mins, secs)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    override fun onBackPressed() {
        // Intercept back button: NEVER return to the blocked app! Always navigate to Android home launcher.
        goToHome()
    }

    private fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
    }
}
