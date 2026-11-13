package com.arabickb.shaped

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kb", Context.MODE_PRIVATE)
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(40))
        }
        fun lp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(8) }
        fun title(t: String) = TextView(ctx).apply {
            text = t; textSize = 22f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
        fun body(t: String) = TextView(ctx).apply { text = t; textSize = 15f; setPadding(0, 0, 0, dp(8)) }
        fun btn(t: String, onClick: () -> Unit) = MaterialButton(ctx).apply {
            text = t; setOnClickListener { onClick() }
        }
        fun sw(t: String, key: String, def: Boolean) = MaterialSwitch(ctx).apply {
            text = t; isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply() }
        }

        root.addView(title("كيبورد عربي محوّل"))
        root.addView(body("يكتب بالعربية بحروف متصلة بترتيب مقلوب، فتظهر سليمة في التطبيقات والألعاب التي لا تدعم العربية (Minecraft، Notion، Photoshop…)."))
        root.addView(btn("1) تفعيل الكيبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, lp())
        root.addView(btn("2) اختيار الكيبورد") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, lp())
        root.addView(EditText(ctx).apply { hint = "جرّب الكتابة هنا"; minLines = 2; gravity = Gravity.TOP }, lp())

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
        root.addView(body("ملاحظة: يظهر الناتج هنا مبعثراً لأن هذا التطبيق يدعم العربية؛ الغرض منه اللصق في التطبيقات التي لا تدعمها."))

        root.addView(title("الإعدادات"))
        root.addView(sw("تفعيل التحويل افتراضياً", "convert", true), lp())
        root.addView(sw("وضع الدفعة: الكتابة في المعاينة ثم الإدراج بزر إدراج أو Enter (الأضمن مع الألعاب)", "deferred", false), lp())
        root.addView(sw("وضع الألعاب: الحذف بضغطات Backspace (فعّله إن ظهر تكرار أو تشويه في المباشر)", "game", false), lp())
        root.addView(sw("اهتزاز عند اللمس", "haptic", true), lp())
        root.addView(sw("صوت عند اللمس", "sound", false), lp())

        root.addView(body("المظهر"))
        val rg = RadioGroup(ctx).apply { orientation = RadioGroup.VERTICAL }
        val names = listOf("حسب النظام", "فاتح", "داكن")
        names.forEachIndexed { i, n ->
            rg.addView(RadioButton(ctx).apply { id = 1000 + i; text = n })
        }
        rg.check(1000 + prefs.getInt("theme", 0))
        rg.setOnCheckedChangeListener { _, id -> prefs.edit().putInt("theme", id - 1000).apply() }
        root.addView(rg, lp())

        val hLabel = body("ارتفاع المفاتيح: ${prefs.getInt("height", 56)}")
        root.addView(hLabel)
        root.addView(SeekBar(ctx).apply {
            max = 28; progress = prefs.getInt("height", 56) - 44
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    prefs.edit().putInt("height", p + 44).apply()
                    hLabel.text = "ارتفاع المفاتيح: ${p + 44}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, lp())
        root.addView(body("تُطبَّق الإعدادات عند فتح الكيبورد في حقل جديد (المظهر: أعد إظهار الكيبورد أو بدّل الكيبورد ثم ارجع)."))

        setContentView(ScrollView(ctx).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(root)
        })
    }
}
