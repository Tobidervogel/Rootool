package de.tobidervogel.roottools.root

import android.content.Context

/**
 * Die Root-Arbeit jedes Werkzeugs steckt in einem Shell-Skript unter assets/. Bei jedem Aufruf kopiert die App es
 * frisch nach [DIR] (so laeuft immer die Fassung dieser App-Version) und ruft es im selben su auf. Die Skripte
 * antworten mit SCHLUESSEL=wert-Zeilen, siehe [Werte].
 */
object Skript {
    const val DIR = "/data/adb/roottools"
    private const val ENDE = "ROOTTOOLS_ENDE"
    private val SICHER = Regex("[A-Za-z0-9._:/@+=-]+")

    /**
     * Ruft assets/[name] mit [argumente] auf. [eingabe] (z. B. eine Liste) landet vorher in $DIR/[name].eingabe,
     * weil sie fuer die Befehlszeile zu lang oder zu frei ist. Alles blockiert.
     */
    fun aufrufen(
        ctx: Context,
        name: String,
        vararg argumente: String,
        eingabe: String? = null,
        zeitlimitSek: Long = 40,
        zeile: ((String) -> Unit)? = null,
    ): Werte {
        for (a in argumente) require(SICHER.matches(a)) { "Unsicheres Argument: $a" }
        // Ohne \r: mit Windows-Zeilenenden (falsch ausgecheckt) liefe das Skript auf dem Handy nicht.
        val text = ctx.assets.open(name).use { it.reader().readText() }.replace("\r", "").trimEnd('\n')
        val skript = buildString {
            append("mkdir -p $DIR && chmod 700 $DIR\n")
            // Erst in eine neue Datei, dann umbenennen: ein laufender Waechter liest seine (alte) Datei weiter,
            // statt mitten im Ueberschreiben Unsinn zu lesen.
            append("cat > $DIR/$name.neu <<'$ENDE'\n$text\n$ENDE\n")
            append("chmod 700 $DIR/$name.neu && mv -f $DIR/$name.neu $DIR/$name\n")
            if (eingabe != null) {
                require(eingabe.lines().none { it == ENDE })
                append("cat > $DIR/$name.eingabe <<'$ENDE'\n${eingabe.replace("\r", "").trimEnd('\n')}\n$ENDE\n")
            }
            append("/system/bin/sh $DIR/$name ${argumente.joinToString(" ")}\n")
        }
        val werte = Werte(Root.ausfuehren(skript, zeitlimitSek, zeile).ausgabe)
        return when {
            werte.root || werte.fehler != null -> werte
            Root.vorhanden() -> werte.mitFehler("Root abgelehnt. In Magisk → Superuser für Root Tools erlauben.")
            else -> werte.mitFehler("Kein su gefunden. Ist Magisk installiert?")
        }
    }
}

/** SCHLUESSEL=wert-Zeilen aus einem Skript. Ein Schluessel darf mehrfach vorkommen (Listen, Protokoll). */
class Werte(val ausgabe: String) {
    private val alle = LinkedHashMap<String, MutableList<String>>()
    private var eigenerFehler: String? = null

    init {
        for (zeile in ausgabe.lineSequence()) {
            val i = zeile.indexOf('=')
            if (i <= 0 || zeile.substring(0, i).any { !(it.isUpperCase() || it.isDigit() || it == '_') }) continue
            alle.getOrPut(zeile.substring(0, i)) { mutableListOf() } += zeile.substring(i + 1)
        }
    }

    /** Letzter Wert zu [schluessel], ohne Leerraum an den Raendern; null, wenn er fehlt oder leer ist. */
    operator fun get(schluessel: String): String? = alle[schluessel]?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    fun liste(schluessel: String): List<String> = alle[schluessel].orEmpty()

    fun ja(schluessel: String) = get(schluessel) == "1"

    val root get() = get("ROOT") == "0"
    val fehler get() = eigenerFehler ?: get("FEHLER")

    fun mitFehler(text: String) = also { eigenerFehler = text }
}
