package de.tobidervogel.roottools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tobidervogel.roottools.R

/*
 * Root Tools – Design
 *
 * Gleiche Schrift und Formen wie Live Up (Outfit, grosse Rundungen, viel Luft), aber dunkel: ein Werkzeugkasten,
 * kein Alltags-Tracker. Eine Akzentfarbe (Minze) fuer "aktiv/gut", sonst Graustufen. Alles bleibt auch mit
 * eingeschalteten Graustufen lesbar, weil Bedeutung nie nur an der Farbe haengt (immer auch Text).
 * MAC-Adressen und Protokolle stehen in Monospace, damit die Paare untereinander stehen.
 */

@OptIn(ExperimentalTextApi::class)
private fun outfit(w: Int) = Font(
    R.font.outfit_variable,
    FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Outfit, OFL-Lizenz, siehe lizenzen/Outfit-OFL.txt. */
val Outfit = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700))

object Schrift {
    val gross = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp)
    val titel = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp)
    val karte = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val text = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val klein = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)
    val etikett = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.8.sp)
    val knopf = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val mac = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp)
    val macKlein = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 20.sp)
    val log = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
}

object Farbe {
    val grund = Color(0xFF0E0F11)
    val karte = Color(0xFF18191C)
    val karte2 = Color(0xFF222327)
    val linie = Color(0xFF2E2F34)
    val text = Color(0xFFF2F1EE)
    val text2 = Color(0xFFA4A3AA)
    val text3 = Color(0xFF6D6C73)
    val akzent = Color(0xFF7FE0B4)
    val akzentDunkel = Color(0xFF0E2A1F)
    val warnung = Color(0xFFF2B65E)
    val fehler = Color(0xFFFF7A70)
}

@Composable
fun RootToolsDesign(inhalt: @Composable () -> Unit) {
    val schema = darkColorScheme(
        primary = Farbe.akzent, onPrimary = Farbe.grund,
        background = Farbe.grund, onBackground = Farbe.text,
        surface = Farbe.karte, onSurface = Farbe.text,
        surfaceVariant = Farbe.karte2, onSurfaceVariant = Farbe.text2,
        outline = Farbe.linie, error = Farbe.fehler,
    )
    val typo = Typography(
        headlineMedium = Schrift.titel, titleMedium = Schrift.karte, bodyLarge = Schrift.text,
        bodyMedium = Schrift.text, labelLarge = Schrift.knopf, labelMedium = Schrift.klein,
    )
    MaterialTheme(colorScheme = schema, typography = typo, content = inhalt)
}

@Composable
fun Karte(
    modifier: Modifier = Modifier,
    farbe: Color = Farbe.karte,
    innen: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(farbe)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(innen),
        content = inhalt,
    )
}

/** Kleine Grossbuchstaben-Ueberschrift ueber einem Wert oder Abschnitt. */
@Composable
fun Etikett(text: String, modifier: Modifier = Modifier, farbe: Color = Farbe.text3) {
    Text(text.uppercase(), style = Schrift.etikett, color = farbe, modifier = modifier)
}

@Composable
fun RundKnopf(symbol: ImageVector, beschreibung: String?, onClick: () -> Unit, aktiv: Boolean = true) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Farbe.karte).clickable(enabled = aktiv, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(symbol, beschreibung, tint = if (aktiv) Farbe.text else Farbe.text3, modifier = Modifier.size(22.dp)) }
}

/** Pillen-Knopf. [haupt] = gefuellt in Minze, sonst nur umrandet. [arbeitet] zeigt statt des Symbols einen Kreisel. */
@Composable
fun Knopf(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haupt: Boolean = true,
    aktiv: Boolean = true,
    arbeitet: Boolean = false,
    symbol: ImageVector? = null,
    textFarbe: Color? = null,
) {
    val form = RoundedCornerShape(26.dp)
    val vorne = when {
        !aktiv -> Farbe.text3
        !haupt -> textFarbe ?: Farbe.text
        else -> Farbe.grund
    }
    Row(
        modifier
            .height(52.dp)
            .clip(form)
            .then(
                if (haupt) Modifier.background(if (aktiv) Farbe.akzent else Farbe.karte2)
                else Modifier.border(1.dp, Farbe.linie, form),
            )
            .clickable(enabled = aktiv && !arbeitet, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            arbeitet -> CircularProgressIndicator(Modifier.size(18.dp), color = vorne, strokeWidth = 2.dp)
            symbol != null -> Icon(symbol, null, tint = vorne, modifier = Modifier.size(20.dp))
        }
        if (arbeitet || symbol != null) Spacer(Modifier.width(8.dp))
        Text(text, style = Schrift.knopf, color = vorne)
    }
}

/** Kleines Abzeichen mit Punkt: Zustand auf einen Blick, Bedeutung steht im Text. */
@Composable
fun Abzeichen(text: String, farbe: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Farbe.karte2).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
        Spacer(Modifier.width(8.dp))
        Text(text, style = Schrift.klein, color = Farbe.text)
    }
}

/** Zeile "Name ......... Wert" in Detail-Karten. */
@Composable
fun WertZeile(name: String, wert: String, mono: Boolean = false, wertFarbe: Color = Farbe.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Schrift.text, color = Farbe.text2, modifier = Modifier.weight(1f))
        Text(wert, style = if (mono) Schrift.macKlein else Schrift.text, color = wertFarbe)
    }
}

@Composable
fun SchalterZeile(titel: String, text: String, an: Boolean, aendern: (Boolean) -> Unit, aktiv: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = aktiv) { aendern(!an) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = Schrift.karte, color = Farbe.text)
            Text(text, style = Schrift.klein, color = Farbe.text2)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = an, onCheckedChange = aendern, enabled = aktiv,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Farbe.grund, checkedTrackColor = Farbe.akzent,
                uncheckedThumbColor = Farbe.text2, uncheckedTrackColor = Farbe.karte2, uncheckedBorderColor = Farbe.linie,
            ),
        )
    }
}

/** Hinweis-Kasten: gelb = Achtung, rot = Fehler. */
@Composable
fun Hinweis(titel: String, text: String, farbe: Color = Farbe.warnung, inhalt: @Composable RowScope.() -> Unit = {}) {
    Karte(farbe = Farbe.karte, modifier = Modifier.border(1.dp, farbe.copy(alpha = 0.45f), RoundedCornerShape(24.dp))) {
        Text(titel, style = Schrift.karte, color = farbe)
        Spacer(Modifier.height(4.dp))
        Text(text, style = Schrift.text, color = Farbe.text2)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), content = inhalt)
    }
}

/** Rahmen jeder Werkzeugseite: scrollt, haelt Abstand zu Status- und Navigationsleiste. */
@Composable
fun Seite(inhalt: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        inhalt()
        Spacer(Modifier.height(24.dp))
    }
}

/** Zurueck, Titel, Neu laden. */
@Composable
fun Kopfzeile(titel: String, zurueck: () -> Unit, neuLaden: (() -> Unit)? = null, aktiv: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RundKnopf(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", zurueck)
        Spacer(Modifier.width(14.dp))
        Text(titel, style = Schrift.titel, color = Farbe.text, modifier = Modifier.weight(1f))
        if (neuLaden != null) RundKnopf(Icons.Rounded.Refresh, "Neu laden", neuLaden, aktiv = aktiv)
    }
    Spacer(Modifier.height(20.dp))
}

/** Abstand zwischen Karten. */
@Composable
fun Luecke() = Spacer(Modifier.height(12.dp))

/** Einzeiliges Suchfeld im Stil der Karten. */
@Composable
fun Suchfeld(text: String, aendern: (String) -> Unit, hinweis: String, grund: Color = Farbe.karte) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(grund).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (text.isEmpty()) Text(hinweis, style = Schrift.text, color = Farbe.text3)
        BasicTextField(
            value = text,
            onValueChange = aendern,
            singleLine = true,
            textStyle = Schrift.text.copy(color = Farbe.text),
            cursorBrush = SolidColor(Farbe.akzent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Zweistufiger Knopf fuer Gefaehrliches: erst tippen, dann "Wirklich?" bestaetigen. */
@Composable
fun SicherKnopf(text: String, frage: String, onClick: () -> Unit, aktiv: Boolean = true, symbol: ImageVector? = null) {
    var fragt by remember { mutableStateOf(false) }
    if (!fragt) {
        Knopf(text, { fragt = true }, haupt = false, aktiv = aktiv, symbol = symbol)
    } else {
        Column {
            Text(frage, style = Schrift.klein, color = Farbe.warnung)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Knopf("Ja", { fragt = false; onClick() }, aktiv = aktiv)
                Knopf("Abbrechen", { fragt = false }, haupt = false)
            }
        }
    }
}
