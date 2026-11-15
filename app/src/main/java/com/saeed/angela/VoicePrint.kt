package com.saeed.angela

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * সাঈদের কণ্ঠের ছাপ। MFCC ও পিচ থেকে ছোট একটা বৈশিষ্ট্য-ভেক্টর বানিয়ে শেখানো কণ্ঠের সাথে মেলায়।
 * সবকিছু ফোনের ভেতরেই চলে, ইন্টারনেটে কিছু যায় না।
 */
object VoicePrint {
    private const val RATE = 16000
    private const val FRAME = 400
    private const val HOP = 160
    private const val NFFT = 512
    private const val NMEL = 26
    private const val NCEP = 13
    private const val DIM = NCEP * 2 + 2

    private val window = FloatArray(FRAME) { (0.54 - 0.46 * cos(2.0 * PI * it / (FRAME - 1))).toFloat() }
    private val melBank: Array<FloatArray> by lazy { buildMel() }
    private val dct: Array<FloatArray> by lazy {
        Array(NCEP) { k -> FloatArray(NMEL) { n -> cos(PI * (k + 1) * (n + 0.5) / NMEL).toFloat() } }
    }

    private fun hz2mel(f: Double): Double = 2595.0 * log10(1.0 + f / 700.0)
    private fun mel2hz(m: Double): Double = 700.0 * (Math.pow(10.0, m / 2595.0) - 1.0)

    private fun buildMel(): Array<FloatArray> {
        val lo = hz2mel(80.0)
        val hi = hz2mel(7600.0)
        val pts = DoubleArray(NMEL + 2) { mel2hz(lo + (hi - lo) * it / (NMEL + 1)) }
        val bins = IntArray(NMEL + 2) { ((NFFT + 1) * pts[it] / RATE).toInt().coerceIn(0, NFFT / 2) }
        return Array(NMEL) { m ->
            val f = FloatArray(NFFT / 2 + 1)
            for (k in bins[m] until bins[m + 1]) f[k] = (k - bins[m]).toFloat() / max(1, bins[m + 1] - bins[m])
            for (k in bins[m + 1] until bins[m + 2]) f[k] = (bins[m + 2] - k).toFloat() / max(1, bins[m + 2] - bins[m + 1])
            f
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] = re[a] + vr
                    im[a] = im[a] + vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun pitch(x: FloatArray, o: Int): Float {
        var r0 = 0f
        for (i in 0 until FRAME) r0 += x[o + i] * x[o + i]
        if (r0 < 1f) return 0f
        var best = 0f
        var bestLag = 0
        for (lag in 40..267) {
            var r = 0f
            for (i in 0 until FRAME - lag) r += x[o + i] * x[o + i + lag]
            if (r > best) {
                best = r
                bestLag = lag
            }
        }
        return if (bestLag > 0 && best / r0 > 0.3f) RATE.toFloat() / bestLag else 0f
    }

    /** ১৬ বিট PCM থেকে বৈশিষ্ট্য-ভেক্টর। কথা খুব কম হলে null */
    fun extract(pcm: ByteArray): FloatArray? {
        val n = pcm.size / 2
        if (n < FRAME * 10) return null
        val x = FloatArray(n)
        for (i in 0 until n) {
            val lo = pcm[2 * i].toInt() and 0xFF
            val hi = pcm[2 * i + 1].toInt()
            x[i] = ((hi shl 8) or lo).toShort().toFloat()
        }
        for (i in n - 1 downTo 1) x[i] = x[i] - 0.97f * x[i - 1]
        val frames = (n - FRAME) / HOP + 1
        val rms = FloatArray(frames)
        for (f in 0 until frames) {
            var s = 0.0
            val o = f * HOP
            for (i in 0 until FRAME) {
                val v = x[o + i]
                s += v * v
            }
            rms[f] = sqrt(s / FRAME).toFloat()
        }
        val sorted = rms.sortedArray()
        val p90 = sorted[(frames * 0.9f).toInt().coerceAtMost(frames - 1)]
        val gate = max(p90 * 0.25f, 60f)
        val ceps = ArrayList<FloatArray>()
        val pitches = ArrayList<Float>()
        val re = FloatArray(NFFT)
        val im = FloatArray(NFFT)
        for (f in 0 until frames) {
            if (rms[f] < gate) continue
            val o = f * HOP
            java.util.Arrays.fill(re, 0f)
            java.util.Arrays.fill(im, 0f)
            for (i in 0 until FRAME) re[i] = x[o + i] * window[i]
            fft(re, im)
            val pw = FloatArray(NFFT / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
            val logm = FloatArray(NMEL)
            for (m in 0 until NMEL) {
                var e = 0f
                val fb = melBank[m]
                for (k in pw.indices) e += pw[k] * fb[k]
                logm[m] = ln(e + 1e-3f)
            }
            val c = FloatArray(NCEP)
            for (k in 0 until NCEP) {
                var s = 0f
                for (m in 0 until NMEL) s += dct[k][m] * logm[m]
                c[k] = s
            }
            ceps.add(c)
            val pch = pitch(x, o)
            if (pch > 0f) pitches.add(pch)
        }
        if (ceps.size < 40) return null
        val out = FloatArray(DIM)
        for (k in 0 until NCEP) {
            var m = 0f
            for (c in ceps) m += c[k]
            m /= ceps.size
            var v = 0f
            for (c in ceps) v += (c[k] - m) * (c[k] - m)
            out[k] = m
            out[NCEP + k] = sqrt(v / ceps.size)
        }
        if (pitches.size >= 10) {
            val lp = FloatArray(pitches.size) { ln(pitches[it]) * 10f }
            lp.sort()
            var m = 0f
            for (v in lp) m += v
            m /= lp.size
            var v2 = 0f
            for (v in lp) v2 += (v - m) * (v - m)
            out[2 * NCEP] = lp[lp.size / 2]
            out[2 * NCEP + 1] = sqrt(v2 / lp.size)
        }
        return out
    }

    private fun floorFor(i: Int): Float = if (i < NCEP) 1.2f else if (i < 2 * NCEP) 0.8f else 3f

    private fun dist(v: FloatArray, mu: FloatArray, sd: FloatArray): Float {
        var s = 0f
        for (i in v.indices) {
            val d = (v[i] - mu[i]) / sd[i]
            s += d * d
        }
        return sqrt(s / v.size)
    }

    /** শেখানো নমুনাগুলো থেকে কণ্ঠের প্রোফাইল (JSON লেখা) */
    fun buildProfile(vs: List<FloatArray>): String {
        val n = vs.size
        val dim = vs[0].size
        val mu = FloatArray(dim)
        for (v in vs) for (i in 0 until dim) mu[i] += v[i] / n
        val sd = FloatArray(dim)
        for (i in 0 until dim) {
            var s = 0f
            for (v in vs) s += (v[i] - mu[i]) * (v[i] - mu[i])
            sd[i] = max(sqrt(s / n), floorFor(i))
        }
        val ds = ArrayList<Float>()
        for (v in vs) {
            val loo = FloatArray(dim) { (mu[it] * n - v[it]) / (n - 1) }
            ds.add(dist(v, loo, sd))
        }
        var mean = 0f
        for (d in ds) mean += d
        mean /= ds.size
        var vr = 0f
        for (d in ds) vr += (d - mean) * (d - mean)
        val sdv = sqrt(vr / ds.size)
        val thr = max(max(mean + 2f * sdv, mean * 1.4f), 1.0f)
        val o = JSONObject()
        o.put("mu", JSONArray(mu.map { it.toDouble() }))
        o.put("sd", JSONArray(sd.map { it.toDouble() }))
        o.put("thr", thr.toDouble())
        return o.toString()
    }

    /** (দূরত্ব, সীমা) ফেরত দেয়। দূরত্ব সীমার ভেতরে থাকলে এটা সাঈদের কণ্ঠ ধরা হয় */
    fun check(profile: String, v: FloatArray, sens: Float): Pair<Float, Float> {
        val o = JSONObject(profile)
        val mu = FloatArray(v.size)
        val sd = FloatArray(v.size)
        val ma = o.getJSONArray("mu")
        val sa = o.getJSONArray("sd")
        for (i in v.indices) {
            mu[i] = ma.getDouble(i).toFloat()
            sd[i] = sa.getDouble(i).toFloat()
        }
        return Pair(dist(v, mu, sd), o.getDouble("thr").toFloat() * sens)
    }
}
