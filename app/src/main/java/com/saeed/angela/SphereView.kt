package com.saeed.angela

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SphereView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var active = false
    private var t = 0f
    private var amp = 0f
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#88FFFFFF")
    }
    private val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#66C4B5FD")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        t += if (active) 0.05f else 0.012f
        amp += ((if (active) 1f else 0f) - amp) * 0.08f
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.27f
        val pulse = 1f + amp * 0.07f * sin(t * 7f) + 0.015f * sin(t * 2f)
        val r = base * pulse

        glow.shader = RadialGradient(
            cx, cy, r * 2.1f,
            intArrayOf(Color.parseColor("#778B5CF6"), Color.parseColor("#2238BDF8"), Color.TRANSPARENT),
            floatArrayOf(0.25f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 2.1f, glow)

        body.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
            intArrayOf(Color.WHITE, Color.parseColor("#D8CCFF"), Color.parseColor("#7C5CFF"), Color.parseColor("#1D3FA8")),
            floatArrayOf(0f, 0.22f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, body)
        canvas.drawCircle(cx, cy, r, rim)

        for (k in 0..1) {
            canvas.save()
            canvas.rotate((if (k == 0) 1 else -1) * t * 18f + k * 55f, cx, cy)
            canvas.drawOval(RectF(cx - r * 1.45f, cy - r * 0.5f, cx + r * 1.45f, cy + r * 0.5f), orbit)
            canvas.restore()
        }

        for (i in 0 until 26) {
            val ang = t * (0.5f + (i % 3) * 0.25f) + i * 0.24f
            val rad = r * (1.25f + 0.3f * sin(t * 1.3f + i))
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad * 0.8f
            dot.color = if (i % 2 == 0) Color.WHITE else Color.parseColor("#67E8F9")
            dot.alpha = (90 + 140 * ((sin(t + i) + 1f) / 2f)).toInt()
            canvas.drawCircle(x, y, 2f + (i % 3), dot)
        }
        postInvalidateOnAnimation()
    }
}

class MicOrb(context: Context) : View(context) {

    // state: 0 মাইক বন্ধ, 1 শুনছে, 2 ভাবছে, 3 অ্যাঞ্জেলা বলছে
    var state = 1
    var level = 0f
    private var smooth = 0f
    private var t = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        t += 0.05f
        smooth += (level - smooth) * 0.3f
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.26f
        val tone = when (state) {
            0 -> Color.parseColor("#6B7280")
            1 -> Color.parseColor("#8B5CF6")
            2 -> Color.parseColor("#38BDF8")
            else -> Color.parseColor("#C4B5FD")
        }

        line.strokeWidth = 3f
        for (k in 0..2) {
            val ph = (t * 0.45f + k / 3f) % 1f
            val grow = if (state == 1) 0.4f + smooth * 1.2f else 0.35f
            val rr = base * (1.1f + ph * grow)
            val strength = if (state == 1) 70 + 180 * smooth else if (state == 0) 0f else 60f
            line.color = tone
            line.alpha = (strength * (1f - ph)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, rr, line)
        }

        val wob = if (state == 1) 1f + smooth * 0.18f else 1f
        fill.shader = RadialGradient(
            cx - base * 0.3f, cy - base * 0.3f, base * 1.6f,
            intArrayOf(Color.WHITE, tone, Color.parseColor("#111827")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, base * wob, fill)

        if (state == 2) {
            line.color = Color.WHITE
            line.alpha = 220
            line.strokeWidth = 5f
            canvas.drawArc(
                RectF(cx - base * 1.35f, cy - base * 1.35f, cx + base * 1.35f, cy + base * 1.35f),
                t * 200f, 90f, false, line
            )
        }
        if (state == 0) {
            line.color = Color.WHITE
            line.alpha = 230
            line.strokeWidth = 5f
            canvas.drawLine(cx - base * 0.5f, cy + base * 0.5f, cx + base * 0.5f, cy - base * 0.5f, line)
        }
        postInvalidateOnAnimation()
    }
}
