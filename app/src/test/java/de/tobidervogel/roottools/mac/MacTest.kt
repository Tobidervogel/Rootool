package de.tobidervogel.roottools.mac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MacTest {
    @Test
    fun normalisiertAlleSchreibweisen() {
        val soll = "aa:bb:cc:dd:ee:f0"
        for (e in listOf("AA:BB:CC:DD:EE:F0", "aa-bb-cc-dd-ee-f0", "aabb.ccdd.eef0", "AABBCCDDEEF0", " aa bb cc dd ee f0 ")) {
            assertEquals(e, soll, Mac.normalisieren(e))
        }
    }

    @Test
    fun lehntUnbrauchbareAb() {
        assertNotNull(Mac.fehler(""))
        assertNotNull(Mac.fehler("AA:BB:CC"))
        assertNotNull(Mac.fehler("AA:BB:CC:DD:EE:F0:11"))
        assertNotNull(Mac.fehler("GG:BB:CC:DD:EE:F0"))
        assertNotNull(Mac.fehler("00:00:00:00:00:00"))
        // Gruppenadressen (Bit 0 im ersten Byte), auch Broadcast
        assertNotNull(Mac.fehler("01:00:5E:00:00:01"))
        assertNotNull(Mac.fehler("FF:FF:FF:FF:FF:FF"))
        assertNull(Mac.normalisieren("13:00:00:00:00:00"))
        assertNull(Mac.fehler("02:00:00:00:00:00"))
        assertNull(Mac.fehler("98:76:54:32:10:FE"))
    }

    @Test
    fun erkenntLokaleAdressen() {
        assertTrue(Mac.istLokal("02:00:00:00:00:00"))
        assertTrue(Mac.istLokal("DA:A1:19:00:00:00"))
        assertFalse(Mac.istLokal("98:76:54:32:10:FE"))
        assertFalse(Mac.istLokal(""))
    }

    @Test
    fun gewuerfelteSindLokalUndUnicast() {
        val r = Random(42)
        repeat(1000) {
            val m = Mac.zufaellig(r)
            assertNull(m, Mac.fehler(m))
            assertTrue(m, Mac.istLokal(m))
            assertEquals(m, Mac.normalisieren(m))
        }
    }

    @Test
    fun formatiertBeimTippen() {
        assertEquals("", Mac.beimTippen(""))
        assertEquals("A", Mac.beimTippen("a"))
        assertEquals("AB", Mac.beimTippen("ab"))
        assertEquals("AB:C", Mac.beimTippen("abc"))
        // Loescht man den Doppelpunkt hinter "AB", bleibt "AB" (kein Zurueckspringen)
        assertEquals("AB", Mac.beimTippen("AB"))
        assertEquals("AB:CD:EF:01:23:45", Mac.beimTippen("ab-cd-ef-01-23-45-67"))
        assertEquals("AB:CD", Mac.beimTippen("a?b x c d"))
    }

    @Test
    fun liestZustandVonMacSh() {
        val z = MacZustand.lesen(
            """
            ROOT=0
            AKTUELL=12:34:56:78:9a:bc
            ORIGINAL=98:76:54:32:10:fe
            SCHALTER=1
            ZUFALL=false
            WLAN=Wifi is enabled
            WLAN=Wifi is connected to "Heim=Netz"
            ZIEL=12:34:56:78:9a:bc
            WAECHTER=1
            BOOT=1
            LOG=25.10. 13:20:01 Waechter gestartet
            LOG=25.10. 13:20:02 3a:11:22:33:44:55 -> 12:34:56:78:9a:bc
            """.trimIndent(),
        )
        assertTrue(z.root)
        assertEquals("12:34:56:78:9a:bc", z.aktuell)
        assertEquals("98:76:54:32:10:fe", z.original)
        assertTrue(z.schalter)
        assertEquals(false, z.androidWuerfelt)
        assertEquals(true, z.wlanAn)
        assertEquals("Heim=Netz", z.verbundenMit)
        assertTrue(z.eigeneAktiv)
        assertTrue(z.waechter)
        assertTrue(z.beimStart)
        assertEquals(2, z.protokoll.size)
        assertNull(z.fehler)
    }

    @Test
    fun zustandOhneRootUndOhneWlan() {
        val leer = MacZustand.lesen("Permission denied\n")
        assertFalse(leer.root)
        assertNull(leer.aktuell)
        assertNull(leer.androidWuerfelt)

        val aus = MacZustand.lesen("ROOT=0\nWLAN=Wifi is disabled\nZIEL=02:00:00:00:00:01\n")
        assertEquals(false, aus.wlanAn)
        assertNull(aus.verbundenMit)
        assertFalse(aus.eigeneAktiv)
        assertFalse(aus.waechter)

        val nichtVerbunden = MacZustand.lesen("ROOT=0\nWLAN=Wifi is enabled\nWLAN=Wifi is not connected\n")
        assertEquals(true, nichtVerbunden.wlanAn)
        assertNull(nichtVerbunden.verbundenMit)
    }
}
