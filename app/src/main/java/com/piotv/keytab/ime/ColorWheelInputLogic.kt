package com.piotv.keytab.ime

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Kernlogik des Farbrads ([com.piotv.keytab.ColorWheelView]) — bewusst
 * Android-frei und ohne `MotionEvent`, damit jeder Zeiger-Übergang per
 * Unit-Test prüfbar ist. Die View liest nur noch Zeiger-IDs und Koordinaten
 * aus dem Event und delegiert die Zustandsentscheidung hierher.
 *
 * Zustand ist bewusst **pro Instanz** (nicht `object`): das Rad ist ein
 * `View`, es kann mehrere davon in einem Bildschirm geben, und zwei Gesten
 * dürfen sich nie in den Zustand teilen.
 *
 * Behobener Fehler (vorher direkt in der View): `MotionEvent.x` ist immer der
 * Zeiger mit Index 0. Sobald ein zweiter Finger auf dem Rad lag, sprang die
 * gewählte Farbe beim Ziehen auf den *anderen* Finger, und beim
 * `ACTION_POINTER_UP` des aktiven Fingers lief das Tracking weiter, ohne eine
 * gültige Position zu besitzen.
 */
class ColorWheelInputLogic {

    /** Unbekannte Zeiger-ID; zugleich „nichts wird verfolgt". */
    companion object {
        const val NO_POINTER = -1

        /** Ein volles Farbrad = 360°; der Gradwert des Farbraums in HSL/HSV. */
        const val FULL_CIRCLE_DEG = 360f
    }

    /** Aktuell verfolgter Zeiger, oder [NO_POINTER]. */
    var activePointer: Int = NO_POINTER
        private set

    /** Läuft gerade eine Geste? */
    fun isTracking(): Boolean = activePointer != NO_POINTER

    /**
     * `ACTION_DOWN`: Tracking starten, aber nur wenn der Finger im Rad liegt.
     * Ein `DOWN` außerhalb wird abgelehnt — die View gibt das Event dann an
     * den Parent weiter, damit dort z. B. gescrollt werden kann.
     */
    fun onDown(pointerId: Int, inside: Boolean): Boolean {
        if (!inside) {
            activePointer = NO_POINTER
            return false
        }
        activePointer = pointerId
        return true
    }

    /**
     * Weitere Finger werden ignoriert, solange eine Geste läuft: `true`
     * signalisiert „Geste läuft weiter, Event konsumieren".
     */
    fun onSecondaryDown(pointerId: Int): Boolean = isTracking() && pointerId != activePointer

    /**
     * `ACTION_MOVE`: Position des *verfolgten* Zeigers liefern.
     * `null` heißt: nichts tun (etwa weil der Zeiger nicht mehr im Event ist).
     */
    fun trackedPosition(
        pointerId: Int,
        x: Float,
        y: Float
    ): Pair<Float, Float>? =
        if (isTracking() && pointerId == activePointer) x to y else null

    /**
     * `ACTION_POINTER_UP`: nur der aktive Finger beendet die Geste. Hebt ein
     * anderer Finger ab, läuft das Tracking unverändert weiter.
     */
    fun onPointerUp(pointerId: Int): Boolean {
        if (!isTracking() || pointerId != activePointer) return false
        activePointer = NO_POINTER
        return true
    }

    /** `ACTION_UP`/`ACTION_CANCEL`: Geste beenden. */
    fun onRelease() {
        activePointer = NO_POINTER
    }

    /**
     * Punkt im Rad auf Hue/Sättigung abbilden. `out[0]` = Hue 0..360,
     * `out[1]` = Sättigung 0..1, `out[2]` bleibt unberührt.
     *
     * Liefert `false`, wenn der Punkt außerhalb des Radius liegt — dann wird
     * nichts geschrieben, damit ein Ziehen über den Rand hinaus die zuletzt
     * gewählte Farbe nicht verändert.
     */
    fun hsvAt(
        cx: Float,
        cy: Float,
        radius: Float,
        x: Float,
        y: Float,
        out: FloatArray
    ): Boolean {
        if (radius <= 0f) return false
        val dx = x - cx
        val dy = y - cy
        val distSq = dx * dx + dy * dy
        if (distSq > radius * radius) return false
        out[0] = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
        out[1] = (sqrt(distSq) / radius).coerceIn(0f, 1f)
        return true
    }
}
