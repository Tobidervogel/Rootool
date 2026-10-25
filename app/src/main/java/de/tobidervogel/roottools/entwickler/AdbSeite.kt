package de.tobidervogel.roottools.entwickler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.Abzeichen
import de.tobidervogel.roottools.ui.Chip
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
import de.tobidervogel.roottools.ui.WertZeile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdbAktionen(
    val neuLaden: () -> Unit = {},
    val an: (String) -> Unit = {},
    val aus: () -> Unit = {},
)

@Composable
fun AdbSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf<AdbStand?>(null) }
    var arbeitet by remember { mutableStateOf(false) }

    fun arbeiten(aufgabe: () -> AdbStand) = scope.launch {
        arbeitet = true
        stand = withContext(Dispatchers.IO) { aufgabe() }
        arbeitet = false
    }
    LaunchedEffect(Unit) { arbeiten { Entwickler.adbZustand(ctx) } }

    AdbInhalt(
        stand, arbeitet, zurueck,
        AdbAktionen(
            neuLaden = { arbeiten { Entwickler.adbZustand(ctx) } },
            an = { heim -> arbeiten { Entwickler.adbAn(ctx, heim) } },
            aus = { arbeiten { Entwickler.adbAus(ctx) } },
        ),
    )
}

@Composable
fun AdbInhalt(stand: AdbStand?, arbeitet: Boolean, zurueck: () -> Unit, a: AdbAktionen) {
    // Vorschlag fuers Heimnetz: was gespeichert ist, sonst das Netz des WLANs von gerade.
    var heim by remember(stand) { mutableStateOf(stand?.heim ?: netzVon(stand?.wlan) ?: "192.168.0.0/24") }
    var ohneHeim by remember(stand) { mutableStateOf(stand?.heim == "keins") }
    val heimOk = ohneHeim || netzVon(heim) == heim.trim()

    Seite {
        Kopfzeile("ADB übers Netz", zurueck, a.neuLaden, aktiv = !arbeitet)
        stand?.fehler?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        Karte {
            when {
                stand == null -> Abzeichen("Lade …", Farbe.text3)
                stand.an -> Abzeichen("An · Port 5555", Farbe.akzent)
                stand.portGesetzt -> Abzeichen("Port gesetzt, adbd lauscht nicht", Farbe.warnung)
                else -> Abzeichen("Aus", Farbe.text3)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Öffnet ADB auf Port 5555, aber nur für Tailscale, dein Heimnetz und das Handy selbst. Dann kann eine " +
                    "Claude-Cloud-Session oder dein PC Apps auf dem Handy installieren und testen. Bleibt nach einem " +
                    "Neustart an.",
                style = Schrift.text, color = Farbe.text2,
            )
            if (stand?.an == true) {
                Spacer(Modifier.height(14.dp))
                Etikett("Verbinden")
                stand.tailscale?.let { Befehl("Tailscale", "adb connect $it:5555") }
                    ?: Text("Tailscale ist gerade nicht verbunden.", style = Schrift.klein, color = Farbe.warnung)
                stand.wlan?.let { Befehl("WLAN", "adb connect ${it.substringBefore('/')}:5555") }
            }
        }
        Luecke()
        Karte {
            Etikett("Wer darf")
            Spacer(Modifier.height(6.dp))
            WertZeile("Tailscale", "100.64.0.0/10", mono = true)
            WertZeile("Dieses Handy", "127.0.0.1", mono = true)
            Spacer(Modifier.height(8.dp))
            Text("Heimnetz", style = Schrift.text, color = Farbe.text2)
            Spacer(Modifier.height(6.dp))
            if (!ohneHeim) {
                Suchfeld(heim, { heim = it }, "192.168.0.0/24", Farbe.karte2)
                if (!heimOk) {
                    Text(
                        "Form: Netzadresse/Größe, z. B. ${netzVon(heim) ?: "192.168.0.0/24"}",
                        style = Schrift.klein, color = Farbe.fehler,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                netzVon(stand?.wlan)?.let { jetzt ->
                    if (!ohneHeim && jetzt != heim) Chip("Dieses WLAN: $jetzt") { heim = jetzt }
                }
                Chip(if (ohneHeim) "Heimnetz erlauben" else "Kein Heimnetz") { ohneHeim = !ohneHeim }
            }
            Spacer(Modifier.height(10.dp))
            Text("Alle anderen Adressen werden verworfen (iptables).", style = Schrift.klein, color = Farbe.text3)
        }
        Luecke()
        Knopf(
            if (stand?.an == true) "Mit diesen Regeln neu einschalten" else "Einschalten",
            { a.an(if (ohneHeim) "keins" else heim.trim()) },
            modifier = Modifier.fillMaxWidth(), aktiv = heimOk && stand?.root == true, arbeitet = arbeitet,
            symbol = Icons.Rounded.PowerSettingsNew,
        )
        if (stand?.portGesetzt == true || stand?.schutz == true) {
            Spacer(Modifier.height(10.dp))
            SicherKnopf("Ausschalten", "ADB übers Netz schließen? Verbindungen brechen ab.", a.aus, aktiv = !arbeitet)
        }
        Luecke()
        Text(
            "Ein Rechner braucht beim ersten Verbinden deine Freigabe am Handy (ADB-Dialog). Wer im erlaubten Netz ist " +
                "und freigegeben wurde, hat volle Kontrolle über das Handy. USB-Debugging wird dafür eingeschaltet.",
            style = Schrift.klein, color = Farbe.text3,
        )
    }
}

@Composable
private fun Befehl(name: String, befehl: String) {
    val zwischenablage = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Farbe.karte2)
            .clickable { zwischenablage.setText(AnnotatedString(befehl)) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = Schrift.klein, color = Farbe.text3)
            Text(befehl, style = Schrift.macKlein, color = Farbe.text)
        }
        Icon(Icons.Rounded.ContentCopy, "Kopieren", tint = Farbe.text3, modifier = Modifier.size(18.dp))
    }
}
