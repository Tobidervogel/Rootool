package de.tobidervogel.roottools.mac

import android.content.Context
import de.tobidervogel.roottools.root.Root

/** Was mac.sh ueber "zustand" meldet. Felder, die auf dem Geraet nicht auslesbar sind, bleiben null. */
data class MacZustand(
    val root: Boolean,
    /** Adresse von wlan0 gerade jetzt; null, wenn es die Schnittstelle nicht gibt. */
    val aktuell: String? = null,
    /** Geraete-MAC aus "ip link" (permaddr), nur sichtbar, solange eine andere gilt. */
    val original: String? = null,
    /** Android kennt "cmd wifi force-overlay-config-value" (ab Android 16 wirksam). */
    val schalter: Boolean = false,
    /** Ob Android bei Verbindungen wuerfelt; null = (noch) nicht bekannt. */
    val androidWuerfelt: Boolean? = null,
    val wlanAn: Boolean? = null,
    /** SSID ohne Anfuehrungszeichen, null = nicht verbunden. */
    val verbundenMit: String? = null,
    /** Eigene MAC aus mac.conf; null = Android verwaltet die MAC. */
    val ziel: String? = null,
    val waechter: Boolean = false,
    val beimStart: Boolean = false,
    val protokoll: List<String> = emptyList(),
    val fehler: String? = null,
) {
    val eigeneAktiv get() = ziel != null && ziel == aktuell

    companion object {
        fun lesen(ausgabe: String): MacZustand {
            val werte = mutableMapOf<String, String>()
            val wlan = mutableListOf<String>()
            val log = mutableListOf<String>()
            for (zeile in ausgabe.lineSequence()) {
                val i = zeile.indexOf('=')
                if (i <= 0) continue
                val k = zeile.substring(0, i)
                val v = zeile.substring(i + 1)
                when (k) {
                    "WLAN" -> wlan += v
                    "LOG" -> log += v
                    else -> werte[k] = v.trim()
                }
            }
            val verbunden = wlan.firstOrNull { it.startsWith("Wifi is connected to ") }
                ?.removePrefix("Wifi is connected to ")?.trim()?.removeSurrounding("\"")
            return MacZustand(
                root = werte["ROOT"] == "0",
                aktuell = werte["AKTUELL"]?.lowercase()?.takeIf { it.isNotEmpty() },
                original = werte["ORIGINAL"]?.lowercase()?.takeIf { it.isNotEmpty() },
                schalter = werte["SCHALTER"] == "1",
                androidWuerfelt = werte["ZUFALL"]?.toBooleanStrictOrNull(),
                wlanAn = when {
                    "Wifi is enabled" in wlan -> true
                    "Wifi is disabled" in wlan -> false
                    else -> null
                },
                verbundenMit = verbunden,
                ziel = werte["ZIEL"]?.takeIf { it.isNotEmpty() },
                waechter = werte["WAECHTER"] == "1",
                beimStart = werte["BOOT"] == "1",
                protokoll = log,
                fehler = werte["FEHLER"],
            )
        }
    }
}

/**
 * Root-Seite des Werkzeugs. Die eigentliche Arbeit macht assets/mac.sh: die App kopiert es bei jedem Aufruf frisch
 * nach [DIR] (so laeuft immer die Fassung dieser App-Version) und ruft es in demselben su auf. Alles blockiert.
 */
object MacSteuerung {
    private const val DIR = "/data/adb/roottools"
    private const val ENDE = "ROOTTOOLS_ENDE"

    fun zustand(ctx: Context) = aufruf(ctx, "zustand")

    /** [mac] muss schon durch [Mac.normalisieren] gegangen sein; mac.sh prueft trotzdem noch einmal. */
    fun setzen(ctx: Context, mac: String, beimStart: Boolean) =
        aufruf(ctx, "setzen $mac ${if (beimStart) "ja" else "nein"}")

    fun aus(ctx: Context) = aufruf(ctx, "aus")

    private fun aufruf(ctx: Context, befehl: String): MacZustand {
        // Ohne \r: mit Windows-Zeilenenden (falsch ausgecheckt) liefe das Skript auf dem Handy nicht.
        val skript = ctx.assets.open("mac.sh").use { it.reader().readText() }.replace("\r", "").trimEnd('\n')
        // Erst in eine neue Datei, dann umbenennen: ein laufender Waechter liest seine (alte) Datei weiter,
        // statt mitten im Ueberschreiben Unsinn zu lesen.
        val ergebnis = Root.ausfuehren(
            "mkdir -p $DIR && chmod 700 $DIR\n" +
                "cat > $DIR/mac.sh.neu <<'$ENDE'\n$skript\n$ENDE\n" +
                "chmod 700 $DIR/mac.sh.neu && mv -f $DIR/mac.sh.neu $DIR/mac.sh\n" +
                "/system/bin/sh $DIR/mac.sh $befehl\n",
        )
        val zustand = MacZustand.lesen(ergebnis.ausgabe)
        return when {
            zustand.fehler != null || zustand.root -> zustand
            Root.vorhanden() -> zustand.copy(fehler = "Root abgelehnt. In Magisk → Superuser für Root Tools erlauben.")
            else -> zustand.copy(fehler = "Kein su gefunden. Ist Magisk installiert?")
        }
    }
}
