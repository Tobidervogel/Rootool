package de.tobidervogel.roottools.update

import de.tobidervogel.roottools.root.Werte
import java.util.Locale

/** Eine Notfall-Sicherung unter /sdcard/RootTools/Notfall. */
data class Sicherung(val name: String, val kb: Long, val komplett: Boolean)

/** Was als Naechstes zu tun ist, damit Root das Update uebersteht. */
enum class UpdateSchritt { KeinUpdate, MagiskPatchen, Neustarten }

/** Was update.sh meldet. Ohne Android, getestet in SystemStandTest. */
class SystemStand(private val w: Werte) {
    val fehler get() = w.fehler
    val geraet get() = w["GERAET"]
    val version get() = w["VERSION"]
    val android get() = w["ANDROID"]
    val patch get() = w["PATCH"]
    val magisk get() = w["MAGISK"]
    val akku get() = w["AKKU"]?.toIntOrNull()
    val protokoll get() = w.liste("UELOG")

    /** "_a" oder "_b"; null, wenn das Geraet keine A/B-Slots hat. */
    val slot get() = w["SLOT"]?.takeIf { it == "_a" || it == "_b" }
    val inaktiv get() = when (slot) {
        "_a" -> "_b"
        "_b" -> "_a"
        else -> null
    }

    /** magisk, original, leer, fremd, fehlt oder unbekannt; [teil] z. B. "init_boot_a". */
    fun abbild(teil: String): String = w["IMG_" + teil.uppercase()] ?: "unbekannt"

    /** In welchem Abbild Magisk auf diesem Geraet sitzt (boot oder init_boot), abgelesen am laufenden Slot. */
    val magiskAbbild: String? get() {
        val s = slot ?: return null
        return listOf("init_boot", "boot").firstOrNull { abbild(it + s) == "magisk" }
    }

    /** Ist der Slot, in den das Update gegangen ist, schon mit Magisk gepatcht? */
    val inaktivGepatcht: Boolean get() {
        val s = inaktiv ?: return false
        val bild = magiskAbbild
        return if (bild != null) abbild(bild + s) == "magisk" else listOf("init_boot", "boot").any { abbild(it + s) == "magisk" }
    }

    /**
     * Wartet ein installiertes Update auf den Neustart? update_engine legt dafuer eine Marke ab; die OnePlus-App
     * schaltet den Slot aber erst bei "Jetzt neu starten" um, dann verraet es nur das Protokoll.
     */
    val updateWartet: Boolean get() = w["UPDATE"] == "wartet" || protokoll.any { z ->
        WARTET.any { z.contains(it) }
    }

    val schritt: UpdateSchritt get() = when {
        !updateWartet -> UpdateSchritt.KeinUpdate
        inaktivGepatcht -> UpdateSchritt.Neustarten
        else -> UpdateSchritt.MagiskPatchen
    }

    val teileKb get() = w["TEILE_KB"]?.toLongOrNull() ?: 0
    val freiKb get() = w["FREI_KB"]?.toLongOrNull() ?: 0
    val otaApp get() = w.ja("OTA_APP")

    val sicherungen: List<Sicherung> get() = w.liste("SICHERUNG").mapNotNull { z ->
        val t = z.trim().split('|')
        if (t.size != 3) null else Sicherung(t[0], t[1].toLongOrNull() ?: 0, t[2] == "1")
    }

    companion object {
        private val WARTET = listOf("kUpdatedButNotActive", "UPDATED_NEED_REBOOT", "Update successfully applied")
    }
}

/** "2026-10-25_1420" wird "25.10.2026, 14:20". */
fun sicherungsDatum(name: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})""").matchEntire(name) ?: return name
    val (j, mo, t, h, mi) = m.destructured
    return "$t.$mo.$j, $h:$mi"
}

fun groesseText(kb: Long): String = when {
    kb >= 1024L * 1024 -> String.format(Locale.GERMANY, "%.1f GB", kb / 1024.0 / 1024.0)
    kb >= 1024 -> "${kb / 1024} MB"
    else -> "$kb KB"
}
