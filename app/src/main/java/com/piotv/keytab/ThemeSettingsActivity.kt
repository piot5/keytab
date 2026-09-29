package com.piotv.keytab

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.piotv.keytab.ime.ThemePrefs
import com.piotv.keytab.sections.ColorSection
import com.piotv.keytab.sections.GradientSection
import com.piotv.keytab.sections.PreviewSection
import com.piotv.keytab.sections.TopSection

private const val PAGE_PADDING_DP = 20
private const val SECTION_SPACING_DP = 12
private const val CONTENT_BOTTOM_PADDING_DP = 16
private const val TITLE_TEXT_SP = 22f
private const val TARGET_MARGIN_DP = 4
private const val TARGET_HEIGHT_DP = 32
private const val TARGET_TOP_MARGIN_DP = 8
private const val LABEL_TEXT_SP = 16f
private const val LABEL_TOP_PADDING_DP = 18
private const val LABEL_BOTTOM_PADDING_DP = 4
private const val CORNER_RADIUS_DP = 4f
private const val LUMINANCE_RED = 0.299
private const val LUMINANCE_GREEN = 0.587
private const val LUMINANCE_BLUE = 0.114
private const val LUMINANCE_MAX = 255
private const val LUMINANCE_THRESHOLD = 0.5

class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var editingDark = false
    private var selectedTarget = ThemePrefs.KIND_BG
    private var targetButtons: List<Pair<String, Button>> = emptyList()

    private lateinit var backgroundSection: com.piotv.keytab.sections.BackgroundSection
    private lateinit var topSection: TopSection
    private lateinit var gradientSection: GradientSection
    private lateinit var colorSection: ColorSection
    private lateinit var trailSection: com.piotv.keytab.sections.TrailSection
    private lateinit var previewSection: PreviewSection

    private val dip: Float by lazy { resources.displayMetrics.density }

    /** Mond/Sonne-Override auf den App-Modus anwenden (beide Activities). */
    private fun applyDarkModeOverride() {
        val mode = if (prefs.contains(ThemePrefs.KEY_DARK)) {
            if (prefs.getBoolean(ThemePrefs.KEY_DARK, false))
                AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        } else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onResume() {
        super.onResume()
        applyDarkModeOverride()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // In der Tastatur gewählter Modus (☾ Dark / ☀ Light) bestimmt auch das
        // App-Layout der Theme-Einstellungen — nicht nur das System-Theme.
        // Muss vor super.onCreate passieren, damit das Layout sofort den Modus trifft.
        val prePrefs = com.piotv.keytab.Prefs.of(this)
        val mode = if (prePrefs.contains(ThemePrefs.KEY_DARK)) {
            if (prePrefs.getBoolean(ThemePrefs.KEY_DARK, false))
                AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        } else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        prefs = com.piotv.keytab.Prefs.of(this)
        com.piotv.keytab.ime.SettingsConfig.importIfChanged(this)
        editingDark = ThemePrefs.isDarkMode(this)

        backgroundSection = com.piotv.keytab.sections.BackgroundSection(this, prefs) { updatePreview() }
        topSection = TopSection(this, prefs) { refreshAllUi() }
        gradientSection = GradientSection(this, prefs, { updatePreview() }) { target ->
            selectedTarget = target
            refreshTargetButtons()
            colorSection.updateControls(target)
        }
        colorSection = ColorSection(this, prefs, { selectedTarget }) {
            updatePreview(); refreshTargetButtons(); gradientSection.updateGradient()
        }
        trailSection = com.piotv.keytab.sections.TrailSection(this, prefs) { updatePreview() }
        previewSection = PreviewSection(this, prefs, { target -> currentColor(target) })

        val scroll = android.widget.ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.kbd_bg))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((PAGE_PADDING_DP * dip).toInt(), (SECTION_SPACING_DP * dip).toInt(),
                (PAGE_PADDING_DP * dip).toInt(), (CONTENT_BOTTOM_PADDING_DP * dip).toInt())
        }
        scroll.addView(col)
        col.addView(TextView(this).apply {
            text = getString(R.string.theme_settings_title)
            textSize = TITLE_TEXT_SP
            setTypeface(null, Typeface.BOLD)
        })

        topSection.build(col)
        gradientSection.build(col)
        sectionLabel(col, getString(R.string.theme_section_colors))
        colorSection.build(col)
        buildTargetButtons(col)
        sectionLabel(col, getString(R.string.theme_section_trail))
        trailSection.build(col)
        backgroundSection.build(col)
        sectionLabel(col, getString(R.string.theme_section_preview))
        previewSection.build(col)
        col.addView(actionRow(), rowParams())

        setContentView(scroll)
        updatePreview()
        refreshThemeIcons()
        refreshTargetButtons()
        colorSection.updateControls(selectedTarget)
    }

    private fun buildTargetButtons(col: LinearLayout) {
        val targetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        targetButtons = listOf(
            ThemePrefs.KIND_BG to "BG",
            ThemePrefs.KIND_KEY to "Key",
            ThemePrefs.KIND_TEXT to "Text",
            ThemePrefs.KIND_TRAIL to getString(R.string.theme_color_trail),
            ThemePrefs.KIND_GRADIENT1 to getString(R.string.settings_gradient_color1),
            ThemePrefs.KIND_GRADIENT2 to getString(R.string.settings_gradient_color2)
        ).map { (target, label) ->
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    selectedTarget = target
                    refreshTargetButtons()
                    colorSection.updateControls(target)
                }
            }
            target to btn
        }
        targetButtons.forEach { (_, btn) ->
            targetRow.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (TARGET_MARGIN_DP * dip).toInt()
                width = (100 * dip).toInt()
                height = (TARGET_HEIGHT_DP * dip).toInt()
            })
        }
        val targetScroll = android.widget.HorizontalScrollView(this).apply { addView(targetRow) }
        col.addView(targetScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (TARGET_TOP_MARGIN_DP * dip).toInt() })
    }

    private fun refreshAllUi() {
        editingDark = ThemePrefs.isDarkMode(this)
        refreshThemeIcons()
        refreshTargetButtons()
        colorSection.updateControls(selectedTarget)
        trailSection.updateButton()
        backgroundSection.refresh()
        gradientSection.updateGradient()
        updatePreview()
    }

    private fun refreshThemeIcons() {
        topSection.updateThemeIcons(editingDark)
    }

    private fun updatePreview() {
        previewSection.updatePreview()
    }

    private fun currentColor(target: String): Int {
        return ThemePrefs.getColor(prefs, editingDark, target, ThemePrefs.defaultColor(this, editingDark, target))
    }

    private fun refreshTargetButtons() {
        targetButtons.forEach { (target, btn) ->
            val color = currentColor(target)
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = CORNER_RADIUS_DP * dip
                setColor(color)
                if (target == selectedTarget) setStroke((3 * dip).toInt(), Color.MAGENTA)
            }
            val luminance = (LUMINANCE_RED * Color.red(color) + LUMINANCE_GREEN * Color.green(color) +
                LUMINANCE_BLUE * Color.blue(color)) / LUMINANCE_MAX
            btn.setTextColor(if (luminance > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE)
        }
    }

    private fun actionRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reset = Button(this).apply {
            text = getString(R.string.theme_reset)
            isAllCaps = false
            setOnClickListener {
                ThemePrefs.resetAll(prefs)
                ThemePrefs.bumpVersion(prefs)
                refreshAllUi()
            }
        }
        val export = Button(this).apply {
            text = getString(R.string.theme_export)
            isAllCaps = false
            setOnClickListener {
                val json = ThemePrefs.exportColors(prefs)
                val file = java.io.File(getExternalFilesDir(null), "theme-export.json")
                try { file.writeText(json) } catch (e: Exception) { e.printStackTrace() }
                val ctx = this@ThemeSettingsActivity
                android.widget.Toast.makeText(ctx,
                    String.format(getString(R.string.theme_export_toast), file.absolutePath),
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
        val import = Button(this).apply {
            text = getString(R.string.theme_import)
            isAllCaps = false
            setOnClickListener {
                val file = java.io.File(getExternalFilesDir(null), "theme-export.json")
                val ctx = this@ThemeSettingsActivity
                val ok = file.isFile && ThemePrefs.importColors(prefs, runCatching {
                    file.readText()
                }.getOrDefault(""))
                android.widget.Toast.makeText(ctx, getString(
                    if (ok) R.string.theme_import_ok else R.string.theme_import_fail),
                    android.widget.Toast.LENGTH_LONG).show()
                if (ok) refreshAllUi()
            }
        }
        listOf(reset, export, import).forEach { row.addView(it, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        return row
    }

    private fun sectionLabel(col: LinearLayout, text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = LABEL_TEXT_SP
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_primary))
            setPadding(0, (LABEL_TOP_PADDING_DP * dip).toInt(), 0, (LABEL_BOTTOM_PADDING_DP * dip).toInt())
        })
    }

    private fun rowParams(heightPx: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            heightPx ?: LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (2 * dip).toInt() }
}
