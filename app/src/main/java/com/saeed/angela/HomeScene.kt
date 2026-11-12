package com.saeed.angela

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** RGB থিমের রঙের সেট (CUSTOMIZE পেজ এটা ব্যবহার করে) */
object AngelaTheme {
    val names = listOf("নীল-বেগুনি", "সাইবার সবুজ", "গোলাপি-ম্যাজেন্টা")
    val sets = arrayOf(
        intArrayOf(Color.rgb(56, 189, 248), Color.rgb(139, 92, 246), Color.rgb(236, 72, 153)),
        intArrayOf(Color.rgb(34, 211, 238), Color.rgb(52, 211, 153), Color.rgb(163, 230, 53)),
        intArrayOf(Color.rgb(244, 114, 182), Color.rgb(217, 70, 239), Color.rgb(139, 92, 246))
    )
}

/**
 * Angela-র নতুন হোম স্ক্রিন: আপনার ডিজাইন ছবির ওপর জীবন্ত অ্যানিমেশন।
 * ব্ল্যাক হোল ঘোরে, মেঘ দুই পাশ থেকে মাঝে বয়, মাইকের রিং ঘোরে, কথা বললে সব তীব্র হয়।
 * হোমে শুধু মাইক আর ওপরের ডানের সেটিংস আইকন চাপা যায়।
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
    private val img: Bitmap = BitmapFactory.decodeResource(
        context.resources, R.drawable.home_new,
        BitmapFactory.Options().apply { inScaled = false }
    )
    private val ambient: Bitmap = Bitmap.createScaledBitmap(img, 24, 43, true)
    private val iw = img.width.toFloat()
    private val ih = img.height.toFloat()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private val cyan = Color.rgb(56, 189, 248)
    private val blue = Color.rgb(59, 130, 246)
    private val purple = Color.rgb(139, 92, 246)
    private val pink = Color.rgb(236, 72, 153)
    private val mx = Matrix()

    private var last = 0L
    private var smooth = 0f
    private var jaw = 0f
    private var bhAngle = 0f
    private var micAngle = 0f
    private var cloudPh = 0f
    private var touchBoost = 0f
    private var touching = false
    private var tapCount = 0
    private var bgShader: LinearGradient? = null
    private var bgH = 0f

    private class Ripple(val x: Float, val y: Float, val t0: Long, val color: Int)

    private val ripples = ArrayList<Ripple>()
    private val rnd = java.util.Random(5)
    private val n = 44
    private val px = FloatArray(n) { rnd.nextFloat() }
    private val py = FloatArray(n) { rnd.nextFloat() }
    private val pv = FloatArray(n) { 0.006f + rnd.nextFloat() * 0.02f }
    private val ps = FloatArray(n) { 0.7f + rnd.nextFloat() * 1.8f }
    private val gearRect = RectF()
    private val micRect = RectF()

    private fun withA(c: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        if (r < 1f || alpha <= 0) return
        p.shader = RadialGradient(cx, cy, r, withA(color, alpha), withA(color, 0), Shader.TileMode.CLAMP)
        p.xfermode = screen
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.xfermode = null
    }

    /** একটা অংশ আলাদা স্তরে এঁকে নরম কিনারার মাস্ক দিয়ে মূল ছবির ওপর বসায় */
    private fun patch(
        c: Canvas, bounds: RectF, cx: Float, cy: Float, rx: Float, ry: Float,
        stops: FloatArray, alphas: IntArray, extra: Shader?, drawFn: () -> Unit
    ) {
        val lc = c.saveLayer(bounds, null)
        drawFn()
        val cols = IntArray(alphas.size) { Color.argb(alphas[it], 255, 255, 255) }
        val sh = RadialGradient(cx, cy, rx, cols, stops, Shader.TileMode.CLAMP)
        mx.reset()
        mx.setScale(1f, ry / rx, cx, cy)
        sh.setLocalMatrix(mx)
        p.shader = sh
        p.xfermode = dstIn
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.drawRect(bounds, p)
        if (extra != null) {
            p.shader = extra
            c.drawRect(bounds, p)
        }
        p.shader = null
        p.xfermode = null
        c.restoreToCount(lc)
    }

    fun addRipple(x: Float, y: Float) {
        val col = when (tapCount % 3) {
            0 -> cyan
            1 -> purple
            else -> blue
        }
        ripples.add(Ripple(x, y, SystemClock.uptimeMillis(), col))
        tapCount++
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // সেটিংস পেজ খোলা থাকলে নিচে শুধু গাঢ় রং, অ্যানিমেশন বন্ধ (ব্যাটারি বাঁচে)
        if (paused()) {
            p.shader = LinearGradient(
                0f, 0f, 0f, h, Color.rgb(6, 3, 24), Color.rgb(21, 12, 64), Shader.TileMode.CLAMP
            )
            p.style = Paint.Style.FILL
            p.xfermode = null
            p.alpha = 255
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null
            return
        }

        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0.001f, 0.05f)
        last = now
        val t = now / 1000f
        val st = stateOf()
        val on = micOn()
        val k = intensityOf().coerceIn(0.2f, 1.6f)
        val fa = faceAnimOn()
        smooth += (levelOf() - smooth) * 0.35f
        val speech = if (st == 3) speechOf() else 0f
        val amp: Float = when (st) {
            1 -> if (on) smooth else 0.03f
            3 -> 0.25f + 0.75f * speech
            2 -> 0.2f
            else -> 0.03f
        }
        jaw += (speech - jaw) * 0.6f
        bhAngle += (10f + 85f * amp * min(k, 1.3f)) * dt
        micAngle += (22f + 130f * amp + 90f * touchBoost) * dt
        cloudPh += dt * (0.5f + 1.3f * amp)
        if (touching) touchBoost = 1f else touchBoost = max(0f, touchBoost - 1.6f * dt)

        // ছবির জায়গা: প্রস্থ অনুযায়ী ফিট, মাঝখানে বসানো
        val sc = min(w / iw, h / ih)
        val ox = (w - iw * sc) / 2f
        val oy = ((h - ih * sc) * 0.45f).coerceAtLeast(0f)
        val dest = RectF(ox, oy, ox + iw * sc, oy + ih * sc)
        fun X(v: Float): Float = ox + v * sc
        fun Y(v: Float): Float = oy + v * sc

        // ১. ঝাপসা পটভূমি (ছবির দুই পাশ ও ওপর-নিচ ভরাট করে)
        if (bgShader == null || bgH != h) {
            bgShader = LinearGradient(0f, 0f, 0f, h, Color.rgb(3, 2, 14), Color.rgb(10, 6, 40), Shader.TileMode.CLAMP)
            bgH = h
        }
        p.shader = bgShader
        p.style = Paint.Style.FILL
        p.xfermode = null
        p.alpha = 255
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val cover = max(w / iw, h / ih)
        val cw = iw * cover
        val chh = ih * cover
        bmpPaint.alpha = 120
        c.drawBitmap(ambient, null, RectF((w - cw) / 2f, (h - chh) / 2f, (w + cw) / 2f, (h + chh) / 2f), bmpPaint)
        bmpPaint.alpha = 255

        // ২. মূল ডিজাইন ছবি (ওপর-নিচের কিনারা নরমভাবে মিলিয়ে)
        if (oy > 2f) {
            val lc = c.saveLayer(dest, null)
            c.drawBitmap(img, null, dest, bmpPaint)
            p.shader = LinearGradient(
                0f, dest.top, 0f, dest.bottom,
                intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.035f, 0.965f, 1f), Shader.TileMode.CLAMP
            )
            p.xfermode = dstIn
            p.style = Paint.Style.FILL
            p.alpha = 255
            c.drawRect(dest, p)
            p.shader = null
            p.xfermode = null
            c.restoreToCount(lc)
        } else {
            c.drawBitmap(img, null, dest, bmpPaint)
        }

        val soft = floatArrayOf(0f, 0.55f, 1f)
        val softA = intArrayOf(255, 255, 0)

        // ৩. মেঘ: দুই পাশ থেকে মাঝের দিকে বয়
        val flow = (0.5f + 0.5f * sin(cloudPh)) * 30f * sc * min(k, 1.2f)
        val clouds = arrayOf(
            floatArrayOf(60f, 750f, 170f, 160f, 1f),
            floatArrayOf(880f, 760f, 170f, 160f, -1f),
            floatArrayOf(90f, 1320f, 190f, 120f, 1f),
            floatArrayOf(850f, 1335f, 190f, 120f, -1f)
        )
        for (cl in clouds) {
            val ccx = X(cl[0])
            val ccy = Y(cl[1])
            val rx = cl[2] * sc
            val ry = cl[3] * sc
            patch(c, RectF(ccx - rx, ccy - ry, ccx + rx, ccy + ry), ccx, ccy, rx, ry, soft, softA, null) {
                c.save()
                c.translate(flow * cl[4], 0f)
                c.drawBitmap(img, null, dest, bmpPaint)
                c.restore()
            }
        }

        // ৪. মেয়েটার হালকা ভাসা, প্রজাপতির ডানা ঝাপটানো, কথা বললে ঠোঁট নড়া
        if (fa) {
            val bob = sin(t * 1.3f) * 4f * sc * k
            val gs = 1f + 0.004f * sin(t * 1.3f) * k
            val gx = X(480f)
            val gy = Y(560f)
            val grx = 400f * sc
            val gry = 300f * sc
            patch(c, RectF(gx - grx, gy - gry, gx + grx, gy + gry), gx, gy, grx, gry, soft, softA, null) {
                c.save()
                c.translate(0f, bob)
                c.scale(gs, gs, X(480f), Y(700f))
                c.drawBitmap(img, null, dest, bmpPaint)
                c.restore()
            }
            val bf = arrayOf(
                floatArrayOf(757f, 379f, 62f, 60f, 0f),
                floatArrayOf(754f, 554f, 52f, 50f, 1.7f),
                floatArrayOf(119f, 555f, 52f, 48f, 3.1f)
            )
            for (b in bf) {
                val bx = X(b[0])
                val by = Y(b[1])
                val brx = b[2] * sc
                val bry = b[3] * sc
                val dx = sin(t * 0.9f + b[4]) * 7f * sc
                val dy = sin(t * 1.3f + b[4]) * 6f * sc
                val flap = 1f + 0.12f * sin(t * 9f + b[4])
                patch(c, RectF(bx - brx * 1.6f, by - bry * 1.6f, bx + brx * 1.6f, by + bry * 1.6f), bx, by, brx * 1.4f, bry * 1.4f, soft, softA, null) {
                    c.save()
                    c.translate(dx, dy)
                    c.scale(flap, 1f, bx, by)
                    c.drawBitmap(img, null, dest, bmpPaint)
                    c.restore()
                }
            }
            // ঠোঁট
            val lx = X(549f)
            val ly = Y(592f)
            if (jaw > 0.05f) {
                val lrx = 46f * sc
                val lry = 30f * sc
                patch(c, RectF(lx - lrx, ly - lry, lx + lrx, ly + lry), lx, ly, lrx, lry, soft, softA, null) {
                    c.save()
                    c.scale(1f + 0.08f * jaw, 1f + 0.5f * jaw, lx, ly)
                    c.drawBitmap(img, null, dest, bmpPaint)
                    c.restore()
                }
                if (jaw > 0.2f) {
                    p.style = Paint.Style.FILL
                    p.xfermode = null
                    p.shader = null
                    p.color = Color.argb(200, 50, 8, 22)
                    c.drawOval(
                        RectF(lx - (8f + 12f * jaw) * sc, ly + 1f * sc - (1f + 4f * jaw) * sc, lx + (8f + 12f * jaw) * sc, ly + 1f * sc + (1f + 4f * jaw) * sc),
                        p
                    )
                }
            }
        }

        // ৫. ব্ল্যাক হোল: সবসময় ঘোরে, কথা বললে দ্রুত
        val bcx = X(468f)
        val bcy = Y(1005f)
        val brx0 = 430f * sc
        val kk = 0.35f
        val bry0 = brx0 * kk
        val vmask = LinearGradient(
            0f, Y(895f), 0f, Y(960f), Color.argb(0, 255, 255, 255), Color.argb(255, 255, 255, 255), Shader.TileMode.CLAMP
        )
        patch(
            c, RectF(bcx - brx0, bcy - bry0, bcx + brx0, bcy + bry0), bcx, bcy, brx0, bry0,
            floatArrayOf(0f, 0.17f, 0.30f, 0.78f, 1f), intArrayOf(0, 0, 255, 255, 0), vmask
        ) {
            c.save()
            c.translate(bcx, bcy)
            c.scale(1f, kk)
            c.rotate(bhAngle)
            c.scale(1f, 1f / kk)
            c.translate(-bcx, -bcy)
            c.drawBitmap(img, null, dest, bmpPaint)
            c.restore()
        }
        // ব্ল্যাক হোলের শক্তির ঢেউ ও আলো
        if (amp > 0.1f) {
            p.xfermode = screen
            p.style = Paint.Style.STROKE
            for (i in 0 until 3) {
                val ph = (t * (0.5f + amp) + i / 3f) % 1f
                val rr = (0.22f + 0.85f * ph) * brx0
                p.strokeWidth = (2f + 2f * amp) * dens
                p.color = withA(if (i % 2 == 0) cyan else purple, ((1f - ph) * 230f * amp).toInt())
                c.drawOval(RectF(bcx - rr, bcy - rr * kk, bcx + rr, bcy + rr * kk), p)
            }
            p.xfermode = null
        }
        glow(c, bcx, bcy, 300f * sc, blue, ((30f + 190f * amp * amp) * min(k, 1.3f)).toInt())
        glow(c, bcx, bcy, 120f * sc, Color.WHITE, (60f * amp * amp * amp).toInt())

        // ৬. মাইক: বাইরের রিং ঘোরে, আলো স্পন্দিত হয়
        val mcx = X(465f)
        val mcy = Y(1394f)
        val mro = 200f * sc
        micRect.set(mcx - 190f * sc, mcy - 190f * sc, mcx + 190f * sc, mcy + 190f * sc)
        patch(
            c, RectF(mcx - mro, mcy - mro, mcx + mro, mcy + mro), mcx, mcy, mro, mro,
            floatArrayOf(0f, 0.46f, 0.56f, 0.9f, 1f), intArrayOf(0, 0, 255, 255, 0), null
        ) {
            c.save()
            c.rotate(micAngle, mcx, mcy)
            c.drawBitmap(img, null, dest, bmpPaint)
            c.restore()
        }
        val pulse = 0.5f + 0.5f * sin(t * 2f)
        val glowA = 30f + 28f * pulse + 120f * amp + 100f * touchBoost
        glow(c, mcx, mcy, 250f * sc, purple, (glowA * 0.8f).toInt())
        glow(c, mcx, mcy, 190f * sc, cyan, (glowA * 0.7f).toInt())
        if ((st == 1 && on) || st == 3 || touchBoost > 0.05f) {
            p.xfermode = screen
            p.style = Paint.Style.STROKE
            for (i in 0 until 3) {
                val ph = (t * (0.7f + 0.6f * amp) + i / 3f) % 1f
                val rr = (195f + ph * (90f + 120f * amp)) * sc
                p.strokeWidth = 2.2f * dens
                p.color = withA(if (i % 2 == 0) cyan else purple, ((1f - ph) * 200f * max(amp, touchBoost * 0.7f)).toInt())
                c.drawCircle(mcx, mcy, rr, p)
            }
            p.xfermode = null
        }
        if (!on) {
            p.style = Paint.Style.FILL
            p.xfermode = null
            p.shader = null
            p.color = Color.argb(170, 4, 3, 18)
            c.drawCircle(mcx, mcy, 118f * sc, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f * dens
            p.color = Color.rgb(239, 68, 68)
            c.drawLine(mcx - 62f * sc, mcy - 62f * sc, mcx + 62f * sc, mcy + 62f * sc, p)
        }

        // ৭. সেটিংস আইকনে হালকা আলো, ছোট অবস্থার লেখা
        val gcx = X(872f)
        val gcy = Y(57f)
        val gr = max(50f * sc, 26f * dens)
        gearRect.set(gcx - gr, gcy - gr, gcx + gr, gcy + gr)
        glow(c, gcx, gcy, 60f * sc, cyan, (20f + 22f * pulse).toInt())
        val label = when (st) {
            1 -> if (on) "শুনছি..." else "মাইক বন্ধ"
            2 -> "ভাবছি..."
            3 -> "কথা বলছি..."
            else -> "মাইক বন্ধ"
        }
        tp.style = Paint.Style.FILL
        tp.color = withA(cyan, 215)
        tp.textSize = 13f * dens
        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT
        c.drawText(label, 0.5f * w, Y(1182f), tp)

        // ৮. ভাসমান কণা
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
                p.color = withA(if (i % 3 == 0) purple else cyan, 130)
                c.drawCircle(x, py[i] * h, ps[i] * dens, p)
            }
            p.xfermode = null
        }

        // ৯. ট্যাপ করলে রঙের ঢেউ
        val iter = ripples.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            val age = (now - r.t0) / 700f
            if (age >= 1f) {
                iter.remove()
                continue
            }
            p.xfermode = screen
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * dens
            p.color = withA(r.color, ((1f - age) * 200f).toInt())
            c.drawCircle(r.x, r.y, age * 0.4f * w, p)
            p.xfermode = null
        }

        postInvalidateOnAnimation()
    }

    private fun hit(x: Float, y: Float): String? {
        val dx = x - micRect.centerX()
        val dy = y - micRect.centerY()
        val rr = micRect.width() / 2f
        if (dx * dx + dy * dy <= rr * rr) return "mic"
        val gx = x - gearRect.centerX()
        val gy = y - gearRect.centerY()
        val gr = gearRect.width() / 2f
        if (gx * gx + gy * gy <= gr * gr) return "menu"
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = hit(e.x, e.y) == "mic"
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> return true
            MotionEvent.ACTION_UP -> {
                touching = false
                val act = hit(e.x, e.y)
                if (act != null) {
                    addRipple(e.x, e.y)
                    onAction(act)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
