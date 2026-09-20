package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparación de fotos del bus USB: es el único indicio externo del lado del host
 * (si el Note10 se desbloquea suele cambiar su configuración USB y re-enumerar).
 */
class UsbBusStateTest {

    private fun snap(name: String, vid: Int = 0x04E8, pid: Int = 0x6860) =
        DeviceSnapshot(name, vid, pid)

    @Test
    fun `sin dispositivos`() {
        assertEquals("ningún dispositivo USB enumerado", UsbDeviceManager.describeSnapshots(emptyList()))
    }

    @Test
    fun `describe una lista con label legible`() {
        val text = UsbDeviceManager.describeSnapshots(listOf(snap("/dev/bus/usb/001/002")))
        assertTrue(text.contains("VID 0x04E8"))
        assertTrue(text.contains("PID 0x6860"))
    }

    @Test
    fun `mismo estado no reporta cambios`() {
        val before = listOf(snap("/dev/bus/usb/001/002"))
        val after = listOf(snap("/dev/bus/usb/001/002"))

        assertEquals("sin cambios en el bus USB", UsbDeviceManager.diffSnapshots(before, after))
        assertFalse(UsbDeviceManager.hasChanges(before, after))
    }

    @Test
    fun `detecta desconexion`() {
        val before = listOf(snap("/dev/bus/usb/001/002"))
        val after = emptyList<DeviceSnapshot>()

        val diff = UsbDeviceManager.diffSnapshots(before, after)
        assertTrue(diff.contains("se desconectó"))
        assertTrue(UsbDeviceManager.hasChanges(before, after))
    }

    @Test
    fun `detecta re-enumeracion por cambio de PID`() {
        val before = listOf(snap("/dev/bus/usb/001/002", pid = 0x6860))
        val after = listOf(snap("/dev/bus/usb/001/002", pid = 0x6861))

        val diff = UsbDeviceManager.diffSnapshots(before, after)
        assertTrue(diff.contains("re-enumeró"))
        assertTrue(diff.contains("0x6860"))
        assertTrue(diff.contains("0x6861"))
        assertTrue(UsbDeviceManager.hasChanges(before, after))
    }

    @Test
    fun `detecta aparicion de un dispositivo nuevo`() {
        val before = emptyList<DeviceSnapshot>()
        val after = listOf(snap("/dev/bus/usb/001/005"))

        val diff = UsbDeviceManager.diffSnapshots(before, after)
        assertTrue(diff.contains("apareció"))
        assertTrue(UsbDeviceManager.hasChanges(before, after))
    }
}
