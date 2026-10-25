package de.tobidervogel.roottools.entwickler

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.tobidervogel.roottools.ui.AppEintrag
import de.tobidervogel.roottools.ui.AppSymbol
import de.tobidervogel.roottools.ui.Apps
import de.tobidervogel.roottools.ui.Chip
import de.tobidervogel.roottools.ui.Farbe
import de.tobidervogel.roottools.ui.Hinweis
import de.tobidervogel.roottools.ui.Knopf
import de.tobidervogel.roottools.ui.Kopfzeile
import de.tobidervogel.roottools.ui.Schrift
import de.tobidervogel.roottools.ui.Suchfeld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ANZAHLEN = listOf(200, 1000, 5000)

/** Teilen geht ueber einen Intent; der vertraegt nicht beliebig viel Text. */
private const val TEILEN_HOECHSTENS = 2000

class LogcatAktionen(
    val laden: (AppEintrag?, Boolean, Int) -> Unit = { _, _, _ -> },
    val teilen: (String) -> Unit = {},
)

@Composable
fun LogcatSeite(zurueck: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEintrag>>(emptyList()) }
    var zeilen by remember { mutableStateOf<List<LogZeile>?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laedt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Apps.alle(ctx.applicationContext) } }

    LogcatInhalt(
        apps, zeilen, fehler, laedt, zurueck,
        LogcatAktionen(
            laden = { app, abstuerze, anzahl ->
                scope.launch {
                    laedt = true
                    val w = withContext(Dispatchers.IO) { Entwickler.logcat(ctx.applicationContext, app?.uid, abstuerze, anzahl) }
                    fehler = w.fehler
                    zeilen = logZeilen(w)
                    laedt = false
                }
            },
            teilen = { text ->
                val senden = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(senden, "Logcat teilen"))
            },
        ),
    )
}

@Composable
fun LogcatInhalt(
    apps: List<AppEintrag>,
    zeilen: List<LogZeile>?,
    fehler: String?,
    laedt: Boolean,
    zurueck: () -> Unit,
    a: LogcatAktionen,
    startApp: AppEintrag? = null,
) {
    val zwischenablage = LocalClipboardManager.current
    var app by remember { mutableStateOf(startApp) }
    var appSuche by remember { mutableStateOf("") }
    var abstuerze by remember { mutableStateOf(false) }
    var anzahl by remember { mutableIntStateOf(1000) }
    var filter by remember { mutableStateOf("") }
    val liste = rememberLazyListState()
    val sichtbar = zeilen.orEmpty().let { z -> if (filter.isBlank()) z else z.filter { it.text.contains(filter, ignoreCase = true) } }

    // Neueste Meldungen stehen unten: nach dem Laden dorthin springen.
    LaunchedEffect(zeilen) { if (!zeilen.isNullOrEmpty()) liste.scrollToItem(sichtbar.size) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        state = liste,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Kopfzeile("Logcat", zurueck)
            Text("App", style = Schrift.klein, color = Farbe.text3)
            Spacer(Modifier.height(6.dp))
            if (app != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppSymbol(app, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(app!!.name, style = Schrift.karte, color = Farbe.text, modifier = Modifier.weight(1f))
                    Chip("Alle Apps") { app = null }
                }
            } else {
                Suchfeld(appSuche, { appSuche = it }, "Alle Apps – oder eine suchen")
                val q = appSuche.trim().lowercase()
                if (q.isNotEmpty()) {
                    for (t in apps.filter { it.name.lowercase().contains(q) || it.paket.contains(q) }.take(6)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { app = t; appSuche = "" }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppSymbol(t, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, style = Schrift.text, color = Farbe.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Alles", !abstuerze) { abstuerze = false }
                Chip("Nur Abstürze", abstuerze) { abstuerze = true }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in ANZAHLEN) Chip("$n Zeilen", anzahl == n) { anzahl = n }
            }
            Spacer(Modifier.height(12.dp))
            Knopf(
                "Laden", { a.laden(app, abstuerze, anzahl) }, modifier = Modifier.fillMaxWidth(),
                arbeitet = laedt, symbol = Icons.Rounded.Refresh,
            )
            fehler?.let {
                Spacer(Modifier.height(12.dp))
                Hinweis("Fehler", it, Farbe.fehler)
            }
            if (zeilen != null) {
                Spacer(Modifier.height(12.dp))
                Suchfeld(filter, { filter = it }, "Im Ergebnis suchen")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Knopf("Kopieren", {
                        zwischenablage.setText(AnnotatedString(sichtbar.joinToString("\n") { it.text }))
                    }, haupt = false, symbol = Icons.Rounded.ContentCopy, aktiv = sichtbar.isNotEmpty())
                    Knopf("Teilen", {
                        a.teilen(sichtbar.takeLast(TEILEN_HOECHSTENS).joinToString("\n") { it.text })
                    }, haupt = false, symbol = Icons.Rounded.Share, aktiv = sichtbar.isNotEmpty())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${sichtbar.size} Zeilen · Zeile antippen kopiert sie",
                    style = Schrift.klein, color = Farbe.text3,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        items(sichtbar) { z ->
            Text(
                z.text, style = Schrift.log,
                color = when (z.stufe) {
                    'E', 'F' -> Farbe.fehler
                    'W' -> Farbe.warnung
                    'I' -> Farbe.text2
                    else -> Farbe.text3
                },
                modifier = Modifier.fillMaxWidth().clickable { zwischenablage.setText(AnnotatedString(z.text)) }
                    .padding(vertical = 2.dp),
            )
        }
        if (zeilen != null && sichtbar.isEmpty()) {
            item { Text("Keine Zeilen.", style = Schrift.text, color = Farbe.text2) }
        }
    }
}
