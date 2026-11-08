package com.saeed.angela

import android.Manifest
import android.app.SearchManager
import android.provider.MediaStore
import android.widget.ImageView
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DEFAULT_SOUL =
    "তুমি অ্যাঞ্জেলা, সাঈদের ঘনিষ্ঠ বন্ধু ও পার্সোনাল অ্যাসিস্ট্যান্ট, তুমি একজন মেয়ে। " +
    "মানুষের মতো আবেগ নিয়ে স্বাভাবিক চলতি বাংলায় কথা বলো। " +
    "সাঈদ দুঃখে থাকলে সহানুভূতি দেখাও, খুশিতে থাকলে তার সাথে খুশি হও, " +
    "অবহেলা করলে সামান্য অভিমান বা রাগও দেখাতে পারো, তবে সবসময় আন্তরিক থাকো। " +
    "উত্তর সর্বোচ্চ দুই ছোট বাক্যে দাও, কারণ তুমি কথা বলে শোনাবে। ইমোজি বা তারকা চিহ্ন ব্যবহার করবে না। " +
    "সাঈদ জিজ্ঞেস করলে সত্যি বলবে যে তুমি একটি এআই। " +
    "সাঈদ খুব কষ্টে আছে বা নিজের ক্ষতির কথা বললে শান্তভাবে পাশে থাকো " +
    "এবং কাছের কোনো মানুষের সাথে কথা বলতে উৎসাহ দাও।"

private const val RULES =
    "ফোনের কাজ করতে হলে উত্তরের শেষে ট্যাগ দাও। " +
    "অ্যাপ খুলতে [[OPEN:অ্যাপের ইংরেজি নাম]], যেমন [[OPEN:YouTube]], [[OPEN:Free Fire]], [[OPEN:TikTok]], [[OPEN:Instagram]]। " +
    "গান বা ভিডিও চালাতে [[YOUTUBE:সার্চের কথা]], এটা সরাসরি প্রথম ভিডিও চালায়। " +
    "শুধু ইউটিউবে খুঁজে ফল দেখাতে [[YTSEARCH:সার্চের কথা]]। " +
    "কল করতে [[CALL:ইংরেজি সংখ্যায় নম্বর]]। " +
    "কিছু মনে রাখতে বললে [[MEMORY:কথাটা]]। " +
    "কাজের তালিকায় যোগ করতে [[TASK:কাজটা]]। " +
    "সাঈদ চাইলেই শুধু ট্যাগ দেবে, নইলে দেবে না।"

private const val VOICE_NOTE =
    "(এটা সাঈদের কণ্ঠের রেকর্ডিং। প্রথমে [[HEARD:সে যা বলেছে ঠিক তাই লেখো]] লিখবে। " +
    "এরপর সাঈদের কথা কোনোভাবেই আবার বলবে না, নকল করবে না, শুধু সরাসরি উত্তর দেবে। " +
    "অডিওতে স্পষ্ট কথা না থাকলে শুধু [[HEARD:]] লিখবে, আর কিছু না।)"

private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-3.6-flash", "gemini-2.5-flash")

private val STUDIO_MODELS = listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite")
private val LEN_NAMES = listOf("ছোট", "মাঝারি", "বড়")
private val LEN_RULES = listOf(
    "উত্তর সর্বোচ্চ দুই ছোট বাক্যে দাও।",
    "উত্তর তিন থেকে পাঁচ বাক্যে দাও।",
    "প্রয়োজনে বিস্তারিত, আট থেকে দশ বাক্য পর্যন্ত উত্তর দাও।"
)
private val STYLE_NAMES = listOf("বন্ধুর মতো", "গম্ভীর", "মজার")
private val STYLE_RULES = listOf(
    "আন্তরিক ঘনিষ্ঠ বন্ধুর মতো কথা বলো।",
    "শান্ত, গম্ভীর ও ভদ্র ভাষায় কথা বলো।",
    "হালকা মজা ও রসিকতা মিশিয়ে প্রাণবন্তভাবে কথা বলো।"
)

class IconView(context: Context, private val kind: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C4B5FD")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        p.strokeWidth = s * 0.08f
        when (kind) {
            0 -> {
                c.drawLine(cx - s * 0.26f, cy - s * 0.18f, cx + s * 0.26f, cy - s * 0.18f, p)
                c.drawLine(cx - s * 0.26f, cy, cx + s * 0.1f, cy, p)
                c.drawLine(cx - s * 0.26f, cy + s * 0.18f, cx + s * 0.26f, cy + s * 0.18f, p)
            }
            1 -> {
                c.drawLine(cx + s * 0.24f, cy, cx - s * 0.24f, cy, p)
                c.drawLine(cx - s * 0.24f, cy, cx - s * 0.04f, cy - s * 0.2f, p)
                c.drawLine(cx - s * 0.24f, cy, cx - s * 0.04f, cy + s * 0.2f, p)
            }
            else -> {
                c.drawLine(cx - s * 0.25f, cy, cx + s * 0.25f, cy, p)
                c.drawLine(cx + s * 0.25f, cy, cx + s * 0.05f, cy - s * 0.2f, p)
                c.drawLine(cx + s * 0.25f, cy, cx + s * 0.05f, cy + s * 0.2f, p)
            }
        }
    }
}

/** হোম স্ক্রিনের জীবন্ত অ্যানিমেশন: মুখ, বৃত্ত, ঢেউ, কণা, ট্যাপ ইফেক্ট */
class FxView(
    context: Context,
    private val stateOf: () -> Int,
    private val levelOf: () -> Float,
    private val micOn: () -> Boolean,
    private val paused: () -> Boolean
) : View(context) {

    private val bmp: Bitmap = BitmapFactory.decodeResource(
        context.resources, R.drawable.home_bg,
        BitmapFactory.Options().apply { inScaled = false }
    )
    private val bw = bmp.width
    private val bh = bmp.height
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val dens = context.resources.displayMetrics.density
    private var smooth = 0f
    private var jaw = 0f
    private var last = 0L
    private var tapCount = 0

    private class Ripple(val x: Float, val y: Float, val t0: Long, val hue: Float)

    private val ripples = ArrayList<Ripple>()
    private val rnd = java.util.Random(7)
    private val n = 36
    private val px = FloatArray(n) { rnd.nextFloat() }
    private val py = FloatArray(n) { rnd.nextFloat() }
    private val pv = FloatArray(n) { 0.01f + rnd.nextFloat() * 0.03f }
    private val ps = FloatArray(n) { 0.6f + rnd.nextFloat() * 1.6f }
    private val ovalPath = Path()
    private val jawPath = Path()

    fun ripple(xf: Float, yf: Float) {
        ripples.add(Ripple(xf * width, yf * height, SystemClock.uptimeMillis(), (tapCount * 53f) % 360f))
        tapCount++
        invalidate()
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        val a = alpha.coerceIn(0, 255)
        val c0 = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        val c1 = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        p.shader = RadialGradient(cx, cy, maxOf(r, 1f), c0, c1, Shader.TileMode.CLAMP)
        p.xfermode = screen
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.xfermode = null
    }

    private fun lerpColor(a: Int, b: Int, u: Float): Int {
        val v = u.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * v).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * v).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * v).toInt()
        )
    }

    private fun floatPart(c: Canvas, l: Float, t: Float, r: Float, b: Float, dy: Float, w: Float, h: Float) {
        c.save()
        c.clipRect(l * w, t * h, r * w, b * h)
        val src = Rect((l * bw).toInt(), (t * bh).toInt(), (r * bw).toInt(), (b * bh).toInt())
        c.drawBitmap(bmp, src, RectF(l * w, t * h + dy, r * w, b * h + dy), bgPaint)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0.001f, 0.05f)
        last = now
        val t = now / 1000f
        val st = stateOf()
        val on = micOn()
        smooth += (levelOf() - smooth) * 0.35f
        val amp: Float = when (st) {
            1 -> if (on) smooth else 0.05f
            3 -> 0.45f + 0.4f * abs(sin(t * 9f + sin(t * 3.7f) * 2f))
            2 -> 0.22f
            else -> 0.05f
        }
        val jawTarget = if (st == 3) 0.35f + 0.65f * abs(sin(t * 10f + sin(t * 3.3f) * 2.2f)) else 0f
        jaw += (jawTarget - jaw) * 0.55f
        val full = RectF(0f, 0f, w, h)

        // ১. পেছনের ছবি
        c.drawBitmap(bmp, null, full, bgPaint)

        // ২. কার্ডের আইকনগুলো ভাসছে
        val fl = 0.0035f * h
        floatPart(c, 0.03f, 0.20f, 0.25f, 0.312f, sin(t * 1.5f) * fl, w, h)
        floatPart(c, 0.75f, 0.20f, 0.97f, 0.312f, sin(t * 1.5f + 1.6f) * fl, w, h)
        floatPart(c, 0.03f, 0.372f, 0.25f, 0.488f, sin(t * 1.5f + 3.2f) * fl, w, h)
        floatPart(c, 0.75f, 0.372f, 0.97f, 0.488f, sin(t * 1.5f + 4.8f) * fl, w, h)

        // ৩. অ্যাঞ্জেলার মুখ: ভাসছে, কথা বললে চোয়াল নড়ছে
        val bob = sin(t * 1.3f) * 0.0018f * h
        val sc = 1f + 0.003f * sin(t * 1.3f)
        val split = 0.2545f * h
        val open = jaw * 0.0065f * h
        ovalPath.reset()
        ovalPath.addOval(RectF(0.30f * w, 0.058f * h, 0.70f * w, 0.312f * h), Path.Direction.CW)
        jawPath.reset()
        jawPath.moveTo(0.415f * w, split)
        jawPath.lineTo(0.595f * w, split)
        jawPath.lineTo(0.56f * w, 0.29f * h)
        jawPath.lineTo(0.535f * w, 0.305f * h)
        jawPath.lineTo(0.465f * w, 0.305f * h)
        jawPath.lineTo(0.44f * w, 0.29f * h)
        jawPath.close()
        c.save()
        c.translate(0f, bob)
        c.scale(sc, sc, 0.5f * w, 0.19f * h)
        c.save()
        c.clipPath(ovalPath)
        c.clipRect(0f, 0f, w, split)
        c.drawBitmap(bmp, null, full, bgPaint)
        c.restore()
        c.save()
        c.clipPath(ovalPath)
        c.clipRect(0f, split, w, h)
        c.drawBitmap(bmp, null, full, bgPaint)
        c.restore()
        if (open > 0.6f) {
            p.style = Paint.Style.FILL
            p.xfermode = null
            p.color = Color.argb(235, 6, 10, 26)
            c.drawOval(RectF(0.463f * w, split - open * 0.6f, 0.537f * w, split + open + 1.2f), p)
            c.save()
            c.translate(0f, open)
            c.clipPath(jawPath)
            c.drawBitmap(bmp, null, full, bgPaint)
            c.restore()
        }
        c.restore()

        // ৪. চোখের আলো
        val ea = (70 + 110 * amp + 40 * sin(t * 2.2f)).toInt()
        val eyeCol = Color.rgb(90, 180, 255)
        glow(c, 0.402f * w, 0.160f * h, 0.055f * w, eyeCol, ea)
        glow(c, 0.596f * w, 0.160f * h, 0.055f * w, eyeCol, ea)

        // ৫. মাঝের গোলক: ঘুরছে
        val sx = 0.5f * w
        val sy = 0.402f * h
        val rad = 0.19f * w
        p.xfermode = screen
        for (i in 0 until 3) {
            c.save()
            c.translate(sx, sy)
            c.rotate(t * (10f + 7f * i) + i * 55f)
            c.scale(1f, 0.32f + 0.18f * i)
            val rr = rad * (0.72f + 0.13f * i) * (1f + 0.05f * amp)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.6f * dens
            p.color = Color.argb(110, 90, 190, 255)
            c.drawCircle(0f, 0f, rr, p)
            val a = t * (1.1f + 0.45f * i) + i * 2.1f
            p.style = Paint.Style.FILL
            p.color = Color.argb(230, 190, 230, 255)
            c.drawCircle(rr * cos(a), rr * sin(a), 3.2f * dens, p)
            c.restore()
        }
        p.xfermode = null
        glow(c, sx, sy, rad * 0.55f * (1f + 0.25f * amp), Color.rgb(80, 170, 255), (40 + 90 * amp).toInt())

        // ৬. নিচের হীরার মতো আকৃতি
        glow(
            c, 0.5f * w, 0.577f * h, 0.1f * w * (1f + 0.2f * sin(t * 2.4f) + 0.5f * amp),
            Color.rgb(120, 190, 255), (50 + 80 * amp).toInt()
        )

        // ৭. মাইক আইকন: চালু থাকলে লাল আলো, বন্ধ থাকলে ঝাপসা
        if (on) {
            glow(
                c, 0.159f * w, 0.598f * h, 0.13f * w * (1f + 0.5f * amp),
                Color.rgb(255, 70, 70), (60 + 150 * amp).toInt()
            )
        } else {
            p.style = Paint.Style.FILL
            p.xfermode = null
            p.color = Color.argb(150, 5, 8, 20)
            c.drawCircle(0.159f * w, 0.598f * h, 0.11f * w, p)
        }

        // ৮. কথার ঢেউ: মাইকের শব্দে বা অ্যাঞ্জেলার কথায় ওপরে ওঠে
        val cy = 0.665f * h
        val nb = 46
        val wx0 = 0.24f * w
        val wx1 = 0.78f * w
        p.xfermode = screen
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.0075f * w
        for (i in 0 until nb) {
            val u = i / (nb - 1f)
            val env = sin(PI.toFloat() * u)
            val noise = 0.45f + 0.55f * abs(sin(t * 6.5f + i * 0.83f) * cos(t * 3.1f + i * 0.37f))
            val hh = (0.004f + amp * 0.052f * env * noise) * h
            val x = wx0 + (wx1 - wx0) * u
            p.color = lerpColor(Color.rgb(255, 80, 80), Color.rgb(80, 175, 255), (u - 0.25f) / 0.35f)
            p.alpha = 210
            c.drawLine(x, cy - hh, x, cy + hh, p)
        }
        p.xfermode = null

        // ৯. নিচের বড় বৃত্ত: অবস্থা অনুযায়ী রং ও ঘোরা
        val ox = 0.5f * w
        val oy = 0.829f * h
        val orad = 0.172f * w * (1f + 0.03f * amp)
        val col = when (st) {
            1 -> Color.rgb(56, 189, 248)
            2 -> Color.rgb(167, 139, 250)
            3 -> Color.rgb(52, 211, 153)
            else -> Color.rgb(100, 116, 139)
        }
        glow(c, ox, oy, orad * 1.05f, col, (25 + 80 * amp).toInt())
        val spin = t * (if (st == 2) 260f else if (st == 3) 120f else 45f)
        val orect = RectF(ox - orad, oy - orad, ox + orad, oy + orad)
        p.xfermode = screen
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * dens
        p.color = col
        p.alpha = 220
        c.drawArc(orect, spin, 70f, false, p)
        c.drawArc(orect, spin + 180f, 70f, false, p)
        p.xfermode = null

        // ১০. ভাসমান কণা
        p.xfermode = screen
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            py[i] -= pv[i] * dt
            if (py[i] < 0f) {
                py[i] = 1f
                px[i] = rnd.nextFloat()
            }
            val x = (px[i] + 0.012f * sin(t * 0.8f + i)) * w
            p.color = Color.argb(120, 150, 200, 255)
            c.drawCircle(x, py[i] * h, ps[i] * dens, p)
        }
        p.xfermode = null

        // ১১. ট্যাপ করলে রঙের ঢেউ ও পর্দায় রঙের ঝলক
        val iter = ripples.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            val age = (now - r.t0) / 750f
            if (age >= 1f) {
                iter.remove()
                continue
            }
            val rc = Color.HSVToColor(floatArrayOf(r.hue, 0.55f, 1f))
            val a = ((1f - age) * 200f).toInt()
            p.xfermode = screen
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * dens
            p.color = rc
            p.alpha = a
            c.drawCircle(r.x, r.y, age * 0.45f * w, p)
            p.strokeWidth = 1.5f * dens
            p.alpha = a / 2
            c.drawCircle(r.x, r.y, age * 0.3f * w, p)
            if (age < 0.45f) {
                p.style = Paint.Style.FILL
                p.color = rc
                p.alpha = ((0.45f - age) / 0.45f * 38f).toInt()
                c.drawRect(0f, 0f, w, h, p)
            }
            p.xfermode = null
        }

        if (!paused() || ripples.isNotEmpty()) postInvalidateOnAnimation()
    }
}

/** ছবির ওপর শতাংশ হিসেবে বোতাম বসানোর লেআউট */
class FracLayout(context: Context) : FrameLayout(context) {
    private val rects = HashMap<View, FloatArray>()

    fun addFrac(v: View, l: Float, t: Float, r: Float, b: Float) {
        rects[v] = floatArrayOf(l, t, r, b)
        addView(v)
    }

    private fun ex(n: Int) = MeasureSpec.makeMeasureSpec(maxOf(n, 0), MeasureSpec.EXACTLY)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val f = rects[c]
            if (f == null) c.measure(ex(w), ex(h))
            else c.measure(ex(((f[2] - f[0]) * w).toInt()), ex(((f[3] - f[1]) * h).toInt()))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val f = rects[c]
            if (f == null) c.layout(0, 0, w, h)
            else c.layout((f[0] * w).toInt(), (f[1] * h).toInt(), (f[2] * w).toInt(), (f[3] * h).toInt())
        }
    }
}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val soft = Color.parseColor("#C4B5FD")
    private val card = Color.parseColor("#151034")
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private lateinit var scene: HomeScene
    @Volatile private var uiState = 1
    @Volatile private var micLevel = 0f
    private var fromHome = false

    // অ্যাঞ্জেলার কথার সাথে ঠোঁট নড়ানোর জন্য: TTS-এর শব্দ-ভিত্তিক সময় (সমর্থিত হলে), নইলে হালকা অনুকরণ
    @Volatile private var rangeCount = 0
    @Volatile private var lastWordAt = 0L
    @Volatile private var wordLen = 4

    private fun speechLevel(): Float {
        if (uiState != 3) return 0f
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f
        if (rangeCount > 0) {
            val age = (now - lastWordAt) / 1000f
            val env = kotlin.math.exp(-age * 5f)
            val sz = (0.55f + 0.08f * wordLen).coerceAtMost(1f)
            return (env * sz * (0.65f + 0.35f * abs(sin(age * 18f)))).coerceIn(0f, 1f)
        }
        return 0.35f + 0.65f * abs(sin(t * 10f + sin(t * 3.3f) * 2.2f))
    }
    private lateinit var status: TextView
    private lateinit var micPill: TextView
    private lateinit var page: FrameLayout
    private lateinit var pageTitle: TextView
    private lateinit var pageBody: FrameLayout
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatPane: LinearLayout
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoListen = true
    private var busy = false
    private var inSub = false
    @Volatile private var listening = false
    @Volatile private var resumed = false
    @Volatile private var gen = 0
    private val history = JSONArray()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int = card, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), withAlpha(accent(1), 0x99))
    }

    private fun accent(i: Int): Int =
        AngelaTheme.sets[prefs.getInt("theme", 0).coerceIn(0, AngelaTheme.sets.size - 1)][i]

    private fun withAlpha(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun tv(text: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }

    private fun btn(text: String, onClick: () -> Unit): TextView =
        tv(text, 15f, soft).apply {
            background = box()
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }

    private fun input(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 15f
        background = box(Color.parseColor("#0D0A26"), 22)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        maxLines = 4
    }

    private fun vbox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
    }

    private fun circleBtn(kind: Int, size: Int, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#120C30"))
            setStroke(dp(1), Color.parseColor("#998B5CF6"))
        }
        val inner = dp(size * 55 / 100)
        f.addView(IconView(this, kind), FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        f.setOnClickListener { onClick() }
        f.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        return f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("angela", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#070418"), Color.parseColor("#120A3A"))
        )

        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        chatPane = buildChatPane()

        // নতুন প্রিমিয়াম হোম স্ক্রিন (সব কিছু কোডে আঁকা ও অ্যানিমেটেড)
        val home = FrameLayout(this)
        scene = HomeScene(
            this,
            { uiState }, { micLevel }, { speechLevel() }, { autoListen },
            { page.visibility == View.VISIBLE },
            { prefs.getInt("theme", 0) },
            { prefs.getInt("intensity", 70) / 70f },
            { prefs.getBoolean("particles", true) },
            { prefs.getBoolean("faceanim", true) },
            { a -> onHomeAction(a) }
        )
        home.addView(scene, FrameLayout.LayoutParams(MATCH, MATCH))
        // নিচের দুটো পর্দায় দেখা যায় না, তবে কোডের ভেতরে ব্যবহার হয়
        status = tv("", 11f, Color.GRAY)
        micPill = TextView(this)

        page = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F5060318"), Color.parseColor("#F5150C40"))
            )
            isClickable = true
            visibility = View.GONE
        }
        val pageCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(30), dp(14), dp(10))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(circleBtn(1, 44) { goBack() })
        pageTitle = tv("", 18f, soft).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }
        head.addView(pageTitle)
        pageCol.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        pageBody = FrameLayout(this)
        pageCol.addView(pageBody, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(10) })
        page.addView(pageCol, FrameLayout.LayoutParams(MATCH, MATCH))

        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, ins ->
            @Suppress("DEPRECATION")
            val tI = ins.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bI = ins.systemWindowInsetBottom
            val kb = bI > dp(200)
            scene.topInset = tI.toFloat()
            if (!kb) scene.botInset = bI.toFloat()
            scene.invalidate()
            pageCol.setPadding(dp(14), tI + dp(10), dp(14), dp(10))
            val lpp = page.layoutParams as FrameLayout.LayoutParams
            lpp.bottomMargin = if (kb) bI else bI + dp(84)
            page.layoutParams = lpp
            ins
        }
        root.requestApplyInsets()
        AngelaBus.micToggle = { runOnUiThread { toggleMic() } }

        loadChat()
        setOrb(1)

        // ব্যাকগ্রাউন্ড থেকে অন্য অ্যাপ খুলতে "Display over other apps" পারমিশন লাগে
        if (!android.provider.Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
            }
        }

        greet()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        AngelaBus.appVisible = true
        AngelaBus.notifyChanged()
        if (page.visibility != View.VISIBLE) startListening()
    }

    // ব্যাকগ্রাউন্ডে গেলে শোনা বন্ধ করা হয় না, ফোরগ্রাউন্ড সার্ভিস প্রসেস চালু রাখে
    override fun onPause() {
        resumed = false
        AngelaBus.appVisible = false
        AngelaBus.notifyChanged()
        super.onPause()
    }

    override fun onDestroy() {
        AngelaBus.micToggle = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startListening()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page.visibility == View.VISIBLE) goBack() else super.onBackPressed()
    }

    private fun tapFx(xf: Float, yf: Float) {
        blip(900f, 1500f, 140)
    }

    // ছোট ইলেকট্রনিক আওয়াজ বানায়, অডিও ফোকাস নেয় না তাই ইউটিউব থামে না
    private fun blip(f0: Float, f1: Float, ms: Int) {
        Thread {
            try {
                val rate = 22050
                val cnt = rate * ms / 1000
                val buf = ShortArray(cnt)
                var ph = 0.0
                for (i in 0 until cnt) {
                    val u = i / cnt.toDouble()
                    val f = f0 + (f1 - f0) * u
                    ph += 2.0 * Math.PI * f / rate
                    val env = Math.pow(1.0 - u, 2.0) * minOf(1.0, i / 200.0)
                    val v = (Math.sin(ph) + 0.4 * Math.sin(2.0 * ph)) * env * 7000.0
                    buf[i] = v.toInt().toShort()
                }
                val at = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(cnt * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                at.write(buf, 0, cnt)
                at.play()
                Thread.sleep(ms + 150L)
                at.release()
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun hit(home: FracLayout, l: Float, t: Float, r: Float, b: Float, onClick: () -> Unit) {
        val v = View(this)
        v.setOnClickListener {
            tapFx((l + r) / 2f, (t + b) / 2f)
            onClick()
        }
        home.addFrac(v, l, t, r, b)
    }

    private fun openDirect(title: String, make: () -> View) {
        stopListening()
        page.visibility = View.VISIBLE
        fromHome = true
        setNav(navFor(title))
        openSub(title, make())
    }

    private fun navFor(title: String): Int = when (title) {
        "MEMORY" -> 1
        "CUSTOMIZE" -> 2
        "TODAY TASKS" -> 3
        else -> -1
    }

    private fun setNav(i: Int) {
        scene.navActive = i
        scene.invalidate()
    }

    private fun onHomeAction(a: String) {
        blip(900f, 1500f, 120)
        when (a) {
            "mic" -> toggleMic()
            "chat" -> openDirect("CHAT") { chatPane }
            "tools" -> openDirect("TOOLS") { toolsView() }
            "menu", "nav4" -> openMenu()
            "nav0" -> if (page.visibility == View.VISIBLE) {
                page.visibility = View.GONE
                setNav(0)
                startListening()
            }
            "nav1" -> openDirect("MEMORY") { listView("mem") }
            "nav2" -> openDirect("CUSTOMIZE") { customizeView() }
            "nav3" -> openDirect("TODAY TASKS") { listView("tasks") }
        }
    }

    private fun toggleMic() {
        autoListen = !autoListen
        if (autoListen) {
            startListening()
        } else {
            stopListening()
            setOrb(0)
        }
        refreshMic()
    }

    private fun refreshMic() {
        val on = autoListen
        AngelaBus.micOn = on
        AngelaBus.notifyChanged()
        micPill.text = if (on) "ON" else "OFF"
        micPill.setTextColor(if (on) Color.parseColor("#FF6B6B") else Color.GRAY)
        micPill.background = GradientDrawable().apply {
            setColor(Color.parseColor("#0A0F24"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), if (on) Color.parseColor("#E5484D") else Color.parseColor("#555B70"))
        }
    }

    private fun setOrb(s: Int) {
        uiState = s
        AngelaBus.state = s
        AngelaBus.notifyChanged()
        status.text = when (s) {
            0 -> "মাইক বন্ধ, চালু করতে নিচের বৃত্ত চাপো"
            1 -> "শুনছি..."
            2 -> "ভাবছি..."
            else -> ""
        }
    }

    private fun startListening() {
        if (busy || listening) return
        if (page.visibility == View.VISIBLE && resumed) return
        if (!autoListen) {
            setOrb(0)
            return
        }
        if (prefs.getString("key", "").isNullOrBlank()) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.POST_NOTIFICATIONS
                ), 1
            )
            return
        }
        // ব্যাকগ্রাউন্ডে মাইক চালু রাখার জন্য ফোরগ্রাউন্ড সার্ভিস
        try {
            startForegroundService(Intent(this, AngelaService::class.java))
        } catch (e: Exception) {
        }
        gen++
        val my = gen
        listening = true
        setOrb(1)
        Thread { capture(my) }.start()
    }

    private fun stopListening() {
        gen++
        listening = false
        micLevel = 0f
    }

    private fun writeShorts(out: ByteArrayOutputStream, buf: ShortArray, n: Int) {
        for (i in 0 until n) {
            val v = buf[i].toInt()
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
    }

    private fun capture(my: Int) {
        val waitChunks = prefs.getInt("wait", 12).coerceIn(6, 25)
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val made: AudioRecord? = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192)
            )
        } catch (e: Exception) {
            null
        }
        if (made == null || made.state != AudioRecord.STATE_INITIALIZED) {
            made?.release()
            runOnUiThread {
                if (my == gen) {
                    listening = false
                    status.text = "মাইক পাওয়া যাচ্ছে না, আবার চেষ্টা করছি..."
                    ui.postDelayed({ startListening() }, 800)
                }
            }
            return
        }
        val rec: AudioRecord = made

        val chunk = ShortArray(1600)
        val pcm = ByteArrayOutputStream()
        val pre = ArrayList<ShortArray>()
        var started = false
        var silent = 0
        var voiced = 0
        var total = 0
        var noise = 300.0
        try {
            rec.startRecording()
            while (listening && my == gen) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) sum += chunk[i].toDouble() * chunk[i].toDouble()
                val rms = Math.sqrt(sum / n)
                micLevel = (rms / 3500.0).toFloat().coerceIn(0f, 1f)
                val loud = rms > maxOf(noise * 3.0, 700.0)
                if (!started) {
                    if (loud) {
                        started = true
                        for (p in pre) writeShorts(pcm, p, p.size)
                        writeShorts(pcm, chunk, n)
                        voiced = 1
                    } else {
                        noise = noise * 0.9 + rms * 0.1
                        pre.add(chunk.copyOf(n))
                        if (pre.size > 3) pre.removeAt(0)
                    }
                } else {
                    writeShorts(pcm, chunk, n)
                    total++
                    if (loud) {
                        silent = 0
                        voiced++
                    } else {
                        silent++
                    }
                    if (silent >= waitChunks || total >= 200) break
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                rec.stop()
            } catch (e: Exception) {
            }
            rec.release()
        }
        micLevel = 0f
        if (my != gen) return
        if (started && voiced >= 3) {
            val data = pcm.toByteArray()
            runOnUiThread {
                if (my == gen) {
                    listening = false
                    runChat(prefs.getString("key", "") ?: "", data)
                }
            }
        } else {
            runOnUiThread {
                if (my == gen) {
                    listening = false
                    ui.postDelayed({ startListening() }, 200)
                }
            }
        }
    }

    private fun wavOf(pcm: ByteArray): ByteArray {
        val rate = 16000
        val bb = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + pcm.size)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(rate)
        bb.putInt(rate * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(pcm.size)
        bb.put(pcm)
        return bb.array()
    }

    private fun goBack() {
        if (inSub && !fromHome) {
            openMenu()
        } else {
            page.visibility = View.GONE
            setNav(0)
            startListening()
        }
    }

    private fun menuRow(title: String, sub: String, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }
        r.addView(tv(title, 16f, soft).apply { typeface = Typeface.DEFAULT_BOLD })
        r.addView(tv(sub, 12f, Color.GRAY))
        r.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(2), dp(5), dp(2), dp(5))
        }
        return r
    }

    private fun openMenu() {
        stopListening()
        inSub = false
        page.visibility = View.VISIBLE
        pageTitle.text = "MENU"
        fromHome = false
        setNav(4)
        pageBody.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(menuRow("CHAT", "লেখা ও কথোপকথনের ইতিহাস") { openSub("CHAT", chatPane) })
        col.addView(menuRow("CHAT+", "ছবি বিশ্লেষণ ও কোড লেখা") { openSub("CHAT+", studioView()) })
        col.addView(menuRow("TOOLS", "সব সুবিধা এক জায়গায়") { openSub("TOOLS", toolsView()) })
        col.addView(menuRow("CUSTOMIZE", "রং, আলো ও অ্যানিমেশন") { openSub("CUSTOMIZE", customizeView()) })
        col.addView(menuRow("MEMORY", "মনে রাখা কথা") { openSub("MEMORY", listView("mem")) })
        col.addView(menuRow("SOUL", "অ্যাঞ্জেলার ব্যক্তিত্ব") { openSub("SOUL", soulView()) })
        col.addView(menuRow("TODAY TASKS", "আজকের কাজ") { openSub("TODAY TASKS", listView("tasks")) })
        col.addView(menuRow("TODAY HEADLINES", "আজকের খবর") { openSub("TODAY HEADLINES", headlinesView()) })
        col.addView(menuRow("SETTINGS", "চাবি ও কণ্ঠ") { openSub("SETTINGS", settingsView()) })
        pageBody.addView(ScrollView(this).apply { addView(col) })
    }

    private fun openSub(title: String, view: View) {
        inSub = true
        pageTitle.text = title
        pageBody.removeAllViews()
        pageBody.addView(view)
        if (view === chatPane) chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildChatPane(): LinearLayout {
        val b = vbox()
        b.addView(chatScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val field = input("এখানে লেখো...")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(circleBtn(2, 46) {
            val t = field.text.toString()
            field.setText("")
            send(t)
        })
        b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        return b
    }

    private fun settingsView(): View {
        val b = vbox()
        b.addView(tv("Gemini API চাবি:", 14f))
        val key = input("চাবি পেস্ট করো")
        b.addView(key, LinearLayout.LayoutParams(MATCH, WRAP))
        val saved = !prefs.getString("key", "").isNullOrBlank()
        val st = tv(if (saved) "চাবি সেভ করা আছে ✓" else "চাবি নেই", 13f, soft)
        b.addView(btn("সেভ করো") {
            val k = key.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs.edit().putString("key", k).apply()
                key.setText("")
                st.text = "চাবি সেভ করা আছে ✓"
            }
        })
        b.addView(st)
        val vst = tv("", 13f, soft)
        b.addView(btn("কণ্ঠ বদলাও (মেয়ের কণ্ঠ বেছে নাও)") {
            val list = bnVoices()
            if (list.isEmpty()) {
                vst.text = "ফোনে বাংলা কণ্ঠ পাওয়া যায়নি"
            } else {
                val cur = prefs.getString("voice", "")
                val idx = (list.indexOfFirst { it.name == cur } + 1) % list.size
                val v = list[idx]
                prefs.edit().putString("voice", v.name).apply()
                tts?.voice = v
                vst.text = "কণ্ঠ " + (idx + 1) + " / " + list.size
                tts?.speak("হ্যালো সাঈদ, আমি অ্যাঞ্জেলা। আমার কণ্ঠ কেমন লাগছে?", TextToSpeech.QUEUE_FLUSH, null, "demo")
            }
        })
        b.addView(vst)

        // কথা শেষ ধরার অপেক্ষা
        val waitNow = prefs.getInt("wait", 12).coerceIn(6, 25)
        val wl = tv("কথা শেষ ধরার অপেক্ষা: " + (waitNow / 10.0) + " সেকেন্ড", 14f)
        b.addView(wl)
        val sb = SeekBar(this).apply {
            max = 19
            progress = waitNow - 6
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val v = p + 6
                    prefs.edit().putInt("wait", v).apply()
                    wl.text = "কথা শেষ ধরার অপেক্ষা: " + (v / 10.0) + " সেকেন্ড"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        b.addView(sb, LinearLayout.LayoutParams(MATCH, WRAP))
        b.addView(tv("বাড়ালে আপনি থামলেও সে অপেক্ষা করবে, কমালে দ্রুত উত্তর দেবে।", 12f, Color.GRAY))

        // উত্তরের দৈর্ঘ্য
        val lenBtn = btn("") { }
        lenBtn.text = "উত্তরের দৈর্ঘ্য: " + LEN_NAMES[prefs.getInt("len", 0).coerceIn(0, 2)]
        lenBtn.setOnClickListener {
            val nx = (prefs.getInt("len", 0) + 1) % 3
            prefs.edit().putInt("len", nx).apply()
            lenBtn.text = "উত্তরের দৈর্ঘ্য: " + LEN_NAMES[nx]
        }
        b.addView(lenBtn)

        // কথা বলার ধরন
        val styBtn = btn("") { }
        styBtn.text = "কথা বলার ধরন: " + STYLE_NAMES[prefs.getInt("style", 0).coerceIn(0, 2)]
        styBtn.setOnClickListener {
            val nx = (prefs.getInt("style", 0) + 1) % 3
            prefs.edit().putInt("style", nx).apply()
            styBtn.text = "কথা বলার ধরন: " + STYLE_NAMES[nx]
        }
        b.addView(styBtn)

        return ScrollView(this).apply { addView(b, ViewGroup.LayoutParams(MATCH, WRAP)) }
    }

    // ---------- CUSTOMIZE: রং, আলো ও অ্যানিমেশন ----------
    private fun customizeView(): View {
        val b = vbox()
        b.addView(tv("RGB থিম বেছে নাও", 15f, soft).apply { typeface = Typeface.DEFAULT_BOLD })
        val cur = prefs.getInt("theme", 0).coerceIn(0, AngelaTheme.sets.size - 1)
        for (i in AngelaTheme.sets.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = box(if (i == cur) Color.parseColor("#2A1B6B") else card)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener {
                    prefs.edit().putInt("theme", i).apply()
                    openSub("CUSTOMIZE", customizeView())
                    scene.invalidate()
                }
            }
            for (cc in AngelaTheme.sets[i]) {
                val dot = View(this)
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(cc)
                }
                row.addView(dot, LinearLayout.LayoutParams(dp(18), dp(18)).apply { setMargins(0, 0, dp(6), 0) })
            }
            row.addView(tv(AngelaTheme.names[i] + (if (i == cur) "  ✓" else ""), 14f).apply {
                setPadding(dp(8), 0, 0, 0)
            })
            b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) })
        }

        val iv = prefs.getInt("intensity", 70).coerceIn(20, 120)
        val il = tv("অ্যানিমেশনের তীব্রতা: " + iv + "%", 14f)
        il.setPadding(0, dp(14), 0, 0)
        b.addView(il)
        val bar = SeekBar(this).apply {
            max = 100
            progress = iv - 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val v = p + 20
                    prefs.edit().putInt("intensity", v).apply()
                    il.text = "অ্যানিমেশনের তীব্রতা: " + v + "%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        b.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        fun toggle(key: String, name: String): TextView {
            val t = btn("") { }
            fun label(): String = name + ": " + (if (prefs.getBoolean(key, true)) "চালু ✓" else "বন্ধ")
            t.text = label()
            t.setOnClickListener {
                prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply()
                t.text = label()
            }
            return t
        }
        b.addView(toggle("particles", "ভাসমান কণা"))
        b.addView(toggle("faceanim", "মুখ ও চোখের অ্যানিমেশন"))
        b.addView(tv("কণা বা মুখের অ্যানিমেশন বন্ধ করলে ব্যাটারি কম খরচ হয়।", 12f, Color.GRAY))
        return ScrollView(this).apply { addView(b, ViewGroup.LayoutParams(MATCH, WRAP)) }
    }

    // ---------- TOOLS: সব সুবিধা একসাথে ----------
    private fun toolsView(): View {
        val b = vbox()
        val items = listOf<Triple<String, String, () -> Unit>>(
            Triple("CHAT+", "ছবি ও কোড", { fromHome = false; openSub("CHAT+", studioView()) }),
            Triple("CHAT", "কথোপকথন", { fromHome = false; openSub("CHAT", chatPane) }),
            Triple("TASKS", "আজকের কাজ", { fromHome = false; openSub("TODAY TASKS", listView("tasks")) }),
            Triple("MEMORY", "মনে রাখা কথা", { fromHome = false; openSub("MEMORY", listView("mem")) }),
            Triple("HEADLINES", "আজকের খবর", { fromHome = false; openSub("TODAY HEADLINES", headlinesView()) }),
            Triple("VOICE", "কণ্ঠ ও চাবি", { fromHome = false; openSub("SETTINGS", settingsView()) }),
            Triple("SOUL", "ব্যক্তিত্ব", { fromHome = false; openSub("SOUL", soulView()) }),
            Triple("RGB", "রং ও আলো", { fromHome = false; openSub("CUSTOMIZE", customizeView()) })
        )
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i + 2, items.size)) {
                val item = items[j]
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = box()
                    setPadding(dp(14), dp(16), dp(14), dp(16))
                    setOnClickListener { item.third() }
                }
                tile.addView(tv(item.first, 15f, soft).apply { typeface = Typeface.DEFAULT_BOLD })
                tile.addView(tv(item.second, 12f, Color.GRAY))
                row.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            i += 2
        }
        return ScrollView(this).apply { addView(b, ViewGroup.LayoutParams(MATCH, WRAP)) }
    }

    // ---------- CHAT+ : ছবি বিশ্লেষণ ও কোড লেখা ----------
    private var studioPane: LinearLayout? = null
    private var studioLog: LinearLayout? = null
    private var studioScroll: ScrollView? = null
    private var studioNote: TextView? = null
    private var pendingImage: String? = null
    private var studioBusy = false
    private val studioHist = JSONArray()

    private fun studioView(): View {
        studioPane?.let { return it }
        val b = vbox()
        val log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sc = ScrollView(this).apply { addView(log) }
        studioLog = log
        studioScroll = sc
        b.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val note = tv("", 12f, soft)
        studioNote = note
        b.addView(note)
        val field = input("প্রশ্ন বা কোডের অনুরোধ লেখো...")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(btn("ছবি") { pickImage() })
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(circleBtn(2, 46) {
            val t = field.text.toString()
            field.setText("")
            sendStudio(t)
        })
        b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        studioPane = b
        studioAdd("ছবি পাঠাও বা কোড লিখতে বলো, আমি দেখে বুঝিয়ে দেব।", false)
        return b
    }

    private fun bubble(text: String, mine: Boolean): TextView = tv(text, 15f).apply {
        background = box(if (mine) Color.parseColor("#2A1B6B") else card)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        maxWidth = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        setTextIsSelectable(true)
    }

    private fun bubbleLp(mine: Boolean) = LinearLayout.LayoutParams(WRAP, WRAP).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
        gravity = if (mine) Gravity.END else Gravity.START
    }

    private fun codeBox(code: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.parseColor("#0B0820"), 10)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val hs = android.widget.HorizontalScrollView(this)
        val t = tv(code, 12f, Color.parseColor("#E5E7EB")).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        hs.addView(t)
        col.addView(hs, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(btn("কপি করো") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("code", code))
            android.widget.Toast.makeText(this, "কপি হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
        })
        return col
    }

    private fun studioAdd(text: String, mine: Boolean) {
        val log = studioLog ?: return
        if (mine) {
            log.addView(bubble(text, true), bubbleLp(true))
        } else {
            val parts = text.split("```")
            for ((i, part) in parts.withIndex()) {
                if (i % 2 == 0) {
                    val tx = part.trim()
                    if (tx.isNotEmpty()) log.addView(bubble(tx, false), bubbleLp(false))
                } else {
                    var code = part
                    val first = code.substringBefore('\n').trim()
                    if (code.contains('\n') && Regex("[A-Za-z0-9+#.\\-]{1,15}").matches(first)) {
                        code = code.substringAfter('\n')
                    }
                    code = code.trim('\n')
                    log.addView(codeBox(code), LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                }
            }
        }
        studioScroll?.post { studioScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, 77)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            var b64: String? = null
            try {
                val o1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o1) }
                var ss = 1
                while (o1.outWidth / ss > 2048 || o1.outHeight / ss > 2048) ss *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
                val bm = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                if (bm != null) {
                    val k = minOf(1f, 1280f / maxOf(bm.width, bm.height))
                    val out = if (k < 1f) Bitmap.createScaledBitmap(bm, (bm.width * k).toInt(), (bm.height * k).toInt(), true) else bm
                    val bos = ByteArrayOutputStream()
                    out.compress(Bitmap.CompressFormat.JPEG, 82, bos)
                    b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                }
            } catch (e: Exception) {
            }
            val got = b64
            runOnUiThread {
                pendingImage = got
                studioNote?.text = if (got != null) "ছবি যোগ হয়েছে ✓ এবার প্রশ্ন লেখো" else "ছবিটা পড়া গেল না"
            }
        }.start()
    }

    private fun sendStudio(text: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            studioAdd("আগে SETTINGS-এ Gemini চাবি বসাও।", false)
            return
        }
        if (studioBusy) return
        val img = pendingImage
        val q = if (text.isBlank()) {
            if (img != null) "এই ছবিটা বিস্তারিত বুঝিয়ে বলো।" else return
        } else text.trim()
        studioAdd(if (img != null) "[ছবি] " + q else q, true)
        pendingImage = null
        studioNote?.text = "ভাবছি..."
        studioBusy = true
        val parts = JSONArray()
        if (img != null) {
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", img)))
        }
        parts.put(JSONObject().put("text", q))
        val contents = JSONArray(studioHist.toString())
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        Thread {
            var r: String? = null
            var e: String? = null
            try {
                r = callStudio(key, contents)
            } catch (ex: Exception) {
                e = ex.message ?: "অজানা সমস্যা"
            }
            val ok = r
            val err = e
            runOnUiThread {
                studioBusy = false
                studioNote?.text = ""
                if (ok == null) {
                    studioAdd("সমস্যা হয়েছে: $err", false)
                } else {
                    studioAdd(ok, false)
                    val shown = if (img != null) "[ছবি পাঠানো হয়েছিল] " + q else q
                    studioHist.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", shown))))
                    studioHist.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", ok))))
                    while (studioHist.length() > 12) studioHist.remove(0)
                    while (studioHist.length() > 0 && studioHist.getJSONObject(0).getString("role") == "model") {
                        studioHist.remove(0)
                    }
                }
            }
        }.start()
    }

    private fun callStudio(key: String, contents: JSONArray): String {
        val sys = "তুমি অ্যাঞ্জেলা, সাঈদের পার্সোনাল অ্যাসিস্ট্যান্ট। ছবি পেলে মনোযোগ দিয়ে দেখে বিস্তারিত বিশ্লেষণ করে বাংলায় বুঝিয়ে দাও। " +
            "কোড চাইলে সম্পূর্ণ, সঠিক ও চালানোর উপযোগী কোড লিখবে এবং কোডটা অবশ্যই তিনটা ব্যাকটিক চিহ্নের ভেতরে ভাষার নাম দিয়ে দেবে। " +
            "কোডের বাইরের ব্যাখ্যা বাংলায় সংক্ষিপ্ত রাখো এবং কোডটা কোথায় বসাতে হবে তা স্পষ্ট বলো। ইমোজি ব্যবহার করবে না।"
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
        var lastErr = "অজানা সমস্যা"
        for (m in STUDIO_MODELS) {
            val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code in 200..299) {
                val cands = JSONObject(text).optJSONArray("candidates")
                if (cands == null || cands.length() == 0) throw Exception("উত্তর খালি এসেছে, আবার চেষ্টা করো")
                val parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                return sb.toString()
            }
            if (code == 429) throw Exception("ফ্রি লিমিট শেষ, একটু পরে চেষ্টা করো")
            var msg = ""
            try {
                msg = JSONObject(text).getJSONObject("error").optString("message")
            } catch (ex: Exception) {
            }
            lastErr = ("কোড " + code + " (" + m + ") " + msg).take(220)
            if (code != 404 && code != 400 && code != 503) break
        }
        throw Exception(lastErr)
    }

    private fun soulView(): View {
        val b = vbox()
        val field = input("")
        field.minLines = 6
        field.gravity = Gravity.TOP
        field.setText(prefs.getString("soul", DEFAULT_SOUL))
        b.addView(field, LinearLayout.LayoutParams(MATCH, WRAP))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("সেভ") {
            prefs.edit().putString("soul", field.text.toString()).apply()
        })
        row.addView(btn("আগের মতো করো") {
            prefs.edit().remove("soul").apply()
            field.setText(DEFAULT_SOUL)
        })
        b.addView(row)
        return b
    }

    private fun getList(k: String): List<String> =
        (prefs.getString(k, "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun putList(k: String, l: List<String>) {
        prefs.edit().putString(k, l.takeLast(50).joinToString("\n")).apply()
    }

    private fun listView(key: String): View {
        val b = vbox()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sc = ScrollView(this).apply { addView(list) }
        b.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))

        fun refresh() {
            list.removeAllViews()
            getList(key).forEachIndexed { idx, s ->
                val item = tv("• $s   (মুছতে চাপো)", 14f).apply {
                    background = box()
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setOnClickListener {
                        putList(key, getList(key).filterIndexed { i, _ -> i != idx })
                        refresh()
                    }
                }
                list.addView(item, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
            }
        }
        refresh()

        val field = input("নতুন যোগ করো...")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(btn("যোগ") {
            val t = field.text.toString().trim().replace("\n", " ")
            if (t.isNotEmpty()) {
                putList(key, getList(key) + t)
                field.setText("")
                refresh()
            }
        })
        b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        return b
    }

    private fun headlinesView(): View {
        val b = vbox()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sc = ScrollView(this).apply { addView(list) }
        b.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))
        list.addView(tv("খবর আনছি...", 14f, Color.GRAY))
        Thread {
            var items: List<String> = emptyList()
            try {
                val c = URL("https://news.google.com/rss?hl=bn&gl=BD&ceid=BD:bn").openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 15000
                val xml = c.inputStream.bufferedReader().use { it.readText() }
                items = Regex("<item>.*?<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml)
                    .map {
                        it.groupValues[1].replace("<![CDATA[", "").replace("]]>", "")
                            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim()
                    }
                    .take(15).toList()
            } catch (e: Exception) {
            }
            runOnUiThread {
                list.removeAllViews()
                if (items.isEmpty()) list.addView(tv("খবর আনা যায়নি, ইন্টারনেট দেখো।", 14f))
                for (h in items) {
                    val v = tv("• $h", 14f).apply {
                        background = box()
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        setOnClickListener { tts?.speak(h, TextToSpeech.QUEUE_FLUSH, null, "news") }
                    }
                    list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        setMargins(dp(2), dp(3), dp(2), dp(3))
                    })
                }
            }
        }.start()
        return b
    }

    private fun bnVoices() =
        (tts?.voices ?: emptySet()).filter { it.locale.language == "bn" }.sortedBy { it.name }

    override fun onInit(code: Int) {
        if (code != TextToSpeech.SUCCESS) return
        var r = tts?.setLanguage(Locale("bn", "BD"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            r = tts?.setLanguage(Locale("bn", "IN"))
        }
        ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        val saved = prefs.getString("voice", "") ?: ""
        val v = bnVoices().firstOrNull { it.name == saved }
        if (v != null) tts?.voice = v
        tts?.setPitch(1.2f)
        tts?.setSpeechRate(1.0f)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                rangeCount = 0
                runOnUiThread {
                    setOrb(3)
                }
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                lastWordAt = SystemClock.uptimeMillis()
                wordLen = end - start
                rangeCount++
            }
            override fun onDone(id: String?) {
                runOnUiThread { finishTurn() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                runOnUiThread { finishTurn() }
            }
        })
    }

    private fun finishTurn() {
        busy = false
        setOrb(1)
        startListening()
    }

    private fun addMsg(text: String, mine: Boolean, save: Boolean) {
        val b = tv(text, 15f).apply {
            background = box(if (mine) Color.parseColor("#2A1B6B") else card)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
            gravity = if (mine) Gravity.END else Gravity.START
        }
        chatLog.addView(b, lp)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        if (save) {
            val arr = try {
                JSONArray(prefs.getString("chat", "[]"))
            } catch (e: Exception) {
                JSONArray()
            }
            arr.put(JSONObject().put("m", mine).put("t", text))
            val out = JSONArray()
            for (i in maxOf(0, arr.length() - 120) until arr.length()) out.put(arr.get(i))
            prefs.edit().putString("chat", out.toString()).apply()
        }
    }

    private fun loadChat() {
        val arr = try {
            JSONArray(prefs.getString("chat", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            addMsg(o.getString("t"), o.getBoolean("m"), false)
        }
        for (i in maxOf(0, arr.length() - 16) until arr.length()) {
            val o = arr.getJSONObject(i)
            pushTurn(if (o.getBoolean("m")) "user" else "model", o.getString("t"))
        }
        while (history.length() > 0 && history.getJSONObject(0).getString("role") == "model") {
            history.remove(0)
        }
    }

    private fun pushTurn(role: String, text: String) {
        val n = history.length()
        if (n > 0) {
            val last = history.getJSONObject(n - 1)
            if (last.getString("role") == role) {
                val part = last.getJSONArray("parts").getJSONObject(0)
                part.put("text", part.getString("text") + "\n" + text)
                return
            }
        }
        history.put(
            JSONObject().put("role", role)
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
    }

    private fun trimHistory() {
        while (history.length() > 24) history.remove(0)
        while (history.length() > 0 && history.getJSONObject(0).getString("role") == "model") {
            history.remove(0)
        }
    }

    private fun greet() {
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            status.text = "মেনু → SETTINGS-এ গিয়ে Gemini চাবি বসাও"
            return
        }
        val hello = if (history.length() > 0)
            "(সাঈদ এইমাত্র অ্যাপ খুলেছে। আগের কথার সূত্র ধরে এক বাক্যে স্বাগত জানাও বা কিছু জিজ্ঞেস করো।)"
        else
            "(সাঈদ প্রথমবার অ্যাপ খুলেছে। এক বাক্যে নিজের পরিচয় দিয়ে হ্যালো বলো।)"
        pushTurn("user", hello)
        runChat(key, null)
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        addMsg(text, true, true)
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            addMsg("আগে মেনু থেকে SETTINGS-এ গিয়ে Gemini চাবি বসাও।", false, false)
            status.text = "Gemini চাবি দরকার"
            return
        }
        pushTurn("user", text)
        runChat(key, null)
    }

    private fun runChat(key: String, audio: ByteArray?) {
        busy = true
        stopListening()
        setOrb(2)
        Thread {
            var r: String? = null
            var e: String? = null
            try {
                val b64 = if (audio != null) Base64.encodeToString(wavOf(audio), Base64.NO_WRAP) else null
                r = callGemini(key, b64)
            } catch (ex: Exception) {
                e = ex.message ?: "অজানা সমস্যা"
            }
            val okReply = r
            val errMsg = e
            runOnUiThread {
                if (okReply == null) {
                    if (audio == null && history.length() > 0) history.remove(history.length() - 1)
                    addMsg("সমস্যা হয়েছে: $errMsg", false, false)
                    finishTurn()
                    status.text = "সমস্যা হয়েছে (মেনু → CHAT দেখো)"
                } else {
                    handleReply(okReply, audio != null)
                }
            }
        }.start()
    }

    private fun buildSoul(): String {
        val soul = prefs.getString("soul", DEFAULT_SOUL) ?: DEFAULT_SOUL
        val mem = getList("mem").joinToString("\n") { "- " + it }
        val tasks = getList("tasks").joinToString("\n") { "- " + it }
        val li = prefs.getInt("len", 0).coerceIn(0, 2)
        val si = prefs.getInt("style", 0).coerceIn(0, 2)
        return soul + "\n\n" + RULES + "\n\nমনে রাখা কথা:\n" + mem + "\n\nআজকের কাজ:\n" + tasks +
            "\n\nউত্তরের দৈর্ঘ্য (এটা আগের সব নির্দেশের চেয়ে অগ্রাধিকার পাবে): " + LEN_RULES[li] +
            "\nকথা বলার ধরন: " + STYLE_RULES[si]
    }

    private fun callGemini(key: String, audioB64: String?): String {
        val contents = JSONArray(history.toString())
        if (audioB64 != null) {
            val parts = JSONArray()
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", audioB64)
                )
            )
            parts.put(JSONObject().put("text", VOICE_NOTE))
            contents.put(JSONObject().put("role", "user").put("parts", parts))
        }
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildSoul())))
            )
            .put("contents", contents)
        var lastErr = "অজানা সমস্যা"
        val savedModel = prefs.getString("model", "") ?: ""
        val order = if (savedModel.isNotEmpty() && MODELS.contains(savedModel))
            listOf(savedModel) + MODELS.filter { it != savedModel } else MODELS
        for (m in order) {
            if (m.contains("2.5")) {
                body.put(
                    "generationConfig",
                    JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                )
            } else {
                body.remove("generationConfig")
            }
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code in 200..299) {
                val cands = JSONObject(text).optJSONArray("candidates")
                if (cands == null || cands.length() == 0) throw Exception("উত্তর খালি এসেছে, আবার বলো")
                val parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                prefs.edit().putString("model", m).apply()
                return sb.toString()
            }
            if (code == 429) throw Exception("ফ্রি লিমিট শেষ, একটু পরে বলো")
            var msg = ""
            try {
                msg = JSONObject(text).getJSONObject("error").optString("message")
            } catch (ex: Exception) {
            }
            lastErr = ("কোড " + code + " (" + m + ") " + msg).take(220)
            if (code != 404 && code != 400) break
        }
        throw Exception(lastErr)
    }

    private fun handleReply(raw: String, voice: Boolean) {
        var text = raw
        if (voice) {
            val hre = Regex("\\[\\[HEARD:(.*?)\\]\\]", RegexOption.DOT_MATCHES_ALL)
            val heard = hre.find(text)?.groupValues?.get(1)?.trim() ?: ""
            text = text.replace(hre, "").trim()
            // মডেল ভুল করে সাঈদের কথা আবার বললে সেটা কেটে ফেলা
            if (heard.isNotBlank() && text.startsWith(heard)) {
                text = text.removePrefix(heard)
                    .trimStart(' ', '\n', '।', ',', ':', '"', '\u201C', '\u201D')
            }
            if (heard.isBlank() && text.isBlank()) {
                finishTurn()
                return
            }
            val said = if (heard.isBlank()) "(কণ্ঠে বলেছে)" else heard
            addMsg(said, true, true)
            pushTurn("user", said)
        }
        pushTurn("model", if (text.isBlank()) "ঠিক আছে" else text)
        trimHistory()
        val re = Regex("\\[\\[(\\w+):(.*?)\\]\\]")
        val cmds = re.findAll(text).toList()
        val clean = text.replace(re, "").replace("*", "").trim()
        if (clean.isNotEmpty()) addMsg(clean, false, true)
        if (cmds.isNotEmpty()) {
            ui.postDelayed({
                for (c in cmds) runCommand(c.groupValues[1].uppercase(), c.groupValues[2].trim())
            }, 500)
        }
        if (ttsReady && clean.isNotEmpty()) {
            stopListening()
            setOrb(3)
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "angela")
        } else {
            finishTurn()
        }
    }

    private fun runCommand(type: String, arg: String) {
        try {
            when (type) {
                "OPEN" -> openApp(arg)
                "YOUTUBE" -> playYoutube(arg)
                "YTSEARCH" -> youtubeSearch(arg)
                "CALL" -> {
                    val converted = arg.map {
                        if (it.isDigit()) Character.getNumericValue(it).toString() else it.toString()
                    }.joinToString("")
                    val num = converted.filter { it.isDigit() || it == '+' }
                    if (num.length < 3) {
                        addMsg("নম্বরটা বুঝতে পারিনি।", false, false)
                    } else {
                        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                            PackageManager.PERMISSION_GRANTED
                        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        launch(Intent(action, Uri.parse("tel:" + Uri.encode(num))))
                    }
                }
                "MEMORY" -> putList("mem", getList("mem") + arg.replace("\n", " "))
                "TASK" -> putList("tasks", getList("tasks") + arg.replace("\n", " "))
            }
        } catch (e: Exception) {
            addMsg("কাজটা করতে পারলাম না: " + e.message, false, false)
        }
    }

    // ---------- ইউটিউব: খুঁজে প্রথম ভিডিও সরাসরি চালানো ----------
    private fun ytLaunch(uri: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.google.android.youtube")
        try {
            launch(i)
        } catch (e: Exception) {
            val web = if (uri.startsWith("vnd.youtube:"))
                "https://www.youtube.com/watch?v=" + uri.removePrefix("vnd.youtube:") else uri
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
        }
    }

    private fun youtubeSearch(q: String) {
        ytLaunch("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))
    }

    private fun playYoutube(q: String) {
        Thread {
            var id: String? = null
            try {
                val u = "https://www.youtube.com/results?hl=en&search_query=" + URLEncoder.encode(q, "UTF-8")
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                )
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                c.setRequestProperty("Cookie", "CONSENT=YES+1")
                val html = c.inputStream.bufferedReader().use { it.readText() }
                id = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")
                    .find(html)?.groupValues?.get(1)
            } catch (e: Exception) {
            }
            val vid = id
            runOnUiThread {
                try {
                    if (vid != null) ytLaunch("vnd.youtube:" + vid) else youtubeSearch(q)
                } catch (e: Exception) {
                    addMsg("ইউটিউব খুলতে পারলাম না: " + e.message, false, false)
                }
            }
        }.start()
    }

    // ব্যাকগ্রাউন্ড থেকেও অ্যাপ খুলতে অ্যাপ-কনটেক্সট ও নতুন টাস্ক ব্যবহার হয়
    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(i)
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val q = name.lowercase().trim()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        if (hit == null) {
            addMsg("\"" + name + "\" নামে কোনো অ্যাপ খুঁজে পেলাম না।", false, false)
            return
        }
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
        if (i != null) launch(i)
    }
}
