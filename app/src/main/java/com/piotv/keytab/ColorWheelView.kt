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
import kotlin.math.atan2
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

    var onColorPicked: ((argb: Int) -> Unit)? = null

    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var alpha = 255

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
        alpha = a.coerceIn(0, 255)
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
        radius = min(w, h) / 2f - 8f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        // Farbring (Hue)
        val n = 13
        val hueColors = IntArray(n) { i ->
            Color.HSVToColor(floatArrayOf(i * 30f % 360f, 1f, 1f))
        }
        paint.shader = SweepGradient(cx, cy, hueColors, null)
        canvas.drawCircle(cx, cy, radius, paint)
        // Weiß im Zentrum (Sättigung außen = 1)
        paint.shader = RadialGradient(cx, cy, radius,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        // Helligkeit: gleichmäßiges schwarzes Overlay (nicht radial – sonst
        // wäre der Rand dunkler als das Zentrum bei niedrigem Value)
        val dim = (255 * (1f - hsv[2])).toInt()
        paint.shader = null
        paint.color = (dim shl 24) or 0x000000
        canvas.drawCircle(cx, cy, radius, paint)
        // Marker
        val angle = Math.toRadians(hsv[0].toDouble())
        val mx = (cx + cos(angle) * hsv[1] * radius).toFloat()
        val my = (cy + sin(angle) * hsv[1] * radius).toFloat()
        markerPaint.color = Color.WHITE
        canvas.drawCircle(mx, my, 10f, markerPaint)
        markerPaint.color = Color.BLACK
        markerPaint.strokeWidth = 2f
        canvas.drawCircle(mx, my, 12f, markerPaint)
        markerPaint.strokeWidth = 4f
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                hsv[0] = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    .toFloat() + 360f) % 360f
                hsv[1] = (min(dx * dx + dy * dy, radius * radius).let {
                    kotlin.math.sqrt(it)
                } / radius).coerceIn(0f, 1f)
                invalidate()
                notifyPicked()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
