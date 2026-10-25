package de.tobidervogel.roottools.mac

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.Abzeichen
import de.tobidervogel.roottools.ui.Etikett
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Hinweis
import de.tobidervogel.roottools.ui.Karte
import de.tobidervogel.roottools.ui.Knopf
import de.tobidervogel.roottools.ui.Kopfzeile
import de.tobidervogel.roottools.ui.SchalterZeile
import de.tobidervogel.roottools.ui.Schrift
import de.tobidervogel.roottools.ui.WertZeile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Arbeit { Laden, Setzen, Pruefen, Aus }

private data class Meldung(val text: String, val farbe: Color)

private fun feld(mac: String) = Mac.anzeigen(mac).let { TextFieldValue(it, TextRange(it.length)) }

@Composable
fun MacSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var zustand by remember { mutableStateOf<MacZustand?>(null) }
    var arbeit by remember { mutableStateOf<Arbeit?>(Arbeit.Laden) }
    var meldung by remember { mutableStateOf<Meldung?>(null) }
    var eingabe by remember { mutableStateOf(TextFieldValue("")) }
    var beimStart by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val z = withContext(Dispatchers.IO) { MacSteuerung.zustand(ctx) }
        zustand = z
        z.ziel?.let { eingabe = feld(it); beimStart = z.beimStart }
        arbeit = null
    }

    fun neuLaden() = scope.launch {
        arbeit = Arbeit.Laden
        zustand = withContext(Dispatchers.IO) { MacSteuerung.zustand(ctx) }
        arbeit = null
    }

    /** [pruefen]: danach abwarten, ob die MAC das Neuverbinden uebersteht. Nicht noetig, wenn sie schon gilt. */
    fun setzen(mac: String, start: Boolean, pruefen: Boolean = true) = scope.launch {
        arbeit = Arbeit.Setzen
        meldung = null
        val z = withContext(Dispatchers.IO) { MacSteuerung.setzen(ctx, mac, start) }
        zustand = z
        if (z.fehler != null || !pruefen) {
            meldung = z.fehler?.let { Meldung(it, Farbe.fehler) }
            arbeit = null
            return@launch
        }
        // Das WLAN trennt beim Setzen kurz. Erst nach dem Neuverbinden zeigt sich, ob Android die MAC laesst.
        arbeit = Arbeit.Pruefen
        meldung = Meldung("Gesetzt. Prüfe, ob sie nach dem Neuverbinden hält …", Farbe.text2)
        delay(10_000)
        val z2 = withContext(Dispatchers.IO) { MacSteuerung.zustand(ctx) }
        zustand = z2
        meldung = when {
            z2.eigeneAktiv && z2.verbundenMit != null ->
                Meldung("Hält: wieder verbunden mit „${z2.verbundenMit}“, mit deiner MAC.", Farbe.akzent)
            z2.eigeneAktiv -> Meldung("Deine MAC ist aktiv. Das WLAN ist gerade nicht verbunden.", Farbe.akzent)
            z2.aktuell == null -> Meldung("WLAN ist aus. Der Wächter setzt die MAC, sobald es angeht.", Farbe.text2)
            else -> Meldung("Android hat die MAC wieder geändert. Siehe Protokoll unten.", Farbe.fehler)
        }
        arbeit = null
    }

    fun aus() = scope.launch {
        arbeit = Arbeit.Aus
        meldung = null
        val z = withContext(Dispatchers.IO) { MacSteuerung.aus(ctx) }
        zustand = z
        meldung = z.fehler?.let { Meldung(it, Farbe.fehler) }
            ?: Meldung("Android verwaltet die MAC wieder. Das WLAN wurde dafür neu gestartet.", Farbe.text2)
        arbeit = null
    }

    val z = zustand
    val beschaeftigt = arbeit != null
    val neueMac = Mac.normalisieren(eingabe.text)
    val kannSetzen = neueMac != null && z?.root == true && !beschaeftigt

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Kopfzeile("MAC-Adresse", zurueck, { neuLaden() }, aktiv = !beschaeftigt)

        // ------------------------------------------------ Jetzt
        Karte {
            Etikett("WLAN-MAC jetzt")
            Spacer(Modifier.height(8.dp))
            Text(z?.aktuell?.let(Mac::anzeigen) ?: "––:––:––:––:––:––", style = Schrift.mac, color = Farbe.text)
            Spacer(Modifier.height(12.dp))
            when {
                z == null -> Abzeichen("Lade …", Farbe.text3)
                z.ziel == null -> Abzeichen("Android verwaltet die MAC", Farbe.text3)
                z.eigeneAktiv -> Abzeichen("Deine MAC ist aktiv", Farbe.akzent)
                !z.waechter -> Abzeichen("Wächter gestoppt", Farbe.fehler)
                z.aktuell == null -> Abzeichen("Wartet aufs WLAN", Farbe.warnung)
                else -> Abzeichen("Weicht ab, wird gesetzt", Farbe.warnung)
            }
            val verbindung = when {
                z == null -> null
                z.wlanAn == false -> "WLAN ist aus"
                z.verbundenMit != null -> "Verbunden mit „${z.verbundenMit}“"
                z.wlanAn == true -> "WLAN an, nicht verbunden"
                else -> null
            }
            if (verbindung != null) {
                Spacer(Modifier.height(10.dp))
                Text(verbindung, style = Schrift.klein, color = Farbe.text2)
            }
        }

        if (z?.fehler != null && z.root.not()) {
            Spacer(Modifier.height(12.dp))
            Hinweis("Kein Root", z.fehler, Farbe.fehler) {
                Knopf("Neu versuchen", { neuLaden() }, haupt = false, aktiv = !beschaeftigt)
            }
        } else if (z != null && !z.schalter) {
            Spacer(Modifier.height(12.dp))
            Hinweis(
                "Android-Schalter fehlt",
                "Dieses Android kennt „cmd wifi force-overlay-config-value“ nicht. Ohne ihn würfelt Android bei der " +
                    "nächsten Verbindung wieder eine MAC. Der Wächter versucht es dann 5-mal und gibt auf.",
            )
        }

        // ------------------------------------------------ Eigene MAC
        Spacer(Modifier.height(12.dp))
        Karte {
            Etikett("Eigene MAC")
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Farbe.karte2)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (eingabe.text.isEmpty()) Text("AA:BB:CC:DD:EE:FF", style = Schrift.mac, color = Farbe.text3)
                BasicTextField(
                    value = eingabe,
                    onValueChange = { neu ->
                        val text = Mac.beimTippen(neu.text)
                        eingabe = TextFieldValue(text, TextRange(text.length))
                    },
                    textStyle = Schrift.mac.copy(color = Farbe.text),
                    cursorBrush = SolidColor(Farbe.akzent),
                    singleLine = true,
                    enabled = !beschaeftigt,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (kannSetzen) neueMac?.let { setzen(it, beimStart) } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            val ziffern = eingabe.text.count { it != ':' }
            val fehler = Mac.fehler(eingabe.text)
            when {
                eingabe.text.isEmpty() -> Text("Eintippen oder würfeln.", style = Schrift.klein, color = Farbe.text3)
                ziffern < 12 -> Text("Noch ${12 - ziffern} Zeichen", style = Schrift.klein, color = Farbe.text3)
                fehler != null -> Text(fehler, style = Schrift.klein, color = Farbe.fehler)
                Mac.istLokal(eingabe.text) ->
                    Text("Lokal vergeben, so sehen auch Androids Zufalls-MACs aus.", style = Schrift.klein, color = Farbe.text2)
                else -> Text(
                    "Herstelleradresse (Kennung ${eingabe.text.take(8)})",
                    style = Schrift.klein, color = Farbe.text2,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Knopf(
                    "Würfeln", { eingabe = feld(Mac.zufaellig()) },
                    haupt = false, aktiv = !beschaeftigt, symbol = Icons.Rounded.Casino,
                )
                z?.original?.let { original ->
                    Knopf(
                        "Geräte-MAC", { eingabe = feld(original) },
                        haupt = false, aktiv = !beschaeftigt, symbol = Icons.Rounded.PhoneAndroid,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Knopf(
                when (arbeit) {
                    Arbeit.Setzen -> "Setze …"
                    Arbeit.Pruefen -> "Prüfe …"
                    else -> if (z?.ziel != null && z.ziel == neueMac) "Erneut setzen" else "Übernehmen"
                },
                { neueMac?.let { setzen(it, beimStart) } },
                modifier = Modifier.fillMaxWidth(),
                aktiv = kannSetzen || arbeit == Arbeit.Setzen || arbeit == Arbeit.Pruefen,
                arbeitet = arbeit == Arbeit.Setzen || arbeit == Arbeit.Pruefen,
                symbol = Icons.Rounded.Check,
            )
            meldung?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.text, style = Schrift.text, color = it.farbe)
            }
            Spacer(Modifier.height(12.dp))
            SchalterZeile(
                "Nach Neustart behalten",
                "Magisk startet den Wächter beim Hochfahren.",
                beimStart,
                { neu ->
                    beimStart = neu
                    // Ist schon eine eigene MAC aktiv, gilt der Schalter sofort. Dieselbe MAC trennt das WLAN nicht.
                    z?.ziel?.let { setzen(it, neu, pruefen = false) }
                },
                aktiv = !beschaeftigt,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Gilt für alle WLANs. Beim Setzen trennt das WLAN kurz und verbindet sich mit der neuen MAC neu.",
                style = Schrift.klein, color = Farbe.text3,
            )
        }

        // ------------------------------------------------ Zurueck zu Android
        if (z?.ziel != null) {
            Spacer(Modifier.height(12.dp))
            Karte {
                Etikett("Zurück zu Android")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android würfelt dann wieder für jedes WLAN eine eigene MAC. Das WLAN startet dafür kurz neu.",
                    style = Schrift.text, color = Farbe.text2,
                )
                Spacer(Modifier.height(14.dp))
                Knopf(
                    if (arbeit == Arbeit.Aus) "Setze zurück …" else "Zurücksetzen", { aus() },
                    modifier = Modifier.fillMaxWidth(), haupt = false, aktiv = !beschaeftigt,
                    arbeitet = arbeit == Arbeit.Aus, symbol = Icons.Rounded.Restore,
                )
            }
        }

        // ------------------------------------------------ Details
        if (z != null && z.root) {
            Spacer(Modifier.height(12.dp))
            Karte {
                Etikett("Details")
                Spacer(Modifier.height(6.dp))
                WertZeile("Geräte-MAC", z.original?.let(Mac::anzeigen) ?: "–", mono = z.original != null)
                WertZeile(
                    "Android würfelt",
                    when (z.androidWuerfelt) {
                        true -> "ja"
                        false -> "nein, abgeschaltet"
                        null -> "unbekannt"
                    },
                )
                WertZeile("Wächter", if (z.waechter) "läuft" else "aus", wertFarbe = if (z.waechter) Farbe.akzent else Farbe.text)
                WertZeile("Nach Neustart", if (z.beimStart) "an" else "aus")
            }
        }

        if (z != null && z.protokoll.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Karte {
                Etikett("Protokoll")
                Spacer(Modifier.height(8.dp))
                for (zeile in z.protokoll) Text(zeile, style = Schrift.log, color = Farbe.text2)
            }
        }

        Spacer(Modifier.height(12.dp))
        Karte(farbe = Farbe.grund, innen = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            Etikett("So funktioniert es")
            Spacer(Modifier.height(6.dp))
            Text(
                "Android würfelt sonst bei jeder Verbindung eine MAC pro WLAN. Root Tools schaltet das bis zum " +
                    "nächsten Neustart ab und setzt deine MAC. Ein kleiner Wächter (Root, im Hintergrund) setzt sie " +
                    "wieder, wenn das WLAN neu startet. Er gibt auf, wenn sie 5-mal in 2 Minuten überschrieben wird, " +
                    "damit das WLAN nicht dauernd trennt.",
                style = Schrift.klein, color = Farbe.text3,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
