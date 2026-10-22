package com.piotv.keytab.ime

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

object KeyAnimations {

    private const val DURATION_LAYOUT = 170L
    private const val DURATION_ROW = 190L
    private const val EASE_FACTOR = 1.6f
    private const val FALLBACK_ROW_HEIGHT_DP = 48

    /** Fallback-Hintergrund, wenn die Root-View keine auswertbare Farbe liefert. */
    private const val FALLBACK_BG = 0xFF1a1a1a.toInt()

    private val ease = DecelerateInterpolator(EASE_FACTOR)

    /**
     * Laufende Zeilen-Animationen, je View. WeakHashMap, damit ausgehängte
     * Views nicht am Animator hängen bleiben. Beim neuen [expandRow]/
     * [collapseRow] wird die alte Animation abgebrochen — sonst laufen zwei
     * Animatoren auf denselben `layoutParams` und der Rebuild (Theme-Wechsel)
     * trifft auf eine noch laufende Animation (N25).
     */
    private val running = java.util.WeakHashMap<View, ValueAnimator>()

    /** Bricht eine laufende Animation der Zeile ab (falls vorhanden). */
    private fun cancelRunning(row: View) {
        running.remove(row)?.cancel()
    }

    /** true, wenn [animation] noch die aktuelle Animation der Zeile ist. */
    private fun isCurrent(row: View, animation: android.animation.Animator): Boolean =
        running[row] === animation

    fun applyLayoutTransition(container: ViewGroup) {
        if (container.layoutTransition != null) return
        val t = LayoutTransition()
        t.setDuration(DURATION_LAYOUT)
        t.enableTransitionType(LayoutTransition.CHANGING)
        t.setAnimator(LayoutTransition.APPEARING,
            ValueAnimator.ofFloat(0f, 1f).apply { duration = DURATION_LAYOUT })
        t.setAnimator(LayoutTransition.DISAPPEARING,
            ValueAnimator.ofFloat(1f, 0f).apply { duration = DURATION_LAYOUT })
        container.layoutTransition = t
    }

    fun expandRow(row: View) {
        val lp = row.layoutParams ?: return
        cancelRunning(row)
        lp.height = 0
        row.requestLayout()
        row.post {
            val target = if (row.height > 0) row.height else
                (FALLBACK_ROW_HEIGHT_DP * row.resources.displayMetrics.density).toInt()
            val anim = ValueAnimator.ofInt(0, target).apply {
                duration = DURATION_ROW
                interpolator = ease
                addUpdateListener { a ->
                    if (!isCurrent(row, this)) return@addUpdateListener
                    lp.height = a.animatedValue as Int
                    row.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!isCurrent(row, animation)) return
                        running.remove(row)
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        row.requestLayout()
                    }
                })
            }
            running[row] = anim
            anim.start()
        }
    }

    fun collapseRow(row: View, onDone: (() -> Unit)? = null) {
        val lp = row.layoutParams ?: return
        cancelRunning(row)
        val from = row.height
        if (from <= 0) {
            row.visibility = View.GONE
            onDone?.invoke()
            return
        }
        val anim = ValueAnimator.ofInt(from, 0).apply {
            duration = DURATION_ROW
            interpolator = ease
            addUpdateListener { a ->
                if (!isCurrent(row, this)) return@addUpdateListener
                lp.height = a.animatedValue as Int
                row.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isCurrent(row, animation)) return
                    running.remove(row)
                    row.visibility = View.GONE
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    row.requestLayout()
                    onDone?.invoke()
                }
            })
        }
        running[row] = anim
        anim.start()
    }

    fun applyRoundedCorners(root: View) {
        val currentBg = root.background
        val drawable = GradientDrawable().apply {
            // Keine abgerundeten Ecken (User-Wunsch: obere Zeile eckig);
            // die Funktion bleibt, um den Hintergrund (inkl. Verlauf) zu wrappen.
            cornerRadii = floatArrayOf(
                0f, 0f,
                0f, 0f,
                0f, 0f,
                0f, 0f)
            // Vorhandenen Hintergrund übernehmen — inklusive Verlauf!
            // Ein GradientDrawable mit 2+ Farben liefert `color == null`; früher
            // fiel das auf den dunklen Fallback zurück und zerstörte den vom
            // Nutzer konfigurierten Verlauf (root.background.colors == null).
            when (currentBg) {
                is GradientDrawable -> {
                    val colors = currentBg.colors
                    if (colors != null && colors.size == 2) {
                        orientation = currentBg.orientation
                        setColors(colors)
                    } else {
                        setColor(currentBg.color?.defaultColor ?: FALLBACK_BG)
                    }
                }
                is ColorDrawable -> setColor(currentBg.color)
                else -> setColor(FALLBACK_BG)
            }
        }
        root.background = drawable
    }
}
