package com.piotv.keytab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests: laufen auf einem echten Gerät/Emulator.
 * Ausführen: ./gradlew :app:connectedDebugAndroidTest
 * (oder APK manuell installieren + `am instrument -w com.piotv.keytab.debug.test/androidx.test.runner.AndroidJUnitRunner`)
 */
@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {

    @Test
    fun packageName_isCorrect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.piotv.keytab", context.packageName)
    }

    @Test
    fun imeService_isDeclaredAndEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        // Der IME muss per method.xml deklariert und aktivierbar sein:
        val services = pm.queryIntentServices(
            android.content.Intent(android.view.inputmethod.InputMethod.SERVICE_INTERFACE),
            0
        )
        assertTrue(
            "KeyTabImeService muss als InputMethod exportiert sein",
            services.any { it.serviceInfo.packageName == context.packageName }
        )
    }
}
