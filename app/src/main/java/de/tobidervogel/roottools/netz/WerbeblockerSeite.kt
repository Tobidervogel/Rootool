package de.tobidervogel.roottools.netz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.Abzeichen
import de.tobidervogel.roottools.ui.Etikett
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Hinweis
import de.tobidervogel.roottools.ui.Karte
import de.tobidervogel.roottools.ui.Knopf
import de.tobidervogel.roottools.ui.Kopfzeile
import de.tobidervogel.roottools.ui.Luecke
import de.tobidervogel.roottools.ui.Schrift
import de.tobidervogel.roottools.ui.Seite
import de.tobidervogel.roottools.ui.SicherKnopf
import de.tobidervogel.roottools.ui.Suchfeld
import de.tobidervogel.roottools.update.groesseText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class WerbeblockerAktionen(
    val neuLaden: () -> Unit = {},
    val laden: (HostsListe, List<Eigen>) -> Unit = { _, _ -> },
    val uebernehmen: (List<Eigen>) -> Unit = {},
    val aus: () -> Unit = {},
)

@Composable
fun WerbeblockerSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf<HostsStand?>(null) }
    var arbeit by remember { mutableStateOf<String?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }

    fun laden() = scope.launch { stand = withContext(Dispatchers.IO) { Werbeblocker.zustand(ctx) } }
    LaunchedEffect(Unit) { laden() }

    fun arbeiten(text: String, aufgabe: suspend () -> HostsStand) = scope.launch {
        arbeit = text
        fehler = null
        runCatching { aufgabe() }
            .onSuccess { stand = it }
            .onFailure { fehler = "Laden fehlgeschlagen: ${it.message}" }
        arbeit = null
    }

    WerbeblockerInhalt(
        stand, arbeit, fehler, zurueck,
        WerbeblockerAktionen(
            neuLaden = { laden() },
            laden = { liste, eigen ->
                arbeiten("Lade Liste …") {
                    val datei = withContext(Dispatchers.IO) {
                        Werbeblocker.laden(ctx, liste) { b -> arbeit = "Lade Liste … ${groesseText(b / 1024)}" }
                    }
                    arbeit = "Schreibe hosts …"
                    withContext(Dispatchers.IO) { Werbeblocker.schreiben(ctx, datei, liste.titel, eigen) }
                }
            },
            uebernehmen = { eigen ->
                arbeiten("Schreibe hosts …") {
                    withContext(Dispatchers.IO) { Werbeblocker.schreiben(ctx, null, stand?.liste ?: "Liste", eigen) }
                }
            },
            aus = { arbeiten("Schalte aus …") { withContext(Dispatchers.IO) { Werbeblocker.aus(ctx) } } },
        ),
    )
}

@Composable
fun WerbeblockerInhalt(
    stand: HostsStand?,
    arbeit: String?,
    fehler: String?,
    zurueck: () -> Unit,
    a: WerbeblockerAktionen,
) {
    var liste by remember { mutableStateOf(HostsListe.Werbung) }
    var eigen by remember(stand) { mutableStateOf(stand?.eigen.orEmpty()) }
    val geaendert = stand != null && eigen != stand.eigen
    val beschaeftigt = arbeit != null

    Seite {
        Kopfzeile("Werbeblocker", zurueck, a.neuLaden, aktiv = !beschaeftigt)
        (fehler ?: stand?.fehler)?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        StatusKarte(stand)
        Luecke()

        Karte {
            Etikett("Liste")
            Spacer(Modifier.height(10.dp))
            for (l in HostsListe.entries) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = !beschaeftigt) { liste = l }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .border(2.dp, if (liste == l) Farbe.akzent else Farbe.linie, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (liste == l) Box(Modifier.size(10.dp).clip(CircleShape).background(Farbe.akzent)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(l.titel, style = Schrift.karte, color = Farbe.text)
                        Text(l.text, style = Schrift.klein, color = Farbe.text2)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Knopf(
                arbeit ?: "Laden und aktivieren", { a.laden(liste, eigen) },
                modifier = Modifier.fillMaxWidth(), aktiv = stand?.root() == true, arbeitet = beschaeftigt,
                symbol = Icons.Rounded.Download,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Lädt die Liste von GitHub (StevenBlack/hosts). Nur dafür geht Root Tools ins Internet.",
                style = Schrift.klein, color = Farbe.text3,
            )
        }
        Luecke()
        EigeneKarte("Eigene Sperren", "z. B. instagram.com", true, eigen, !beschaeftigt) { eigen = it }
        Luecke()
        EigeneKarte("Ausnahmen", "Adresse, die frei bleiben soll", false, eigen, !beschaeftigt) { eigen = it }
        if (geaendert) {
            Luecke()
            Karte(modifier = Modifier.border(1.dp, Farbe.akzent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) {
                Text("Noch nicht übernommen", style = Schrift.karte, color = Farbe.text)
                if (stand?.unser != true) {
                    Text("Erst eine Liste laden, dann gelten auch die eigenen Einträge.", style = Schrift.klein, color = Farbe.text2)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Knopf("Übernehmen", { a.uebernehmen(eigen) }, aktiv = stand?.unser == true && !beschaeftigt, symbol = Icons.Rounded.Check)
                    Knopf("Verwerfen", { eigen = stand?.eigen.orEmpty() }, haupt = false, aktiv = !beschaeftigt)
                }
            }
        }
        if (stand?.unser == true && !stand.entfernen) {
            Luecke()
            SicherKnopf(
                "Werbeblocker ausschalten", "hosts wieder leeren? Das Magisk-Modul verschwindet beim nächsten Neustart.",
                a.aus, aktiv = !beschaeftigt, symbol = Icons.Rounded.Block,
            )
        }
        Luecke()
        Text(
            "hosts sperrt genau die eingetragenen Adressen, keine Unteradressen. Browser mit eigenem DNS (z. B. Chrome " +
                "„Sicheres DNS“ mit festem Anbieter) umgehen die Liste.",
            style = Schrift.klein, color = Farbe.text3,
        )
    }
}

private fun HostsStand.root() = w.root

@Composable
private fun StatusKarte(stand: HostsStand?) {
    Karte {
        when {
            stand == null -> Abzeichen("Lade …", Farbe.text3)
            stand.entfernen -> {
                Abzeichen("Aus", Farbe.text3)
                Spacer(Modifier.height(8.dp))
                Text("Das hosts-Modul wird beim nächsten Neustart entfernt.", style = Schrift.text, color = Farbe.text2)
            }
            stand.unser && stand.aktiv -> {
                Abzeichen("Aktiv", Farbe.akzent)
                Spacer(Modifier.height(10.dp))
                Text(
                    "${String.format(Locale.GERMANY, "%,d", stand.anzahl)} Adressen gesperrt",
                    style = Schrift.titel, color = Farbe.text,
                )
                Text(
                    listOfNotNull(stand.liste, stand.stand?.let { "Stand $it" }).joinToString(" · "),
                    style = Schrift.klein, color = Farbe.text2,
                )
            }
            stand.unser -> {
                Abzeichen("Wirkt nach Neustart", Farbe.warnung)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Die Liste steht bereit (${String.format(Locale.GERMANY, "%,d", stand.anzahl)} Adressen). Magisk hängt " +
                        "sie beim nächsten Start ein, danach wirken Änderungen sofort.",
                    style = Schrift.text, color = Farbe.text2,
                )
            }
            stand.modul -> {
                Abzeichen("Fremde Liste", Farbe.warnung)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Magisks hosts-Modul ist da, aber mit einer anderen Liste (z. B. von AdAway). Laden ersetzt sie.",
                    style = Schrift.text, color = Farbe.text2,
                )
            }
            else -> {
                Abzeichen("Aus", Farbe.text3)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sperrt Werbung und Tracker in allen Apps über die hosts-Datei. Ohne VPN, läuft neben Tailscale.",
                    style = Schrift.text, color = Farbe.text2,
                )
            }
        }
    }
}

@Composable
private fun EigeneKarte(
    titel: String,
    hinweis: String,
    sperre: Boolean,
    eigen: List<Eigen>,
    aktiv: Boolean,
    aendern: (List<Eigen>) -> Unit,
) {
    var eingabe by remember { mutableStateOf("") }
    val domain = Werbeblocker.domain(eingabe)
    Karte {
        Etikett(titel)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Suchfeld(eingabe, { eingabe = it }, hinweis, Farbe.karte2) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (domain != null && aktiv) Farbe.akzent else Farbe.karte2)
                    .clickable(enabled = domain != null && aktiv) {
                        val neu = Werbeblocker.mitWww(domain!!).map { Eigen(sperre, it) }.filter { it !in eigen }
                        aendern(eigen + neu)
                        eingabe = ""
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Hinzufügen", tint = if (domain != null) Farbe.grund else Farbe.text3) }
        }
        val liste = eigen.filter { it.sperre == sperre }
        if (liste.isNotEmpty()) Spacer(Modifier.height(8.dp))
        for (e in liste) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.domain, style = Schrift.macKlein, color = Farbe.text, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(enabled = aktiv) { aendern(eigen - e) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Entfernen", tint = Farbe.text3, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
