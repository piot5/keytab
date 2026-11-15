package com.piotv.keytab.ime

import android.net.Uri
import com.piotv.keytab.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SAF-Ordnerzugriff ([SafAccess]): Übernahme, Persistenz und Entzug der
 * Baum-URI. Robolectric verwaltet keine echten Dokument-Provider — geprüft wird
 * der Zustands- und Persistenz-Teil (Prefs, Testhaken, Revoke), nicht das
 * Listing; das Listing fällt überall dort defensiv auf `null`/`false` zurück.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafAccessTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @After
    fun reset() {
        SafAccess.resetForTests()
        Prefs.of(app).edit().remove(SafAccess.KEY_TREE).commit()
    }

    @Test
    fun `Ohne Freigabe ist kein Baum aktiv`() {
        assertNull(SafAccess.treeUri(app))
        assertFalse(SafAccess.granted(app))
    }

    @Test
    fun `Grant persistiert die Baum-URI in den Prefs`() {
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        )
        SafAccess.grant(app, uri)
        assertEquals(uri.toString(), Prefs.of(app).getString(SafAccess.KEY_TREE, null))
    }

    @Test
    fun `Revoke raeumt Pref und persistierte Erlaubnis auf`() {
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        )
        SafAccess.grant(app, uri)
        SafAccess.revoke(app)
        assertNull(Prefs.of(app).getString(SafAccess.KEY_TREE, null))
        assertNull(SafAccess.treeUri(app))
    }

    @Test
    fun `Testhaken schaltet auf eine Stellvertreter-URI`() {
        SafAccess.override = true
        assertEquals(SafAccess.TEST_URI, SafAccess.treeUri(app)?.toString())
        assertTrue(SafAccess.granted(app))
        // override = false erzwingt den leeren Zustand (negativer Test)
        SafAccess.override = false
        assertNull(SafAccess.treeUri(app))
        assertFalse(SafAccess.granted(app))
    }
}
