package de.tobidervogel.roottools.root

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root ueber Magisk, wie in Live Up (rechte/Root.kt). Jeder Aufruf ist ein eigenes su: Magisk zeigt dann jedes Mal
 * kurz seinen Hinweis, darum buendelt jedes Werkzeug seine Befehle in moeglichst wenige Aufrufe.
 */
object Root {
    /** su liegt je nach Magisk-Version woanders (Nord 3: /product/bin), darum ueber PATH wie die Shell. */
    fun vorhanden(): Boolean =
        (System.getenv("PATH") ?: "").split(':').any { it.isNotEmpty() && File(it, "su").exists() }

    /** [code] -1 heisst: su fehlt, wurde abgelehnt oder hing laenger als das Zeitlimit. */
    data class Ergebnis(val code: Int, val ausgabe: String) {
        val ok get() = code == 0
    }

    /**
     * Fuehrt [skript] als root aus. Das Skript geht ueber stdin an su und nicht ueber "su -c": so braucht es kein
     * Quoting und darf mehrzeilig sein. Blockiert bis zum Ende (beim ersten Mal bis zur Antwort im Magisk-Dialog),
     * also nie auf dem Hauptthread. [zeile] bekommt jede Ausgabezeile sofort (Fortschritt langer Arbeiten), auf dem
     * Lesefaden.
     */
    fun ausfuehren(skript: String, zeitlimitSek: Long = 40, zeile: ((String) -> Unit)? = null): Ergebnis = runCatching {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        val text = StringBuilder()
        // Eigener Lesefaden: sonst kann su bei voller Pipe haengen, waehrend hier noch geschrieben wird.
        // runCatching, weil destroy() den Strom mitten im Lesen schliesst und das den ganzen Prozess beenden wuerde.
        val leser = Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine {
                    synchronized(text) { text.appendLine(it) }
                    zeile?.invoke(it)
                }
            }
        }.apply { start() }
        p.outputStream.bufferedWriter().use { it.write(skript) }
        if (!p.waitFor(zeitlimitSek, TimeUnit.SECONDS)) {
            p.destroy()
            return@runCatching Ergebnis(-1, "Zeitlimit von $zeitlimitSek s überschritten")
        }
        leser.join(2000)
        Ergebnis(p.exitValue(), synchronized(text) { text.toString() })
    }.getOrElse { Ergebnis(-1, it.message ?: "su nicht startbar") }

    /** Fragt nach Root (beim ersten Mal mit dem Magisk-Dialog). */
    fun erteilt(): Boolean = ausfuehren("id -u").ausgabe.trim() == "0"
}
