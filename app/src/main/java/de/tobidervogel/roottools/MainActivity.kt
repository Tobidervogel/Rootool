package de.tobidervogel.roottools

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.mac.MacSeite
import de.tobidervogel.roottools.update.NotfallSeite
import de.tobidervogel.roottools.update.UpdateSeite
import de.tobidervogel.roottools.root.Root
import de.tobidervogel.roottools.sicherung.AppSicherungSeite
import de.tobidervogel.roottools.ui.Abzeichen
import de.tobidervogel.roottools.ui.Etikett
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Hinweis
import de.tobidervogel.roottools.ui.Karte
import de.tobidervogel.roottools.ui.Knopf
import de.tobidervogel.roottools.ui.RootToolsDesign
import de.tobidervogel.roottools.ui.Schrift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Bereich(val titel: String) { System("System"), Sicherung("Sicherung"), Netz("Netz") }

/** Alle Werkzeuge der App. Ein neues Werkzeug = ein Eintrag hier plus seine Seite in [App]. */
enum class Werkzeug(val titel: String, val text: String, val symbol: ImageVector, val bereich: Bereich) {
    Update("System-Update", "Updates installieren, ohne Root zu verlieren", Icons.Rounded.SystemUpdate, Bereich.System),
    Notfall("Notfall-Sicherung", "Boot-Abbilder und Funkdaten sichern", Icons.Rounded.Save, Bereich.Sicherung),
    AppSicherung("App-Sicherung", "Apps mit Daten sichern, neue APK einspielen", Icons.Rounded.Apps, Bereich.Sicherung),
    Mac("MAC-Adresse", "Eigene WLAN-MAC statt Androids Zufalls-MAC", Icons.Rounded.Wifi, Bereich.Netz),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { RootToolsDesign { App() } }
    }
}

@Composable
private fun App() {
    // Geoeffnete Seiten als Stapel ("Update,Notfall"), damit Zurueck von einer verlinkten Seite dorthin fuehrt,
    // woher man kam. Als Text, weil rememberSaveable den ueber eine Drehung retten kann.
    var stapel by rememberSaveable { mutableStateOf("") }
    val seiten = stapel.split(',').filter { it.isNotEmpty() }.mapNotNull { n -> Werkzeug.entries.find { it.name == n } }
    fun oeffnen(w: Werkzeug) { stapel = (seiten.map { it.name } + w.name).joinToString(",") }
    fun zurueck() { stapel = seiten.dropLast(1).joinToString(",") { it.name } }
    BackHandler(enabled = seiten.isNotEmpty()) { zurueck() }
    Box(Modifier.fillMaxSize().background(Farbe.grund)) {
        when (seiten.lastOrNull()) {
            Werkzeug.Update -> UpdateSeite(::zurueck, notfallOeffnen = { oeffnen(Werkzeug.Notfall) })
            Werkzeug.Notfall -> NotfallSeite(::zurueck)
            Werkzeug.AppSicherung -> AppSicherungSeite(::zurueck)
            Werkzeug.Mac -> MacSeite(::zurueck)
            null -> StartSeite(::oeffnen)
        }
    }
}

private enum class RootStand { Pruefe, Erteilt, Abgelehnt, Fehlt }

@Composable
private fun StartSeite(oeffnen: (Werkzeug) -> Unit) {
    var stand by remember { mutableStateOf(RootStand.Pruefe) }
    var versuch by remember { mutableIntStateOf(0) }
    LaunchedEffect(versuch) {
        stand = RootStand.Pruefe
        stand = withContext(Dispatchers.IO) {
            when {
                !Root.vorhanden() -> RootStand.Fehlt
                Root.erteilt() -> RootStand.Erteilt
                else -> RootStand.Abgelehnt
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Root Tools", style = Schrift.gross, color = Farbe.text)
        Spacer(Modifier.height(4.dp))
        Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", style = Schrift.text, color = Farbe.text2)
        Spacer(Modifier.height(16.dp))
        when (stand) {
            RootStand.Pruefe -> Abzeichen("Root wird geprüft …", Farbe.text3)
            RootStand.Erteilt -> Abzeichen("Root aktiv", Farbe.akzent)
            RootStand.Abgelehnt -> Hinweis(
                "Root abgelehnt",
                "Magisk hat Root Tools keinen Zugriff gegeben. In Magisk unter Superuser erlauben oder neu fragen.",
                Farbe.fehler,
            ) { Knopf("Neu fragen", { versuch++ }, haupt = false) }
            RootStand.Fehlt -> Hinweis(
                "Kein Root",
                "Auf diesem Handy gibt es kein su. Root Tools braucht Magisk.",
                Farbe.fehler,
            )
        }

        for (b in Bereich.entries) {
            Spacer(Modifier.height(28.dp))
            Etikett(b.titel)
            Spacer(Modifier.height(10.dp))
            for (w in Werkzeug.entries.filter { it.bereich == b }) {
                WerkzeugKarte(w) { oeffnen(w) }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Privat: Root Tools hat keine Internet-Berechtigung und schickt nichts nach draußen.",
            style = Schrift.klein, color = Farbe.text3,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WerkzeugKarte(w: Werkzeug, onClick: () -> Unit) {
    Karte(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Farbe.akzentDunkel),
                contentAlignment = Alignment.Center,
            ) { Icon(w.symbol, null, tint = Farbe.akzent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(w.titel, style = Schrift.karte, color = Farbe.text)
                Text(w.text, style = Schrift.klein, color = Farbe.text2)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Farbe.text3)
        }
    }
}
