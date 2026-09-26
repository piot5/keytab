package com.piotv.keytab.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Overlay-View, die einen vorgegebenen [Path] als Kanten-Linie zeichnet.
 *
 * Wird von [SwipeManager] unter die Tasten gelegt, um die Verbindungslinien
 * zwischen den Preview-Knoten anzuzeigen (KIND_SWIPE_EDGE). Bewusst ein
 * eigenständiger Top-Level-Typ, damit die Zeichen-Logik von der
 * Preview-Steuerung getrennt bleibt.
 *
 * @param path der zu zeichnende Pfad (Linienzug, in View-Koordinaten).
 * @param color Strichfarbe der Kante.
 */
internal class SwipeEdgeOverlay(context: Context, val path: Path, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    private companion object {
        /** Strichbreite der Kanten-Linie in px. */
        const val STROKE_WIDTH = 6f
    }
}
