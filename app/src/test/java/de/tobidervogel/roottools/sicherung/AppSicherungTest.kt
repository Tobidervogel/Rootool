package de.tobidervogel.roottools.sicherung

import de.tobidervogel.roottools.root.Werte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSicherungTest {
    @Test
    fun gruppiertNachPaketNeuesteZuerst() {
        val b = appBackups(
            Werte(
                """
                ROOT=0
                SICHERUNG=de.tobidervogel.liveup|2026-10-20_080000|5120|0.24.0
                SICHERUNG=de.tobidervogel.liveup|2026-10-25_142033|6144|0.24.1
                SICHERUNG=com.urbandroid.sleep|2026-10-25_090000|300|
                SICHERUNG=kaputt
                """.trimIndent(),
            ),
        )
        assertEquals(setOf("de.tobidervogel.liveup", "com.urbandroid.sleep"), b.keys)
        assertEquals("2026-10-25_142033", b.getValue("de.tobidervogel.liveup").first().ordner)
        assertEquals("0.24.1", b.getValue("de.tobidervogel.liveup").first().version)
        assertNull(b.getValue("com.urbandroid.sleep").first().version)
    }

    @Test
    fun datumLesbar() {
        assertEquals("25.10.2026, 14:20", backupDatum("2026-10-25_142033"))
        assertEquals("anders", backupDatum("anders"))
    }
}
