package de.tobidervogel.roottools.update

import android.content.Context
import de.tobidervogel.roottools.root.Skript
import de.tobidervogel.roottools.root.Werte

/** Root-Seite von System-Update und Notfall-Sicherung: die Arbeit macht assets/update.sh. Alles blockiert. */
object UpdateSteuerung {
    /** Prueft auch die Boot-Abbilder beider Slots (je ein paar Sekunden). */
    fun zustand(ctx: Context) = SystemStand(Skript.aufrufen(ctx, "update.sh", "zustand", zeitlimitSek = 120))

    /** Nur Platz und vorhandene Sicherungen, schnell. */
    fun sicherungen(ctx: Context) = SystemStand(Skript.aufrufen(ctx, "update.sh", "sicherungen"))

    /** [schritt] bekommt "3/24 nvdata" usw., solange es laeuft. Antwort: FERTIG=<Ordner> oder FEHLER. */
    fun sichern(ctx: Context, schritt: (String) -> Unit): Werte =
        Skript.aufrufen(ctx, "update.sh", "sichern", zeitlimitSek = 1800) { z ->
            if (z.startsWith("SCHRITT=")) schritt(z.removePrefix("SCHRITT="))
        }

    fun otaFreischalten(ctx: Context) = Skript.aufrufen(ctx, "update.sh", "ota_frei")

    fun netzAn(ctx: Context) = Skript.aufrufen(ctx, "update.sh", "netz_an")
}
