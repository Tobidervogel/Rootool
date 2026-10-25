package de.tobidervogel.roottools.sicherung

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.root.Werte
import de.tobidervogel.roottools.ui.AppEintrag
import de.tobidervogel.roottools.ui.AppSymbol
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
import de.tobidervogel.roottools.update.groesseText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppMeldung(val text: String, val farbe: Color)

/** Was die Seite ausloest; getrennt vom Zustand, damit sich [AppSicherungInhalt] ohne Handy darstellen laesst. */
class AppSicherungAktionen(
    val neuLaden: () -> Unit = {},
    val sichern: (String) -> Unit = {},
    val zurueck: (AppBackup, Boolean) -> Unit = { _, _ -> },
    val loeschen: (AppBackup) -> Unit = {},
    val tauschen: (String) -> Unit = {},
)

@Composable
fun AppSicherungSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEintrag>?>(null) }
    var backups by remember { mutableStateOf<Map<String, List<AppBackup>>>(emptyMap()) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var arbeit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var meldung by remember { mutableStateOf<AppMeldung?>(null) }
    var tauschPaket by remember { mutableStateOf<String?>(null) }

    fun laden(neu: Boolean = false) = scope.launch {
        apps = withContext(Dispatchers.IO) { Apps.alle(ctx, neu) }
        val w = withContext(Dispatchers.IO) { AppSicherung.liste(ctx) }
        backups = appBackups(w)
        fehler = w.fehler
    }
    LaunchedEffect(Unit) { laden() }

    /** Fuehrt eine Root-Arbeit fuer [paket] aus, zeigt ihre Schritte und danach das Ergebnis. */
    fun arbeiten(paket: String, erfolg: String, aufgabe: ((String) -> Unit) -> Werte) = scope.launch {
        arbeit = paket to "Starte …"
        meldung = null
        val w = withContext(Dispatchers.IO) { aufgabe { arbeit = paket to it } }
        meldung = w.fehler?.let { AppMeldung(it, Farbe.fehler) } ?: AppMeldung(erfolg, Farbe.akzent)
        arbeit = null
        laden(neu = true)
    }

    val apkWahl = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val p = tauschPaket ?: return@rememberLauncherForActivityResult
        tauschPaket = null
        if (uri != null) {
            arbeiten(p, "Neue APK ist drin, die Daten sind übernommen.") { s -> AppSicherung.tauschen(ctx, p, uri, s) }
        }
    }

    AppSicherungInhalt(
        apps, backups, fehler, arbeit, meldung, zurueck,
        AppSicherungAktionen(
            neuLaden = { laden(neu = true) },
            sichern = { p -> arbeiten(p, "Gesichert.") { s -> AppSicherung.sichern(ctx, p, s) } },
            zurueck = { b, mitApp ->
                arbeiten(b.paket, "Zurückgespielt.") { s -> AppSicherung.zurueck(ctx, b.paket, b.ordner, mitApp, s) }
            },
            loeschen = { b -> arbeiten(b.paket, "Sicherung gelöscht.") { _ -> AppSicherung.loeschen(ctx, b.paket, b.ordner) } },
            tauschen = { p ->
                tauschPaket = p
                apkWahl.launch("application/vnd.android.package-archive")
            },
        ),
    )
}

@Composable
fun AppSicherungInhalt(
    apps: List<AppEintrag>?,
    backups: Map<String, List<AppBackup>>,
    fehler: String?,
    arbeit: Pair<String, String>?,
    meldung: AppMeldung?,
    zurueck: () -> Unit,
    a: AppSicherungAktionen,
    startSuche: String = "",
    startOffen: String? = null,
) {
    var suche by remember { mutableStateOf(startSuche) }
    var offen by remember { mutableStateOf(startOffen) }
    Seite {
        Kopfzeile("App-Sicherung", zurueck, a.neuLaden, aktiv = arbeit == null)
        fehler?.let {
            Hinweis("Fehler", it, Farbe.fehler)
            Luecke()
        }
        Karte {
            Text(
                "Sichert eine App mit allen Daten nach Interner Speicher/RootTools/Apps. Für anders signierte Builds " +
                    "(Cloud oder PC): „Neue APK einspielen“ sichert, deinstalliert, installiert die neue APK und " +
                    "spielt die Daten zurück.",
                style = Schrift.text, color = Farbe.text2,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Was eine App mit dem Android-Schlüsselspeicher verschlüsselt (z. B. Inhalte hinter dem Fingerabdruck), " +
                    "ist nach einer Neuinstallation nicht mehr lesbar.",
                style = Schrift.klein, color = Farbe.warnung,
            )
        }
        Luecke()
        Suchfeld(suche, { suche = it }, "App suchen (auch Systemapps)")
        Luecke()
        if (apps == null) {
            Text("Lade Apps …", style = Schrift.text, color = Farbe.text2)
            return@Seite
        }
        val q = suche.trim().lowercase()
        val sichtbar = apps
            .filter { app ->
                if (q.isEmpty()) !app.system || backups.containsKey(app.paket)
                else app.name.lowercase().contains(q) || app.paket.lowercase().contains(q)
            }
            .sortedWith(compareBy({ !backups.containsKey(it.paket) }, { it.name.lowercase() }))
        if (sichtbar.isEmpty()) Text("Keine App gefunden.", style = Schrift.text, color = Farbe.text2)
        for (app in sichtbar) {
            AppKarte(
                app, backups[app.paket].orEmpty(), offen == app.paket,
                arbeit?.takeIf { it.first == app.paket }?.second, arbeit != null,
                meldung.takeIf { offen == app.paket }, a,
            ) { offen = if (offen == app.paket) null else app.paket }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AppKarte(
    app: AppEintrag,
    liste: List<AppBackup>,
    ausgeklappt: Boolean,
    schritt: String?,
    gesperrt: Boolean,
    meldung: AppMeldung?,
    a: AppSicherungAktionen,
    umschalten: () -> Unit,
) {
    Karte(innen = PaddingValues(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = umschalten),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSymbol(app)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = Schrift.karte, color = Farbe.text)
                Text(
                    liste.firstOrNull()?.let { "Gesichert ${backupDatum(it.ordner)}" } ?: "Noch nie gesichert",
                    style = Schrift.klein, color = if (liste.isEmpty()) Farbe.text3 else Farbe.text2,
                )
            }
            Icon(if (ausgeklappt) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Farbe.text3)
        }
        if (!ausgeklappt) return@Karte
        Spacer(Modifier.height(4.dp))
        Text(app.paket, style = Schrift.log, color = Farbe.text3)
        Spacer(Modifier.height(14.dp))
        if (schritt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Farbe.akzent, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(schritt, style = Schrift.text, color = Farbe.text2)
            }
            Spacer(Modifier.height(12.dp))
        }
        meldung?.let {
            Text(it.text, style = Schrift.text, color = it.farbe)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Knopf("Jetzt sichern", { a.sichern(app.paket) }, aktiv = !gesperrt, symbol = Icons.Rounded.Save)
        }
        Spacer(Modifier.height(10.dp))
        SicherKnopf(
            "Neue APK einspielen …",
            "Sichert die App, deinstalliert sie, installiert die gewählte APK und spielt die Daten zurück. Klappt die " +
                "Installation nicht, kommt die alte App wieder.",
            { a.tauschen(app.paket) }, aktiv = !gesperrt, symbol = Icons.Rounded.InstallMobile,
        )
        if (liste.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Etikett("Sicherungen")
            for (b in liste) BackupZeile(b, gesperrt, a)
        }
    }
}

@Composable
private fun BackupZeile(b: AppBackup, gesperrt: Boolean, a: AppSicherungAktionen) {
    var offen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { offen = !offen }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(backupDatum(b.ordner), style = Schrift.text, color = Farbe.text)
                Text(
                    listOfNotNull(b.version?.let { "Version $it" }, groesseText(b.kb)).joinToString(" · "),
                    style = Schrift.klein, color = Farbe.text3,
                )
            }
            Icon(if (offen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Farbe.text3)
        }
        if (offen) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SicherKnopf(
                    "Nur Daten zurück", "Die jetzigen Daten der App werden durch diese Sicherung ersetzt.",
                    { a.zurueck(b, false) }, aktiv = !gesperrt, symbol = Icons.Rounded.Restore,
                )
                SicherKnopf(
                    "App und Daten zurück", "Installiert die gesicherte Version und ersetzt die Daten.",
                    { a.zurueck(b, true) }, aktiv = !gesperrt, symbol = Icons.Rounded.Restore,
                )
                SicherKnopf(
                    "Löschen", "Diese Sicherung endgültig löschen?",
                    { a.loeschen(b) }, aktiv = !gesperrt, symbol = Icons.Rounded.DeleteOutline,
                )
            }
        }
    }
}
