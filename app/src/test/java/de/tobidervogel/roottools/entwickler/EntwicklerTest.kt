package de.tobidervogel.roottools.entwickler

import de.tobidervogel.roottools.root.Werte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntwicklerTest {
    @Test
    fun netzAusAdresse() {
        assertEquals("192.168.0.0/24", netzVon("192.168.0.115/24"))
        assertEquals("10.0.0.0/8", netzVon("10.20.30.40/8"))
        assertEquals("172.16.4.0/22", netzVon("172.16.7.9/22"))
        assertEquals("192.168.0.115/32", netzVon("192.168.0.115/32"))
        assertNull(netzVon("192.168.0.115"))
        assertNull(netzVon("300.1.1.1/24"))
        assertNull(netzVon("1.2.3.4/33"))
        assertNull(netzVon(null))
    }

    @Test
    fun adbStand() {
        val an = AdbStand(Werte("ROOT=0\nPORT=5555\nLAUSCHT=1\nSCHUTZ=1\nTAILSCALE=100.101.7.12\nWLAN=192.168.0.115/24\nHEIM=keins"))
        assertTrue(an.an)
        assertEquals("keins", an.heim)
        val nurPort = AdbStand(Werte("ROOT=0\nPORT=5555"))
        assertFalse(nurPort.an)
        assertTrue(nurPort.portGesetzt)
    }

    @Test
    fun logStufen() {
        val z = logZeilen(
            Werte(
                "ROOT=0\nZ=10-25 18:40:01.123  1234  1250 E AndroidRuntime: FATAL EXCEPTION: main\n" +
                    "Z=10-25 18:40:01.124  1234  1250 W Foo: bar\nZ=--------- beginning of crash\n",
            ),
        )
        assertEquals(listOf('E', 'W', ' '), z.map { it.stufe })
    }
}
