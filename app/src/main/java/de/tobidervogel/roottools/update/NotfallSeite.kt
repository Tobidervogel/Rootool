package de.tobidervogel.roottools.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.Etikett
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Hinweis
import de.tobidervogel.roottools.ui.Karte
import de.tobidervogel.roottools.ui.Knopf
import de.tobidervogel.roottools.ui.Kopfzeile
import de.tobidervogel.roottools.ui.Luecke
import de.tobidervogel.roottools.ui.Schrift
import de.tobidervogel.roottools.ui.Seite
import de.tobidervogel.roottools.ui.WertZeile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotfallMeldung(val text: String, val farbe: Color)

@Composable
fun NotfallSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf<SystemStand?>(null) }
    var laeuft by remember { mutableStateOf<String?>(null) }
    var meldung by remember { mutableStateOf<NotfallMeldung?>(null) }

    fun laden() = scope.launch { stand = withContext(Dispatchers.IO) { UpdateSteuerung.sicherungen(ctx) } }
    LaunchedEffect(Unit) { laden() }

    NotfallInhalt(
        stand, laeuft, meldung, zurueck,
        neuLaden = { laden() },
        sichern = {
            scope.launch {
                laeuft = "Starte …"
                meldung = null
                val w = withContext(Dispatchers.IO) { UpdateSteuerung.sichern(ctx) { laeuft = it.replaceFirst(" ", " · ") } }
                meldung = w.fehler?.let { NotfallMeldung(it, Farbe.fehler) }
                    ?: w["FERTIG"]?.let {
                        NotfallMeldung("Fertig: ${it.substringAfterLast('/')}. Jetzt auf PC oder NAS kopieren.", Farbe.akzent)
                    }
                    ?: NotfallMeldung("Abgebrochen, ohne Rückmeldung. Siehe Ordner RootTools/Notfall.", Farbe.fehler)
                laeuft = null
                stand = withContext(Dispatchers.IO) { UpdateSteuerung.sicherungen(ctx) }
            }
        },
    )
}

@Composable
fun NotfallInhalt(
    stand: SystemStand?,
    laeuft: String?,
    meldung: NotfallMeldung?,
    zurueck: () -> Unit,
    neuLaden: () -> Unit = {},
    sichern: () -> Unit = {},
) {
    Seite {
        Kopfzeile("Notfall-Sicherung", zurueck, neuLaden, aktiv = laeuft == null)
        stand?.fehler?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        Karte {
            Etikett("Was gesichert wird")
            Spacer(Modifier.height(8.dp))
            Text(
                "Alles, was das Handy zum Starten braucht (boot, init_boot, vbmeta, dtbo, lk – beide Slots), und die " +
                    "MediaTek-Funkdaten mit IMEI, WLAN- und Bluetooth-Adressen. Root Tools liest nur, es schreibt nichts zurück.",
                style = Schrift.text, color = Farbe.text2,
            )
            Spacer(Modifier.height(8.dp))
            WertZeile("Umfang", stand?.let { groesseText(it.teileKb) } ?: "…")
            WertZeile("Frei", stand?.let { groesseText(it.freiKb) } ?: "…")
            Spacer(Modifier.height(12.dp))
            Knopf(
                laeuft?.let { "Sichere $it" } ?: "Jetzt sichern", sichern,
                modifier = Modifier.fillMaxWidth(),
                aktiv = stand?.fehler == null && stand != null,
                arbeitet = laeuft != null,
                symbol = Icons.Rounded.Save,
            )
            meldung?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.text, style = Schrift.text, color = it.farbe)
            }
        }
        Luecke()
        Karte {
            Etikett("Vorhandene Sicherungen")
            Spacer(Modifier.height(6.dp))
            val liste = stand?.sicherungen.orEmpty()
            if (liste.isEmpty()) Text("Noch keine.", style = Schrift.text, color = Farbe.text2)
            for (s in liste) {
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sicherungsDatum(s.name), style = Schrift.text, color = Farbe.text, modifier = Modifier.weight(1f))
                    Text(groesseText(s.kb), style = Schrift.klein, color = Farbe.text2)
                    Text(
                        if (s.komplett) "  ✓ komplett" else "  ✗ unvollständig",
                        style = Schrift.klein, color = if (s.komplett) Farbe.akzent else Farbe.fehler,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Liegt unter Interner Speicher/RootTools/Notfall, mit Prüfsummen und einer Anleitung. Auf PC oder NAS " +
                    "kopieren: Auf dem Handy selbst hilft sie nicht, wenn es nicht mehr startet.",
                style = Schrift.klein, color = Farbe.text3,
            )
        }
    }
}
