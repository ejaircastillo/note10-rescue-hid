package com.ejair.note10rescue

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El transporte simulado es lo que permite probar el flujo completo **sin** gastar
 * intentos de desbloqueo en el dispositivo: tiene que responder igual que un Android
 * con AOA2 para que el pipeline (GET_PROTOCOL → REGISTER_HID → SET_DESC → reports)
 * se ejerza de verdad.
 */
class SimulatedTransportTest {

    @Test
    fun `responde protocolo 2 en little endian`() {
        val transport = SimulatedTransport()

        val inTransfer = transport.transferIn(AoaProtocol.ACCESSORY_GET_PROTOCOL, 0, 0, 2, 1000)

        assertEquals(2, inTransfer.result.bytesTransferred)
        assertArrayEquals(byteArrayOf(0x02, 0x00), inTransfer.data)
        assertTrue(inTransfer.result.ok)
    }

    @Test
    fun `register y unregister devuelven cero bytes`() {
        val transport = SimulatedTransport()

        assertEquals(
            0,
            transport.transferOut(AoaProtocol.ACCESSORY_REGISTER_HID, 1, 63, null, 1000).bytesTransferred
        )
        assertEquals(
            0,
            transport.transferOut(AoaProtocol.ACCESSORY_UNREGISTER_HID, 1, 0, null, 1000).bytesTransferred
        )
    }

    @Test
    fun `descriptor y reports devuelven la cantidad enviada`() {
        val transport = SimulatedTransport()

        val descriptor = transport.transferOut(
            AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC, 1, 0,
            HidKeyboardDescriptor.REPORT_DESCRIPTOR, 1000
        )
        assertEquals(HidKeyboardDescriptor.SIZE, descriptor.bytesTransferred)

        val report = transport.transferOut(
            AoaProtocol.ACCESSORY_SEND_HID_EVENT, 1, 0,
            HidKeyboardReports.keyDown(HidKeycodes.ENTER), 1000
        )
        assertEquals(HidKeyboardReports.REPORT_SIZE, report.bytesTransferred)
    }

    @Test
    fun `un comando desconocido falla como en un dispositivo real`() {
        val transport = SimulatedTransport()

        val result = transport.transferOut(99, 0, 0, null, 1000)

        assertEquals(-1, result.bytesTransferred)
        assertTrue(!result.ok)
    }

    @Test
    fun `el pipeline completo funciona contra el transporte simulado`() {
        val transport = SimulatedTransport()
        val keyboard = AoaHidKeyboard(transport, slumber = { })
        val logs = mutableListOf<String>()
        keyboard.onEvent = { logs += it }

        assertEquals(2, keyboard.prepare())
        assertTrue(keyboard.isRegistered)

        val stats = keyboard.sendPinAndEnter("1357", wakeKeyFirst = true)

        assertEquals(4, stats.digits)
        // TAB + 4 dígitos + ENTER = 6 pulsaciones = 12 reports
        assertEquals(12, stats.reports)
        assertTrue(logs.contains("HID READY"))
        assertTrue(logs.contains("sequence summary: ${stats.summary()}"))
        assertTrue(logs.none { it.contains("1357") })
    }
}
