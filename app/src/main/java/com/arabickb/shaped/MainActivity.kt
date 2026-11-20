package com.arabickb.shaped

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private var preview: KeyboardView? = null
    private var previewFrame: BgLinearLayout? = null
    private var previewRev = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onImagePicked(uri, false)
    }
    private val pickImageR = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onImagePicked(uri, true)
    }

    private fun onImagePicked(uri: android.net.Uri?, rev: Boolean) {
        if (uri == null) return
        if (BgImage.saveFrom(this, uri, rev)) {
            edit { putInt(if (rev) "bgmode_r" else "bgmode", 2) }
            Toast.makeText(this, "تم تعيين الصورة كخلفية", Toast.LENGTH_SHORT).show()
            recreate()
        } else {
            Toast.makeText(this, "تعذّر قراءة الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    private val themeInts = listOf(
        "theme", "accent", "radius", "gap", "font", "keyalpha", "height", "width", "align", "digits", "splitgap",
        "c_key", "c_func", "c_bg", "c_accent", "c_text", "bgmode", "bggrad", "bgdim", "bgmode_r", "bggrad_r", "bgdim_r",
    )
    private val themeBools = listOf("fround", "edge", "hints", "preview", "flip", "numbers", "split", "bgsplit", "prow")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** يحفظ الإعداد، ويحدّث "بصمة" الإعدادات ليعيد الكيبورد بناء نفسه، ويحدّث المعاينة. */
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = prefs.edit()
        e.block()
        e.putLong("stamp", System.currentTimeMillis())
        e.apply()
        val c = KbConfig.load(prefs, this)
        previewFrame?.applyBg(c, previewRev)
        preview?.applyConfig(c)
    }

    // ===== الثيمات المحفوظة =====
    private fun themeData(): String {
        val parts = ArrayList<String>()
        for (k in themeInts) if (prefs.contains(k)) parts.add("$k=${prefs.getInt(k, 0)}")
        for (k in themeBools) if (prefs.contains(k)) parts.add("$k=${prefs.getBoolean(k, false)}")
        return parts.joinToString(";")
    }

    private fun applyThemeData(d: String) {
        edit {
            for (k in themeInts) remove(k)
            for (k in themeBools) remove(k)
            for (part in d.split(";")) {
                val kv = part.split("=")
                if (kv.size != 2) continue
                val k = kv[0]
                val v = kv[1]
                if (k in themeInts) {
                    val n = v.toIntOrNull()
                    if (n != null) putInt(k, n)
                } else if (k in themeBools) {
                    if (v == "true" || v == "false") putBoolean(k, v == "true")
                }
            }
        }
    }

    private fun readThemes(): List<Pair<String, String>> =
        (prefs.getString("themes", "") ?: "").split("\n").mapNotNull {
            val i = it.indexOf('\t')
            if (i > 0) Pair(it.substring(0, i), it.substring(i + 1)) else null
        }

    private fun writeThemes(list: List<Pair<String, String>>) {
        prefs.edit().putString("themes", list.joinToString("\n") { it.first + "\t" + it.second }).apply()
    }

    private fun cleanName(s: String) = s.replace(Regex("[\\t\\n|;]"), " ").trim().take(30)

    // ===== نافذة اختيار لون =====
    private fun colorDialog(title: String, key: String, onDone: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val start = if (prefs.contains(key)) prefs.getInt(key, 0) else 0xFF3F5EFB.toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(start, hsv)
        val swatch = View(this)
        val hex = EditText(this).apply { hint = "#RRGGBB"; inputType = InputType.TYPE_CLASS_TEXT }
        fun cur(): Int = Color.HSVToColor(hsv)
        fun sync() {
            swatch.setBackgroundColor(cur())
            hex.setText(String.format("#%06X", 0xFFFFFF and cur()))
        }
        fun bar(label: String, max: Int, init: Int, set: (Int) -> Unit) {
            box.addView(TextView(this).apply { text = label })
            val sb = SeekBar(this)
            sb.max = max
            sb.progress = init
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) { set(p); sync() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            box.addView(sb)
        }
        box.addView(swatch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        bar("الدرجة", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        bar("التشبع", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        bar("السطوع", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        box.addView(hex)
        sync()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val t = hex.text.toString().trim()
                val parsed = try { Color.parseColor(if (t.startsWith("#")) t else "#$t") } catch (e: Exception) { null }
                edit { putInt(key, parsed ?: cur()) }
                onDone()
            }
            .setNeutralButton("افتراضي") { _, _ -> edit { remove(key) }; onDone() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("kb", Context.MODE_PRIVATE)
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(48))
        }
        fun lp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(8) }
        fun title(t: String) = TextView(ctx).apply {
            text = t; textSize = 20f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(8))
        }
        fun body(t: String) = TextView(ctx).apply { text = t; textSize = 15f; setPadding(0, 0, 0, dp(8)) }
        fun btn(t: String, onClick: () -> Unit) = MaterialButton(ctx).apply {
            text = t; setOnClickListener { onClick() }
        }
        fun sw(t: String, key: String, def: Boolean) {
            root.addView(MaterialSwitch(ctx).apply {
                text = t; isChecked = prefs.getBoolean(key, def)
                setOnCheckedChangeListener { _, c -> edit { putBoolean(key, c) } }
            }, lp())
        }
        fun slider(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String = "") {
            val cur = prefs.getInt(key, def).coerceIn(lo, hi)
            val tv = TextView(ctx).apply { textSize = 15f; text = "$label: $cur$unit" }
            root.addView(tv)
            val sb = SeekBar(ctx)
            sb.max = hi - lo
            sb.progress = cur - lo
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    tv.text = "$label: ${p + lo}$unit"
                    if (u) edit { putInt(key, p + lo) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            root.addView(sb, lp())
        }
        fun spinner(label: String, key: String, items: List<String>, def: Int) {
            root.addView(body(label))
            val sp = Spinner(ctx)
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
            sp.setSelection(prefs.getInt(key, def).coerceIn(0, items.size - 1))
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos != prefs.getInt(key, def)) edit { putInt(key, pos) }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            root.addView(sp, lp())
        }
        fun preset(name: String, vararg v: Pair<String, Int?>) {
            root.addView(btn(name) {
                edit { for ((k, x) in v) { if (x == null) remove(k) else putInt(k, x) } }
                recreate()
            }, lp())
        }
        fun colorRow(label: String, key: String) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val tv = TextView(ctx).apply { text = label; textSize = 15f }
            val swatch = View(ctx)
            fun paint() {
                swatch.setBackgroundColor(if (prefs.contains(key)) prefs.getInt(key, 0) else 0xFF9E9E9E.toInt())
            }
            paint()
            val change = btn("تغيير") { colorDialog(label, key) { paint() } }
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(swatch, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8); marginStart = dp(8) })
            row.addView(change, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            root.addView(row, lp())
        }
        fun bgSection(heading: String, suffix: String, rev: Boolean) {
            root.addView(body(heading))
            spinner("نوع الخلفية", "bgmode$suffix", listOf("لون الثيم", "تدرّج لوني", "صورة من المعرض"), 0)
            spinner("التدرّج", "bggrad$suffix", Backgrounds.NAMES, 0)
            root.addView(btn("اختيار صورة من المعرض") { if (rev) pickImageR.launch("image/*") else pickImage.launch("image/*") }, lp())
            root.addView(btn("حذف الصورة") {
                BgImage.delete(ctx, rev)
                edit { putInt("bgmode$suffix", 0) }
                recreate()
            }, lp())
            slider("تعتيم الصورة", "bgdim$suffix", 0, 80, 30, "%")
        }

        root.addView(title("كيبورد عربي محوّل"))
        root.addView(body("يكتب بالعربية بحروف متصلة بترتيب مقلوب، فتظهر سليمة في التطبيقات والألعاب التي لا تدعم العربية (Genshin، Minecraft، Notion، Photoshop…)."))
        root.addView(btn("1) تفعيل الكيبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, lp())
        root.addView(btn("2) اختيار الكيبورد") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, lp())
        root.addView(EditText(ctx).apply { hint = "جرّب الكتابة هنا"; minLines = 2; gravity = Gravity.TOP }, lp())

        root.addView(title("معاينة مباشرة"))
        root.addView(body("أي تغيير في الإعدادات أدناه يظهر هنا فوراً، ويُطبَّق على الكيبورد عند فتحه في أي حقل."))
        val cfg0 = KbConfig.load(prefs, ctx)
        val frame = BgLinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        frame.applyBg(cfg0, previewRev)
        val pv = KeyboardView(ctx, cfg0)
        frame.addView(pv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        previewFrame = frame
        preview = pv
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val pvRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        pvRow.addView(btn("معاينة: عادي") { previewRev = false; previewFrame?.applyBg(KbConfig.load(prefs, ctx), false) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pvRow.addView(btn("معاينة: معكوس") { previewRev = true; previewFrame?.applyBg(KbConfig.load(prefs, ctx), true) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(pvRow, lp())

        root.addView(title("أنماط جاهزة"))
        preset("الافتراضي", "radius" to null, "gap" to null, "height" to null, "font" to null, "width" to null, "align" to null, "keyalpha" to null)
        preset("يد واحدة (يمين)", "width" to 65, "align" to 2, "height" to 60)
        preset("يد واحدة (يسار)", "width" to 65, "align" to 0, "height" to 60)
        preset("كبير ومريح", "height" to 68, "font" to 115, "gap" to 8, "radius" to 14, "width" to 100)
        preset("مدمج", "height" to 46, "font" to 90, "gap" to 4, "radius" to 8, "width" to 100)

        root.addView(title("الثيم والألوان"))
        spinner("وضع الألوان", "theme", listOf("حسب النظام", "فاتح", "داكن", "أسود تماماً (AMOLED)"), 0)
        spinner("لون التمييز", "accent", listOf("أزرق", "أخضر", "وردي", "بنفسجي", "برتقالي", "سماوي"), 5)
        slider("استدارة المفاتيح", "radius", 0, 24, 10, " dp")
        slider("المسافة بين المفاتيح", "gap", 2, 12, 6, " dp")
        slider("حجم الخط", "font", 80, 140, 100, "%")
        slider("شفافية المفاتيح (قلّلها لتظهر الخلفية)", "keyalpha", 30, 100, 100, "%")
        sw("مفاتيح الوظائف بيضاوية (حذف، إدخال، ؟123)", "fround", true)
        sw("حافة سفلية للمفاتيح (مظهر ثلاثي الأبعاد)", "edge", true)
        sw("إظهار حرف أو رقم البدائل الصغير على المفاتيح", "hints", true)
        sw("فقاعة تكبير الحرف عند الضغط", "preview", true)

        root.addView(title("ألوان مخصصة"))
        root.addView(body("اختر أي لون، أو اضغط «افتراضي» للرجوع إلى لون الثيم."))
        colorRow("لون المفاتيح", "c_key")
        colorRow("لون مفاتيح الوظائف", "c_func")
        colorRow("لون الخلفية", "c_bg")
        colorRow("لون التمييز", "c_accent")
        colorRow("لون النص", "c_text")

        root.addView(title("الخلفية"))
        sw("خلفية مختلفة للكتابة المعكوسة (تتغير عند الضغط على زر معكوس)", "bgsplit", false)
        bgSection("خلفية الكتابة العادية (وهي الخلفية الوحيدة إن لم تفعّل الخيار أعلاه)", "", false)
        bgSection("خلفية الكتابة المعكوسة", "_r", true)

        root.addView(title("الحجم والموضع"))
        slider("ارتفاع المفاتيح", "height", 40, 76, 56, " dp")
        slider("عرض الكيبورد", "width", 50, 100, 100, "%")
        spinner("مكان الكيبورد (عند تصغير العرض)", "align", listOf("يسار", "وسط", "يمين"), 1)
        sw("كيبورد منقسم (مناسب للتابلت)", "split", false)
        slider("عرض الفراغ الأوسط", "splitgap", 10, 40, 20, "%")
        sw("قلب أماكن مفاتيح الحذف والإدخال", "flip", false)
        sw("صف أرقام مستقل (وإلا تظهر الأرقام كتلميحات على الصف الأول)", "numbers", false)
        spinner("شكل الأرقام", "digits", listOf("1 2 3 (لاتيني)", "١ ٢ ٣ (عربي)", "۱ ۲ ۳ (فارسي)"), 1)
        sw("إظهار سطر المعاينة فوق الكيبورد", "prow", true)

        root.addView(title("الكتابة الذكية"))
        slider("تقسيم الأسطر الطويلة: عدد الأحرف في السطر (0 = معطّل)", "wrap", 0, 120, 0, "")
        root.addView(body("يفيد في صناديق النص التي تدعم عدة أسطر؛ يُقلب كل سطر وحده فيظهر الكلام الطويل سليماً."))
        sw("اقتراح الكلمات (يتعلم من كتابتك ويحفظ ذلك على جهازك فقط)", "suggest", true)
        sw("تصحيح الأخطاء الإملائية الشائعة (مثل: هاذا ← هذا، لاكن ← لكن)", "autofix", true)
        root.addView(btn("مسح الكلمات التي تعلّمها الكيبورد") {
            edit { remove("learned") }
            Toast.makeText(ctx, "تم المسح", Toast.LENGTH_SHORT).show()
        }, lp())
        root.addView(body("العبارات الجاهزة (كل سطر عبارة):"))
        val phrasesEdit = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; gravity = Gravity.TOP
            setText((prefs.getString("phrases", "") ?: "").split("\u0001").filter { it.isNotEmpty() }.joinToString("\n"))
        }
        root.addView(phrasesEdit, lp())
        root.addView(btn("حفظ العبارات") {
            val list = phrasesEdit.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(50)
            edit { putString("phrases", list.joinToString("\u0001")) }
            Toast.makeText(ctx, "تم الحفظ", Toast.LENGTH_SHORT).show()
        }, lp())

        root.addView(title("التحكم"))
        sw("تفعيل الكتابة المعكوسة افتراضياً", "convert", true)
        sw("وضع الدفعة: الكتابة في المعاينة ثم الإدراج بزر إدراج أو Enter (الأضمن مع الألعاب)", "deferred", false)
        sw("وضع الألعاب: الحذف بضغطات Backspace (فعّله إن ظهر تكرار أو تشويه في المباشر)", "game", false)
        sw("السحب على مفتاح المسافة لتحريك المؤشر", "swipe", true)
        sw("مسافتان تعطيان نقطة", "dspace", true)
        slider("مدة الضغط المطوّل للبدائل", "longms", 200, 600, 380, " ms")
        slider("سرعة تكرار الحذف (أقل = أسرع)", "repeatms", 20, 100, 45, " ms")
        sw("اهتزاز عند اللمس", "haptic", true)
        sw("صوت عند اللمس", "sound", false)
        root.addView(btn("إعادة ضبط كل الإعدادات") {
            edit { clear() }
            recreate()
        }, lp())

        root.addView(title("الثيمات المحفوظة"))
        root.addView(body("احفظ مظهرك الحالي (الألوان والأحجام والخلفية المتدرجة) واستعده لاحقاً، أو شاركه كنص. صور الخلفية لا تُحفظ مع الثيم."))
        val nameEdit = EditText(ctx).apply { hint = "اسم الثيم"; inputType = InputType.TYPE_CLASS_TEXT }
        root.addView(nameEdit, lp())
        root.addView(btn("حفظ المظهر الحالي كثيم") {
            val n = cleanName(nameEdit.text.toString())
            if (n.isEmpty()) {
                Toast.makeText(ctx, "اكتب اسماً للثيم", Toast.LENGTH_SHORT).show()
            } else {
                val list = readThemes().filter { it.first != n } + Pair(n, themeData())
                writeThemes(list)
                recreate()
            }
        }, lp())
        for ((name, data) in readThemes()) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(ctx).apply { text = name; textSize = 16f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(btn("تطبيق") { applyThemeData(data); recreate() })
            row.addView(btn("نسخ") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("theme", "SKB1|$name|$data"))
                Toast.makeText(ctx, "تم نسخ الثيم، الصقه لمن تريد مشاركته", Toast.LENGTH_SHORT).show()
            })
            row.addView(btn("حذف") {
                writeThemes(readThemes().filter { it.first != name })
                recreate()
            })
            root.addView(row, lp())
        }
        val importEdit = EditText(ctx).apply { hint = "الصق نص ثيم هنا (يبدأ بـ SKB1)"; inputType = InputType.TYPE_CLASS_TEXT }
        root.addView(importEdit, lp())
        root.addView(btn("استيراد ثيم") {
            val parts = importEdit.text.toString().trim().split("|")
            if (parts.size == 3 && parts[0] == "SKB1") {
                val n = cleanName(parts[1]).ifEmpty { "ثيم مستورد" }
                writeThemes(readThemes().filter { it.first != n } + Pair(n, parts[2]))
                applyThemeData(parts[2])
                recreate()
            } else {
                Toast.makeText(ctx, "النص غير صالح", Toast.LENGTH_SHORT).show()
            }
        }, lp())

        root.addView(title("المحوّل (مثل الموقع)"))
        val input = EditText(ctx).apply {
            hint = "الصق أو اكتب نصاً عربياً"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; gravity = Gravity.TOP
        }
        val output = TextView(ctx).apply { textSize = 18f; setTextIsSelectable(true); setPadding(0, dp(8), 0, dp(8)) }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { output.text = ArabicShaper.shape(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(input, lp())
        root.addView(output, lp())
        root.addView(btn("نسخ النص المحوّل") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("shaped", output.text))
            Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
        }, lp())
        root.addView(body("يظهر الناتج هنا مبعثراً لأن هذا التطبيق يدعم العربية؛ الغرض منه اللصق في التطبيقات التي لا تدعمها."))

        setContentView(ScrollView(ctx).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(root)
        })
    }
}
