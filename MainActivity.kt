package com.arabickb.shaped

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private var preview: KeyboardView? = null
    private var previewFrame: BgLinearLayout? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (BgImage.saveFrom(this@MainActivity, uri)) {
                edit { putInt("bgmode", 2) }
                Toast.makeText(this@MainActivity, "تم تعيين الصورة كخلفية", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this@MainActivity, "تعذّر قراءة الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** يحفظ الإعداد، ويحدّث "بصمة" الإعدادات ليعيد الكيبورد بناء نفسه، ويحدّث المعاينة. */
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = prefs.edit()
        e.block()
        e.putLong("stamp", System.currentTimeMillis())
        e.apply()
        val c = KbConfig.load(prefs, this)
        previewFrame?.applyBg(c)
        preview?.applyConfig(c)
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
        frame.applyBg(cfg0)
        val pv = KeyboardView(ctx, cfg0)
        frame.addView(pv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        previewFrame = frame
        preview = pv
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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

        root.addView(title("الخلفية"))
        spinner("نوع الخلفية", "bgmode", listOf("لون الثيم", "تدرّج لوني", "صورة من المعرض"), 0)
        spinner("التدرّج", "bggrad", Backgrounds.NAMES, 0)
        root.addView(btn("اختيار صورة من المعرض") { pickImage.launch("image/*") }, lp())
        root.addView(btn("حذف صورة الخلفية") {
            BgImage.delete(ctx)
            edit { putInt("bgmode", 0) }
            recreate()
        }, lp())
        slider("تعتيم الصورة", "bgdim", 0, 80, 30, "%")

        root.addView(title("الحجم والموضع"))
        slider("ارتفاع المفاتيح", "height", 40, 76, 56, " dp")
        slider("عرض الكيبورد", "width", 50, 100, 100, "%")
        spinner("مكان الكيبورد (عند تصغير العرض)", "align", listOf("يسار", "وسط", "يمين"), 1)
        sw("قلب أماكن مفاتيح الحذف والإدخال", "flip", false)
        sw("صف أرقام مستقل (وإلا تظهر الأرقام كتلميحات على الصف الأول)", "numbers", false)
        spinner("شكل الأرقام", "digits", listOf("1 2 3 (لاتيني)", "١ ٢ ٣ (عربي)", "۱ ۲ ۳ (فارسي)"), 1)
        sw("إظهار سطر المعاينة فوق الكيبورد", "prow", true)

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
