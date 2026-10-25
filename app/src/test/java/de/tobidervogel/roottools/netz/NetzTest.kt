package de.tobidervogel.roottools.netz

import de.tobidervogel.roottools.root.Werte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetzTest {
    @Test
    fun regelnHinUndZurueck() {
        val r = listOf(Regel("com.instagram.android", true), Regel("com.google.android.youtube", false, "22:00", "07:00"))
        for (regel in r) assertEquals(regel, Regel.lesen(regel.zeile()))
        assertNull(Regel.lesen("x|zeit|25:00|07:00"))
        assertNull(Regel.lesen("x|unsinn"))
        assertEquals("Gesperrt 22:00–07:00", r[1].text())
    }

    @Test
    fun uhrzeiten() {
        assertTrue(Regel.zeitOk("00:00"))
        assertTrue(Regel.zeitOk("23:59"))
        assertFalse(Regel.zeitOk("24:00"))
        assertFalse(Regel.zeitOk("7:00"))
        assertFalse(Regel.zeitOk("07:60"))
    }

    @Test
    fun firewallStand() {
        val s = FirewallStand(
            Werte("ROOT=0\nKETTE=1\nREGEL=com.a|immer\nREGEL=com.b|zeit|08:00|12:00\nGESPERRT=com.a\nWAECHTER=1\n"),
        )
        assertEquals(setOf("com.a", "com.b"), s.regeln.keys)
        assertEquals(setOf("com.a"), s.gesperrt)
        assertTrue(s.kette)
        assertTrue(s.waechter)
    }

    @Test
    fun adressenAusEingaben() {
        assertEquals("instagram.com", Werbeblocker.domain("Instagram.com"))
        assertEquals("www.instagram.com", Werbeblocker.domain("https://www.instagram.com/reels/abc"))
        assertEquals("example.com", Werbeblocker.domain("example.com:443"))
        assertNull(Werbeblocker.domain("instagram"))
        assertNull(Werbeblocker.domain("bad domain.com"))
        assertNull(Werbeblocker.domain("a..b"))
        assertNull(Werbeblocker.domain("-a.com"))
        assertEquals(listOf("tiktok.com", "www.tiktok.com"), Werbeblocker.mitWww("tiktok.com"))
        assertEquals(listOf("i.instagram.com"), Werbeblocker.mitWww("i.instagram.com"))
    }

    @Test
    fun hostsStand() {
        val s = HostsStand(
            Werte(
                "ROOT=0\nMODUL=1\nUNSER=1\nANZAHL=84312\nLISTE=Werbung und Schadsoftware\nAKTIV=1\n" +
                    "EIGEN=SPERRE instagram.com\nEIGEN=AUSNAHME ads.example.com\nEIGEN=kaputt\n",
            ),
        )
        assertTrue(s.aktiv && s.unser && s.modul)
        assertEquals(84312, s.anzahl)
        assertEquals(listOf(Eigen(true, "instagram.com"), Eigen(false, "ads.example.com")), s.eigen)
    }
}
