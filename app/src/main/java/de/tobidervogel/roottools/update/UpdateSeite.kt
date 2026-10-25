package de.tobidervogel.roottools.update

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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.Abzeichen
import de.tobidervogel.roottools.ui.Etikett
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Fremd
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

/** Was die Seite ausloest; getrennt vom Zustand, damit sich [UpdateInhalt] ohne Handy darstellen laesst. */
class UpdateAktionen(
    val neuLaden: () -> Unit = {},
    val notfall: () -> Unit = {},
    val magisk: () -> Unit = {},
    val systemupdate: () -> Unit = {},
    val oxygenUpdater: () -> Unit = {},
    val otaFreischalten: () -> Unit = {},
    val netzAn: () -> Unit = {},
)

@Composable
fun UpdateSeite(zurueck: () -> Unit, notfallOeffnen: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf<SystemStand?>(null) }
    var laedt by remember { mutableStateOf(true) }
    var meldung by remember { mutableStateOf<String?>(null) }

    fun laden() = scope.launch {
        laedt = true
        stand = withContext(Dispatchers.IO) { UpdateSteuerung.zustand(ctx) }
        laedt = false
    }
    LaunchedEffect(Unit) { laden() }

    fun oeffnen(paket: String, ersatz: String? = null, name: String) {
        meldung = if (Fremd.oeffnen(ctx, paket, ersatz)) null else "$name nicht gefunden. Bitte selbst öffnen."
    }

    UpdateInhalt(
        stand, laedt, meldung, zurueck,
        UpdateAktionen(
            neuLaden = { laden() },
            notfall = notfallOeffnen,
            // Versteckt man Magisk ("App verstecken"), hat es einen zufaelligen Paketnamen: dann bleibt nur die Meldung.
            magisk = { oeffnen(Fremd.MAGISK, name = "Magisk") },
            systemupdate = { oeffnen(Fremd.OTA, "android.settings.SYSTEM_UPDATE_SETTINGS", "Systemupdate") },
            oxygenUpdater = { oeffnen(Fremd.OXYGEN_UPDATER, name = "Oxygen Updater") },
            otaFreischalten = {
                scope.launch {
                    val w = withContext(Dispatchers.IO) { UpdateSteuerung.otaFreischalten(ctx) }
                    meldung = w.fehler ?: "WLAN und Daten sind aus, die Update-App ist geleert. Jetzt Systemupdate öffnen."
                }
            },
            netzAn = {
                scope.launch {
                    val w = withContext(Dispatchers.IO) { UpdateSteuerung.netzAn(ctx) }
                    meldung = w.fehler ?: "WLAN und mobile Daten sind wieder an."
                }
            },
        ),
    )
}

@Composable
fun UpdateInhalt(stand: SystemStand?, laedt: Boolean, meldung: String?, zurueck: () -> Unit, a: UpdateAktionen) {
    Seite {
        Kopfzeile("System-Update", zurueck, a.neuLaden, aktiv = !laedt)
        if (stand == null) {
            Karte {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Farbe.akzent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Prüfe Slots und Boot-Abbilder …", style = Schrift.text, color = Farbe.text2)
                }
            }
            return@Seite
        }
        stand.fehler?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        meldung?.let {
            Hinweis("Hinweis", it, Farbe.text2)
            Luecke()
        }

        StatusKarte(stand, a)
        Luecke()

        Karte {
            Etikett("Dieses System")
            Spacer(Modifier.height(6.dp))
            WertZeile("Version", stand.version ?: "–")
            WertZeile("Android", listOfNotNull(stand.android, stand.patch?.let { "Patch $it" }).joinToString(" · ").ifEmpty { "–" })
            WertZeile("Aktiver Slot", stand.slot?.removePrefix("_") ?: "–")
            WertZeile("Magisk", stand.magisk?.takeIf { it != "()" } ?: "–")
            stand.akku?.let { WertZeile("Akku", "$it %", wertFarbe = if (it < 60) Farbe.warnung else Farbe.text) }
        }
        Luecke()

        AbbildKarte(stand)
        Luecke()

        AnleitungKarte(a)

        if (stand.protokoll.isNotEmpty()) {
            Luecke()
            Karte {
                Etikett("update_engine")
                Spacer(Modifier.height(8.dp))
                for (z in stand.protokoll) Text(z, style = Schrift.log, color = Farbe.text2)
            }
        }
    }
}

@Composable
private fun StatusKarte(stand: SystemStand, a: UpdateAktionen) {
    val (farbe, titel, text) = when (stand.schritt) {
        UpdateSchritt.KeinUpdate -> Triple(
            Farbe.text3, "Kein Update wartet",
            "Nach dem Installieren eines Updates hierher zurück, bevor du neu startest. Root Tools prüft dann, " +
                "ob Root den Neustart übersteht.",
        )
        UpdateSchritt.MagiskPatchen -> Triple(
            Farbe.warnung, "Update installiert: noch nicht neu starten!",
            "Sonst ist Root weg. Öffne Magisk → Installieren → „In inaktiven Slot installieren (nach OTA)“. " +
                "Danach hier neu prüfen.",
        )
        UpdateSchritt.Neustarten -> Triple(
            Farbe.akzent, "Bereit: Root bleibt",
            "Slot ${stand.inaktiv?.removePrefix("_")} ist mit Magisk gepatcht. Starte jetzt in der OnePlus-App mit " +
                "„Jetzt neu starten“ neu, nicht über Magisk.",
        )
    }
    Karte(modifier = Modifier.border(1.dp, farbe.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) {
        Abzeichen(
            when (stand.schritt) {
                UpdateSchritt.KeinUpdate -> "Alles ruhig"
                UpdateSchritt.MagiskPatchen -> "Schritt: Magisk"
                UpdateSchritt.Neustarten -> "Schritt: Neustart"
            },
            farbe,
        )
        Spacer(Modifier.height(12.dp))
        Text(titel, style = Schrift.karte, color = if (farbe == Farbe.text3) Farbe.text else farbe)
        Spacer(Modifier.height(4.dp))
        Text(text, style = Schrift.text, color = Farbe.text2)
        if (stand.akku != null && stand.akku!! < 60) {
            Spacer(Modifier.height(8.dp))
            Text("Akku unter 60 %: vor einem Update erst laden.", style = Schrift.klein, color = Farbe.warnung)
        }
        when (stand.schritt) {
            UpdateSchritt.MagiskPatchen -> {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Knopf("Magisk öffnen", a.magisk, symbol = Icons.Rounded.OpenInNew)
                    Knopf("Neu prüfen", a.neuLaden, haupt = false, symbol = Icons.Rounded.Refresh)
                }
            }
            UpdateSchritt.Neustarten -> {
                Spacer(Modifier.height(14.dp))
                Knopf("Systemupdate öffnen", a.systemupdate, symbol = Icons.Rounded.OpenInNew)
            }
            UpdateSchritt.KeinUpdate -> {}
        }
    }
}

private fun abbildText(s: String) = when (s) {
    "magisk" -> "Magisk"
    "original" -> "Original"
    "leer" -> "ohne Ramdisk"
    "fremd" -> "fremd gepatcht"
    "fehlt" -> "–"
    else -> "?"
}

private fun abbildFarbe(s: String) = when (s) {
    "magisk" -> Farbe.akzent
    "fremd" -> Farbe.fehler
    else -> Farbe.text2
}

@Composable
private fun AbbildKarte(stand: SystemStand) {
    Karte {
        Etikett("Boot-Abbilder")
        Spacer(Modifier.height(10.dp))
        Row {
            Spacer(Modifier.weight(1.1f))
            for (s in listOf("_a", "_b")) {
                Text(
                    "Slot ${s.removePrefix("_")}" + if (s == stand.slot) " · aktiv" else "",
                    style = Schrift.klein, color = if (s == stand.slot) Farbe.text else Farbe.text3,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
            }
        }
        for (bild in listOf("boot", "init_boot")) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(bild, style = Schrift.macKlein, color = Farbe.text2, modifier = Modifier.weight(1.1f))
                for (s in listOf("_a", "_b")) {
                    val st = stand.abbild(bild + s)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(abbildFarbe(st)))
                            Spacer(Modifier.width(6.dp))
                            Text(abbildText(st), style = Schrift.klein, color = Farbe.text)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stand.magiskAbbild?.let { "Magisk sitzt auf diesem Handy in $it." }
                ?: "Im laufenden Slot ist kein Magisk-Abbild zu sehen.",
            style = Schrift.klein, color = Farbe.text3,
        )
    }
}

@Composable
private fun AnleitungKarte(a: UpdateAktionen) {
    var fragen by remember { mutableStateOf(false) }
    Karte {
        Etikett("So läuft ein Update")
        Spacer(Modifier.height(10.dp))
        Schritt(1, "Notfall-Sicherung machen.") {
            Knopf("Notfall-Sicherung", a.notfall, haupt = false, symbol = Icons.Rounded.Save)
        }
        Schritt(2, "Das volle Update (Full OTA) der neuen Version mit Oxygen Updater laden: Methode „Stable (full)“, fortgeschrittener Modus an.") {
            Knopf("Oxygen Updater", a.oxygenUpdater, haupt = false, symbol = Icons.Rounded.Download)
        }
        Schritt(3, "Systemupdate → Lokale Installation → Paket wählen und installieren lassen (etwa 30 Minuten).") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Knopf("Systemupdate", a.systemupdate, haupt = false, symbol = Icons.Rounded.OpenInNew)
                if (!fragen) {
                    Text(
                        "Lokale Installation ausgegraut? Freischalten …",
                        style = Schrift.klein, color = Farbe.akzent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { fragen = true }.padding(vertical = 4.dp),
                    )
                } else {
                    Text(
                        "Schaltet WLAN und mobile Daten aus und leert die Update-App. Dann findet sie online nichts " +
                            "mehr und lässt die lokale Installation zu (so hat es am 17.10. geklappt).",
                        style = Schrift.klein, color = Farbe.text2,
                    )
                    Knopf("Freischalten", { fragen = false; a.otaFreischalten() }, haupt = false, textFarbe = Farbe.warnung)
                }
            }
        }
        Schritt(4, "Nach der Installation NICHT neu starten. In Magisk: Installieren → „In inaktiven Slot installieren (nach OTA)“.") {
            Knopf("Magisk öffnen", a.magisk, haupt = false, symbol = Icons.Rounded.OpenInNew)
        }
        Schritt(5, "Hier neu prüfen: Oben muss „Bereit: Root bleibt“ stehen.")
        Schritt(6, "In der OnePlus-App „Jetzt neu starten“, nicht über Magisk. Der Start dauert etwa eine Minute.")
        Schritt(7, "Danach WLAN und Daten wieder einschalten, falls du freigeschaltet hast.", letzter = true) {
            Knopf("WLAN und Daten an", a.netzAn, haupt = false, symbol = Icons.Rounded.Wifi)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Automatische und kleine (inkrementelle) Updates nicht installieren: Sie passen nicht zu einem gepatchten System.",
            style = Schrift.klein, color = Farbe.text3,
        )
    }
}

@Composable
private fun Schritt(nr: Int, text: String, letzter: Boolean = false, knopf: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (letzter) 4.dp else 14.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Farbe.karte2),
            contentAlignment = Alignment.Center,
        ) { Text("$nr", style = Schrift.klein, color = Farbe.text) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = Schrift.text, color = Farbe.text)
            if (knopf != null) {
                Spacer(Modifier.height(8.dp))
                knopf()
            }
        }
    }
}
