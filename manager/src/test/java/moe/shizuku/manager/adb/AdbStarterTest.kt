package moe.shizuku.manager.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The decision in front of closing the classic ADB port.
 *
 * Closing it is `usb:` on adbd, not a change to a port, so it can take away the session the phone
 * is currently being debugged over. Which of those states the phone is in is checked here rather
 * than on a device, because the failure it prevents is one nobody sees until their connection
 * drops.
 */
class AdbStarterTest {

    @Test
    fun `wireless debugging in the way is answered before anything is tried`() {
        assertEquals(
            AdbStarter.CloseOutcome.WIRELESS_IN_USE,
            AdbStarter.closeBlocker(wirelessDebugging = true, usbDebugging = true)
        )
    }

    @Test
    fun `the mode in use outranks the toggle that is off`() {
        // Both are true on a phone with wireless debugging on and USB debugging off, and the one
        // that would be dropped is the one worth saying first.
        assertEquals(
            AdbStarter.CloseOutcome.WIRELESS_IN_USE,
            AdbStarter.closeBlocker(wirelessDebugging = true, usbDebugging = false)
        )
    }

    @Test
    fun `usb debugging off is its own answer rather than a failure`() {
        // The command is only accepted as USB debugging, and this app no longer turns that toggle
        // on to issue it: the answer names the toggle instead, and nothing is attempted.
        assertEquals(
            AdbStarter.CloseOutcome.USB_DEBUGGING_OFF,
            AdbStarter.closeBlocker(wirelessDebugging = false, usbDebugging = false)
        )
    }

    @Test
    fun `nothing in the way leaves the port closable`() {
        assertNull(AdbStarter.closeBlocker(wirelessDebugging = false, usbDebugging = true))
    }
}
