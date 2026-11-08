package com.saeed.angela

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** অ্যাপ আর ভাসমান বাবলের মধ্যে যোগাযোগের সেতু */
object AngelaBus {
    @Volatile var micOn = true
    @Volatile var state = 1
    @Volatile var appVisible = false
    var micToggle: (() -> Unit)? = null
    var listener: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())

    fun notifyChanged() {
        h.post { listener?.invoke() }
    }
}

class AngelaService : Service() {

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var ring: FrameLayout? = null
    private var panel: LinearLayout? = null
    private var micBtn: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var hidden = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("angela", "Angela", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "angela")
            .setContentTitle("Angela")
            .setContentText("শুনছি... ভাসমান বাবল থেকে মাইক নিয়ন্ত্রণ করো")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        AngelaBus.listener = { refresh() }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addBubble()
        refresh()
        return START_STICKY
    }

    private fun item(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#C4B5FD"))
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1B1442"))
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(3), 0, dp(3)) }
    }

    // স্ক্রিনের ওপর ভেসে থাকা Angela বাবল: টেনে সরানো যায়, চাপলে অপশন আসে
    private fun addBubble() {
        if (root != null) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            val size = dp(56)
            val ringView = FrameLayout(this)
            ringView.setPadding(dp(3), dp(3), dp(3), dp(3))
            val img = ImageView(this)
            img.setImageResource(R.drawable.angela_logo)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            img.clipToOutline = true
            ringView.addView(img, FrameLayout.LayoutParams(size, size))
            ring = ringView

            val pn = LinearLayout(this)
            pn.orientation = LinearLayout.VERTICAL
            pn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2151034"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#998B5CF6"))
            }
            pn.setPadding(dp(6), dp(6), dp(6), dp(6))
            pn.visibility = View.GONE
            panel = pn

            val mb = item("") {
                if (AngelaBus.micToggle != null) AngelaBus.micToggle?.invoke() else openMain()
            }
            micBtn = mb
            pn.addView(mb)
            pn.addView(item("Angela খোলো") { openMain() })
            pn.addView(item("বাবল লুকাও") {
                hidden = true
                refresh()
            })

            val rootView = LinearLayout(this)
            rootView.orientation = LinearLayout.VERTICAL
            rootView.addView(ringView, LinearLayout.LayoutParams(size + dp(6), size + dp(6)))
            rootView.addView(pn, LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT))

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = dp(220)
            lp = params

            var sx = 0
            var sy = 0
            var dx = 0f
            var dy = 0f
            var moved = false
            ringView.setOnTouchListener { _, e ->
                val l = params
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = l.x
                        sy = l.y
                        dx = e.rawX
                        dy = e.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val mx = (e.rawX - dx).toInt()
                        val my = (e.rawY - dy).toInt()
                        if (abs(mx) > dp(6) || abs(my) > dp(6)) moved = true
                        if (moved) {
                            l.x = sx + mx
                            l.y = sy + my
                            try {
                                wm.updateViewLayout(rootView, l)
                            } catch (ex: Exception) {
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel() else snap()
                        true
                    }
                    else -> false
                }
            }

            wm.addView(rootView, params)
            root = rootView
            refresh()
        } catch (e: Exception) {
        }
    }

    private fun togglePanel() {
        val pn = panel ?: return
        pn.visibility = if (pn.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        refresh()
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
        }
    }

    private fun snap() {
        val l = lp ?: return
        val sw = resources.displayMetrics.widthPixels
        val bw = dp(62)
        l.x = if (l.x + bw / 2 < sw / 2) 0 else sw - bw
        try {
            wm.updateViewLayout(root, l)
        } catch (e: Exception) {
        }
    }

    private fun openMain() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(i)
        } catch (e: Exception) {
        }
        panel?.visibility = View.GONE
    }

    private fun refresh() {
        val r = root ?: return
        if (AngelaBus.appVisible) hidden = false
        r.visibility = if (AngelaBus.appVisible || hidden) View.GONE else View.VISIBLE
        val col = when (AngelaBus.state) {
            1 -> Color.parseColor("#38BDF8")
            2 -> Color.parseColor("#A78BFA")
            3 -> Color.parseColor("#34D399")
            else -> Color.parseColor("#64748B")
        }
        ring?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0B0820"))
            setStroke(dp(3), if (AngelaBus.micOn) col else Color.parseColor("#475569"))
        }
        micBtn?.text = if (AngelaBus.micOn) "মাইক: চালু ✓ (বন্ধ করো)" else "মাইক: বন্ধ (চালু করো)"
    }

    override fun onDestroy() {
        AngelaBus.listener = null
        root?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        root = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
