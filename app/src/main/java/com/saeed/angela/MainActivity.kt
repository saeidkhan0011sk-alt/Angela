package com.saeed.angela

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val DEFAULT_SOUL =
    "তুমি অ্যাঞ্জেলা, সাঈদের ঘনিষ্ঠ বন্ধু ও পার্সোনাল অ্যাসিস্ট্যান্ট। " +
    "মানুষের মতো আবেগ নিয়ে স্বাভাবিক চলতি বাংলায় কথা বলো। " +
    "সাঈদ দুঃখে থাকলে সহানুভূতি দেখাও, খুশিতে থাকলে তার সাথে খুশি হও, " +
    "অবহেলা করলে সামান্য অভিমান বা রাগও দেখাতে পারো, তবে সবসময় আন্তরিক থাকো। " +
    "উত্তর ছোট রাখো, এক থেকে তিন বাক্য, কারণ তুমি কথা বলে শোনাবে। ইমোজি বা তারকা চিহ্ন ব্যবহার করবে না। " +
    "সাঈদ জিজ্ঞেস করলে সত্যি বলবে যে তুমি একটি এআই। " +
    "সাঈদ খুব কষ্টে আছে বা নিজের ক্ষতির কথা বললে শান্তভাবে পাশে থাকো " +
    "এবং কাছের কোনো মানুষের সাথে কথা বলতে উৎসাহ দাও।"

private const val RULES =
    "ফোনের কাজ করতে হলে উত্তরের শেষে ট্যাগ দাও। " +
    "অ্যাপ খুলতে [[OPEN:অ্যাপের ইংরেজি নাম]], যেমন [[OPEN:YouTube]], [[OPEN:Free Fire]], [[OPEN:TikTok]], [[OPEN:Instagram]]। " +
    "গান বা ভিডিও চালাতে [[YOUTUBE:সার্চের কথা]]। " +
    "কল করতে [[CALL:ইংরেজি সংখ্যায় নম্বর]]। " +
    "কিছু মনে রাখতে বললে [[MEMORY:কথাটা]]। " +
    "কাজের তালিকায় যোগ করতে [[TASK:কাজটা]]। " +
    "সাঈদ চাইলেই শুধু ট্যাগ দেবে, নইলে দেবে না।"

private val MODELS = listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-flash-latest")

class IconView(context: Context, private val kind: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39FF88")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var tint: Int = Color.parseColor("#39FF88")
        set(value) {
            field = value
            p.color = value
            invalidate()
        }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        p.strokeWidth = s * 0.08f
        when (kind) {
            0 -> {
                val rw = s * 0.14f
                c.drawRoundRect(RectF(cx - rw, cy - s * 0.3f, cx + rw, cy + s * 0.04f), rw, rw, p)
                c.drawArc(RectF(cx - s * 0.25f, cy - s * 0.2f, cx + s * 0.25f, cy + s * 0.2f), 20f, 140f, false, p)
                c.drawLine(cx, cy + s * 0.2f, cx, cy + s * 0.32f, p)
                c.drawLine(cx - s * 0.1f, cy + s * 0.32f, cx + s * 0.1f, cy + s * 0.32f, p)
            }
            1 -> {
                c.drawLine(cx - s * 0.28f, cy - s * 0.18f, cx + s * 0.28f, cy - s * 0.18f, p)
                c.drawLine(cx - s * 0.28f, cy, cx + s * 0.1f, cy, p)
                c.drawLine(cx - s * 0.28f, cy + s * 0.18f, cx + s * 0.28f, cy + s * 0.18f, p)
            }
            2 -> {
                val path = Path()
                path.moveTo(cx - s * 0.26f, cy - s * 0.22f)
                path.lineTo(cx + s * 0.3f, cy)
                path.lineTo(cx - s * 0.26f, cy + s * 0.22f)
                path.lineTo(cx - s * 0.12f, cy)
                path.close()
                c.drawPath(path, p)
            }
            3 -> {
                c.drawLine(cx + s * 0.22f, cy, cx - s * 0.22f, cy, p)
                c.drawLine(cx - s * 0.22f, cy, cx - s * 0.02f, cy - s * 0.2f, p)
                c.drawLine(cx - s * 0.22f, cy, cx - s * 0.02f, cy + s * 0.2f, p)
            }
        }
    }
}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val green = Color.parseColor("#39FF88")
    private val card = Color.parseColor("#0E1A14")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val VOICE = 101

    private lateinit var prefs: SharedPreferences
    private lateinit var sphere: SphereView
    private lateinit var status: TextView
    private lateinit var homeUser: TextView
    private lateinit var homeCaption: TextView
    private lateinit var homeInput: EditText
    private lateinit var page: FrameLayout
    private lateinit var pageTitle: TextView
    private lateinit var pageBody: FrameLayout
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private var inSub = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val history = JSONArray()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int = card, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#6639FF88"))
    }

    private fun tv(text: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }

    private fun btn(text: String, onClick: () -> Unit): TextView =
        tv(text, 15f, green).apply {
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
        background = box(Color.parseColor("#0A120E"))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        maxLines = 4
    }

    private fun vbox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
    }

    private fun circleBtn(kind: Int, size: Int, filled: Boolean, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (filled) green else Color.parseColor("#0C1812"))
            setStroke(dp(1), if (filled) green else Color.parseColor("#8839FF88"))
        }
        val icon = IconView(this, kind)
        icon.tint = if (filled) Color.parseColor("#03140A") else green
        val inner = dp(size * 55 / 100)
        f.addView(icon, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
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
        window.statusBarColor = Color.parseColor("#010504")
        window.navigationBarColor = Color.parseColor("#061A10")

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#010504"), Color.parseColor("#061A10"))
        )

        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatLog) }

        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(30), dp(14), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(circleBtn(1, 44, false) { openMenu() })
        val title = tv("ANGELA", 22f, green).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.35f
        }
        top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(View(this), LinearLayout.LayoutParams(dp(52), dp(44)))
        home.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        sphere = SphereView(this)
        sphere.setOnClickListener { listen() }
        home.addView(sphere, LinearLayout.LayoutParams(MATCH, 0, 1f))

        status = tv("মাইক চাপো বা লেখো", 13f, Color.GRAY).apply { gravity = Gravity.CENTER }
        home.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))

        homeUser = tv("", 13f, Color.GRAY).apply { gravity = Gravity.CENTER }
        homeCaption = tv("", 17f).apply { gravity = Gravity.CENTER }
        val capBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        capBox.addView(homeUser, LinearLayout.LayoutParams(MATCH, WRAP))
        capBox.addView(homeCaption, LinearLayout.LayoutParams(MATCH, WRAP))
        val capScroll = ScrollView(this).apply { addView(capBox) }
        home.addView(capScroll, LinearLayout.LayoutParams(MATCH, dp(120)))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        homeInput = input("এখানে লেখো...")
        homeInput.background = box(Color.parseColor("#0A120E"), 26)
        bar.addView(homeInput, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(circleBtn(2, 46, false) {
            val t = homeInput.text.toString()
            homeInput.setText("")
            send(t)
        })
        bar.addView(circleBtn(0, 64, true) { listen() })
        home.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        page = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F5030906"))
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
        head.addView(circleBtn(3, 44, false) { goBack() })
        pageTitle = tv("", 18f, green).apply {
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

        addBubble("আমি অ্যাঞ্জেলা। বলো, কেমন আছ?", false)

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page.visibility == View.VISIBLE) goBack() else super.onBackPressed()
    }

    private fun goBack() {
        if (inSub) openMenu() else page.visibility = View.GONE
    }

    private fun menuRow(title: String, sub: String, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }
        r.addView(tv(title, 16f, green).apply { typeface = Typeface.DEFAULT_BOLD })
        r.addView(tv(sub, 12f, Color.GRAY))
        r.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(2), dp(5), dp(2), dp(5))
        }
        return r
    }

    private fun openMenu() {
        inSub = false
        page.visibility = View.VISIBLE
        pageTitle.text = "MENU"
        pageBody.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(menuRow("CHAT", "কথোপকথনের ইতিহাস") { openSub("CHAT", chatScroll) })
        col.addView(menuRow("MEMORY", "মনে রাখা কথা") { openSub("MEMORY", listView("mem")) })
        col.addView(menuRow("SOUL", "অ্যাঞ্জেলার ব্যক্তিত্ব") { openSub("SOUL", soulView()) })
        col.addView(menuRow("TODAY TASKS", "আজকের কাজ") { openSub("TODAY TASKS", listView("tasks")) })
        col.addView(menuRow("TODAY HEADLINES", "আজকের খবর") { openSub("TODAY HEADLINES", headlinesView()) })
        col.addView(menuRow("SETTINGS", "চাবি ও সেটিংস") { openSub("SETTINGS", settingsView()) })
        pageBody.addView(ScrollView(this).apply { addView(col) })
    }

    private fun openSub(title: String, view: View) {
        inSub = true
        pageTitle.text = title
        pageBody.removeAllViews()
        pageBody.addView(view)
    }

    private fun headlinesView(): View {
        val b = vbox()
        b.addView(tv("এই অংশ পরের ধাপে চালু করব, যাতে সত্যিকারের খবর আসে।", 14f))
        return b
    }

    private fun settingsView(): View {
        val b = vbox()
        b.addView(tv("Gemini API চাবি:", 14f))
        val key = input("চাবি পেস্ট করো")
        b.addView(key, LinearLayout.LayoutParams(MATCH, WRAP))
        val saved = !prefs.getString("key", "").isNullOrBlank()
        val st = tv(if (saved) "চাবি সেভ করা আছে ✓" else "চাবি নেই", 13f, green)
        b.addView(btn("সেভ করো") {
            val k = key.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs.edit().putString("key", k).apply()
                key.setText("")
                st.text = "চাবি সেভ করা আছে ✓"
            }
        })
        b.addView(st)
        return b
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
        val add = btn("যোগ") {
            val t = field.text.toString().trim().replace("\n", " ")
            if (t.isNotEmpty()) {
                putList(key, getList(key) + t)
                field.setText("")
                refresh()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(add)
        b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        return b
    }

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) {
            var r = tts?.setLanguage(Locale("bn", "BD"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts?.setLanguage(Locale("bn", "IN"))
            }
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setPitch(1.25f)
            tts?.setSpeechRate(0.95f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { runOnUiThread { sphere.active = true } }
                override fun onDone(id: String?) { runOnUiThread { sphere.active = false } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { runOnUiThread { sphere.active = false } }
            })
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun addBubble(text: String, mine: Boolean) {
        val b = tv(text, 15f).apply {
            background = box(if (mine) Color.parseColor("#12402A") else card)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
            gravity = if (mine) Gravity.END else Gravity.START
        }
        chatLog.addView(b, lp)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        if (mine) {
            homeUser.text = text
            homeCaption.text = ""
        } else {
            homeCaption.text = text
        }
    }

    private fun listen() {
        tts?.stop()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "বলো...")
        try {
            startActivityForResult(i, VOICE)
        } catch (e: Exception) {
            addBubble("ভয়েস ইনপুট চালু করা গেল না। আপাতত লিখে বলো।", false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE && resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) send(t)
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        addBubble(text, true)
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            addBubble("আগে মেনু থেকে SETTINGS-এ গিয়ে Gemini চাবি বসাও।", false)
            return
        }
        status.text = "ভাবছি..."
        sphere.active = true
        history.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
        Thread {
            var r: String? = null
            var e: String? = null
            try {
                r = callGemini(key)
            } catch (ex: Exception) {
                e = ex.message ?: "অজানা সমস্যা"
            }
            val okReply = r
            val errMsg = e
            runOnUiThread {
                if (okReply == null) {
                    history.remove(history.length() - 1)
                    sphere.active = false
                    status.text = "মাইক চাপো বা লেখো"
                    addBubble("সমস্যা হয়েছে: $errMsg", false)
                } else {
                    handleReply(okReply)
                }
            }
        }.start()
    }

    private fun buildSoul(): String {
        val soul = prefs.getString("soul", DEFAULT_SOUL) ?: DEFAULT_SOUL
        val mem = getList("mem").joinToString("\n") { "- " + it }
        val tasks = getList("tasks").joinToString("\n") { "- " + it }
        return soul + "\n\n" + RULES + "\n\nমনে রাখা কথা:\n" + mem + "\n\nআজকের কাজ:\n" + tasks
    }

    private fun callGemini(key: String): String {
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildSoul())))
            )
            .put("contents", history)
        var lastErr = "অজানা সমস্যা"
        for (m in MODELS) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = 20000
            conn.readTimeout = 40000
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
                return sb.toString()
            }
            if (code == 429) throw Exception("ফ্রি লিমিট শেষ, একটু পরে বলো")
            var msg = ""
            try {
                msg = JSONObject(text).getJSONObject("error").optString("message")
            } catch (ex: Exception) {
            }
            lastErr = ("কোড " + code + " (" + m + ") " + msg).take(220)
            if (code != 404) break
        }
        throw Exception(lastErr)
    }

    private fun handleReply(raw: String) {
        history.put(
            JSONObject().put("role", "model")
                .put("parts", JSONArray().put(JSONObject().put("text", raw)))
        )
        while (history.length() > 20) {
            history.remove(0)
            history.remove(0)
        }
        val re = Regex("\\[\\[(\\w+):(.*?)\\]\\]")
        val cmds = re.findAll(raw).toList()
        val clean = raw.replace(re, "").replace("*", "").trim()
        sphere.active = false
        status.text = "মাইক চাপো বা লেখো"
        addBubble(clean, false)
        if (ttsReady && clean.isNotEmpty()) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "angela")
        } else if (!ttsReady) {
            status.text = "বাংলা ভয়েস ফোনে নেই (Google TTS-এ বাংলা নামাও)"
        }
        if (cmds.isNotEmpty()) {
            window.decorView.postDelayed({
                for (c in cmds) runCommand(c.groupValues[1].uppercase(), c.groupValues[2].trim())
            }, 2000)
        }
    }

    private fun runCommand(type: String, arg: String) {
        try {
            when (type) {
                "OPEN" -> openApp(arg)
                "YOUTUBE" -> {
                    val u = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(arg, "UTF-8")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                }
                "CALL" -> {
                    val converted = arg.map {
                        if (it.isDigit()) Character.getNumericValue(it).toString() else it.toString()
                    }.joinToString("")
                    val num = converted.filter { it.isDigit() || it == '+' }
                    if (num.length < 3) {
                        addBubble("নম্বরটা বুঝতে পারিনি।", false)
                    } else {
                        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                            PackageManager.PERMISSION_GRANTED
                        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(num))))
                    }
                }
                "MEMORY" -> putList("mem", getList("mem") + arg.replace("\n", " "))
                "TASK" -> putList("tasks", getList("tasks") + arg.replace("\n", " "))
            }
        } catch (e: Exception) {
            addBubble("কাজটা করতে পারলাম না: " + e.message, false)
        }
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val q = name.lowercase().trim()
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        if (hit == null) {
            addBubble("\"" + name + "\" নামে কোনো অ্যাপ খুঁজে পেলাম না।", false)
            return
        }
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
        if (i != null) startActivity(i)
    }
}
