package com.saeed.angela

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    "গান বা ভিডিও চালাতে [[YOUTUBE:সার্চের কথা]]। " +
    "কল করতে [[CALL:ইংরেজি সংখ্যায় নম্বর]]। " +
    "কিছু মনে রাখতে বললে [[MEMORY:কথাটা]]। " +
    "কাজের তালিকায় যোগ করতে [[TASK:কাজটা]]। " +
    "সাঈদ চাইলেই শুধু ট্যাগ দেবে, নইলে দেবে না।"

private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-3.6-flash", "gemini-2.5-flash")

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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val soft = Color.parseColor("#C4B5FD")
    private val card = Color.parseColor("#151034")
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private lateinit var sphere: SphereView
    private lateinit var orb: MicOrb
    private lateinit var status: TextView
    private lateinit var page: FrameLayout
    private lateinit var pageTitle: TextView
    private lateinit var pageBody: FrameLayout
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatPane: LinearLayout
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoListen = true
    private var listening = false
    private var busy = false
    private var resumed = false
    private var inSub = false
    private val history = JSONArray()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int = card, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#668B5CF6"))
    }

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
        window.statusBarColor = Color.parseColor("#070418")
        window.navigationBarColor = Color.parseColor("#120A3A")

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#070418"), Color.parseColor("#120A3A"))
        )

        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        chatPane = buildChatPane()

        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(30), dp(14), dp(10))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(circleBtn(0, 44) { openMenu() })
        val title = tv("ANGELA", 22f, soft).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.35f
        }
        top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(View(this), LinearLayout.LayoutParams(dp(52), dp(44)))
        home.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        sphere = SphereView(this)
        home.addView(sphere, LinearLayout.LayoutParams(MATCH, 0, 1f))

        status = tv("", 13f, Color.GRAY).apply { gravity = Gravity.CENTER }
        home.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))

        orb = MicOrb(this)
        orb.setOnClickListener {
            autoListen = !autoListen
            if (autoListen) startListening() else {
                stopListening()
                setOrb(0)
            }
        }
        home.addView(orb, LinearLayout.LayoutParams(MATCH, dp(150)))

        page = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F5070418"))
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

        loadChat()
        setOrb(1)
        greet()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (page.visibility != View.VISIBLE) startListening()
    }

    override fun onPause() {
        resumed = false
        stopListening()
        tts?.stop()
        sphere.active = false
        busy = false
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
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

    private fun setOrb(s: Int) {
        orb.state = s
        status.text = when (s) {
            0 -> "মাইক বন্ধ, চালু করতে নিচের বৃত্ত চাপো"
            1 -> "শুনছি..."
            2 -> "ভাবছি..."
            else -> ""
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            orb.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onError(error: Int) {
            listening = false
            orb.level = 0f
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                autoListen = false
                setOrb(0)
                status.text = "মাইকের অনুমতি দাও (ফোনের সেটিংস থেকে)"
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recognizer?.destroy()
                recognizer = null
            }
            val wait = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 300L else 1500L
            ui.postDelayed({ startListening() }, wait)
        }
        override fun onResults(results: Bundle?) {
            listening = false
            orb.level = 0f
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (t.isNullOrBlank()) startListening() else send(t)
        }
    }

    private fun startListening() {
        if (!resumed || busy || listening) return
        if (page.visibility == View.VISIBLE) return
        if (!autoListen) {
            setOrb(0)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 1)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try {
            listening = true
            setOrb(1)
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listening = false
        }
    }

    private fun stopListening() {
        listening = false
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
        }
        orb.level = 0f
    }

    private fun goBack() {
        if (inSub) {
            openMenu()
        } else {
            page.visibility = View.GONE
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
        pageBody.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(menuRow("CHAT", "লেখা ও কথোপকথনের ইতিহাস") { openSub("CHAT", chatPane) })
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
                runOnUiThread {
                    sphere.active = true
                    setOrb(3)
                }
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
        sphere.active = false
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
        runChat(key)
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
        runChat(key)
    }

    private fun runChat(key: String) {
        busy = true
        stopListening()
        setOrb(2)
        sphere.active = true
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
                    if (history.length() > 0) history.remove(history.length() - 1)
                    addMsg("সমস্যা হয়েছে: $errMsg", false, false)
                    finishTurn()
                    status.text = "সমস্যা হয়েছে (মেনু → CHAT দেখো)"
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

    private fun handleReply(raw: String) {
        pushTurn("model", raw)
        trimHistory()
        val re = Regex("\\[\\[(\\w+):(.*?)\\]\\]")
        val cmds = re.findAll(raw).toList()
        val clean = raw.replace(re, "").replace("*", "").trim()
        sphere.active = false
        if (clean.isNotEmpty()) addMsg(clean, false, true)
        if (cmds.isNotEmpty()) {
            ui.postDelayed({
                for (c in cmds) runCommand(c.groupValues[1].uppercase(), c.groupValues[2].trim())
            }, 2500)
        }
        if (ttsReady && clean.isNotEmpty()) {
            stopListening()
            setOrb(3)
            sphere.active = true
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "angela")
        } else {
            finishTurn()
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
                        addMsg("নম্বরটা বুঝতে পারিনি।", false, false)
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
            addMsg("কাজটা করতে পারলাম না: " + e.message, false, false)
        }
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
        if (i != null) startActivity(i)
    }
}
