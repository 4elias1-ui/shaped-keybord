package com.arabickb.shaped

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import kotlin.math.abs

class KeyboardView(context: Context, private var cfg: KbConfig) : View(context) {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onDeleteWord() {}
        fun onEnter()
        fun onLangLongPress()
        fun onCursor(dir: Int)
        fun onLayerChanged() {}
    }

    var listener: Listener? = null
    private var pal = Palette.of(cfg)
    private var layer = Layer.AR
    private var lastLetters = Layer.AR
    private var shift = 0 // 0 = off, 1 = once, 2 = lock
    private var lastShiftTap = 0L

    private class Cell(val key: Key, val r: RectF, val row: Int) {
        var grad: LinearGradient? = null
        var gradBase = 0
    }
    private var cells: List<List<Cell>> = emptyList()
    private var cachedRows: List<List<Key>> = emptyList()

    private inner class Press(val cell: Cell, val startX: Float, val startY: Float) {
        var lastX = startX
        val t0 = SystemClock.uptimeMillis()
        var longDone = false
        var job: Runnable? = null
    }

    private class Popup(
        val pointer: Int,
        val items: List<String>,
        val rects: List<RectF>,
        val box: RectF,
        val below: Boolean,
        val cellTop: Float,
        val cellBottom: Float,
    ) {
        var hover = -1
        fun nearest(x: Float): Int {
            var best = 0
            var bd = Float.MAX_VALUE
            for (i in rects.indices) {
                val d = abs(rects[i].centerX() - x)
                if (d < bd) { bd = d; best = i }
            }
            return best
        }
    }

    private class Fade(val rect: RectF, val label: String, val t: Long)

    private val presses = HashMap<Int, Press>()
    private var popup: Popup? = null
    private val releasedAt = HashMap<Cell, Long>()
    private val fades = ArrayList<Fade>()
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? = try { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator } catch (e: Throwable) { null }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()
    private val tmp2 = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity * cfg.fontScale

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun scaleAlpha(c: Int, f: Float) = withAlpha(c, (Color.alpha(c) * f).toInt())
    private fun keyAlpha(c: Int): Int = withAlpha(c, Color.alpha(c) * cfg.keyAlpha.coerceIn(20, 100) / 100)

    init { rebuild() }

    fun applyConfig(c: KbConfig) {
        cfg = c
        pal = Palette.of(c)
        rebuild()
        requestLayout()
    }

    private fun totalHeight(): Float = dp(8f) * 2 + 5 * dp(cfg.keyHeight) + 4 * dp(cfg.gap)
    fun heightPx(): Int = totalHeight().toInt()

    fun isEnglish(): Boolean =
        layer == Layer.EN || (layer in listOf(Layer.NUM, Layer.SYM1, Layer.SYM2) && lastLetters == Layer.EN)
    fun isExtLayer(): Boolean = layer == Layer.AR_EXT
    fun showLanguage(english: Boolean) = setLayer(if (english) Layer.EN else Layer.AR)
    fun toggleExtLayer() = setLayer(if (layer == Layer.AR_EXT) Layer.AR else Layer.AR_EXT)
    fun showNumbers() { if (layer != Layer.NUM) setLayer(Layer.NUM) }
    fun backToLetters() { if (layer == Layer.NUM || layer == Layer.SYM1 || layer == Layer.SYM2) setLayer(lastLetters) }

    /** يفعّل Shift لمرة واحدة تلقائياً (بداية الجملة) دون إشعار المستمع. */
    fun autoShift(on: Boolean) {
        if (on && layer == Layer.EN && shift == 0) { shift = 1; rebuild() }
    }

    private fun rebuild() {
        layoutCells(Layouts.rows(layer, shift != 0, lastLetters, cfg), width)
        invalidate()
    }

    private fun layoutCells(rows: List<List<Key>>, w: Int) {
        cachedRows = rows
        val pad = dp(8f)
        val gap = dp(cfg.gap)
        val contentW = w * cfg.widthPct.coerceIn(40, 100) / 100f
        val left = when (cfg.align) {
            0 -> 0f
            2 -> w - contentW
            else -> (w - contentW) / 2f
        }
        val rowH = (totalHeight() - 2 * pad - (rows.size - 1) * gap) / rows.size
        val gapMid = if (cfg.split) contentW * cfg.splitGap.coerceIn(5, 40) / 100f else 0f
        val out = ArrayList<List<Cell>>()
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var mid = -1
            if (cfg.split && ri < rows.size - 1 && row.size >= 6) {
                var cum = 0.0
                for (j in row.indices) {
                    if (cum + row[j].weight / 2.0 >= total / 2.0) { mid = j; break }
                    cum += row[j].weight
                }
            }
            val avail = contentW - 2 * pad - gap * (row.size - 1) - (if (mid > 0) gapMid else 0f)
            var x = left + pad
            val y = pad + ri * (rowH + gap)
            out.add(
                row.mapIndexed { j, key ->
                    if (j == mid && mid > 0) x += gapMid
                    val kw = avail * key.weight / total
                    val c = Cell(key, RectF(x, y, x + kw, y + rowH), ri)
                    x += kw + gap
                    c
                },
            )
        }
        cells = out
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), totalHeight().toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        layoutCells(cachedRows.ifEmpty { Layouts.rows(layer, shift != 0, lastLetters, cfg) }, w)
    }

    private fun setLayer(l: Layer) {
        layer = l
        if (l == Layer.AR || l == Layer.EN) lastLetters = l
        shift = 0
        rebuild()
        listener?.onLayerChanged()
    }

    private fun labelOf(k: Key): String = when (k.kind) {
        Kind.SHIFT -> if (shift == 2) "⇪" else "⇧"
        else -> k.label
    }

    /** الحركات تُعرض فوق تطويلة ليسهل رؤيتها. */
    private fun display(s: String): String =
        if (s.isNotEmpty() && s.all { it in '\u064B'..'\u0652' || it == '\u0670' }) "ـ$s" else s

    private fun centerText(c: Canvas, s: String, r: RectF) {
        val fm = txt.fontMetrics
        c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt)
    }

    // ===== الاهتزاز =====
    private fun buzz() {
        if (!cfg.haptic) return
        val v = vibrator
        if (cfg.vibMs > 0 && v != null) {
            try {
                v.vibrate(VibrationEffect.createOneShot(cfg.vibMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                return
            } catch (e: Throwable) {
                // نكمل بالاهتزاز العادي
            }
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ===== الرسم =====
    /** مستوى الضغط من 0 إلى 1 (يرتفع بسرعة عند اللمس وينزل بنعومة عند الرفع). */
    private fun pressLevel(cell: Cell, now: Long): Float {
        for (p in presses.values) {
            if (p.cell === cell) return ((now - p.t0) / 60f).coerceIn(0f, 1f)
        }
        val tr = releasedAt[cell] ?: return 0f
        return 1f - ((now - tr) / 170f).coerceIn(0f, 1f)
    }

    private fun gradFor(cell: Cell, base: Int): LinearGradient {
        val g = cell.grad
        if (g != null && cell.gradBase == base) return g
        val ng = LinearGradient(
            0f, cell.r.top, 0f, cell.r.bottom,
            lerp(base, Color.WHITE, 0.10f), lerp(base, Color.BLACK, 0.09f), Shader.TileMode.CLAMP,
        )
        cell.grad = ng
        cell.gradBase = base
        return ng
    }

    private fun drawKey(c: Canvas, cell: Cell, level: Float, rad: Float) {
        val k = cell.key
        val isFunc = k.kind != Kind.CHAR && k.kind != Kind.SPACE
        val accentKey = k.kind == Kind.ENTER || (k.kind == Kind.SHIFT && shift != 0)
        val base = when {
            accentKey -> pal.accent
            isFunc -> pal.func
            else -> pal.key
        }
        val target = if (accentKey) lerp(pal.accent, pal.text, 0.2f) else pal.pressed
        val color = lerp(base, target, level)
        val rr = if (cfg.funcRound && k.pill) cell.r.height() / 2f else rad
        val inset = level * dp(1.3f)
        tmp.set(cell.r.left + inset, cell.r.top + inset, cell.r.right - inset, cell.r.bottom - inset)
        fill.style = Paint.Style.FILL
        fill.shader = null
        when (cfg.keyStyle) {
            1 -> { // مسطّح
                fill.color = keyAlpha(color)
                c.drawRoundRect(tmp, rr, rr, fill)
            }
            2 -> { // حدود فقط
                fill.color = withAlpha(color, 34 + (110 * level).toInt())
                c.drawRoundRect(tmp, rr, rr, fill)
                stroke.strokeWidth = dp(1.3f)
                stroke.color = lerp(if (accentKey) pal.accent else pal.hint, pal.accent, level)
                c.drawRoundRect(tmp, rr, rr, stroke)
            }
            3 -> { // زجاجي
                val glass = lerp(color, Color.WHITE, 0.25f)
                fill.color = withAlpha(glass, (if (cfg.night) 80 else 165) + (60 * level).toInt())
                if (accentKey) fill.color = withAlpha(pal.accent, 210)
                c.drawRoundRect(tmp, rr, rr, fill)
                stroke.strokeWidth = dp(1f)
                stroke.color = Color.argb(if (cfg.night) 70 else 150, 255, 255, 255)
                c.drawRoundRect(tmp, rr, rr, stroke)
            }
            else -> { // ناعم متدرّج
                if (cfg.edge && level < 0.5f) {
                    tmp2.set(tmp.left, tmp.top + dp(1.6f), tmp.right, tmp.bottom + dp(1.6f))
                    fill.color = keyAlpha(pal.edge)
                    c.drawRoundRect(tmp2, rr, rr, fill)
                }
                if (level < 0.02f) {
                    fill.shader = gradFor(cell, base)
                    fill.color = Color.WHITE
                    fill.alpha = (255 * cfg.keyAlpha.coerceIn(20, 100) / 100)
                } else {
                    fill.color = keyAlpha(color)
                }
                c.drawRoundRect(tmp, rr, rr, fill)
                fill.shader = null
                stroke.strokeWidth = dp(1f)
                stroke.color = Color.argb(if (cfg.night) 34 else 110, 255, 255, 255)
                c.drawRoundRect(tmp, rr, rr, stroke)
            }
        }
    }

    private fun drawBubble(c: Canvas, r: RectF, label: String, alpha: Float, lift: Float) {
        val bw = maxOf(r.width() * 1.45f, dp(60f))
        val bh = r.height() * 1.3f
        val l = (r.centerX() - bw / 2).coerceIn(dp(2f), maxOf(dp(2f), width - bw - dp(2f)))
        val t = maxOf(dp(2f), r.top - bh + dp(8f) - lift)
        val body = RectF(l, t, l + bw, t + bh)
        val neck = RectF(r.left + dp(1f), t + bh - dp(16f), r.right - dp(1f), r.top + r.height() * 0.4f)
        val corner = dp(14f)
        val face = lerp(pal.key, pal.text, if (cfg.night) 0.14f else 0.03f)
        // ظل خفيف
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = Color.argb((70 * alpha).toInt(), 0, 0, 0)
        tmp2.set(body.left, body.top + dp(3f), body.right, body.bottom + dp(3f))
        c.drawRoundRect(tmp2, corner, corner, fill)
        // الجسم والعنق
        fill.color = scaleAlpha(face or (0xFF shl 24), alpha)
        c.drawRoundRect(body, corner, corner, fill)
        c.drawRoundRect(neck, dp(8f), dp(8f), fill)
        stroke.strokeWidth = dp(1f)
        stroke.color = scaleAlpha(withAlpha(pal.accent, 150), alpha)
        c.drawRoundRect(body, corner, corner, stroke)
        txt.color = scaleAlpha(pal.text or (0xFF shl 24), alpha)
        txt.textSize = sp(32f)
        tmp.set(body.left, body.top, body.right, body.bottom - dp(8f))
        centerText(c, label, tmp)
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        var animating = false
        val rad = dp(cfg.radius)
        txt.typeface = if (cfg.boldKeys) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        val ri = releasedAt.entries.iterator()
        while (ri.hasNext()) { if (now - ri.next().value > 180) ri.remove() }
        val fi = fades.iterator()
        while (fi.hasNext()) { if (now - fi.next().t > 130) fi.remove() }

        for (row in cells) for (cell in row) {
            val k = cell.key
            if (k.kind == Kind.SPACER) continue
            val level = pressLevel(cell, now)
            if (level > 0f && level < 1f) animating = true
            if (releasedAt.containsKey(cell)) animating = true
            drawKey(c, cell, level, rad)

            val onAcc = k.kind == Kind.ENTER || (k.kind == Kind.SHIFT && shift != 0)
            val label = labelOf(k)
            txt.textSize = when {
                k.kind == Kind.SPACE -> sp(13f)
                k.kind == Kind.CHAR && label.length <= 2 -> sp(23f)
                k.kind == Kind.SYMBOLS || k.kind == Kind.NUMBERS -> sp(17f)
                else -> sp(16f)
            }
            txt.color = when {
                k.kind == Kind.SPACE -> pal.hint
                onAcc -> pal.onAccent
                k.kind == Kind.SYMBOLS || k.kind == Kind.NUMBERS || k.kind == Kind.DELETE -> pal.accent
                else -> pal.text
            }
            val dy = level * dp(0.8f)
            tmp.set(cell.r.left, cell.r.top + dy, cell.r.right, cell.r.bottom + dy)
            centerText(c, label, tmp)
            val isDigitKey = label.length == 1 && Character.isDigit(label[0])
            if (cfg.hints && !isDigitKey && k.kind == Kind.CHAR && k.alts.isNotEmpty()) {
                txt.color = pal.accent
                txt.textSize = sp(11f)
                val f2 = txt.fontMetrics
                c.drawText(display(k.alts[0]), cell.r.right - dp(10f), cell.r.top + dp(6f) - f2.ascent, txt)
            }
        }

        // فقاعة معاينة الحرف: تبقى أثناء الضغط ثم تختفي بنعومة
        if (cfg.preview && popup == null) {
            for (p in presses.values) {
                val k = p.cell.key
                if (k.kind != Kind.CHAR || p.longDone || k.label.isEmpty()) continue
                val e = ((now - p.t0) / 70f).coerceIn(0f, 1f)
                if (e < 1f) animating = true
                drawBubble(c, p.cell.r, k.label, 0.35f + 0.65f * e, dp(6f) * (1f - e))
            }
            for (f in fades) {
                val a = 1f - ((now - f.t) / 130f).coerceIn(0f, 1f)
                animating = true
                drawBubble(c, f.rect, f.label, a, 0f)
            }
        }

        popup?.let { p ->
            val corner = dp(14f)
            fill.style = Paint.Style.FILL
            fill.shader = null
            fill.color = Color.argb(80, 0, 0, 0)
            tmp2.set(p.box.left, p.box.top + dp(3f), p.box.right, p.box.bottom + dp(3f))
            c.drawRoundRect(tmp2, corner, corner, fill)
            fill.color = pal.func or (0xFF shl 24)
            c.drawRoundRect(p.box, corner, corner, fill)
            stroke.strokeWidth = dp(1f)
            stroke.color = withAlpha(pal.accent, 140)
            c.drawRoundRect(p.box, corner, corner, stroke)
            p.rects.forEachIndexed { i, r ->
                if (i == p.hover) {
                    fill.color = pal.accent
                    c.drawRoundRect(r, dp(10f), dp(10f), fill)
                }
                txt.color = if (i == p.hover) pal.onAccent else pal.text
                txt.textSize = sp(24f)
                centerText(c, display(p.items[i]), r)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    // ===== اللمس =====
    private fun hit(x: Float, y: Float): Cell? {
        var best: Cell? = null
        var bd = Float.MAX_VALUE
        for (row in cells) for (cell in row) {
            if (cell.key.kind == Kind.SPACER) continue
            val dx = if (x < cell.r.left) cell.r.left - x else if (x > cell.r.right) x - cell.r.right else 0f
            val dy = if (y < cell.r.top) cell.r.top - y else if (y > cell.r.bottom) y - cell.r.bottom else 0f
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = cell }
        }
        return if (bd <= dp(8f) * dp(8f)) best else null
    }

    private fun begin(id: Int, x: Float, y: Float) {
        val cell = hit(x, y) ?: return
        val p = Press(cell, x, y)
        presses[id] = p
        buzz()
        if (cfg.sound) playSoundEffect(SoundEffectConstants.CLICK)
        val k = cell.key
        when {
            k.kind == Kind.DELETE -> {
                p.longDone = true
                listener?.onDelete()
                val rep = object : Runnable {
                    var n = 0
                    override fun run() {
                        n++
                        if (n > 10) listener?.onDeleteWord() else listener?.onDelete()
                        handler.postDelayed(this, if (n > 10) 140L else cfg.repeatMs.toLong())
                    }
                }
                p.job = rep
                handler.postDelayed(rep, 420)
            }
            k.kind == Kind.CHAR && k.alts.isNotEmpty() -> {
                val r = Runnable { openPopup(id, p) }
                p.job = r
                handler.postDelayed(r, cfg.longPressMs.toLong())
            }
            k.kind == Kind.SPACE -> {
                val r = Runnable {
                    p.longDone = true
                    presses.remove(id)
                    releasedAt[p.cell] = SystemClock.uptimeMillis()
                    listener?.onLangLongPress()
                    invalidate()
                }
                p.job = r
                handler.postDelayed(r, cfg.longPressMs * 2L)
            }
        }
        invalidate()
    }

    /** يفتح قائمة البدائل (بالضغط المطوّل أو بالسحب للأعلى). */
    private fun openPopup(id: Int, p: Press) {
        val k = p.cell.key
        if (k.alts.isEmpty() || popup != null) return
        p.longDone = true
        p.job?.let { handler.removeCallbacks(it) }
        buzz()
        val n = k.alts.size
        val cw = minOf(maxOf(p.cell.r.width(), dp(46f)), (width - dp(8f)) / n)
        val total = cw * n
        // المفتاح الأول من البدائل يقع فوق المفتاح مباشرة (ليكون السحب القصير = أول بديل)
        val x0 = p.cell.r.left.coerceIn(dp(4f), maxOf(dp(4f), width - total - dp(4f)))
        val kh = p.cell.r.height()
        val below = p.cell.row == 0
        val y0 = if (below) p.cell.r.bottom + dp(8f) else p.cell.r.top - kh - dp(8f)
        val rects = k.alts.indices.map {
            RectF(x0 + it * cw + dp(3f), y0 + dp(3f), x0 + (it + 1) * cw - dp(3f), y0 + kh - dp(3f))
        }
        val pop = Popup(id, k.alts, rects, RectF(x0, y0, x0 + total, y0 + kh), below, p.cell.r.top, p.cell.r.bottom)
        pop.hover = pop.nearest(p.lastX)
        popup = pop
        invalidate()
    }

    private fun hoverAt(pop: Popup, x: Float, y: Float): Int {
        val cancel = if (pop.below) y < pop.cellTop - dp(30f) else y > pop.cellBottom + dp(40f)
        return if (cancel) -1 else pop.nearest(x)
    }

    private fun end(id: Int, x: Float, y: Float, cancel: Boolean) {
        val p = presses.remove(id) ?: return
        p.job?.let { handler.removeCallbacks(it) }
        val now = SystemClock.uptimeMillis()
        releasedAt[p.cell] = now
        val pop = popup
        if (pop != null && pop.pointer == id) {
            val h = if (cancel) -1 else hoverAt(pop, x, y)
            popup = null
            if (h >= 0) { listener?.onText(pop.items[h]); afterChar() }
            invalidate()
            return
        }
        if (!cancel && !p.longDone && cfg.preview && p.cell.key.kind == Kind.CHAR && p.cell.key.label.isNotEmpty()) {
            fades.add(Fade(RectF(p.cell.r), p.cell.key.label, now))
        }
        if (!p.longDone && !cancel) fire(p.cell.key)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                begin(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val p = presses[id] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    val pop = popup
                    if (pop != null && pop.pointer == id) {
                        val h = hoverAt(pop, x, y)
                        if (h != pop.hover) {
                            pop.hover = h
                            if (h >= 0 && cfg.haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            invalidate()
                        }
                        p.lastX = x
                    } else if (p.cell.key.kind == Kind.SPACE && cfg.swipeSpace) {
                        val step = dp(22f)
                        val dx = x - p.lastX
                        if (abs(dx) >= step) {
                            p.job?.let { handler.removeCallbacks(it) }
                            val n = (abs(dx) / step).toInt()
                            val dir = if (dx > 0) 1 else -1
                            repeat(n) { listener?.onCursor(dir) }
                            p.lastX += dir * n * step
                            p.longDone = true
                        }
                    } else if (p.cell.key.kind == Kind.CHAR && p.cell.key.alts.isNotEmpty() &&
                        p.cell.row > 0 && p.startY - y > dp(16f) && !p.longDone
                    ) {
                        // سحب للأعلى: تظهر البدائل فوراً بدون انتظار الضغط المطوّل
                        p.lastX = x
                        openPopup(id, p)
                    } else {
                        val cell = hit(x, y)
                        if (cell?.key !== p.cell.key) {
                            p.job?.let { handler.removeCallbacks(it) }
                            presses.remove(id)
                            releasedAt[p.cell] = SystemClock.uptimeMillis()
                            begin(id, x, y)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                end(e.getPointerId(i), e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                presses.keys.toList().forEach { end(it, 0f, 0f, true) }
                popup = null
                invalidate()
            }
        }
        return true
    }

    private fun afterChar() {
        if (layer == Layer.EN && shift == 1) { shift = 0; rebuild() }
    }

    private fun fire(k: Key) {
        when (k.kind) {
            Kind.CHAR, Kind.SPACE -> { listener?.onText(k.text); afterChar() }
            Kind.ENTER -> listener?.onEnter()
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (now - lastShiftTap < 350 && shift == 1) 2 else if (shift == 0) 1 else 0
                lastShiftTap = now
                rebuild()
            }
            Kind.EXT -> setLayer(if (layer == Layer.AR_EXT) Layer.AR else Layer.AR_EXT)
            Kind.SYMBOLS -> setLayer(Layer.SYM1)
            Kind.NUMBERS -> setLayer(Layer.NUM)
            Kind.LETTERS -> setLayer(lastLetters)
            Kind.SYM_PAGE -> setLayer(if (layer == Layer.SYM1) Layer.SYM2 else Layer.SYM1)
            else -> {}
        }
    }
}
