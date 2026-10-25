package de.tobidervogel.roottools.sicherung

import android.content.Context
import android.net.Uri
import de.tobidervogel.roottools.root.Skript
import de.tobidervogel.roottools.root.Werte
import java.io.File

/** Eine App-Sicherung unter /sdcard/RootTools/Apps/<paket>/<ordner>. */
data class AppBackup(val paket: String, val ordner: String, val kb: Long, val version: String?)

/** Liest SICHERUNG=paket|ordner|kb|version-Zeilen, nach Paket gruppiert, neueste zuerst. Getestet in AppSicherungTest. */
fun appBackups(w: Werte): Map<String, List<AppBackup>> = w.liste("SICHERUNG").mapNotNull { z ->
    val t = z.trim().split('|')
    if (t.size < 3) null else AppBackup(t[0], t[1], t[2].toLongOrNull() ?: 0, t.getOrNull(3)?.takeIf { it.isNotBlank() })
}.groupBy { it.paket }.mapValues { (_, l) -> l.sortedByDescending { it.ordner } }

/** "2026-10-25_142033" wird "25.10.2026, 14:20". */
fun backupDatum(ordner: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})\d{0,2}""").matchEntire(ordner) ?: return ordner
    val (j, mo, t, h, mi) = m.destructured
    return "$t.$mo.$j, $h:$mi"
}

/** Root-Seite der App-Sicherung: die Arbeit macht assets/apps.sh. Alles blockiert. */
object AppSicherung {
    fun liste(ctx: Context) = Skript.aufrufen(ctx, "apps.sh", "sicherungen")

    fun sichern(ctx: Context, paket: String, schritt: (String) -> Unit) =
        Skript.aufrufen(ctx, "apps.sh", "sichern", paket, zeitlimitSek = 1800, zeile = schrittVon(schritt))

    fun zurueck(ctx: Context, paket: String, ordner: String, mitApp: Boolean, schritt: (String) -> Unit) =
        Skript.aufrufen(
            ctx, "apps.sh", "zurueck", paket, ordner, if (mitApp) "alles" else "daten",
            zeitlimitSek = 1800, zeile = schrittVon(schritt),
        )

    fun loeschen(ctx: Context, paket: String, ordner: String) = Skript.aufrufen(ctx, "apps.sh", "loeschen", paket, ordner)

    /**
     * Neue APK (z. B. anders signiert gebaut) einspielen und die Daten behalten. Kopiert sie erst in den eigenen
     * Cache, weil root dort lesen kann, und prueft, dass sie wirklich zu [paket] gehoert.
     */
    fun tauschen(ctx: Context, paket: String, apk: Uri, schritt: (String) -> Unit): Werte {
        val datei = File(ctx.cacheDir, "neu.apk")
        ctx.contentResolver.openInputStream(apk)?.use { ein -> datei.outputStream().use { ein.copyTo(it) } }
            ?: return Werte("").mitFehler("APK nicht lesbar")
        val name = ctx.packageManager.getPackageArchiveInfo(datei.path, 0)?.packageName
        if (name != paket) {
            datei.delete()
            return Werte("").mitFehler(if (name == null) "Das ist keine gültige APK." else "Diese APK ist $name, nicht $paket.")
        }
        return Skript.aufrufen(ctx, "apps.sh", "tauschen", paket, datei.path, zeitlimitSek = 1800, zeile = schrittVon(schritt))
            .also { datei.delete() }
    }

    private fun schrittVon(schritt: (String) -> Unit): (String) -> Unit = { z ->
        if (z.startsWith("SCHRITT=")) schritt(z.removePrefix("SCHRITT="))
    }
}
