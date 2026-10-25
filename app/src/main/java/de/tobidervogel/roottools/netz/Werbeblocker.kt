package de.tobidervogel.roottools.netz

import android.content.Context
import de.tobidervogel.roottools.root.Skript
import de.tobidervogel.roottools.root.Werte
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Listen von StevenBlack/hosts (MIT-Lizenz, Quellen siehe dort). */
enum class HostsListe(val titel: String, val text: String, val url: String) {
    Werbung(
        "Werbung und Schadsoftware", "Die bekannte Grundliste",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
    ),
    Sozial(
        "Dazu soziale Netzwerke", "Sperrt auch Instagram, TikTok, Facebook und Co.",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts",
    ),
}

/** Eigene Sperre oder Ausnahme. */
data class Eigen(val sperre: Boolean, val domain: String)

/** Was hosts.sh meldet. Getestet in WerbeblockerTest. */
class HostsStand(val w: Werte) {
    val fehler get() = w.fehler
    val aktiv get() = w.ja("AKTIV")
    val unser get() = w.ja("UNSER")
    val modul get() = w.ja("MODUL")
    val entfernen get() = w.ja("ENTFERNEN")
    val anzahl get() = w["ANZAHL"]?.toIntOrNull() ?: 0
    val liste get() = w["LISTE"]
    val stand get() = w["STAND"]
    val eigen: List<Eigen> get() = w.liste("EIGEN").mapNotNull { z ->
        val (art, dom) = z.trim().split(' ', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        when (art) {
            "SPERRE" -> Eigen(true, dom)
            "AUSNAHME" -> Eigen(false, dom)
            else -> null
        }
    }
}

object Werbeblocker {
    private val DOMAIN = Regex("""[a-z0-9-]+(\.[a-z0-9-]+)+""")

    /** "https://www.Instagram.com/reels/" wird "www.instagram.com"; null, wenn es keine Adresse ist. */
    fun domain(eingabe: String): String? {
        val d = eingabe.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore(':')
        return d.takeIf { DOMAIN.matches(it) && it.split('.').none { teil -> teil.isEmpty() || teil.startsWith('-') } }
    }

    /**
     * hosts kennt keine Platzhalter: es sperrt genau die Adresse. Zu "instagram.com" kommt darum "www.instagram.com"
     * dazu, die Adresse, die man im Browser meist erwischt.
     */
    fun mitWww(domain: String): List<String> =
        if (domain.count { it == '.' } == 1) listOf(domain, "www.$domain") else listOf(domain)

    fun zustand(ctx: Context) = HostsStand(Skript.aufrufen(ctx, "hosts.sh", "zustand"))

    /**
     * Laedt [liste] in den eigenen Cache. Die einzige Stelle, an der Root Tools ins Internet geht.
     * [fortschritt] bekommt die bisher geladenen Bytes.
     */
    fun laden(ctx: Context, liste: HostsListe, fortschritt: (Long) -> Unit): File {
        val ziel = File(ctx.cacheDir, "hosts.quelle")
        val c = URL(liste.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            check(c.responseCode == 200) { "Server antwortet ${c.responseCode}" }
            c.inputStream.use { ein ->
                ziel.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    var gesamt = 0L
                    while (true) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        gesamt += n
                        fortschritt(gesamt)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        // Grobe Pruefung: eine echte Liste hat Zehntausende Eintraege, eine Fehlerseite keine.
        val eintraege = ziel.useLines { z -> z.count { it.startsWith("0.0.0.0 ") } }
        check(eintraege > 1000) { "Die geladene Datei sieht nicht wie eine hosts-Liste aus." }
        return ziel
    }

    /** [quelle] = geladene Datei, oder null fuer die zuletzt benutzte Liste. */
    fun schreiben(ctx: Context, quelle: File?, name: String, eigen: List<Eigen>) = HostsStand(
        Skript.aufrufen(
            ctx, "hosts.sh", "schreiben", quelle?.path ?: "-", name.replace(' ', '_'),
            eingabe = eigen.joinToString("\n") { (if (it.sperre) "SPERRE " else "AUSNAHME ") + it.domain },
        ),
    ).also { quelle?.delete() }

    fun aus(ctx: Context) = HostsStand(Skript.aufrufen(ctx, "hosts.sh", "aus"))
}
