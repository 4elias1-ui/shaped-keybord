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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ShapedKeyboardService : InputMethodService(), KeyboardView.Listener, ToolActions {
    private val logical = StringBuilder()   // النص الأصلي المقروء للمقطع الحالي
    private var shown = ""                  // ما كتبناه فعلاً في الحقل (وضع المباشر)
    private var converting = true           // true = كتابة معكوسة (تحويل)، false = كتابة عادية
    private var deferred = false            // وضع الدفعة: لا نكتب في الحقل حتى الإدراج
    private var gameMode = false            // الحذف بضغطات Backspace (للألعاب)
    private var rawField = false
    private var lastStamp = -1L
    private var panelMode = 0               // 0 كيبورد، 1 إيموجي، 2 أدوات وحافظة
    private lateinit var prefs: SharedPreferences
    private lateinit var cfg: KbConfig
    private lateinit var pal: Palette
    private lateinit var keyboard: KeyboardView
    private lateinit var preview: TextView
    private lateinit var previewRow: LinearLayout
    private lateinit var chipMode: TextView
    private lateinit var actionPill: TextView
    private lateinit var segNormal: TextView
    private lateinit var segRev: TextView
    private lateinit var btnEmoji: TextView
    private lateinit var btnClip: TextView
    private lateinit var btnExt: TextView
    private lateinit var btnEn: TextView
    private lateinit var btnAr: TextView
    private lateinit var suggestRow: LinearLayout
    private val sugViews = ArrayList<TextView>()
    private lateinit var suggester: Suggester
    private var rootView: BgLinearLayout? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipPanel: ClipPanel? = null

    private val clips = ArrayList<String>()
    private val pins = ArrayList<String>()
    private val phrases = ArrayList<String>()
    private var clipMgr: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("kb", Context.MODE_PRIVATE)
        suggester = Suggester(prefs)
        loadLists()
        clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipMgr?.addPrimaryClipChangedListener(clipListener)
    }

    private fun readList(key: String): List<String> =
        (prefs.getString(key, "") ?: "").split("\u0001").filter { it.isNotEmpty() }

    private fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString("\u0001")).apply()
    }

    private fun loadLists() {
        clips.clear(); clips.addAll(readList("clips"))
        pins.clear(); pins.addAll(readList("pins"))
        phrases.clear(); phrases.addAll(readList("phrases"))
    }

    private fun refreshPanels() {
        clipPanel?.setData(clips, pins, phrases)
    }

    override fun onDestroy() {
        clipMgr?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setInputView(onCreateInputView())
    }

    private fun loadPrefs() {
        cfg = KbConfig.load(prefs, this)
        pal = Palette.of(cfg)
        converting = prefs.getBoolean("convert", true)
        deferred = prefs.getBoolean("deferred", false)
        gameMode = prefs.getBoolean("game", false)
        lastStamp = prefs.getLong("stamp", 0L)
    }

    private fun withA(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun bg(color: Int, r: Int = 19) = GradientDrawable().apply { cornerRadius = dp(r.toFloat()).toFloat(); setColor(color) }

    private fun icon(label: String, w: Float, size: Float = 18f, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(pal.text)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
    }

    private fun pill(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14f), 0, dp(14f), 0)
        setOnClickListener { onClick() }
    }

    override fun onCreateInputView(): View {
        loadPrefs()
        panelMode = 0

        val root = BgLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.applyBg(cfg, converting)
        rootView = root

        // ===== الشريط العلوي (أيقونات) =====
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(withA(pal.key, 210), 22)
            setPadding(dp(4f), 0, dp(4f), 0)
        }
        strip.addView(icon("⚙", 1f) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        btnEmoji = icon("😊", 1f) { showPanel(if (panelMode == 1) 0 else 1) }
        strip.addView(btnEmoji)
        btnClip = icon("📋", 1f) { showPanel(if (panelMode == 2) 0 else 2) }
        strip.addView(btnClip)
        btnExt = icon("پچ", 1f, 16f) { showPanel(0); keyboard.toggleExtLayer() }
        strip.addView(btnExt)

        // زرّا الكتابة: عادي / معكوس
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(34f), 2.8f)
            background = bg(withA(pal.func, 200), 17)
        }
        segNormal = icon("عادي", 1f, 14f) { setConverting(false) }
        segRev = icon("معكوس", 1f, 14f) { setConverting(true) }
        seg.addView(segNormal)
        seg.addView(segRev)
        strip.addView(seg)

        strip.addView(icon("🌐", 1f) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        btnEn = icon("En", 1f, 16f) { showPanel(0); keyboard.showLanguage(true) }
        strip.addView(btnEn)
        btnAr = icon("ع", 1f, 18f) { showPanel(0); keyboard.showLanguage(false) }
        strip.addView(btnAr)
        root.addView(
            strip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)).apply {
                setMargins(dp(8f), dp(6f), dp(8f), dp(2f))
            },
        )

        // ===== سطر المعاينة =====
        previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        preview = TextView(this).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            textSize = 16f
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        chipMode = pill("") { toggleDeferred() }
        actionPill = pill("") { onAction() }
        previewRow.addView(preview, LinearLayout.LayoutParams(0, dp(34f), 1f))
        previewRow.addView(chipMode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30f)).apply { marginEnd = dp(6f) })
        previewRow.addView(actionPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30f)))
        root.addView(previewRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== شريط اقتراح الكلمات =====
        suggestRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), 0, dp(8f), dp(2f))
            visibility = if (cfg.suggest) View.VISIBLE else View.GONE
        }
        sugViews.clear()
        for (i in 0 until 3) {
            val tv = TextView(this).apply {
                textSize = 17f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(pal.text)
                background = bg(withA(pal.key, 200), 14)
                textDirection = View.TEXT_DIRECTION_ANY_RTL
                setOnClickListener { (tag as? String)?.let { w -> applySuggestion(w) } }
            }
            sugViews.add(tv)
            val lp = LinearLayout.LayoutParams(0, dp(36f), 1f)
            lp.marginStart = dp(3f)
            lp.marginEnd = dp(3f)
            suggestRow.addView(tv, lp)
        }
        root.addView(suggestRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== المحتوى: الكيبورد + اللوحات =====
        keyboard = KeyboardView(this, cfg).apply { listener = this@ShapedKeyboardService }
        val content = FrameLayout(this)
        content.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val ep = EmojiPanel(this, pal, prefs) { e -> onText(e) }
        ep.visibility = View.GONE
        content.addView(ep, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        emojiPanel = ep
        val cp = ClipPanel(this, pal, this)
        cp.visibility = View.GONE
        cp.setData(clips, pins, phrases)
        content.addView(cp, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        clipPanel = cp
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboard.heightPx()))

        updateUi()
        return root
    }

    private fun showPanel(mode: Int) {
        panelMode = mode
        keyboard.visibility = if (mode == 0) View.VISIBLE else View.INVISIBLE
        emojiPanel?.visibility = if (mode == 1) View.VISIBLE else View.GONE
        clipPanel?.visibility = if (mode == 2) View.VISIBLE else View.GONE
        if (mode == 2) refreshPanels()
        updateUi()
    }

    private fun mark(tv: TextView, on: Boolean) {
        tv.setTextColor(if (on) pal.onAccent else pal.text)
        tv.background = if (on) bg(pal.accent, 17) else null
    }

    override fun onLayerChanged() { updateUi() }

    private fun updateUi() {
        mark(segNormal, !converting)
        mark(segRev, converting)
        mark(btnEmoji, panelMode == 1)
        mark(btnClip, panelMode == 2)
        mark(btnExt, panelMode == 0 && keyboard.isExtLayer())
        mark(btnEn, panelMode == 0 && keyboard.isEnglish())
        mark(btnAr, panelMode == 0 && !keyboard.isEnglish())
        chipMode.text = if (deferred) "دفعة" else "مباشر"
        chipMode.setTextColor(pal.text)
        chipMode.background = bg(withA(pal.func, 220), 15)
        actionPill.text = if (deferred) "إدراج" else "فصل"
        actionPill.setTextColor(if (deferred) pal.onAccent else pal.text)
        actionPill.background = bg(if (deferred) pal.accent else withA(pal.func, 220), 15)
        previewRow.visibility = if (cfg.previewRow || deferred) View.VISIBLE else View.GONE
        val empty = logical.isEmpty()
        preview.setTextColor(if (empty) pal.hint else pal.text)
        preview.text = when {
            !empty -> logical.toString()
            rawField -> "هذا الحقل يستقبل نصاً عادياً"
            !converting -> "الكتابة العادية مفعّلة"
            deferred -> "اكتب ثم اضغط إدراج أو Enter"
            else -> "الكتابة المعكوسة مفعّلة — ما تكتبه يظهر هنا مقروءاً"
        }
        updateSuggestions()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetSegment()
        // إن غيّر المستخدم الإعدادات نعيد بناء الواجهة
        if (prefs.getLong("stamp", 0L) != lastStamp) {
            suggester = Suggester(prefs)
            setInputView(onCreateInputView())
        }
        val cls = info.inputType and EditorInfo.TYPE_MASK_CLASS
        val v = info.inputType and EditorInfo.TYPE_MASK_VARIATION
        val password = cls == EditorInfo.TYPE_CLASS_TEXT && (v == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            v == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        rawField = (cls != EditorInfo.TYPE_CLASS_TEXT) || password
        converting = prefs.getBoolean("convert", true)
        deferred = prefs.getBoolean("deferred", false)
        gameMode = prefs.getBoolean("game", false)
        loadLists()
        rootView?.applyBg(cfg, converting)
        captureClip()
        showPanel(0)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (!deferred) resetSegment()
    }

    private fun active() = converting && !rawField

    private fun shapeFor(t: String): String =
        if (cfg.wrapLen > 0) ArabicShaper.shapeWrapped(t, cfg.wrapLen) else ArabicShaper.shape(t)

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
        val out = shapeFor(logical.toString())
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

    private fun isWordChar(c: Char) = Character.isLetterOrDigit(c) || c in '\u064B'..'\u0652' || c == '\u0640'

    private fun wordAtEnd(s: CharSequence): String {
        var i = s.length
        while (i > 0 && isWordChar(s[i - 1])) i--
        return s.subSequence(i, s.length).toString()
    }

    private fun currentWord(): String =
        if (active()) wordAtEnd(logical)
        else (currentInputConnection?.getTextBeforeCursor(40, 0)?.let { wordAtEnd(it) } ?: "")

    /** عند إنهاء كلمة بمسافة: تصحيح الخطأ الشائع، وتعلّم الكلمة. */
    private fun finishWord() {
        val w = wordAtEnd(logical)
        if (w.isEmpty()) return
        var out = w
        if (cfg.autoFix) {
            val f = suggester.fix(w)
            if (f != null) {
                logical.setLength(logical.length - w.length)
                logical.append(f)
                out = f
            }
        }
        if (cfg.suggest && out.length >= 2) suggester.learn(out)
    }

    private fun updateSuggestions() {
        if (!cfg.suggest) return
        val w = if (panelMode == 0 && !rawField) currentWord() else ""
        val list: List<String> = if (w.isEmpty()) emptyList<String>() else suggester.suggest(w, 3)
        for (i in 0 until sugViews.size) {
            val t = list.getOrNull(i)
            sugViews[i].text = t ?: ""
            sugViews[i].tag = t
        }
    }

    private fun applySuggestion(word: String) {
        if (active() && !deferred && !segmentIntact()) resetSegment()
        val w = currentWord()
        if (active()) {
            logical.setLength(logical.length - w.length)
            logical.append(word).append(' ')
            if (deferred) updateUi() else refresh()
        } else {
            val ic = currentInputConnection ?: return
            if (w.isNotEmpty()) ic.deleteSurroundingText(w.length, 0)
            ic.commitText("$word ", 1)
        }
        if (cfg.suggest) suggester.learn(word)
        updateSuggestions()
    }

    private fun appendLogical(text: String) {
        if (text == " ") finishWord()
        val n = logical.length
        if (text == " " && cfg.doubleSpace && n >= 2 && logical[n - 1] == ' ' && logical[n - 2] !in " .،؟!?:;\n") {
            logical.setLength(n - 1)
            logical.append(". ")
        } else {
            logical.append(text)
        }
    }

    override fun onText(text: String) {
        if (!active()) {
            val ic = currentInputConnection
            if (text == " " && !rawField && (cfg.suggest || cfg.autoFix)) {
                val w = currentWord()
                if (w.isNotEmpty()) {
                    val f = if (cfg.autoFix) suggester.fix(w) else null
                    if (f != null) { ic?.deleteSurroundingText(w.length, 0); ic?.commitText(f, 1) }
                    val done = f ?: w
                    if (cfg.suggest && done.length >= 2) suggester.learn(done)
                }
            }
            ic?.commitText(text, 1)
            updateSuggestions()
            return
        }
        if (text == "\n") { onEnter(); return }
        if (deferred) { appendLogical(text); updateUi(); return }
        if (!segmentIntact()) resetSegment()
        appendLogical(text)
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
        val out = if (active()) shapeFor(logical.toString()) else logical.toString()
        currentInputConnection?.commitText(out, 1)
        resetSegment()
        updateUi()
    }

    override fun onEnter() {
        if (deferred && active() && logical.isNotEmpty()) { insertPending(); return }
        if (active() && cfg.suggest) {
            val w = wordAtEnd(logical)
            if (w.length >= 2) suggester.learn(w)
        }
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

    override fun onCursor(dir: Int) {
        moveCursor(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    private fun onAction() {
        if (deferred) insertPending() else { resetSegment(); updateUi() }
    }

    private fun setConverting(on: Boolean) {
        if (deferred) insertPending()
        converting = on
        prefs.edit().putBoolean("convert", on).apply()
        rootView?.applyBg(cfg, converting)
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

    // ===== ToolActions =====
    override fun cursor(left: Boolean) {
        moveCursor(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun menu(id: Int) {
        if (deferred) insertPending() else resetSegment()
        currentInputConnection?.performContextMenuAction(id)
        updateUi()
    }

    private fun commitConverted(t: String) {
        if (t.isEmpty()) return
        if (deferred) insertPending() else resetSegment()
        currentInputConnection?.commitText(if (active()) shapeFor(t) else t, 1)
        updateUi()
    }

    override fun pasteConverted() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        commitConverted(t)
    }

    override fun insertClip(text: String) = commitConverted(text)

    override fun clearClips() {
        clips.clear()
        saveList("clips", clips)
        refreshPanels()
    }

    override fun pinClip(text: String) {
        if (!pins.remove(text)) pins.add(0, text)
        saveList("pins", pins)
        refreshPanels()
    }

    override fun savePhraseFromCurrent() {
        val t = logical.toString().trim()
        if (t.isEmpty()) {
            android.widget.Toast.makeText(this, "اكتب جملة أولاً ثم احفظها", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        phrases.remove(t)
        phrases.add(0, t)
        while (phrases.size > 50) phrases.removeAt(phrases.size - 1)
        saveList("phrases", phrases)
        refreshPanels()
        android.widget.Toast.makeText(this, "تم حفظ العبارة", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun deletePhrase(text: String) {
        phrases.remove(text)
        saveList("phrases", phrases)
        refreshPanels()
    }

    private fun captureClip() {
        try {
            val t = clipMgr?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (t != null && t.isNotBlank()) {
                clips.remove(t)
                clips.add(0, t)
                while (clips.size > 20) clips.removeAt(clips.size - 1)
                saveList("clips", clips)
                refreshPanels()
            }
        } catch (e: Throwable) {
            // بعض الأجهزة تمنع قراءة الحافظة أحياناً؛ نتجاهل
        }
    }
}
