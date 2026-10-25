package de.tobidervogel.roottools.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** Eine installierte App, wie Root Tools sie in Listen zeigt. */
class AppEintrag(val paket: String, val name: String, val uid: Int, val system: Boolean, val symbol: ImageBitmap?)

/**
 * Installierte Apps mit Namen und Symbol. Braucht QUERY_ALL_PACKAGES, sonst sieht Android 11+ fast nichts.
 * Blockiert (Symbole laden dauert), also auf Dispatchers.IO aufrufen. Root Tools selbst fehlt in der Liste.
 */
object Apps {
    @Volatile private var zwischen: List<AppEintrag>? = null

    fun alle(ctx: Context, neu: Boolean = false): List<AppEintrag> {
        if (!neu) zwischen?.let { return it }
        val pm = ctx.packageManager
        val px = (40 * ctx.resources.displayMetrics.density).toInt()
        return pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .map { a ->
                AppEintrag(
                    paket = a.packageName,
                    name = a.loadLabel(pm).toString(),
                    uid = a.uid,
                    system = a.flags and ApplicationInfo.FLAG_SYSTEM != 0 && a.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    symbol = runCatching { a.loadIcon(pm).toBitmap(px, px).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.name.lowercase() }
            .also { zwischen = it }
    }
}

@Composable
fun AppSymbol(app: AppEintrag?, groesse: Dp = 40.dp) {
    val s = app?.symbol
    if (s != null) {
        Image(s, null, Modifier.size(groesse).clip(RoundedCornerShape(groesse / 4)))
    } else {
        Box(
            Modifier.size(groesse).clip(RoundedCornerShape(groesse / 4)).background(Farbe.karte2),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Android, null, tint = Farbe.text3, modifier = Modifier.size(groesse * 0.55f)) }
    }
}
