package com.arabickb.shaped

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import kotlin.math.abs

class KeyboardView(context: Context, private var cfg: KbConfig) : View(context) {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
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

    private class Cell(val key: Key, val r: RectF, val row: Int)
    private var cells: List<List<Cell>> = emptyList()
    private var cachedRows: List<List<Key>> = emptyList()

    private inner class Press(val cell: Cell, var lastX: Float) {
        var longDone = false
        var job: Runnable? = null
    }
    private class Popup(val pointer: Int, val items: List<String>, val rects: List<RectF>) {
        var hover = -1
        fun hit(x: Float, y: Float) = rects.indexOfFirst { it.contains(x, y) }
    }
    private val presses = HashMap<Int, Press>()
    private var popup: Popup? = null
    private val handler = Handler(Looper.getMainLooper())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity * cfg.fontScale
    private fun alpha(color: Int): Int = (((color ushr 24) * cfg.keyAlpha.coerceIn(20, 100) / 100) shl 24) or (color and 0x00FFFFFF)

    init { rebuild() }

    fun applyConfig(c: KbConfig) {
        cfg = c
        pal = Palette.of(c)
        rebuild()
        requestLayout()
    }

    private fun totalHeight(): Float = dp(8f) * 2 + 5 * dp(cfg.keyHeight) + 4 * dp(cfg.gap)
    fun heightPx(): Int = totalHeight().toInt()

    fun isEnglish(): Boolean = layer == Layer.EN || (layer in listOf(Layer.NUM, Layer.SYM1, Layer.SYM2) && lastLetters == Layer.EN)
    fun isExtLayer(): Boolean = layer == Layer.AR_EXT
    fun showLanguage(english: Boolean) = setLayer(if (english) Layer.EN else Layer.AR)
    fun toggleExtLayer() = setLayer(if (layer == Layer.AR_EXT) Layer.AR else Layer.AR_EXT)

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
        val out = ArrayList<List<Cell>>()
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val avail = contentW - 2 * pad - gap * (row.size - 1)
            var x = left + pad
            val y = pad + ri * (rowH + gap)
            out.add(row.map { key ->
                val kw = avail * key.weight / total
                val c = Cell(key, RectF(x, y, x + kw, y + rowH), ri)
                x += kw + gap
                c
            })
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

    private fun centerText(c: Canvas, s: String, r: RectF) {
        val fm = txt.fontMetrics
        c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt)
    }

    override fun onDraw(c: Canvas) {
        val rad = dp(cfg.radius)
        for (row in cells) for (cell in row) {
            val k = cell.key
            if (k.kind == Kind.SPACER) continue
            val isPressed = presses.values.any { it.cell === cell }
            val isFunc = k.kind != Kind.CHAR && k.kind != Kind.SPACE
            val rr = if (cfg.funcRound && k.pill) cell.r.height() / 2f else rad
            if (cfg.edge && !isPressed) {
                tmp.set(cell.r.left, cell.r.top + dp(1.5f), cell.r.right, cell.r.bottom + dp(1.5f))
                fill.color = alpha(pal.edge)
                c.drawRoundRect(tmp, rr, rr, fill)
            }
            fill.color = alpha(
                when {
                    isPressed -> pal.pressed
                    k.kind == Kind.ENTER -> pal.accent
                    k.kind == Kind.SHIFT && shift != 0 -> pal.accent
                    k.kind == Kind.EXT -> pal.func
                    isFunc -> pal.func
                    else -> pal.key
                },
            )
            c.drawRoundRect(cell.r, rr, rr, fill)
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
                k.kind == Kind.SYMBOLS || k.kind == Kind.NUMBERS -> pal.accent
                k.kind == Kind.DELETE -> pal.accent
                else -> pal.text
            }
            centerText(c, label, cell.r)
            if (cfg.hints && k.kind == Kind.CHAR && k.alts.isNotEmpty()) {
                txt.color = pal.accent
                txt.textSize = sp(11f)
                val f2 = txt.fontMetrics
                c.drawText(k.alts[0], cell.r.right - dp(10f), cell.r.top + dp(6f) - f2.ascent, txt)
            }
        }
        if (cfg.preview && popup == null) {
            for (p in presses.values) {
                val k = p.cell.key
                if (k.kind != Kind.CHAR || p.longDone || k.text.isEmpty()) continue
                val bw = maxOf(p.cell.r.width() * 1.3f, dp(56f))
                val bh = p.cell.r.height() * 1.25f
                val l = (p.cell.r.centerX() - bw / 2).coerceIn(0f, maxOf(0f, width - bw))
                val t = maxOf(0f, p.cell.r.top - bh - dp(4f))
                tmp.set(l, t, l + bw, t + bh)
                fill.color = pal.accent
                c.drawRoundRect(tmp, dp(14f), dp(14f), fill)
                txt.color = pal.onAccent
                txt.textSize = sp(30f)
                centerText(c, k.label, tmp)
            }
        }
        popup?.let { p ->
            p.rects.forEachIndexed { i, r ->
                fill.color = if (i == p.hover) pal.accent else pal.func
                c.drawRoundRect(r, rad, rad, fill)
                txt.color = if (i == p.hover) pal.onAccent else pal.text
                txt.textSize = sp(24f)
                centerText(c, p.items[i], r)
            }
        }
    }

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
        val p = Press(cell, x)
        presses[id] = p
        if (cfg.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (cfg.sound) playSoundEffect(SoundEffectConstants.CLICK)
        val k = cell.key
        when {
            k.kind == Kind.DELETE -> {
                p.longDone = true
                listener?.onDelete()
                val rep = object : Runnable {
                    override fun run() { listener?.onDelete(); handler.postDelayed(this, cfg.repeatMs.toLong()) }
                }
                p.job = rep
                handler.postDelayed(rep, 420)
            }
            k.kind == Kind.CHAR && k.alts.isNotEmpty() -> {
                val r = Runnable { longPress(id, p) }
                p.job = r
                handler.postDelayed(r, cfg.longPressMs.toLong())
            }
            k.kind == Kind.SPACE -> {
                val r = Runnable {
                    p.longDone = true
                    presses.remove(id)
                    listener?.onLangLongPress()
                    invalidate()
                }
                p.job = r
                handler.postDelayed(r, cfg.longPressMs * 2L)
            }
        }
        invalidate()
    }

    private fun longPress(id: Int, p: Press) {
        p.longDone = true
        val k = p.cell.key
        if (cfg.haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val cw = maxOf(p.cell.r.width(), dp(52f))
        val total = cw * k.alts.size
        val x0 = (p.cell.r.centerX() - total / 2).coerceIn(dp(4f), maxOf(dp(4f), width - total - dp(4f)))
        val kh = p.cell.r.height()
        val y0 = if (p.cell.row > 0) p.cell.r.top - kh - dp(6f) else p.cell.r.bottom + dp(6f)
        val rects = k.alts.indices.map { RectF(x0 + it * cw, y0, x0 + (it + 1) * cw - dp(4f), y0 + kh) }
        popup = Popup(id, k.alts, rects)
        invalidate()
    }

    private fun end(id: Int, x: Float, y: Float, cancel: Boolean) {
        val p = presses.remove(id) ?: return
        p.job?.let { handler.removeCallbacks(it) }
        val pop = popup
        if (pop != null && pop.pointer == id) {
            val i = pop.hit(x, y)
            popup = null
            if (!cancel && i >= 0) { listener?.onText(pop.items[i]); afterChar() }
            invalidate()
            return
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
                    val pop = popup
                    if (pop != null && pop.pointer == id) {
                        val h = pop.hit(e.getX(i), e.getY(i))
                        if (h != pop.hover) { pop.hover = h; invalidate() }
                    } else if (p.cell.key.kind == Kind.SPACE && cfg.swipeSpace) {
                        val step = dp(22f)
                        val dx = e.getX(i) - p.lastX
                        if (abs(dx) >= step) {
                            p.job?.let { handler.removeCallbacks(it) }
                            val n = (abs(dx) / step).toInt()
                            val dir = if (dx > 0) 1 else -1
                            repeat(n) { listener?.onCursor(dir) }
                            p.lastX += dir * n * step
                            p.longDone = true
                        }
                    } else {
                        val cell = hit(e.getX(i), e.getY(i))
                        if (cell?.key !== p.cell.key) {
                            p.job?.let { handler.removeCallbacks(it) }
                            presses.remove(id)
                            begin(id, e.getX(i), e.getY(i))
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
