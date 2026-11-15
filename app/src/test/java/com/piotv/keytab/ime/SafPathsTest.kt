package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Mapping-Logik des SAF-Baums ([SafPaths]) — bewusst ohne Robolectric,
 * weil nur Strings verarbeitet werden (Baum-/Dokument-IDs, echte Pfade).
 */
class SafPathsTest {

    @Test
    fun `Primarvolume ordnet Dokument-IDs echte Pfade zu`() {
        assertEquals(
            "/storage/emulated/0/Download/a.txt",
            SafPaths.realPath("primary:Download", "primary:Download/a.txt")
        )
        // Baumwurzel selbst
        assertEquals(
            "/storage/emulated/0/Download",
            SafPaths.realPath("primary:Download", "primary:Download")
        )
        // Volume-Wurzel als Baum (treeId ohne Pfad)
        assertEquals("/storage/emulated/0", SafPaths.realPath("primary", "primary"))
        assertEquals(
            "/storage/emulated/0/Download",
            SafPaths.realPath("primary", "primary:Download")
        )
    }

    @Test
    fun `Fremde Volumes und Dokumente ausserhalb des Baums liefern keinen Pfad`() {
        assertNull(SafPaths.realPath("primary:Download", "9016-4EF8:Download/a.txt"))
        assertNull(SafPaths.realPath("home:Documents", "home:Documents/x.txt"))
        // Dokument liegt nicht unterhalb der Baumwurzel
        assertNull(SafPaths.realPath("primary:Download", "primary:DCIM/x.jpg"))
    }

    @Test
    fun `Containment-Logik schuetzt vor Selbstkopien`() {
        assertTrue(SafPaths.isSameOrInside("primary:D", "primary:D/a.txt"))
        assertTrue(SafPaths.isSameOrInside("primary:D", "primary:D"))
        assertTrue(SafPaths.isSameOrInside("primary:D", "primary:D/sub/x.txt"))
        assertFalse(SafPaths.isSameOrInside("primary:D", "primary:DCIM/a.jpg"))
        // Präfix-Verwechslung: "Download" vs "Downloads"
        assertFalse(SafPaths.isSameOrInside("primary:Download", "primary:Downloads/x"))
    }

    @Test
    fun `Parent-Logik endet an der Baumwurzel`() {
        assertNull(SafPaths.parentDocId("primary:Download"))
        assertEquals("primary:Download", SafPaths.parentDocId("primary:Download/a.txt"))
        assertEquals("primary:Download/a", SafPaths.parentDocId("primary:Download/a/b"))
        assertEquals(
            "primary:Download/a.txt",
            SafPaths.childDocId("primary:Download", "a.txt")
        )
    }

    @Test
    fun `pseudoPath faellt auf die Dokument-ID zurueck`() {
        assertEquals(
            "/storage/emulated/0/Download",
            SafPaths.pseudoPath("primary:Download", "primary:Download")
        )
        assertEquals(
            "9016-4EF8:Download",
            SafPaths.pseudoPath("9016-4EF8:Download", "9016-4EF8:Download")
        )
    }

    @Test
    fun `relativePath kennt Wurzel und Tiefe`() {
        assertEquals("", SafPaths.relativePath("primary:Download", "primary:Download"))
        assertEquals("a.txt", SafPaths.relativePath("primary:Download", "primary:Download/a.txt"))
        assertNull(SafPaths.relativePath("primary:Download", "primary:Other/x"))
    }
}
