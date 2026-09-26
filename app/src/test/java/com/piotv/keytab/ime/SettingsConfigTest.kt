package com.piotv.keytab.ime

import android.content.Context
import com.piotv.keytab.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsConfigTest {
    private val prefs get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun changedFileOverridesPrefsButUnchangedFileDoesNotOverrideUi() {
        prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, true).apply()
        val text = "num_row = false\nlanguage = en"
        assertTrue(SettingsConfig.importTextIfChanged(text, prefs))
        assertFalse(prefs.getBoolean(Prefs.KEY_NUM_ROW, true))
        assertEquals("en", prefs.getString(Prefs.KEY_LANGUAGE, null))
        prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, true).apply()
        assertFalse(SettingsConfig.importTextIfChanged(text, prefs))
        assertTrue(prefs.getBoolean(Prefs.KEY_NUM_ROW, false))
        assertTrue(SettingsConfig.importTextIfChanged(text + "\n# external edit", prefs))
        assertFalse(prefs.getBoolean(Prefs.KEY_NUM_ROW, true))
    }

    @Test fun invalidValuesAndAbsentKeysPreservePreferences() {
        prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, true).putString(Prefs.KEY_LANGUAGE, "fr")
            .putString(Prefs.KEY_BG_IMAGE_FILL, "cover").apply()
        SettingsConfig.importTextIfChanged("num_row = yes\nlanguage = xx\nbg_image_fill = crop\n" +
            "theme_dark_bg = #bad\nkeyboard_height_ratio = 0.9", prefs)
        assertTrue(prefs.getBoolean(Prefs.KEY_NUM_ROW, false))
        assertEquals("fr", prefs.getString(Prefs.KEY_LANGUAGE, null))
        assertEquals("cover", prefs.getString(Prefs.KEY_BG_IMAGE_FILL, null))
        assertFalse(prefs.contains(ThemePrefs.colorKey(true, ThemePrefs.KIND_BG)))
        assertFalse(prefs.contains("keyboard_height_ratio"))
    }

    @Test fun colorsAndUriFragmentsSurviveComments() {
        SettingsConfig.importTextIfChanged("""
            # heading
            gradient_color1 = #80123456 # alpha
            gradient_color2 = #aabbcc
            theme_dark_key = #00112233
            bg_image_uri = content://media/image/1#fragment # comment
            bg_image_fill = stretch
            gaming_mode = false
            gaming_effect = false
        """.trimIndent(), prefs)
        assertEquals(0x80123456.toInt(), prefs.getInt(ThemePrefs.KEY_GRADIENT_COLOR1, 0))
        assertEquals(0xFFAABBCC.toInt(), prefs.getInt(ThemePrefs.KEY_GRADIENT_COLOR2, 0))
        assertEquals(0x00112233, prefs.getInt(ThemePrefs.colorKey(true, ThemePrefs.KIND_KEY), 0))
        assertEquals("content://media/image/1#fragment", prefs.getString(Prefs.KEY_BG_IMAGE_URI, null))
        assertEquals("stretch", prefs.getString(Prefs.KEY_BG_IMAGE_FILL, null))
        assertFalse(ThemePrefs.likelyHighlighting(prefs))
        assertFalse(ThemePrefs.likelyEffect(prefs))
    }

    @Test fun explicitDefaultsRemoveColorAndDarkOverrides() {
        prefs.edit().putBoolean(ThemePrefs.KEY_DARK, true)
            .putInt(ThemePrefs.KEY_GRADIENT_COLOR1, 123).apply()
        SettingsConfig.importTextIfChanged("dark_mode = system\ngradient_color1 = default", prefs)
        assertFalse(prefs.contains(ThemePrefs.KEY_DARK))
        assertFalse(prefs.contains(ThemePrefs.KEY_GRADIENT_COLOR1))
    }

    @Test fun completionPreservesEditsAddsAllSettingsAndDoesNotReimport() {
        val file = File.createTempFile("keytab", ".txt")
        try {
            val edited = "# keep me\nnum_row = true # custom\nunknown = value\nmax_scale = 1.6\n"
            file.writeText(edited)
            SettingsConfig.importIfChanged(file, prefs)
            prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, false).putString(Prefs.KEY_LANGUAGE, "it").apply()
            SettingsConfig.fillMissing(file, prefs)
            val completed = file.readText()
            assertTrue(completed.startsWith(edited))
            assertTrue(completed.contains("language = it"))
            assertTrue(completed.contains("theme_light_gaming = default"))
            assertTrue(completed.contains("bg_image_fill = fit"))
            assertFalse(completed.contains("keyboard_height_ratio"))
            assertFalse(SettingsConfig.importIfChanged(file, prefs))
            assertFalse(prefs.getBoolean(Prefs.KEY_NUM_ROW, true))
            SettingsConfig.fillMissing(file, prefs)
            assertEquals(completed, file.readText())
        } finally { file.delete() }
    }

    @Test fun fullExportImportsAllUiSettingsWithoutCreatingColorOverrides() {
        val completed = SettingsConfig.completeText("", prefs)
        // 45 Alt-Keys + 2 Verlauf-Modi (dark/light) + 4 Verlaufs-Farben (je Modus)
        assertEquals(45, KeyTabConfig.entries(completed).size)
        assertTrue(SettingsConfig.importTextIfChanged(completed, prefs))
        assertFalse(prefs.getBoolean(Prefs.KEY_NUM_ROW, true))
        for (key in listOf(Prefs.KEY_CLIP_TAB, Prefs.KEY_SNIPPET_TAB,
            Prefs.KEY_SUGGESTIONS, Prefs.KEY_AUTOCORRECT, Prefs.KEY_DYNAMIC_KEYS)) {
            assertTrue(key, prefs.getBoolean(key, false))
        }
        // Trail ist im Export als Default enthalten (aus) und wird beim Import
        // als expliziter Wert übernommen – deshalb prüfen wir die Werte, nicht
        // die Abwesenheit der Keys.
        assertFalse(prefs.getBoolean(ThemePrefs.KEY_TRAIL, true))
        assertFalse(prefs.getBoolean(ThemePrefs.KEY_TRAIL_TRACE, true))
        assertEquals(TrailLogic.DEFAULT_STEPS, prefs.getInt(ThemePrefs.KEY_TRAIL_STEPS, 0))
        assertEquals("de", prefs.getString(Prefs.KEY_LANGUAGE, null))
        assertFalse(prefs.contains(ThemePrefs.KEY_DARK))
        assertFalse(prefs.contains(ThemePrefs.KEY_GRADIENT_COLOR1))
        assertFalse(prefs.contains(ThemePrefs.colorKey(true, ThemePrefs.KIND_BG)))
    }

    @Test fun snapshotTracksUiButNotLearnedWords() {
        val before = SettingsConfig.snapshot(prefs)
        prefs.edit().putString(Prefs.KEY_USER_DICT, "learned words").apply()
        assertEquals(before, SettingsConfig.snapshot(prefs))
        prefs.edit().putBoolean(Prefs.KEY_DYNAMIC_KEYS, false).apply()
        assertNotEquals(before, SettingsConfig.snapshot(prefs))
    }
}
