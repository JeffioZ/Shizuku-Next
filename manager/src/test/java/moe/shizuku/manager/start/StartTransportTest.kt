package moe.shizuku.manager.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a start has to wait for a network, which is the difference between a start that
 * happens on a device with no Wi-Fi and one that sits there until a network appears.
 */
class StartTransportTest {

    @Test
    fun `a classic port with TCP mode on needs no network`() {
        assertFalse(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = true))
    }

    @Test
    fun `TCP mode off closes the port, so the network is needed again`() {
        assertTrue(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = false))
    }

    @Test
    fun `no port means discovery, which means the network`() {
        assertTrue(StartTransport.wifiRequired(tcpPort = -1, tcpMode = true))
        assertTrue(StartTransport.wifiRequired(tcpPort = 0, tcpMode = true))
    }

    @Test
    fun `the classic port is a fallback whenever there is one`() {
        assertEquals(5555, StartTransport.classicPortFallback(5555))
    }

    @Test
    fun `there is nothing to fall back to without a port`() {
        assertNull(StartTransport.classicPortFallback(-1))
        assertNull(StartTransport.classicPortFallback(0))
    }

    @Test
    fun `the experiment does not wait for a network either`() {
        // Waiting would gate the start the setting exists to make possible.
        assertFalse(StartTransport.wifiRequired(tcpPort = -1, tcpMode = false, forceWireless = true))
        assertFalse(StartTransport.wifiRequired(tcpPort = 5555, tcpMode = false, forceWireless = true))
    }

    // -- which port a start uses ----------------------------------------------------------------

    @Test
    fun `a start with TCP mode on and a port uses that port`() {
        // The transport the promise above is about: the port TCP mode keeps open is the one the
        // start should take, rather than looking for a wireless one over the network it said it
        // did not need.
        assertTrue(
            StartTransport.classicPortInUse(
                usbMethod = false,
                tlsSupported = true,
                tcpPort = 5555,
                tcpMode = true
            )
        )
    }

    @Test
    fun `TCP mode without a port leaves the wireless search alone`() {
        // A mode is not the port itself: with nothing listening there is nothing to use, and
        // discovery is the only way to a port at all.
        assertFalse(StartTransport.classicPortInUse(false, true, tcpPort = -1, tcpMode = true))
        assertFalse(StartTransport.classicPortInUse(false, true, tcpPort = 0, tcpMode = true))
    }

    @Test
    fun `a port left open with TCP mode off is not taken by a wireless start`() {
        // Unchanged from before: the mode is what says a port is wanted, and one left behind is not
        // an invitation to skip discovery.
        assertFalse(StartTransport.classicPortInUse(false, true, tcpPort = 5555, tcpMode = false))
    }

    @Test
    fun `the USB method and platforms without TLS use the classic port as before`() {
        assertTrue(StartTransport.classicPortInUse(true, true, tcpPort = -1, tcpMode = false))
        assertTrue(StartTransport.classicPortInUse(false, false, tcpPort = -1, tcpMode = false))
    }

    @Test
    fun `the two answers agree when a port is what removes the need for a network`() {
        val tcpPort = 5555
        val tcpMode = true

        assertFalse(StartTransport.wifiRequired(tcpPort, tcpMode))
        assertTrue(StartTransport.classicPortInUse(false, true, tcpPort, tcpMode))
    }
}
