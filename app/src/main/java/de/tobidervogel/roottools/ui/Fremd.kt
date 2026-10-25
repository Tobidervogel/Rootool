package de.tobidervogel.roottools.ui

import android.content.Context
import android.content.Intent

/** Pakete anderer Apps, die Root Tools oeffnet. Stehen auch unter <queries> im Manifest, sonst sieht Android 11+ sie nicht. */
object Fremd {
    const val MAGISK = "com.topjohnwu.magisk"
    const val OTA = "com.oplus.ota"
    const val OXYGEN_UPDATER = "com.arjanvlek.oxygenupdater"

    fun installiert(ctx: Context, paket: String) = ctx.packageManager.getLaunchIntentForPackage(paket) != null

    /** Oeffnet [paket], sonst [ersatz] (eine Systemseite). false, wenn beides nicht geht. */
    fun oeffnen(ctx: Context, paket: String, ersatz: String? = null): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(paket) ?: ersatz?.let { Intent(it) } ?: return false
        return runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
