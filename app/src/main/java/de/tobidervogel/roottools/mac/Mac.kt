package de.tobidervogel.roottools.mac

import kotlin.random.Random

/** MAC-Adressen pruefen, formatieren und wuerfeln. Ohne Android, getestet in MacTest. */
object Mac {
    private const val HEX = "0123456789abcdef"
    private val TRENNER = setOf(':', '-', '.', ' ')

    private fun ziffern(eingabe: String) = eingabe.filterNot { it in TRENNER }.lowercase()

    /** "AA-bb.cc dd:EE:ff" wird "aa:bb:cc:dd:ee:ff" (so steht sie auch in /sys/class/net); null, wenn sie nicht geht. */
    fun normalisieren(eingabe: String): String? {
        if (fehler(eingabe) != null) return null
        return ziffern(eingabe).chunked(2).joinToString(":")
    }

    /** Warum [eingabe] als WLAN-MAC nicht geht, oder null, wenn sie passt. */
    fun fehler(eingabe: String): String? {
        val z = ziffern(eingabe)
        return when {
            z.isEmpty() -> "MAC eingeben"
            z.any { it !in HEX } -> "Nur 0–9 und A–F"
            z.length < 12 -> "Noch ${12 - z.length} Zeichen"
            z.length > 12 -> "Genau 12 Zeichen"
            z == "000000000000" -> "00:00:00:00:00:00 geht nicht"
            // Bit 0 des ersten Bytes = Gruppenadresse. Damit nimmt kein Router eine Verbindung an.
            z.take(2).toInt(16) and 1 == 1 -> "Die 2. Stelle muss gerade sein (0, 2, 4, 6, 8, A, C, E)"
            else -> null
        }
    }

    /** Bit 1 des ersten Bytes: lokal vergeben, kein Hersteller dahinter. So sehen auch Androids Zufalls-MACs aus. */
    fun istLokal(mac: String): Boolean = ziffern(mac).take(2).toIntOrNull(16)?.let { it and 2 == 2 } ?: false

    /** Zufaellige lokale Unicast-MAC, gebaut wie Androids eigene (MacAddressUtils.createRandomUnicastAddress). */
    fun zufaellig(zufall: Random = Random.Default): String {
        val b = zufall.nextBytes(6)
        b[0] = ((b[0].toInt() and 0xFC) or 0x02).toByte()
        return b.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Beim Tippen: nur Hex-Ziffern, hoechstens 12, nach je zwei ein Doppelpunkt, gross geschrieben. */
    fun beimTippen(text: String): String =
        ziffern(text).filter { it in HEX }.take(12).chunked(2).joinToString(":").uppercase()

    fun anzeigen(mac: String): String = mac.uppercase()
}
