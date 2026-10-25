package de.tobidervogel.roottools.netz

import android.content.Context
import de.tobidervogel.roottools.root.Skript
import de.tobidervogel.roottools.root.Werte

/** Eine Sperre: immer, oder nur zwischen [von] und [bis] (auch ueber Mitternacht). */
data class Regel(val paket: String, val immer: Boolean, val von: String = "22:00", val bis: String = "07:00") {
    fun zeile() = if (immer) "$paket|immer" else "$paket|zeit|$von|$bis"

    fun text() = if (immer) "Immer gesperrt" else "Gesperrt $von–$bis"

    companion object {
        private val ZEIT = Regex("""([01]\d|2[0-3]):[0-5]\d""")

        fun zeitOk(z: String) = ZEIT.matches(z)

        fun lesen(zeile: String): Regel? {
            val t = zeile.trim().split('|')
            return when {
                t.size == 2 && t[1] == "immer" -> Regel(t[0], true)
                t.size == 4 && t[1] == "zeit" && zeitOk(t[2]) && zeitOk(t[3]) -> Regel(t[0], false, t[2], t[3])
                else -> null
            }
        }
    }
}

/** Was firewall.sh meldet. Getestet in FirewallTest. */
class FirewallStand(val w: Werte) {
    val fehler get() = w.fehler
    val regeln: Map<String, Regel> get() = w.liste("REGEL").mapNotNull { Regel.lesen(it) }.associateBy { it.paket }
    val gesperrt: Set<String> get() = w.liste("GESPERRT").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val kette get() = w.ja("KETTE")
    val waechter get() = w.ja("WAECHTER")
    val protokoll get() = w.liste("LOG")
}

/** Root-Seite der Firewall: die Arbeit macht assets/firewall.sh. Alles blockiert. */
object Firewall {
    fun zustand(ctx: Context) = FirewallStand(Skript.aufrufen(ctx, "firewall.sh", "zustand"))

    fun setzen(ctx: Context, regeln: Collection<Regel>) = FirewallStand(
        Skript.aufrufen(ctx, "firewall.sh", "setzen", eingabe = regeln.joinToString("\n") { it.zeile() }),
    )

    fun aus(ctx: Context) = FirewallStand(Skript.aufrufen(ctx, "firewall.sh", "aus"))
}
