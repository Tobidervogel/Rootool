package de.tobidervogel.roottools.entwickler

import android.content.Context
import de.tobidervogel.roottools.root.Skript
import de.tobidervogel.roottools.root.Werte

/** Was adb.sh meldet. Getestet in EntwicklerTest. */
class AdbStand(val w: Werte) {
    val fehler get() = w.fehler
    val root get() = w.root
    val an get() = w["PORT"] == "5555" && w.ja("LAUSCHT")
    val portGesetzt get() = w["PORT"] == "5555"
    val usbDebugging get() = w.ja("USB_DEBUG")
    val schutz get() = w.ja("SCHUTZ")
    val beimStart get() = w.ja("BOOT")
    val heim get() = w["HEIM"]
    val tailscale get() = w["TAILSCALE"]

    /** WLAN-Adresse mit Netzgroesse, z. B. "192.168.0.115/24". */
    val wlan get() = w["WLAN"]
}

/** "192.168.0.115/24" wird "192.168.0.0/24"; null, wenn es keine IPv4-Adresse mit Netzgroesse ist. */
fun netzVon(adresse: String?): String? {
    val m = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})""").matchEntire(adresse?.trim() ?: return null)
        ?: return null
    val teile = m.groupValues.drop(1).map { it.toInt() }
    val bits = teile[4]
    if (teile.take(4).any { it > 255 } || bits > 32) return null
    val ip = teile.take(4).fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
    val maske = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
    val netz = ip and maske
    return listOf(24, 16, 8, 0).joinToString(".") { ((netz shr it) and 0xFF).toString() } + "/$bits"
}

/** Eine Logcat-Zeile; [stufe] ist V, D, I, W, E, F oder ' ' (unbekannt). */
data class LogZeile(val stufe: Char, val text: String)

/** threadtime-Format: "10-25 18:40:01.123  1234  1250 E Tag: Nachricht" */
fun logZeilen(w: Werte): List<LogZeile> = w.liste("Z").map { z ->
    val m = Regex("""^\S+\s+\S+\s+\d+\s+\d+\s+([VDIWEF])\s""").find(z)
    LogZeile(m?.groupValues?.get(1)?.single() ?: ' ', z)
}

object Entwickler {
    fun adbZustand(ctx: Context) = AdbStand(Skript.aufrufen(ctx, "adb.sh", "zustand"))

    /** [heim] = "192.168.0.0/24" oder "keins". */
    fun adbAn(ctx: Context, heim: String) = AdbStand(Skript.aufrufen(ctx, "adb.sh", "an", heim))

    fun adbAus(ctx: Context) = AdbStand(Skript.aufrufen(ctx, "adb.sh", "aus"))

    /** [uid] null = alle Apps. */
    fun logcat(ctx: Context, uid: Int?, nurAbstuerze: Boolean, anzahl: Int): Werte = Skript.aufrufen(
        ctx, "logcat.sh", "lesen", uid?.toString() ?: "alle", if (nurAbstuerze) "abstuerze" else "normal", anzahl.toString(),
        zeitlimitSek = 60,
    )
}
