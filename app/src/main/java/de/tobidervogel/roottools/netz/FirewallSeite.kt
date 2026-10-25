package de.tobidervogel.roottools.netz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.AppEintrag
import de.tobidervogel.roottools.ui.AppSymbol
import de.tobidervogel.roottools.ui.Chip
import de.tobidervogel.roottools.ui.Apps
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Androids eigene Apps mit System-UID (unter 10000) teilen sich die Kennung mit dem System: nie sperren. */
private const val ERSTE_APP_UID = 10000

class FirewallAktionen(
    val neuLaden: () -> Unit = {},
    val uebernehmen: (Map<String, Regel>) -> Unit = {},
    val aus: () -> Unit = {},
)

@Composable
fun FirewallSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEintrag>?>(null) }
    var stand by remember { mutableStateOf<FirewallStand?>(null) }
    var arbeitet by remember { mutableStateOf(false) }

    fun laden() = scope.launch {
        apps = withContext(Dispatchers.IO) { Apps.alle(ctx) }
        stand = withContext(Dispatchers.IO) { Firewall.zustand(ctx) }
    }
    LaunchedEffect(Unit) { laden() }

    fun arbeiten(aufgabe: () -> FirewallStand) = scope.launch {
        arbeitet = true
        stand = withContext(Dispatchers.IO) { aufgabe() }
        arbeitet = false
    }

    FirewallInhalt(
        apps, stand, arbeitet, zurueck,
        FirewallAktionen(
            neuLaden = { laden() },
            uebernehmen = { r -> arbeiten { Firewall.setzen(ctx, r.values) } },
            aus = { arbeiten { Firewall.aus(ctx) } },
        ),
    )
}

@Composable
fun FirewallInhalt(
    apps: List<AppEintrag>?,
    stand: FirewallStand?,
    arbeitet: Boolean,
    zurueck: () -> Unit,
    a: FirewallAktionen,
    startEntwurf: Map<String, Regel>? = null,
    startOffen: String? = null,
) {
    // Entwurf: was der Nutzer einstellt, bevor er uebernimmt. Startet beim Stand vom Handy.
    var entwurf by remember(stand) { mutableStateOf(startEntwurf ?: stand?.regeln.orEmpty()) }
    var suche by remember { mutableStateOf("") }
    var offen by remember { mutableStateOf(startOffen) }
    val geaendert = stand != null && entwurf != stand.regeln
    val zeitenOk = entwurf.values.all { it.immer || (Regel.zeitOk(it.von) && Regel.zeitOk(it.bis) && it.von != it.bis) }

    Seite {
        Kopfzeile("Firewall", zurueck, a.neuLaden, aktiv = !arbeitet)
        stand?.fehler?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        Karte {
            Text(
                "Sperrt einzelnen Apps das Internet, über WLAN und mobile Daten. Nutzt Androids eingebaute Sperre: " +
                    "kein VPN, läuft also neben Tailscale. Nach einem Neustart stellt Magisk die Sperren wieder her.",
                style = Schrift.text, color = Farbe.text2,
            )
            if (stand != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${stand.regeln.size} Regeln · gerade gesperrt: ${stand.gesperrt.size}" +
                        if (stand.waechter) " · Zeitplan läuft" else "",
                    style = Schrift.klein, color = if (stand.gesperrt.isEmpty()) Farbe.text3 else Farbe.akzent,
                )
            }
        }
        if (geaendert) {
            Luecke()
            Karte(modifier = Modifier.border(1.dp, Farbe.akzent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) {
                Text("Noch nicht übernommen", style = Schrift.karte, color = Farbe.text)
                if (!zeitenOk) Text("Eine Uhrzeit ist ungültig (HH:MM, von ≠ bis).", style = Schrift.klein, color = Farbe.fehler)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Knopf("Übernehmen", { a.uebernehmen(entwurf) }, aktiv = zeitenOk, arbeitet = arbeitet, symbol = Icons.Rounded.Check)
                    Knopf("Verwerfen", { entwurf = stand?.regeln.orEmpty() }, haupt = false, aktiv = !arbeitet)
                }
            }
        }
        Luecke()
        Suchfeld(suche, { suche = it }, "App suchen (auch Systemapps)")
        Luecke()
        if (apps == null || stand == null) {
            Text("Lade …", style = Schrift.text, color = Farbe.text2)
            return@Seite
        }
        if (!entwurf.containsKey("com.oplus.ota") && apps.any { it.paket == "com.oplus.ota" } && suche.isEmpty()) {
            Text(
                "Tipp: „com.oplus.ota“ (Systemupdate) sperren, dann lädt OxygenOS keine automatischen Updates. " +
                    "Die lokale Installation geht weiter.",
                style = Schrift.klein, color = Farbe.text3,
            )
            Luecke()
        }
        val q = suche.trim().lowercase()
        val sichtbar = apps
            .filter { it.uid >= ERSTE_APP_UID }
            .filter { app ->
                if (q.isEmpty()) !app.system || entwurf.containsKey(app.paket)
                else app.name.lowercase().contains(q) || app.paket.lowercase().contains(q)
            }
            .sortedWith(compareBy({ !entwurf.containsKey(it.paket) }, { it.name.lowercase() }))
        for (app in sichtbar) {
            FirewallZeile(
                app, entwurf[app.paket], app.paket in stand.gesperrt, offen == app.paket, !arbeitet,
                umschalten = { offen = if (offen == app.paket) null else app.paket },
                aendern = { r ->
                    // Neu gesperrt: gleich aufklappen, damit man den Zeitplan sieht.
                    if (r != null && !entwurf.containsKey(app.paket)) offen = app.paket
                    entwurf = if (r == null) entwurf - app.paket else entwurf + (app.paket to r)
                },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (stand.regeln.isNotEmpty()) {
            Luecke()
            SicherKnopf(
                "Firewall ausschalten", "Alle Sperren aufheben und die Regeln löschen?", a.aus,
                aktiv = !arbeitet, symbol = Icons.Rounded.Block,
            )
        }
        if (stand.protokoll.isNotEmpty()) {
            Luecke()
            Karte {
                Etikett("Protokoll")
                Spacer(Modifier.height(8.dp))
                for (z in stand.protokoll) Text(z, style = Schrift.log, color = Farbe.text2)
            }
        }
    }
}

@Composable
private fun FirewallZeile(
    app: AppEintrag,
    regel: Regel?,
    jetztGesperrt: Boolean,
    ausgeklappt: Boolean,
    aktiv: Boolean,
    umschalten: () -> Unit,
    aendern: (Regel?) -> Unit,
) {
    Karte(innen = PaddingValues(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = regel != null, onClick = umschalten),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSymbol(app)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = Schrift.karte, color = Farbe.text)
                Text(
                    (regel?.text() ?: "Frei") + if (jetztGesperrt) " · jetzt gesperrt" else "",
                    style = Schrift.klein,
                    color = when {
                        jetztGesperrt -> Farbe.akzent
                        regel != null -> Farbe.text2
                        else -> Farbe.text3
                    },
                )
            }
            Switch(
                checked = regel != null,
                onCheckedChange = { an -> aendern(if (an) Regel(app.paket, true) else null) },
                enabled = aktiv,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Farbe.grund, checkedTrackColor = Farbe.akzent,
                    uncheckedThumbColor = Farbe.text2, uncheckedTrackColor = Farbe.karte2, uncheckedBorderColor = Farbe.linie,
                ),
            )
        }
        if (regel == null || !ausgeklappt) return@Karte
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Immer", regel.immer) { aendern(regel.copy(immer = true)) }
            Chip("Nach Zeitplan", !regel.immer) { aendern(regel.copy(immer = false)) }
        }
        if (!regel.immer) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gesperrt von", style = Schrift.text, color = Farbe.text2)
                Spacer(Modifier.width(10.dp))
                Uhrzeit(regel.von) { aendern(regel.copy(von = it)) }
                Spacer(Modifier.width(10.dp))
                Text("bis", style = Schrift.text, color = Farbe.text2)
                Spacer(Modifier.width(10.dp))
                Uhrzeit(regel.bis) { aendern(regel.copy(bis = it)) }
            }
        }
    }
}

/** Uhrzeit HH:MM; beim Tippen nur Ziffern, der Doppelpunkt kommt von selbst. */
@Composable
private fun Uhrzeit(wert: String, aendern: (String) -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Farbe.karte2).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = wert,
            onValueChange = { neu ->
                val z = neu.filter { it.isDigit() }.take(4)
                aendern(if (z.length > 2) z.take(2) + ":" + z.drop(2) else z)
            },
            singleLine = true,
            textStyle = Schrift.macKlein.copy(color = if (Regel.zeitOk(wert)) Farbe.text else Farbe.fehler),
            cursorBrush = SolidColor(Farbe.akzent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(52.dp),
        )
    }
}
