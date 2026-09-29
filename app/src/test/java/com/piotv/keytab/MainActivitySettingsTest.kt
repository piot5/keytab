package com.piotv.keytab

import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySettingsTest {

    @Test
    fun `Autokorrektur-Schalter zeigt und speichert den gewaehlten Zustand`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs.of(app)
        prefs.edit().putBoolean(Prefs.KEY_AUTOCORRECT, false).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val toggle = activity.findViewById<MaterialSwitch>(R.id.sw_autocorrect)
        assertFalse("gespeicherter Aus-Zustand muss im Schalter erscheinen", toggle.isChecked)

        toggle.performClick()
        assertTrue("Umschalten muss den Aus-Zustand aktivieren", toggle.isChecked)
        assertTrue("aktivierter Zustand muss persistent sein",
            prefs.getBoolean(Prefs.KEY_AUTOCORRECT, false))

        controller.pause().stop().destroy()
    }

    @Test
    fun `Schaltplan-Vorschau ist schaltbar wenn eine Score-Quelle aktiv ist`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs.of(app)
        // Dynamische Tastengröße an (Default) → Score-Quelle vorhanden.
        prefs.edit().putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, true)
            .putBoolean(Prefs.KEY_SWIPE_PREVIEW, false).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val toggle = activity.findViewById<MaterialSwitch>(R.id.sw_swipe_preview)
        assertTrue("Schalter muss bedienbar sein", toggle.isEnabled)
        assertFalse(toggle.isChecked)

        toggle.performClick()
        assertTrue("Vorschau muss sich einschalten lassen", toggle.isChecked)
        assertTrue("Zustand muss persistent sein",
            prefs.getBoolean(Prefs.KEY_SWIPE_PREVIEW, false))

        controller.pause().stop().destroy()
    }

    @Test
    fun `Vorschau-Schalter ist gesperrt und speichert nichts wenn beide Quellen aus sind`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs.of(app)
        prefs.edit().putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false)
            .putBoolean(Prefs.KEY_SWIPE_PREVIEW, false).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val toggle = activity.findViewById<MaterialSwitch>(R.id.sw_swipe_preview)
        assertFalse("ohne Score-Quelle darf der Schalter nicht aktiv sein", toggle.isEnabled)

        // Selbst ein erzwungenes Klicken darf keinen "an"-Zustand speichern.
        toggle.isChecked = true
        assertFalse("der Schalter muss zurueckspringen", toggle.isChecked)
        assertFalse("es darf kein aktiver, wirkungsloser Zustand entstehen",
            prefs.getBoolean(Prefs.KEY_SWIPE_PREVIEW, false))

        controller.pause().stop().destroy()
    }

    @Test
    fun `Einschalten von Swipe entsperrt den Vorschau-Schalter im laufenden Screen`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs.of(app)
        prefs.edit().putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false)
            .putBoolean(Prefs.KEY_SWIPE_PREVIEW, false).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val swipe = activity.findViewById<MaterialSwitch>(R.id.sw_swipe)
        val preview = activity.findViewById<MaterialSwitch>(R.id.sw_swipe_preview)
        assertFalse(preview.isEnabled)

        swipe.performClick()
        assertTrue("nach dem Einschalten von Swipe muss die Vorschau bedienbar sein",
            preview.isEnabled)

        controller.pause().stop().destroy()
    }

    @Test
    fun `gespeicherter Vorschau-Wunsch bleibt erhalten wenn Swipe wieder aus geht`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Prefs.of(app)
        prefs.edit().putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, true).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<MaterialSwitch>(R.id.sw_swipe_preview).performClick()
        assertTrue(prefs.getBoolean(Prefs.KEY_SWIPE_PREVIEW, false))

        // Zweite Quelle aus: der Schalter sperrt, der Wunsch wird aber nicht verworfen.
        activity.findViewById<MaterialSwitch>(R.id.sw_dynamic_keys).performClick()
        assertFalse(activity.findViewById<MaterialSwitch>(R.id.sw_swipe_preview).isEnabled)
        assertTrue("der Wunsch muss den Aus-Schalter ueberleben",
            prefs.getBoolean(Prefs.KEY_SWIPE_PREVIEW, false))

        controller.pause().stop().destroy()
    }
}
