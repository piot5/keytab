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
}
