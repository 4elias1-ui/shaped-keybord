package com.arabickb.shaped

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class ShapedKeyboardService : InputMethodService(), KeyboardView.Listener {
    private val logical = StringBuilder()   // النص الأصلي المقروء للمقطع الحالي
    private var shown = ""                  // ما كتبناه فعلاً في الحقل (وضع المباشر)
    private var converting = true
    private var deferred = false            // وضع الدفعة: لا نكتب في الحقل حتى الإدراج
    private var gameMode = false            // الحذف بضغطات Backspace (للألعاب)
    private var rawField = false
    private lateinit var prefs: SharedPreferences
    private lateinit var keyboard: KeyboardView
    private lateinit var preview: TextView
    private lateinit var chipConvert: TextView
    private lateinit var chipMode: TextView
    private lateinit var actionPill: TextView
    private val pills = ArrayList<TextView>()
    private var pal = Palette.of(false)

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("kb", Context.MODE_PRIVATE)
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setInputView(onCreateInputView())
    }

    private fun isNight(): Boolean = when (prefs.getInt("theme", 0)) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun loadPrefs() {
        converting = prefs.getBoolean("convert", true)
        deferred = prefs.getBoolean("deferred", false)
        gameMode = prefs.getBoolean("game", false)
    }

    override fun onCreateInputView(): View {
        val night = isNight()
        pal = Palette.of(night)
        loadPrefs()
        pills.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(pal.bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(6f), dp(8f), 0)
        }
        val h = dp(38f)
        fun lpw() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h).apply { marginEnd = dp(6f) }

        chipConvert = pill("") { toggleConvert() }
        chipMode = pill("") { toggleDeferred() }
        preview = TextView(this).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            textSize = 17f
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            setPadding(dp(10f), 0, dp(10f), 0)
        }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun tool(label: String, f: () -> Unit) = tools.addView(pill(label, true, f), lpw())
        tool("◀") { moveCursor(KeyEvent.KEYCODE_DPAD_LEFT) }
        tool("▶") { moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT) }
        tool("تحديد الكل") { menuAction(android.R.id.selectAll) }
        tool("نسخ") { menuAction(android.R.id.copy) }
        tool("قص") { menuAction(android.R.id.cut) }
        tool("لصق") { menuAction(android.R.id.paste) }
        tool("لصق وتحويل") { pasteConverted() }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
        }
        actionPill = pill("") { onAction() }
        val gear = pill("⚙") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        bar.addView(chipConvert, lpw())
        bar.addView(chipMode, lpw())
        bar.addView(preview, LinearLayout.LayoutParams(0, h, 1f))
        bar.addView(scroll, LinearLayout.LayoutParams(0, h, 1.2f).apply { marginEnd = dp(6f) })
        bar.addView(actionPill, lpw())
        bar.addView(gear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        keyboard = KeyboardView(this, night).apply {
            listener = this@ShapedKeyboardService
            keyHeightDp = prefs.getInt("height", 56).toFloat()
            haptic = prefs.getBoolean("haptic", true)
            sound = prefs.getBoolean("sound", false)
        }
        root.addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pills.add(actionPill); pills.add(gear)
        updateUi()
        return root
    }

    private fun pill(label: String, track: Boolean = false, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14f), 0, dp(14f), 0)
        setOnClickListener { onClick() }
        if (track) pills.add(this)
    }

    private fun bg(color: Int) = GradientDrawable().apply { cornerRadius = dp(19f).toFloat(); setColor(color) }

    private fun updateUi() {
        chipConvert.text = if (converting) "تحويل ●" else "تحويل ○"
        chipConvert.setTextColor(if (converting) pal.onAccent else pal.text)
        chipConvert.background = bg(if (converting) pal.accent else pal.func)
        chipMode.text = if (deferred) "دفعة" else "مباشر"
        chipMode.setTextColor(pal.text)
        chipMode.background = bg(pal.func)
        for (p in pills) { p.setTextColor(pal.text); p.background = bg(pal.func) }
        actionPill.text = if (deferred) "إدراج" else "فصل"
        if (deferred) { actionPill.setTextColor(pal.onAccent); actionPill.background = bg(pal.accent) }
        val empty = logical.isEmpty()
        preview.setTextColor(if (empty) pal.hint else pal.text)
        preview.text = when {
            !empty -> logical.toString()
            !converting || rawField -> "التحويل متوقف"
            deferred -> "اكتب ثم اضغط إدراج أو Enter"
            else -> "ما تكتبه يظهر هنا مقروءاً"
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetSegment()
        val cls = info.inputType and EditorInfo.TYPE_MASK_CLASS
        val v = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        val password = cls == EditorInfo.TYPE_CLASS_TEXT && (v == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            v == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        rawField = (cls != EditorInfo.TYPE_CLASS_TEXT) || password
        loadPrefs()
        keyboard.keyHeightDp = prefs.getInt("height", 56).toFloat()
        keyboard.haptic = prefs.getBoolean("haptic", true)
        keyboard.sound = prefs.getBoolean("sound", false)
        updateUi()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // في وضع الدفعة لا نفقد النص غير المُدرج عند تبديل الحقل بسرعة؛ نتركه في المعاينة
        if (!deferred) resetSegment()
    }

    private fun active() = converting && !rawField

    private fun resetSegment() { logical.setLength(0); shown = "" }

    /** هل ما زال نص المقطع قبل المؤشر كما كتبناه؟ (إن حرّك المستخدم المؤشر نبدأ مقطعاً جديداً) */
    private fun segmentIntact(): Boolean {
        if (shown.isEmpty() || gameMode) return true
        val before = currentInputConnection?.getTextBeforeCursor(shown.length, 0) ?: return true
        return before.length != shown.length || before.toString() == shown
    }

    private fun deleteShown() {
        val ic = currentInputConnection ?: return
        if (shown.isEmpty()) return
        if (gameMode) repeat(shown.length) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        else ic.deleteSurroundingText(shown.length, 0)
        shown = ""
    }

    private fun refresh() {
        val ic = currentInputConnection ?: return
        val out = ArabicShaper.shape(logical.toString())
        if (gameMode) {
            deleteShown()
            ic.commitText(out, 1)
        } else {
            ic.beginBatchEdit()
            deleteShown()
            ic.commitText(out, 1)
            ic.endBatchEdit()
        }
        shown = out
        updateUi()
    }

    override fun onText(text: String) {
        if (!active()) { currentInputConnection?.commitText(text, 1); return }
        if (deferred) { logical.append(text); updateUi(); return }
        if (!segmentIntact()) resetSegment()
        logical.append(text)
        refresh()
    }

    override fun onDelete() {
        if (active() && logical.isNotEmpty()) {
            if (deferred) {
                logical.setLength(logical.offsetByCodePoints(logical.length, -1))
                updateUi()
                return
            }
            if (segmentIntact()) {
                logical.setLength(logical.offsetByCodePoints(logical.length, -1))
                if (logical.isEmpty()) { deleteShown(); updateUi() } else refresh()
                return
            }
            resetSegment()
            updateUi()
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun insertPending() {
        if (logical.isEmpty()) return
        val out = if (active()) ArabicShaper.shape(logical.toString()) else logical.toString()
        currentInputConnection?.commitText(out, 1)
        resetSegment()
        updateUi()
    }

    override fun onEnter() {
        if (deferred && active() && logical.isNotEmpty()) { insertPending(); return }
        resetSegment()
        updateUi()
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDefaultEditorAction(true)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onLangLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun onAction() {
        if (deferred) insertPending() else { resetSegment(); updateUi() }
    }

    private fun toggleConvert() {
        converting = !converting
        prefs.edit().putBoolean("convert", converting).apply()
        resetSegment()
        updateUi()
    }

    private fun toggleDeferred() {
        if (deferred) insertPending()
        deferred = !deferred
        prefs.edit().putBoolean("deferred", deferred).apply()
        resetSegment()
        updateUi()
    }

    private fun moveCursor(code: Int) {
        if (deferred) insertPending() else resetSegment()
        sendDownUpKeyEvents(code)
        updateUi()
    }

    private fun menuAction(id: Int) {
        if (deferred) insertPending() else resetSegment()
        currentInputConnection?.performContextMenuAction(id)
        updateUi()
    }

    private fun pasteConverted() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (t.isEmpty()) return
        if (deferred) insertPending() else resetSegment()
        currentInputConnection?.commitText(if (active()) ArabicShaper.shape(t) else t, 1)
        updateUi()
    }
}
