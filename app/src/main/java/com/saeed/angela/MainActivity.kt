package com.saeed.angela

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val green = Color.parseColor("#39FF88")
    private val bg = Color.parseColor("#05080A")
    private val card = Color.parseColor("#0E1A14")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val VOICE = 101

    private lateinit var prefs: SharedPreferences
    private lateinit var sphere: SphereView
    private lateinit var status: TextView
    private lateinit var panel: FrameLayout
    private lateinit var chatView: View
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val history = JSONArray()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int = card): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(14).toFloat()
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
        setPadding(dp(12), dp(10), dp(12), dp(10))
        maxLines = 4
    }

    private fun vbox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
    }

    private fun label(title: String): TextView = tv(title, 16f, green).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(4), dp(4), dp(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("angela", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(28), dp(12), dp(8))
        }

        val title = tv("ANGELA", 24f, green).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.3f
        }
        root.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))

        sphere = SphereView(this)
        sphere.setOnClickListener { listen() }
        root.addView(sphere, LinearLayout.LayoutParams(MATCH, dp(190)))

        status = tv("গোলক চাপো বা মাইক চাপো", 13f, Color.GRAY).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))

        val cards: List<Pair<String, () -> Unit>> = listOf(
            "CHAT" to { showChat() },
            "MEMORY" to { showList("MEMORY", "mem") },
            "SOUL" to { showSoul() },
            "TASKS" to { showList("TODAY TASKS", "tasks") },
            "HEADLINES" to { showHeadlines() },
            "SETTINGS" to { showSettings() }
        )
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..2) {
                val item = cards[r * 3 + c]
                val tile = tv(item.first, 12f, green).apply {
                    background = box()
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                    setOnClickListener { item.second() }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        panel = FrameLayout(this)
        root.addView(panel, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })

        chatView = buildChat()
        setContentView(root)
        showChat()
        addBubble("আমি অ্যাঞ্জেলা। বলো, কেমন আছ?", false)

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
        }
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

    private fun buildChat(): View {
        val b = vbox()
        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        b.addView(chatScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val field = input("এখানে লেখো...")
        val mic = btn("🎤") { listen() }
        val go = btn("➤") {
            val t = field.text.toString()
            field.setText("")
            send(t)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(mic)
        row.addView(go)
        b.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        return b
    }

    private fun showChat() {
        panel.removeAllViews()
        panel.addView(chatView)
    }

    private fun showHeadlines() {
        panel.removeAllViews()
        val b = vbox()
        b.addView(label("TODAY HEADLINES"))
        b.addView(tv("এই কার্ড পরের ধাপে চালু করব, যাতে সত্যিকারের খবর আসে।", 14f))
        panel.addView(b)
    }

    private fun showSettings() {
        panel.removeAllViews()
        val b = vbox()
        b.addView(label("SETTINGS"))
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
        panel.addView(b)
    }

    private fun showSoul() {
        panel.removeAllViews()
        val b = vbox()
        b.addView(label("SOUL (ব্যক্তিত্ব)"))
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
        panel.addView(b)
    }

    private fun getList(k: String): List<String> =
        (prefs.getString(k, "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun putList(k: String, l: List<String>) {
        prefs.edit().putString(k, l.takeLast(50).joinToString("\n")).apply()
    }

    private fun showList(title: String, key: String) {
        panel.removeAllViews()
        val b = vbox()
        b.addView(label(title))
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
        panel.addView(b)
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
        showChat()
        addBubble(text, true)
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            addBubble("আগে SETTINGS-এ গিয়ে Gemini চাবি বসাও।", false)
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
                    status.text = "গোলক চাপো বা মাইক চাপো"
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
            .put(
                "generationConfig",
                JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            )
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
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
        if (code == 429) throw Exception("ফ্রি লিমিট শেষ, একটু পরে বলো")
        if (code !in 200..299) throw Exception("কোড " + code)
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
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
        status.text = "গোলক চাপো বা মাইক চাপো"
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
