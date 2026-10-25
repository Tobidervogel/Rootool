package de.tobidervogel.roottools.update

import de.tobidervogel.roottools.root.Werte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemStandTest {
    private fun stand(vararg zeilen: String) = SystemStand(Werte(("ROOT=0\n" + zeilen.joinToString("\n"))))

    @Test
    fun ohneUpdateIstAllesRuhig() {
        val s = stand("SLOT=_b", "IMG_BOOT_B=magisk", "IMG_BOOT_A=original")
        assertEquals(UpdateSchritt.KeinUpdate, s.schritt)
        assertEquals("_a", s.inaktiv)
        assertEquals("boot", s.magiskAbbild)
    }

    @Test
    fun updateMitOriginalAbbildHeisstMagiskPatchen() {
        val s = stand("SLOT=_b", "UPDATE=wartet", "IMG_BOOT_B=magisk", "IMG_BOOT_A=original", "IMG_INIT_BOOT_A=leer")
        assertEquals(UpdateSchritt.MagiskPatchen, s.schritt)
    }

    @Test
    fun updateMitGepatchtemSlotIstBereit() {
        val s = stand("SLOT=_b", "UPDATE=wartet", "IMG_BOOT_B=magisk", "IMG_BOOT_A=magisk")
        assertEquals(UpdateSchritt.Neustarten, s.schritt)
    }

    @Test
    fun schautImRichtigenAbbildNach() {
        // Magisk sitzt in init_boot: ein (altes) gepatchtes boot im anderen Slot zaehlt nicht.
        val s = stand(
            "SLOT=_a", "UPDATE=wartet",
            "IMG_INIT_BOOT_A=magisk", "IMG_BOOT_A=leer",
            "IMG_INIT_BOOT_B=original", "IMG_BOOT_B=magisk",
        )
        assertEquals("init_boot", s.magiskAbbild)
        assertFalse(s.inaktivGepatcht)
        assertEquals(UpdateSchritt.MagiskPatchen, s.schritt)
    }

    @Test
    fun oneplusMeldungImProtokollZaehltAlsWartend() {
        val s = stand("SLOT=_b", "UELOG=I update_engine: ... ErrorCode::kUpdatedButNotActive (52)")
        assertTrue(s.updateWartet)
        val nichts = stand("SLOT=_b", "UELOG=I update_engine: Update attempt flags in use = 0x0")
        assertFalse(nichts.updateWartet)
    }

    @Test
    fun liestSicherungenUndGroessen() {
        val s = stand(
            "TEILE_KB=812345", "FREI_KB=52428800",
            "SICHERUNG=2026-10-25_1420|812400|1",
            "SICHERUNG=2026-10-24_0900|1200|0",
            "SICHERUNG=kaputt",
        )
        assertEquals(2, s.sicherungen.size)
        assertTrue(s.sicherungen[0].komplett)
        assertFalse(s.sicherungen[1].komplett)
        assertEquals("25.10.2026, 14:20", sicherungsDatum(s.sicherungen[0].name))
        assertEquals("793 MB", groesseText(s.teileKb))
        assertEquals("50,0 GB", groesseText(s.freiKb))
    }

    @Test
    fun ohneSlotsKeinInaktiverSlot() {
        val s = stand("SLOT=", "UPDATE=wartet")
        assertNull(s.slot)
        assertNull(s.inaktiv)
        assertEquals(UpdateSchritt.MagiskPatchen, s.schritt)
    }

    @Test
    fun werteFiltertFremdeZeilen() {
        val w = Werte("ROOT=0\nWifi is enabled\nlowercase=nein\nLOG=a=b\nLOG=c\n")
        assertTrue(w.root)
        assertNull(w["lowercase"])
        assertEquals(listOf("a=b", "c"), w.liste("LOG"))
        assertEquals("Fehler", Werte("").mitFehler("Fehler").fehler)
    }
}
