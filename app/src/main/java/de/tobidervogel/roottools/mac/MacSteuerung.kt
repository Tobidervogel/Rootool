package de.tobidervogel.roottools.mac

import android.content.Context
import de.tobidervogel.roottools.root.Skript

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

/** Root-Seite des Werkzeugs: die Arbeit macht assets/mac.sh (siehe [Skript]). Alles blockiert. */
object MacSteuerung {
    fun zustand(ctx: Context) = aufruf(ctx, "zustand")

    /** [mac] muss schon durch [Mac.normalisieren] gegangen sein; mac.sh prueft trotzdem noch einmal. */
    fun setzen(ctx: Context, mac: String, beimStart: Boolean) =
        aufruf(ctx, "setzen", mac, if (beimStart) "ja" else "nein")

    fun aus(ctx: Context) = aufruf(ctx, "aus")

    private fun aufruf(ctx: Context, vararg befehl: String): MacZustand {
        val werte = Skript.aufrufen(ctx, "mac.sh", *befehl)
        val zustand = MacZustand.lesen(werte.ausgabe)
        return if (zustand.fehler == null && werte.fehler != null) zustand.copy(fehler = werte.fehler) else zustand
    }
}
