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

    /** Fallback-Hintergrund, wenn die Root-View keine auswertbare Farbe liefert. */
    private const val FALLBACK_BG = 0xFF1a1a1a.toInt()

    private val ease = DecelerateInterpolator(1.6f)

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
        lp.height = 0
        row.requestLayout()
        row.post {
            val target = if (row.height > 0) row.height else
                (48 * row.resources.displayMetrics.density).toInt()
            ValueAnimator.ofInt(0, target).apply {
                duration = DURATION_ROW
                interpolator = ease
                addUpdateListener { a ->
                    lp.height = a.animatedValue as Int
                    row.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        row.requestLayout()
                    }
                })
                start()
            }
        }
    }

    fun collapseRow(row: View, onDone: (() -> Unit)? = null) {
        val lp = row.layoutParams ?: return
        val from = row.height
        if (from <= 0) {
            row.visibility = View.GONE
            onDone?.invoke()
            return
        }
        ValueAnimator.ofInt(from, 0).apply {
            duration = DURATION_ROW
            interpolator = ease
            addUpdateListener { a ->
                lp.height = a.animatedValue as Int
                row.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    row.visibility = View.GONE
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    row.requestLayout()
                    onDone?.invoke()
                }
            })
            start()
        }
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
