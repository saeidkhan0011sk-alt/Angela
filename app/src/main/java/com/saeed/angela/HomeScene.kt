package com.saeed.angela

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** RGB থিমের রঙের সেট */
object AngelaTheme {
    val names = listOf("নীল-বেগুনি", "সাইবার সবুজ", "গোলাপি-ম্যাজেন্টা")
    val sets = arrayOf(
        intArrayOf(Color.rgb(56, 189, 248), Color.rgb(139, 92, 246), Color.rgb(236, 72, 153)),
        intArrayOf(Color.rgb(34, 211, 238), Color.rgb(52, 211, 153), Color.rgb(163, 230, 53)),
        intArrayOf(Color.rgb(244, 114, 182), Color.rgb(217, 70, 239), Color.rgb(139, 92, 246))
    )
}

/**
 * Angela-র প্রিমিয়াম হোম স্ক্রিন: পুরোটা কোডে আঁকা ও অ্যানিমেটেড।
 * মুখ (পলক, শ্বাস, মাথা নড়া, ঠোঁট), হলোগ্রাফিক রিং, RGB মাইক বোতাম, ঢেউ, কণা, নিচের নেভিগেশন।
 */
class HomeScene(
    context: Context,
    private val stateOf: () -> Int,
    private val levelOf: () -> Float,
    private val speechOf: () -> Float,
    private val micOn: () -> Boolean,
    private val paused: () -> Boolean,
    private val themeOf: () -> Int,
    private val intensityOf: () -> Float,
    private val particlesOn: () -> Boolean,
    private val faceAnimOn: () -> Boolean,
    private val onAction: (String) -> Unit
) : View(context) {

    var topInset = 0f
    var botInset = 0f
    var navActive = 0

    private val dens = context.resources.displayMetrics.density
    private val face: Bitmap = BitmapFactory.decodeResource(
        context.resources, R.drawable.angela_logo,
        BitmapFactory.Options().apply { inScaled = false }
    )
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private val skinHi = Color.rgb(150, 166, 192)
    private val skinLo = Color.rgb(104, 120, 146)

    private var last = 0L
    private var smooth = 0f
    private var jaw = 0f
    private var lookX = 0f
    private var lookTarget = 0f
    private var nextBlink = 0L
    private var blinkStart = 0L
    private var tapCount = 0
    private var bgShader: LinearGradient? = null
    private var bgH = 0f
    private var sweepTheme = -1
    private val sweeps = arrayOfNulls<SweepGradient>(3)
    private var micSweep: SweepGradient? = null

    private class Ripple(val x: Float, val y: Float, val t0: Long, val color: Int)

    private val ripples = ArrayList<Ripple>()
    private val rnd = java.util.Random(11)
    private val n = 46
    private val px = FloatArray(n) { rnd.nextFloat() }
    private val py = FloatArray(n) { rnd.nextFloat() }
    private val pv = FloatArray(n) { 0.008f + rnd.nextFloat() * 0.025f }
    private val ps = FloatArray(n) { 0.7f + rnd.nextFloat() * 1.8f }
    private val stx = FloatArray(70) { rnd.nextFloat() }
    private val sty = FloatArray(70) { rnd.nextFloat() }
    private val stp = FloatArray(70) { rnd.nextFloat() }

    private val rMic = RectF()
    private val rChat = RectF()
    private val rTools = RectF()
    private val rMenu = RectF()
    private val rNav = Array(5) { RectF() }
    private val tmpPath = Path()
    private val jawPath = Path()
    private val mx = Matrix()

    private fun withA(c: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun lerp(a: Int, b: Int, u: Float): Int {
        val v = u.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * v).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * v).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * v).toInt()
        )
    }

    private fun grad3(pal: IntArray, u: Float): Int =
        if (u < 0.5f) lerp(pal[0], pal[1], u * 2f) else lerp(pal[1], pal[2], (u - 0.5f) * 2f)

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        if (r < 1f) return
        p.shader = RadialGradient(
            cx, cy, r, withA(color, alpha), withA(color, 0), Shader.TileMode.CLAMP
        )
        p.xfermode = screen
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.xfermode = null
    }

    private fun ensureShaders(pal: IntArray, theme: Int) {
        if (theme == sweepTheme) return
        sweepTheme = theme
        val cols = intArrayOf(pal[0], pal[1], pal[2], pal[0])
        for (i in 0 until 3) sweeps[i] = SweepGradient(0f, 0f, cols, null)
        micSweep = SweepGradient(0f, 0f, cols, null)
    }

    fun addRipple(x: Float, y: Float) {
        val pal = AngelaTheme.sets[themeOf().coerceIn(0, AngelaTheme.sets.size - 1)]
        ripples.add(Ripple(x, y, SystemClock.uptimeMillis(), pal[tapCount % 3]))
        tapCount++
        invalidate()
    }

    private fun eyelid(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, close: Float) {
        if (close <= 0.03f) return
        c.save()
        tmpPath.reset()
        tmpPath.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
        c.clipPath(tmpPath)
        val bottom = cy - ry + 2f * ry * close
        p.shader = LinearGradient(0f, cy - ry, 0f, cy + ry, skinHi, skinLo, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        p.xfermode = null
        p.alpha = 255
        c.drawRect(cx - rx, cy - ry, cx + rx, bottom, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * dens
        p.color = Color.argb(220, 20, 28, 48)
        c.drawLine(cx - rx, bottom, cx + rx, bottom, p)
        c.restore()
    }

    private fun navIcon(c: Canvas, i: Int, cx: Float, cy: Float, r: Float, color: Int) {
        p.xfermode = null
        p.shader = null
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * dens
        p.strokeJoin = Paint.Join.ROUND
        tmpPath.reset()
        when (i) {
            0 -> {
                tmpPath.moveTo(cx - r, cy)
                tmpPath.lineTo(cx, cy - r)
                tmpPath.lineTo(cx + r, cy)
                tmpPath.moveTo(cx - r * 0.7f, cy - r * 0.1f)
                tmpPath.lineTo(cx - r * 0.7f, cy + r)
                tmpPath.lineTo(cx + r * 0.7f, cy + r)
                tmpPath.lineTo(cx + r * 0.7f, cy - r * 0.1f)
                c.drawPath(tmpPath, p)
            }
            1 -> {
                c.drawCircle(cx - r * 0.45f, cy, r * 0.62f, p)
                c.drawCircle(cx + r * 0.45f, cy, r * 0.62f, p)
                c.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.55f, p)
            }
            2 -> {
                tmpPath.moveTo(cx, cy - r)
                tmpPath.lineTo(cx + r * 0.28f, cy - r * 0.28f)
                tmpPath.lineTo(cx + r, cy)
                tmpPath.lineTo(cx + r * 0.28f, cy + r * 0.28f)
                tmpPath.lineTo(cx, cy + r)
                tmpPath.lineTo(cx - r * 0.28f, cy + r * 0.28f)
                tmpPath.lineTo(cx - r, cy)
                tmpPath.lineTo(cx - r * 0.28f, cy - r * 0.28f)
                tmpPath.close()
                p.style = Paint.Style.FILL
                c.drawPath(tmpPath, p)
            }
            3 -> {
                tmpPath.moveTo(cx + r * 0.2f, cy - r)
                tmpPath.lineTo(cx - r * 0.5f, cy + r * 0.1f)
                tmpPath.lineTo(cx, cy + r * 0.1f)
                tmpPath.lineTo(cx - r * 0.2f, cy + r)
                tmpPath.lineTo(cx + r * 0.5f, cy - r * 0.1f)
                tmpPath.lineTo(cx, cy - r * 0.1f)
                tmpPath.close()
                c.drawPath(tmpPath, p)
            }
            else -> {
                c.drawCircle(cx, cy, r * 0.5f, p)
                for (j in 0 until 8) {
                    val a = j * PI.toFloat() / 4f
                    c.drawLine(
                        cx + cos(a) * r * 0.72f, cy + sin(a) * r * 0.72f,
                        cx + cos(a) * r, cy + sin(a) * r, p
                    )
                }
            }
        }
    }

    private fun sideBtn(
        c: Canvas, cx: Float, cy: Float, s: Float, kind: Int, label: String, pal: IntArray, r: RectF
    ) {
        r.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        p.xfermode = null
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom, Color.argb(215, 32, 22, 96), Color.argb(225, 12, 8, 44),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawRoundRect(r, s * 0.26f, s * 0.26f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * dens
        p.color = withA(pal[0], 170)
        c.drawRoundRect(r, s * 0.26f, s * 0.26f, p)
        p.color = Color.rgb(200, 230, 255)
        if (kind == 0) {
            val bw = s * 0.5f
            val bh = s * 0.36f
            val bubble = RectF(cx - bw / 2f, cy - bh / 2f - s * 0.04f, cx + bw / 2f, cy + bh / 2f - s * 0.04f)
            c.drawRoundRect(bubble, s * 0.1f, s * 0.1f, p)
            tmpPath.reset()
            tmpPath.moveTo(cx - bw * 0.25f, bubble.bottom)
            tmpPath.lineTo(cx - bw * 0.32f, bubble.bottom + s * 0.1f)
            tmpPath.lineTo(cx - bw * 0.05f, bubble.bottom)
            c.drawPath(tmpPath, p)
            p.style = Paint.Style.FILL
            for (d in -1..1) c.drawCircle(cx + d * bw * 0.22f, bubble.centerY(), s * 0.028f, p)
        } else {
            val sq = s * 0.17f
            val g = s * 0.07f
            val st0 = -(sq * 2f + g) / 2f
            for (a in 0..1) {
                for (b in 0..1) {
                    val x = cx + st0 + a * (sq + g)
                    val y = cy + st0 + b * (sq + g)
                    c.drawRoundRect(RectF(x, y, x + sq, y + sq), sq * 0.25f, sq * 0.25f, p)
                }
            }
        }
        tp.style = Paint.Style.FILL
        tp.color = Color.rgb(170, 160, 225)
        tp.textSize = 11f * dens
        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT
        c.drawText(label, cx, r.bottom + 15f * dens, tp)
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
        val k = intensityOf().coerceIn(0.2f, 1.6f)
        val fa = faceAnimOn()
        val theme = themeOf().coerceIn(0, AngelaTheme.sets.size - 1)
        val pal = AngelaTheme.sets[theme]
        ensureShaders(pal, theme)
        smooth += (levelOf() - smooth) * 0.35f
        val speech = if (st == 3) speechOf() else 0f
        val amp: Float = when (st) {
            1 -> if (on) smooth else 0.04f
            3 -> 0.2f + 0.8f * speech
            2 -> 0.22f
            else -> 0.04f
        }
        jaw += (speech - jaw) * 0.6f
        lookX += (lookTarget - lookX) * 0.08f

        val navAreaH = 84f * dens
        val top = topInset
        val ch = h - botInset - navAreaH - top
        fun yy(f: Float): Float = top + f * ch

        // ১. পেছন: গ্রাডিয়েন্ট, নীহারিকা, তারা
        if (bgShader == null || bgH != h) {
            bgShader = LinearGradient(
                0f, 0f, 0f, h, Color.rgb(4, 2, 14), Color.rgb(16, 8, 52), Shader.TileMode.CLAMP
            )
            bgH = h
        }
        p.shader = bgShader
        p.style = Paint.Style.FILL
        p.xfermode = null
        p.alpha = 255
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        glow(c, 0.2f * w + sin(t * 0.15f) * 0.05f * w, yy(0.25f), 0.55f * w, pal[1], (46 * k).toInt())
        glow(c, 0.85f * w + cos(t * 0.12f) * 0.05f * w, yy(0.55f), 0.6f * w, pal[2], (36 * k).toInt())
        glow(c, 0.5f * w, yy(0.92f), 0.7f * w, pal[0], (34 * k).toInt())
        p.style = Paint.Style.FILL
        p.xfermode = screen
        for (i in stx.indices) {
            val a = 40 + (90 * (0.5f + 0.5f * sin(t * 1.4f + stp[i] * 20f))).toInt()
            p.color = Color.argb(a, 200, 220, 255)
            c.drawCircle(stx[i] * w, sty[i] * h, (0.5f + stp[i]) * dens, p)
        }
        p.xfermode = null

        // ২. অ্যাঞ্জেলার মুখ
        val dw = min(1.3f * w, 0.703f * ch)
        val dl = 0.5f * w - dw / 2f
        val dtp = yy(0.07f)
        fun fx(f: Float): Float = dl + f * dw
        fun fy(f: Float): Float = dtp + f * dw

        glow(c, 0.5f * w, fy(0.36f), 0.55f * dw, pal[1], (70 * k).toInt())

        val bob = if (fa) sin(t * 1.3f) * 0.004f * dw * k else 0f
        val sc = if (fa) 1f + 0.006f * sin(t * 1.3f) * k else 1f
        var tilt = lookX * 2.5f
        if (fa) tilt += sin(t * 0.7f) * 1.1f * k
        if (st == 1) tilt += 2.2f
        if (st == 3) tilt += sin(t * 5f) * 0.8f * speech
        val shiftX = lookX * 0.012f * w
        val pivotX = 0.5f * w
        val pivotY = fy(0.78f)

        var closeAmt = 0f
        if (fa) {
            if (nextBlink == 0L) nextBlink = now + 2500L
            if (blinkStart == 0L && now >= nextBlink) blinkStart = now
            if (blinkStart != 0L) {
                val u = (now - blinkStart) / 200f
                if (u >= 1f) {
                    blinkStart = 0L
                    nextBlink = now + 2200L + rnd.nextInt(4200)
                } else {
                    closeAmt = if (u < 0.5f) u * 2f else (1f - u) * 2f
                }
            }
        }

        val layerRect = RectF(dl - 0.1f * dw, dtp - 0.05f * dw, dl + 1.1f * dw, dtp + 0.86f * dw)
        val lc = c.saveLayer(layerRect, null)
        c.save()
        c.translate(shiftX, bob)
        c.rotate(tilt, pivotX, pivotY)
        c.scale(sc, sc, pivotX, pivotY)
        val dst = RectF(dl, dtp, dl + dw, dtp + dw)
        val mouthY = fy(0.578f)
        val open = jaw * 0.012f * dw
        c.save()
        c.clipRect(dl, dtp, dl + dw, mouthY)
        c.drawBitmap(face, null, dst, bmpPaint)
        c.restore()
        c.save()
        c.clipRect(dl, mouthY, dl + dw, dtp + 0.78f * dw)
        c.drawBitmap(face, null, dst, bmpPaint)
        c.restore()
        if (open > 0.8f) {
            p.style = Paint.Style.FILL
            p.xfermode = null
            p.shader = null
            p.color = Color.argb(240, 6, 10, 26)
            c.drawOval(RectF(fx(0.462f), mouthY - open * 0.5f, fx(0.538f), mouthY + open + 1.5f), p)
            jawPath.reset()
            jawPath.moveTo(fx(0.43f), mouthY)
            jawPath.lineTo(fx(0.572f), mouthY)
            jawPath.lineTo(fx(0.56f), fy(0.63f))
            jawPath.lineTo(fx(0.535f), fy(0.672f))
            jawPath.lineTo(fx(0.465f), fy(0.672f))
            jawPath.lineTo(fx(0.44f), fy(0.63f))
            jawPath.close()
            c.save()
            c.translate(0f, open)
            c.clipPath(jawPath)
            c.drawBitmap(face, null, dst, bmpPaint)
            c.restore()
        }
        eyelid(c, fx(0.397f), fy(0.372f), 0.066f * dw, 0.03f * dw, closeAmt)
        eyelid(c, fx(0.590f), fy(0.372f), 0.066f * dw, 0.03f * dw, closeAmt)
        c.restore()
        // কিনারা মসৃণভাবে মিলিয়ে দেওয়ার মাস্ক
        val mcx = 0.5f * w
        val mcy = fy(0.37f)
        val mrx = 0.5f * dw
        val mry = 0.42f * dw
        val maskShader = RadialGradient(
            mcx, mcy, mrx, intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP
        )
        mx.reset()
        mx.setScale(1f, mry / mrx, mcx, mcy)
        maskShader.setLocalMatrix(mx)
        p.shader = maskShader
        p.xfermode = dstIn
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawRect(layerRect, p)
        p.shader = null
        p.xfermode = null
        c.restoreToCount(lc)

        // চোখের আলো
        c.save()
        c.translate(shiftX, bob)
        c.rotate(tilt, pivotX, pivotY)
        c.scale(sc, sc, pivotX, pivotY)
        val gl = 70f + 110f * amp + 40f * sin(t * (if (st == 2) 4f else 2.2f))
        val eyeA = (gl * min(k, 1.2f) * (1f - closeAmt)).toInt()
        val gx = lookX * 0.01f * dw
        val gy = if (st == 2) -0.012f * dw else 0f
        val eyeCol = Color.rgb(90, 180, 255)
        glow(c, fx(0.397f) + gx, fy(0.372f) + gy, 0.085f * dw, eyeCol, eyeA)
        glow(c, fx(0.590f) + gx, fy(0.372f) + gy, 0.085f * dw, eyeCol, eyeA)
        c.restore()

        // ৩. হলোগ্রাফিক প্ল্যাটফর্ম রিং
        val pcx = 0.5f * w
        val pcy = yy(0.595f)
        glow(c, pcx, pcy, 0.5f * w, pal[1], (50 + 70 * amp).toInt())
        p.style = Paint.Style.STROKE
        p.xfermode = screen
        for (i in 0 until 3) {
            val rr = (0.26f + 0.075f * i) * w * (1f + 0.03f * amp)
            val dir = if (i % 2 == 0) 1f else -1f
            mx.reset()
            mx.setRotate(t * (18f + 12f * i) * dir * (0.6f + 0.8f * k))
            mx.postTranslate(pcx, pcy)
            sweeps[i]?.setLocalMatrix(mx)
            p.shader = sweeps[i]
            p.strokeWidth = (2.6f - 0.5f * i + 1.6f * amp) * dens
            p.alpha = 210
            c.drawOval(RectF(pcx - rr, pcy - rr * 0.16f, pcx + rr, pcy + rr * 0.16f), p)
        }
        p.shader = null
        p.xfermode = null

        // ৪. কথার ঢেউ (মাইকের আসল শব্দ বা অ্যাঞ্জেলার কথায় ওঠে)
        val wy = yy(0.665f)
        val nb = 44
        val wx0 = 0.12f * w
        val wx1 = 0.88f * w
        p.xfermode = screen
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.0065f * w
        for (i in 0 until nb) {
            val u = i / (nb - 1f)
            val env = sin(PI.toFloat() * u)
            val noise = 0.45f + 0.55f * abs(sin(t * 6.5f + i * 0.83f) * cos(t * 3.1f + i * 0.37f))
            val bh2 = 0.003f * ch + amp * 0.045f * ch * env * noise * min(k, 1.3f)
            val x = wx0 + (wx1 - wx0) * u
            p.color = grad3(pal, u)
            p.alpha = 200
            c.drawLine(x, wy - bh2, x, wy + bh2, p)
        }
        p.xfermode = null

        // ৫. অবস্থার লেখা
        val label = when (st) {
            1 -> if (on) "শুনছি..." else "মাইক বন্ধ"
            2 -> "ভাবছি..."
            3 -> "কথা বলছি..."
            else -> "মাইক বন্ধ"
        }
        tp.style = Paint.Style.FILL
        tp.color = withA(pal[0], 235)
        tp.textSize = 14f * dens
        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(label, 0.5f * w, yy(0.725f), tp)

        // ৬. বড় RGB মাইক বোতাম
        val mR = 0.115f * w
        val baseY = yy(0.865f)
        val floatY = if (st == 0 || st == 1) sin(t * 1.6f) * 3f * dens * k else 0f
        val mcx2 = 0.5f * w
        val mcy2 = baseY + floatY
        rMic.set(mcx2 - mR, mcy2 - mR, mcx2 + mR, mcy2 + mR)
        glow(c, mcx2, mcy2, mR * (2f + amp * 0.8f), pal[1], ((60 + 130 * amp) * min(k, 1.3f)).toInt())
        if ((st == 1 && on) || st == 3) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * dens
            p.xfermode = screen
            for (i in 0 until 3) {
                val ph = (t * (if (st == 3) 0.9f else 0.7f) + i / 3f) % 1f
                val rr = mR * (1.05f + ph * (0.9f + 0.8f * amp))
                p.color = withA(grad3(pal, ph), ((1f - ph) * 180f * (0.4f + amp)).toInt())
                c.drawCircle(mcx2, mcy2, rr, p)
            }
            p.xfermode = null
        }
        val spin = when (st) {
            1 -> 90f
            2 -> 220f
            3 -> 130f
            else -> 28f
        }
        mx.reset()
        mx.setRotate(t * spin)
        mx.postTranslate(mcx2, mcy2)
        micSweep?.setLocalMatrix(mx)
        p.shader = micSweep
        p.style = Paint.Style.STROKE
        p.xfermode = screen
        p.strokeWidth = 13f * dens
        p.alpha = 50
        c.drawCircle(mcx2, mcy2, mR, p)
        p.xfermode = null
        p.strokeWidth = (5f + 3f * amp) * dens
        p.alpha = ((if (on) 170 else 70) + (85 * amp).toInt()).coerceAtMost(255)
        c.drawCircle(mcx2, mcy2, mR, p)
        p.shader = RadialGradient(
            mcx2, mcy2 - mR * 0.3f, mR, Color.rgb(34, 24, 96), Color.rgb(8, 6, 32), Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawCircle(mcx2, mcy2, mR - 4f * dens, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * dens
        p.color = withA(Color.WHITE, 40)
        c.drawArc(
            RectF(mcx2 - mR * 0.85f, mcy2 - mR * 0.85f, mcx2 + mR * 0.85f, mcy2 + mR * 0.85f),
            200f, 80f, false, p
        )
        val ic = if (on) Color.rgb(220, 240, 255) else Color.rgb(130, 140, 160)
        val cw = mR * 0.26f
        val chh = mR * 0.5f
        p.color = ic
        p.style = Paint.Style.FILL
        c.drawRoundRect(
            RectF(mcx2 - cw, mcy2 - chh * 0.95f, mcx2 + cw, mcy2 + chh * 0.15f), cw, cw, p
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = mR * 0.075f
        c.drawArc(
            RectF(mcx2 - cw * 1.7f, mcy2 - chh * 0.55f, mcx2 + cw * 1.7f, mcy2 + chh * 0.55f),
            0f, 180f, false, p
        )
        c.drawLine(mcx2, mcy2 + chh * 0.55f, mcx2, mcy2 + chh * 0.85f, p)
        c.drawLine(mcx2 - cw * 0.9f, mcy2 + chh * 0.85f, mcx2 + cw * 0.9f, mcy2 + chh * 0.85f, p)
        if (!on) {
            p.color = Color.rgb(239, 68, 68)
            c.drawLine(mcx2 - mR * 0.45f, mcy2 - mR * 0.45f, mcx2 + mR * 0.45f, mcy2 + mR * 0.45f, p)
        }

        // পাশের বোতাম: Chat ও Tools
        val sb = 0.15f * w
        sideBtn(c, 0.19f * w, baseY, sb, 0, "Chat", pal, rChat)
        sideBtn(c, 0.81f * w, baseY, sb, 1, "Tools", pal, rTools)

        // ৭. ওপরের অংশ: অবতার, শুভেচ্ছা, মেনু
        val hy = top + 36f * dens
        val ar = 24f * dens
        val cxA = 16f * dens + ar
        c.save()
        tmpPath.reset()
        tmpPath.addCircle(cxA, hy, ar, Path.Direction.CW)
        c.clipPath(tmpPath)
        val src = Rect(
            (face.width * 0.28f).toInt(), (face.height * 0.17f).toInt(),
            (face.width * 0.72f).toInt(), (face.height * 0.61f).toInt()
        )
        c.drawBitmap(face, src, RectF(cxA - ar, hy - ar, cxA + ar, hy + ar), bmpPaint)
        c.restore()
        mx.reset()
        mx.setRotate(t * 60f)
        mx.postTranslate(cxA, hy)
        sweeps[0]?.setLocalMatrix(mx)
        p.shader = sweeps[0]
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.2f * dens
        p.alpha = 255
        c.drawCircle(cxA, hy, ar, p)
        p.shader = null
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.LEFT
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.color = Color.WHITE
        tp.textSize = 17f * dens
        c.drawText("Hello, সাঈদ", cxA + ar + 12f * dens, hy - 2f * dens, tp)
        tp.typeface = Typeface.DEFAULT
        tp.color = Color.rgb(170, 160, 225)
        tp.textSize = 12f * dens
        c.drawText("আজ কীভাবে সাহায্য করতে পারি?", cxA + ar + 12f * dens, hy + 16f * dens, tp)
        val mr = 22f * dens
        val mcx3 = w - 16f * dens - mr
        rMenu.set(mcx3 - mr, hy - mr, mcx3 + mr, hy + mr)
        p.style = Paint.Style.FILL
        p.color = Color.argb(215, 22, 14, 70)
        c.drawCircle(mcx3, hy, mr, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dens
        p.color = withA(pal[0], 170)
        c.drawCircle(mcx3, hy, mr, p)
        p.strokeWidth = 2f * dens
        p.color = Color.rgb(200, 230, 255)
        c.drawLine(mcx3 - mr * 0.42f, hy - mr * 0.3f, mcx3 + mr * 0.42f, hy - mr * 0.3f, p)
        c.drawLine(mcx3 - mr * 0.42f, hy, mcx3 + mr * 0.1f, hy, p)
        c.drawLine(mcx3 - mr * 0.42f, hy + mr * 0.3f, mcx3 + mr * 0.42f, hy + mr * 0.3f, p)

        // ৮. নিচের নেভিগেশন বার
        val navTop = h - botInset - navAreaH + 10f * dens
        val navBot = h - botInset - 10f * dens
        val bar = RectF(0.04f * w, navTop, 0.96f * w, navBot)
        p.shader = LinearGradient(
            0f, bar.top, 0f, bar.bottom, Color.argb(235, 26, 18, 80), Color.argb(240, 10, 6, 38),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawRoundRect(bar, 22f * dens, 22f * dens, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dens
        p.color = withA(pal[1], 170)
        c.drawRoundRect(bar, 22f * dens, 22f * dens, p)
        val slot = bar.width() / 5f
        val names = arrayOf("Home", "Memory", "", "Activities", "Settings")
        for (i in 0 until 5) {
            val cx = bar.left + slot * (i + 0.5f)
            val raise = if (i == 2) 14f * dens else 0f
            rNav[i].set(bar.left + slot * i, bar.top - raise, bar.left + slot * (i + 1), bar.bottom)
            val active = navActive == i
            if (i == 2) {
                val cy = bar.top + 6f * dens
                val rr = 27f * dens
                glow(c, cx, cy, rr * 2f, pal[1], (90 + 50 * sin(t * 2f)).toInt())
                p.style = Paint.Style.FILL
                p.color = Color.rgb(14, 9, 52)
                c.drawCircle(cx, cy, rr, p)
                mx.reset()
                mx.setRotate(t * 70f)
                mx.postTranslate(cx, cy)
                sweeps[1]?.setLocalMatrix(mx)
                p.shader = sweeps[1]
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.6f * dens
                p.alpha = 255
                c.drawCircle(cx, cy, rr, p)
                p.shader = null
                navIcon(c, 2, cx, cy, 11f * dens, if (active) Color.WHITE else pal[0])
            } else {
                val cy = bar.centerY() - 7f * dens
                val col = if (active) pal[0] else Color.rgb(150, 150, 190)
                if (active) glow(c, cx, cy, 30f * dens, pal[0], 110)
                navIcon(c, i, cx, cy, 10f * dens, col)
                tp.style = Paint.Style.FILL
                tp.textAlign = Paint.Align.CENTER
                tp.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                tp.color = col
                tp.textSize = 10f * dens
                c.drawText(names[i], cx, cy + 25f * dens, tp)
            }
        }

        // ৯. ভাসমান কণা
        if (particlesOn()) {
            val cnt = (n * min(k, 1.2f) / 1.2f).toInt().coerceIn(6, n)
            p.xfermode = screen
            p.style = Paint.Style.FILL
            for (i in 0 until cnt) {
                py[i] -= pv[i] * dt
                if (py[i] < 0f) {
                    py[i] = 1f
                    px[i] = rnd.nextFloat()
                }
                val x = (px[i] + 0.012f * sin(t * 0.8f + i)) * w
                p.color = withA(pal[i % 3], 130)
                c.drawCircle(x, py[i] * h, ps[i] * dens, p)
            }
            p.xfermode = null
        }

        // ১০. ট্যাপ করলে রঙের ঢেউ
        val iter = ripples.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            val age = (now - r.t0) / 750f
            if (age >= 1f) {
                iter.remove()
                continue
            }
            val a = ((1f - age) * 200f).toInt()
            p.xfermode = screen
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * dens
            p.color = withA(r.color, a)
            c.drawCircle(r.x, r.y, age * 0.45f * w, p)
            p.strokeWidth = 1.5f * dens
            p.color = withA(r.color, a / 2)
            c.drawCircle(r.x, r.y, age * 0.3f * w, p)
            if (age < 0.45f) {
                p.style = Paint.Style.FILL
                p.color = withA(r.color, ((0.45f - age) / 0.45f * 34f).toInt())
                c.drawRect(0f, 0f, w, h, p)
            }
            p.xfermode = null
        }

        if (!paused() || ripples.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun hit(x: Float, y: Float): String? {
        val dx = x - rMic.centerX()
        val dy = y - rMic.centerY()
        val rr = rMic.width() / 2f
        if (dx * dx + dy * dy <= rr * rr) return "mic"
        if (rChat.contains(x, y)) return "chat"
        if (rTools.contains(x, y)) return "tools"
        val mx2 = x - rMenu.centerX()
        val my2 = y - rMenu.centerY()
        val mr = rMenu.width() / 2f
        if (mx2 * mx2 + my2 * my2 <= mr * mr) return "menu"
        for (i in 0 until 5) if (rNav[i].contains(x, y)) return "nav$i"
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                lookTarget = ((e.x / w) - 0.5f) * 2f
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                lookTarget = 0f
                val act = hit(e.x, e.y)
                if (act != null) {
                    addRipple(e.x, e.y)
                    onAction(act)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                lookTarget = 0f
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
