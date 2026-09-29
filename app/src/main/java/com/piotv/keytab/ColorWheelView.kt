package com.piotv.keytab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Farbwahlrad (HSV): Winkel = Farbton (Hue), Radius = Sättigung, plus
 * einstellbare Helligkeit (Value) und Alpha (von außen gesetzt). Beim Antippen/
 * Ziehen wird [onColorPicked] mit der aktuellen ARGB-Farbe aufgerufen.
 */
class ColorWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val RING_INNER_PADDING = 8f
        private const val HUE_SEGMENT_COUNT = 13
        private const val HUE_STEP_DEG = 30f
        private const val HUE_FULL_CIRCLE_DEG = 360f
        private const val MAX_ALPHA = 255
        private const val ALPHA_SHIFT_BITS = 24
        private const val MARKER_RADIUS_INNER = 10f
        private const val MARKER_RADIUS_OUTER = 12f
        private const val MARKER_STROKE_WIDTH = 4f
    }

    var onColorPicked: ((argb: Int) -> Unit)? = null

    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var alpha = MAX_ALPHA

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MARKER_STROKE_WIDTH
        color = Color.WHITE
    }

    init {
        // Overlay-Gradients mit Transparenz zeichnen korrekt in Software-Layer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Farbe von außen laden (ARGB) – Marker/Regler aktualisieren sich. */
    fun setArgb(argb: Int) {
        Color.RGBToHSV(Color.red(argb), Color.green(argb), Color.blue(argb), hsv)
        alpha = Color.alpha(argb)
        invalidate()
    }

    /** Helligkeit (Value) 0..1 setzen und Änderung melden. */
    fun setBrightness(value: Float) {
        hsv[2] = value.coerceIn(0f, 1f)
        invalidate()
        notifyPicked()
    }

    /** Alpha 0..255 setzen und Änderung melden. */
    fun setAlphaValue(a: Int) {
        alpha = a.coerceIn(0, MAX_ALPHA)
        notifyPicked()
    }

    private fun currentColor(): Int = Color.HSVToColor(alpha, hsv)

    private fun notifyPicked() {
        onColorPicked?.invoke(currentColor())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - RING_INNER_PADDING
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        // Farbring (Hue)
        val n = HUE_SEGMENT_COUNT
        val hueColors = IntArray(n) { i ->
            Color.HSVToColor(floatArrayOf(i * HUE_STEP_DEG % HUE_FULL_CIRCLE_DEG, 1f, 1f))
        }
        paint.shader = SweepGradient(cx, cy, hueColors, null)
        canvas.drawCircle(cx, cy, radius, paint)
        // Weiß im Zentrum (Sättigung außen = 1)
        paint.shader = RadialGradient(cx, cy, radius,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        // Helligkeit: gleichmäßiges schwarzes Overlay (nicht radial – sonst
        // wäre der Rand dunkler als das Zentrum bei niedrigem Value)
        val dim = (MAX_ALPHA * (1f - hsv[2])).toInt()
        paint.shader = null
        paint.color = (dim shl ALPHA_SHIFT_BITS) or 0x000000
        canvas.drawCircle(cx, cy, radius, paint)
        // Marker
        val angle = Math.toRadians(hsv[0].toDouble())
        val mx = (cx + cos(angle) * hsv[1] * radius).toFloat()
        val my = (cy + sin(angle) * hsv[1] * radius).toFloat()
        markerPaint.color = Color.WHITE
        canvas.drawCircle(mx, my, MARKER_RADIUS_INNER, markerPaint)
        markerPaint.color = Color.BLACK
        markerPaint.strokeWidth = 2f
        canvas.drawCircle(mx, my, MARKER_RADIUS_OUTER, markerPaint)
        markerPaint.strokeWidth = MARKER_STROKE_WIDTH
    }

    private val input = com.piotv.keytab.ime.ColorWheelInputLogic()

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Zeiger-Position per Index; -1, wenn der Zeiger nicht (mehr) im Event ist. */
    private fun xAt(event: android.view.MotionEvent, index: Int): Float =
        if (index in 0 until event.pointerCount) event.getX(index) else 0f

    private fun yAt(event: android.view.MotionEvent, index: Int): Float =
        if (index in 0 until event.pointerCount) event.getY(index) else 0f

    private fun insideAt(event: android.view.MotionEvent, index: Int): Boolean {
        if (index < 0 || index >= event.pointerCount || radius <= 0f) return false
        val dx = xAt(event, index) - cx
        val dy = yAt(event, index) - cy
        return dx * dx + dy * dy <= radius * radius
    }

    /**
     * Multi-Touch-sichere Geste. Entscheidungen über Zeiger-Übergänge trifft
     * [com.piotv.keytab.ime.ColorWheelInputLogic]; hier wird nur noch
     * `MotionEvent` übersetzt. Kernpunkt: `event.x` ist **immer** der Zeiger
     * mit Index 0 — mit zwei Fingern auf dem Rad sprang die Farbe dadurch auf
     * den falschen Finger, und `ACTION_POINTER_UP` des aktiven Fingers
     * beendete das Tracking nicht.
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (!input.onDown(event.getPointerId(0), insideAt(event, 0))) return false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN ->
                // Zweiter Finger: Geste läuft weiter, nur dieser eine wird nicht verfolgt.
                return input.onSecondaryDown(event.getPointerId(event.actionIndex))
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!input.isTracking()) return false
                val id = input.activePointer
                val index = event.findPointerIndex(id)
                if (index < 0) {
                    // Der verfolgte Zeiger ist aus dem Event verschwunden: Geste sauber beenden.
                    input.onRelease()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }
                applyPosition(xAt(event, index), yAt(event, index))
                return true
            }
            android.view.MotionEvent.ACTION_POINTER_UP -> {
                val lifted = event.getPointerId(event.actionIndex)
                // Nur der aktive Finger beendet die Geste; das Event wird in jedem
                // Fall konsumiert, solange eine Geste lief.
                val wasTracking = input.isTracking()
                if (input.onPointerUp(lifted)) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return wasTracking
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (!input.isTracking()) return false
                val index = event.findPointerIndex(input.activePointer)
                val endedInside = insideAt(event, if (index < 0) 0 else index)
                applyPosition(xAt(event, if (index < 0) 0 else index),
                    yAt(event, if (index < 0) 0 else index))
                input.onRelease()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (endedInside) performClick()
                return true
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (!input.isTracking()) return false
                input.onRelease()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            else -> return input.isTracking()
        }
        applyPosition(xAt(event, 0), yAt(event, 0))
        return true
    }

    /**
     * Position übernehmen. Außerhalb des Radius wird **nichts** geschrieben —
     * ein Ziehen über den Rand hinaus verändert die zuletzt gewählte Farbe nicht.
     */
    private fun applyPosition(x: Float, y: Float) {
        val hsvOut = FloatArray(2)
        if (!input.hsvAt(cx, cy, radius, x, y, hsvOut)) return
        hsv[0] = hsvOut[0]
        hsv[1] = hsvOut[1]
        invalidate()
        notifyPicked()
    }
}
