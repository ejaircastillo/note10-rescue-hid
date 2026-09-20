package com.ejair.note10rescue

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del protocolo AOA HID con un transporte falso: no se emula USB físico,
 * se verifica que los control transfers salgan con los valores correctos
 * (request / value / index / data) y en el orden correcto.
 */
class AoaHidKeyboardTest {

    private val logs = mutableListOf<String>()

    private fun newKeyboard(transport: FakeTransport): AoaHidKeyboard {
        val keyboard = AoaHidKeyboard(transport, slumber = { })
        keyboard.onEvent = { line -> logs += line }
        return keyboard
    }

    private fun prepared(transport: FakeTransport): AoaHidKeyboard {
        val keyboard = newKeyboard(transport)
        keyboard.prepare()
        return keyboard
    }

    // ------------------------------------------------------------- protocolo

    @Test
    fun `get protocol lee uint16 little endian`() {
        val transport = FakeTransport()
        transport.protocolResponse = byteArrayOf(0x02, 0x00)

        assertEquals(2, newKeyboard(transport).getProtocolVersion())
        // request IN de tipo vendor 0xC0 => 51, longitud 2
        val transfer = transport.recorded.single()
        assertEquals(AoaProtocol.ACCESSORY_GET_PROTOCOL, transfer.request)
        assertTrue(transfer.directionIn)

        transport.protocolResponse = byteArrayOf(0x02, 0x01) // 0x0102 = 258
        assertEquals(258, newKeyboard(transport).getProtocolVersion())
    }

    @Test
    fun `el byte bajo del protocolo es el primero`() {
        val transport = FakeTransport()
        transport.protocolResponse = byteArrayOf(0x01, 0x00)
        assertEquals(1, newKeyboard(transport).getProtocolVersion())

        transport.protocolResponse = byteArrayOf(0x00, 0x02)
        assertEquals(512, newKeyboard(transport).getProtocolVersion())
    }

    @Test
    fun `get protocol fallido devuelve AOA_PROTOCOL_QUERY_FAILED`() {
        val transport = FakeTransport()
        transport.protocolResult = -1
        transport.protocolResponse = null

        val error = assertThrows(AoaException::class.java) { newKeyboard(transport).getProtocolVersion() }
        assertEquals(AoaError.AOA_PROTOCOL_QUERY_FAILED, error.aoaError)
        assertTrue(error.detail.contains("request=51"))
    }

    // ------------------------------------------------------------- preparación

    @Test
    fun `prepare registra el HID y manda el descriptor en el orden correcto`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        assertEquals(
            listOf(
                AoaProtocol.ACCESSORY_GET_PROTOCOL,
                AoaProtocol.ACCESSORY_REGISTER_HID,
                AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC
            ),
            transport.requests()
        )

        // REGISTER_HID: value = hidId, index = longitud del descriptor, sin data
        val register = transport.outTransfers(AoaProtocol.ACCESSORY_REGISTER_HID).single()
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, register.value)
        assertEquals(HidKeyboardDescriptor.SIZE, register.index)
        assertEquals(0, register.dataSize)

        // SET_HID_REPORT_DESC: value = hidId, index = 0, data = descriptor completo
        val descriptor = transport.outTransfers(AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC).single()
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, descriptor.value)
        assertEquals(0, descriptor.index)
        assertArrayEquals(HidKeyboardDescriptor.REPORT_DESCRIPTOR, descriptor.data)

        assertTrue(keyboard.isRegistered)
        assertEquals(2, keyboard.lastProtocolVersion)
        assertTrue(logs.contains("AOA protocol: 2"))
        assertTrue(logs.contains("REGISTER_HID: OK"))
        assertTrue(logs.contains("SET_HID_REPORT_DESC: OK"))
        assertTrue(logs.contains("HID READY"))
    }

    @Test
    fun `protocolo 1 no soporta AOA2 HID y no registra nada`() {
        val transport = FakeTransport()
        transport.protocolResponse = byteArrayOf(0x01, 0x00)

        val error = assertThrows(AoaException::class.java) { newKeyboard(transport).prepare() }

        assertEquals(AoaError.AOA_PROTOCOL_UNSUPPORTED, error.aoaError)
        assertEquals(listOf(AoaProtocol.ACCESSORY_GET_PROTOCOL), transport.requests())
    }

    @Test
    fun `el fallback manual fuerza un unico intento con protocolo menor a 2`() {
        val transport = FakeTransport()
        transport.protocolResponse = byteArrayOf(0x01, 0x00)

        val keyboard = newKeyboard(transport)
        assertEquals(1, keyboard.prepare(forceIfUnsupported = true))

        assertEquals(
            listOf(
                AoaProtocol.ACCESSORY_GET_PROTOCOL,
                AoaProtocol.ACCESSORY_REGISTER_HID,
                AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC
            ),
            transport.requests()
        )
        assertEquals(1, transport.requests().count { it == AoaProtocol.ACCESSORY_GET_PROTOCOL })
    }

    @Test
    fun `register hid fallido devuelve REGISTER_HID_FAILED`() {
        val transport = FakeTransport()
        transport.registerResult = -1

        val error = assertThrows(AoaException::class.java) { newKeyboard(transport).prepare() }

        assertEquals(AoaError.REGISTER_HID_FAILED, error.aoaError)
        assertEquals(
            listOf(AoaProtocol.ACCESSORY_GET_PROTOCOL, AoaProtocol.ACCESSORY_REGISTER_HID),
            transport.requests()
        )
    }

    @Test
    fun `descriptor fallido devuelve SET_DESCRIPTOR_FAILED`() {
        val transport = FakeTransport()
        transport.descriptorResult = -1

        val error = assertThrows(AoaException::class.java) { newKeyboard(transport).prepare() }

        assertEquals(AoaError.SET_DESCRIPTOR_FAILED, error.aoaError)
    }

    // --------------------------------------------------------------- reports

    @Test
    fun `no se puede enviar un report sin HID preparado`() {
        val transport = FakeTransport()

        val error = assertThrows(AoaException::class.java) {
            newKeyboard(transport).sendReport(HidKeyboardReports.keyRelease())
        }

        assertEquals(AoaError.HID_NOT_PREPARED, error.aoaError)
        assertTrue(transport.requests().isEmpty())
    }

    @Test
    fun `send report fallido devuelve SEND_REPORT_FAILED`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)
        transport.eventResult = -1

        val error = assertThrows(AoaException::class.java) {
            keyboard.sendReport(HidKeyboardReports.keyDown(HidKeycodes.ENTER))
        }

        assertEquals(AoaError.SEND_REPORT_FAILED, error.aoaError)
    }

    @Test
    fun `press key envia down y luego release`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        keyboard.pressKey(HidKeycodes.forDigit('5'))

        val reports = transport.sentReports()
        assertEquals(2, reports.size)
        assertEquals(0x22, reports[0][2].toInt())                        // KEY DOWN del '5'
        assertArrayEquals(ByteArray(HidKeyboardReports.REPORT_SIZE), reports[1]) // KEY RELEASE

        val down = transport.outTransfers(AoaProtocol.ACCESSORY_SEND_HID_EVENT)[0]
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, down.value)
        assertEquals(0, down.index)
        assertEquals(HidKeyboardReports.REPORT_SIZE, down.dataSize)
    }

    // ------------------------------------------------------------------ PIN

    @Test
    fun `send pin and enter manda los digitos y un unico ENTER`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val stats = keyboard.sendPinAndEnter("12")

        assertEquals(2, stats.digits)
        assertEquals(6, stats.reports)
        assertFalse(stats.wakeKeyFirst)
        assertTrue(stats.durationMs >= 0)
        val reports = transport.sentReports()
        // 2 dígitos * (down + release) + ENTER (down + release)
        assertEquals(6, reports.size)
        assertEquals(0x1E, reports[0][2].toInt()) // '1' down
        assertTrue(reports[1].all { it.toInt() == 0 })
        assertEquals(0x1F, reports[2][2].toInt()) // '2' down
        assertTrue(reports[3].all { it.toInt() == 0 })
        assertEquals(HidKeycodes.ENTER, reports[4][2].toInt()) // ENTER down
        assertTrue(reports[5].all { it.toInt() == 0 })
        assertTrue(logs.contains("2 digit sequence sent"))
        assertTrue(logs.contains("ENTER sent"))
    }

    @Test
    fun `el registro tecnico nunca contiene el pin ni los keycodes`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        keyboard.sendPinAndEnter("1234")

        assertFalse(logs.any { it.contains("1234") })
        assertFalse(logs.any { it.contains("1e", ignoreCase = true) })
        assertFalse(logs.any { it.contains("1f", ignoreCase = true) })
        // Sólo cantidad de dígitos, nunca los dígitos.
        assertTrue(logs.any { it == "4 digit sequence sent" })
    }

    @Test
    fun `un pin vacio o no numerico es INVALID_PIN y no envia nada`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        assertEquals(
            AoaError.INVALID_PIN,
            assertThrows(AoaException::class.java) { keyboard.sendPinAndEnter("") }.aoaError
        )
        assertEquals(
            AoaError.INVALID_PIN,
            assertThrows(AoaException::class.java) { keyboard.sendPinAndEnter("12a4") }.aoaError
        )
        assertTrue(transport.sentReports().isEmpty())
    }

    @Test
    fun `un pin de mas de un digito no repite secuencias`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        keyboard.sendPinAndEnter("9876543210")

        // 10 dígitos * 2 reports + ENTER * 2 reports; una sola pasada.
        assertEquals(22, transport.sentReports().size)
        assertEquals(1, transport.requests().count { it == AoaProtocol.ACCESSORY_GET_PROTOCOL })
    }

    // ---------------------------------------------------------- manuales

    @Test
    fun `las teclas manuales se envian de a una`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        keyboard.sendKeyOnce(HidKeycodes.TAB)
        keyboard.sendKeyOnce(HidKeycodes.BACKSPACE)
        keyboard.sendKeyOnce(HidKeycodes.ENTER)

        val reports = transport.sentReports().filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals(HidKeycodes.TAB, reports[0][2].toInt())
        assertEquals(HidKeycodes.BACKSPACE, reports[1][2].toInt())
        assertEquals(HidKeycodes.ENTER, reports[2][2].toInt())
        assertTrue(logs.contains("KEY sent: TAB"))
    }

    @Test
    fun `unregister hid usa el comando 55 con el hid id`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        assertTrue(keyboard.unregisterHid())

        val unregister = transport.outTransfers(AoaProtocol.ACCESSORY_UNREGISTER_HID).single()
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, unregister.value)
        assertEquals(0, unregister.index)
        assertEquals(0, unregister.dataSize)
        assertFalse(keyboard.isRegistered)
        assertTrue(logs.contains("UNREGISTER_HID: OK"))
    }

    @Test
    fun `los transfers registrados llevan request result y duracion`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val transfers = keyboard.transfers
        assertTrue(transfers.isNotEmpty())
        assertTrue(transfers.all { it.durationMs >= 0 })
        assertEquals(AoaProtocol.ACCESSORY_REGISTER_HID, transfers[1].request)
        assertTrue(transfers[1].technicalDetail().startsWith("request=54 result="))
    }

    // ------------------------------------ modo forzado (regresión v1.0.0 -> v1.0.1)

    @Test
    fun `GET_PROTOCOL fallido sin forzar conserva QUERY_FAILED y no registra nada`() {
        val transport = FakeTransport()
        transport.protocolResult = -1
        transport.protocolResponse = null

        val error = assertThrows(AoaException::class.java) {
            newKeyboard(transport).prepare(forceIfUnsupported = false)
        }

        assertEquals(AoaError.AOA_PROTOCOL_QUERY_FAILED, error.aoaError)
        assertEquals(listOf(AoaProtocol.ACCESSORY_GET_PROTOCOL), transport.requests())
    }

    @Test
    fun `GET_PROTOCOL fallido con forzado explicito registra el HID una sola vez`() {
        val transport = FakeTransport()
        transport.protocolResult = -1
        transport.protocolResponse = null

        val keyboard = newKeyboard(transport)
        val version = keyboard.prepare(forceIfUnsupported = true)

        assertEquals(AoaHidKeyboard.PROTOCOL_UNKNOWN, version)
        assertEquals(
            listOf(
                AoaProtocol.ACCESSORY_GET_PROTOCOL,
                AoaProtocol.ACCESSORY_REGISTER_HID,
                AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC
            ),
            transport.requests()
        )
        // Sin loops ni reintentos: cada comando aparece exactamente una vez.
        assertEquals(1, transport.requests().count { it == AoaProtocol.ACCESSORY_GET_PROTOCOL })
        assertEquals(1, transport.requests().count { it == AoaProtocol.ACCESSORY_REGISTER_HID })
        assertEquals(1, transport.requests().count { it == AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC })
        assertTrue(keyboard.isRegistered)
        assertTrue(logs.contains("GET_PROTOCOL failed; forced HID attempt requested"))
        assertTrue(logs.contains("REGISTER_HID: OK"))
        assertTrue(logs.contains("SET_HID_REPORT_DESC: OK"))
        assertTrue(logs.contains("HID READY"))

        val register = transport.outTransfers(AoaProtocol.ACCESSORY_REGISTER_HID).single()
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, register.value)
        assertEquals(HidKeyboardDescriptor.SIZE, register.index)
        val descriptor = transport.outTransfers(AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC).single()
        assertEquals(0, descriptor.index)
        assertArrayEquals(HidKeyboardDescriptor.REPORT_DESCRIPTOR, descriptor.data)
    }

    @Test
    fun `si GET_PROTOCOL falla no se inventa una version de protocolo`() {
        val transport = FakeTransport()
        transport.protocolResult = -1
        transport.protocolResponse = null

        val keyboard = newKeyboard(transport)

        assertEquals(AoaHidKeyboard.PROTOCOL_UNKNOWN, keyboard.prepare(forceIfUnsupported = true))
        assertEquals(AoaHidKeyboard.PROTOCOL_UNKNOWN, keyboard.lastProtocolVersion)
        assertFalse(logs.any { it.contains("AOA protocol: -1") })
    }

    @Test
    fun `una respuesta de protocolo truncada tambien entra al modo forzado`() {
        // Sigue siendo AOA_PROTOCOL_QUERY_FAILED, así que el forzado aplica.
        val sinForzar = FakeTransport()
        sinForzar.protocolResult = 1
        sinForzar.protocolResponse = byteArrayOf(0x02) // 1 byte en vez de 2
        val sinForzarError = assertThrows(AoaException::class.java) {
            newKeyboard(sinForzar).prepare(forceIfUnsupported = false)
        }
        assertEquals(AoaError.AOA_PROTOCOL_QUERY_FAILED, sinForzarError.aoaError)
        assertFalse(sinForzar.requests().contains(AoaProtocol.ACCESSORY_REGISTER_HID))

        val forzado = FakeTransport()
        forzado.protocolResult = 1
        forzado.protocolResponse = byteArrayOf(0x02)
        val keyboard = newKeyboard(forzado)
        assertEquals(AoaHidKeyboard.PROTOCOL_UNKNOWN, keyboard.prepare(forceIfUnsupported = true))
        assertTrue(forzado.requests().contains(AoaProtocol.ACCESSORY_REGISTER_HID))
        assertTrue(forzado.requests().contains(AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC))
        assertTrue(keyboard.isRegistered)
    }

    // ------------------------------------------------- cleanup del descriptor

    @Test
    fun `si falla el descriptor se desregistra el HID y registered queda false`() {
        val transport = FakeTransport()
        transport.descriptorResult = -1

        val keyboard = newKeyboard(transport)
        val error = assertThrows(AoaException::class.java) { keyboard.prepare() }

        assertEquals(AoaError.SET_DESCRIPTOR_FAILED, error.aoaError)
        assertEquals(
            listOf(
                AoaProtocol.ACCESSORY_GET_PROTOCOL,
                AoaProtocol.ACCESSORY_REGISTER_HID,
                AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC,
                AoaProtocol.ACCESSORY_UNREGISTER_HID
            ),
            transport.requests()
        )
        assertFalse(keyboard.isRegistered)
        val unregister = transport.outTransfers(AoaProtocol.ACCESSORY_UNREGISTER_HID).single()
        assertEquals(AoaProtocol.HID_ID_KEYBOARD, unregister.value)
        assertEquals(0, unregister.index)
    }

    @Test
    fun `si el cleanup tambien falla el error principal sigue siendo SET_DESCRIPTOR_FAILED`() {
        val transport = FakeTransport()
        transport.descriptorResult = -1
        transport.unregisterResult = -1

        val keyboard = newKeyboard(transport)
        val error = assertThrows(AoaException::class.java) { keyboard.prepare() }

        assertEquals(AoaError.SET_DESCRIPTOR_FAILED, error.aoaError)
        assertTrue(transport.requests().contains(AoaProtocol.ACCESSORY_UNREGISTER_HID))
        assertFalse(keyboard.isRegistered)
        assertFalse(logs.any { it.contains(AoaError.REGISTER_HID_FAILED.code) })
    }

    @Test
    fun `si REGISTER_HID falla no se intenta desregistrar`() {
        val transport = FakeTransport()
        transport.registerResult = -1

        val error = assertThrows(AoaException::class.java) { newKeyboard(transport).prepare() }

        assertEquals(AoaError.REGISTER_HID_FAILED, error.aoaError)
        assertFalse(transport.requests().contains(AoaProtocol.ACCESSORY_UNREGISTER_HID))
    }

    @Test
    fun `unregister fallido tambien deja registered en false`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)
        transport.unregisterResult = -1

        assertFalse(keyboard.unregisterHid())
        assertFalse(keyboard.isRegistered)
    }

    @Test
    fun `tras un fallo de descriptor se puede volver a preparar sin problema`() {
        val transport = FakeTransport()
        transport.descriptorResult = -1
        val keyboard = newKeyboard(transport)

        assertThrows(AoaException::class.java) { keyboard.prepare() }
        assertFalse(keyboard.isRegistered)

        transport.descriptorResult = HidKeyboardDescriptor.SIZE
        assertEquals(2, keyboard.prepare())

        assertTrue(keyboard.isRegistered)
        assertEquals(2, transport.requests().count { it == AoaProtocol.ACCESSORY_REGISTER_HID })
    }

    @Test
    fun `el modo forzado no filtra nada sensible al registro tecnico`() {
        val transport = FakeTransport()
        transport.protocolResult = -1
        transport.protocolResponse = null

        val keyboard = newKeyboard(transport)
        keyboard.prepare(forceIfUnsupported = true)
        keyboard.sendPinAndEnter("1234")

        assertFalse(logs.any { it.contains("1234") })
        assertFalse(logs.any { it.contains("1e", ignoreCase = true) })
        assertFalse(logs.any { it.contains("1f", ignoreCase = true) })
        assertTrue(logs.contains("1234").not())
        assertTrue(logs.contains("4 digit sequence sent"))
    }

    // ------------------------------------------------- visibilidad (v1.0.2)

    @Test
    fun `la tecla de despertar va antes del PIN y no altera la secuencia`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val stats = keyboard.sendPinAndEnter("12", wakeKeyFirst = true)

        assertEquals(2, stats.digits)
        // TAB + 2 dígitos + ENTER = 4 pulsaciones = 8 reports
        assertEquals(8, stats.reports)
        assertTrue(stats.wakeKeyFirst)

        val reports = transport.sentReports()
        assertEquals(8, reports.size)
        assertEquals(HidKeycodes.TAB, reports[0][2].toInt())    // tecla de despertar
        assertTrue(reports[1].all { it.toInt() == 0 })
        assertEquals(0x1E, reports[2][2].toInt())               // '1'
        assertEquals(0x1F, reports[4][2].toInt())               // '2'
        assertEquals(HidKeycodes.ENTER, reports[6][2].toInt())  // ENTER
        assertTrue(logs.contains("wake key (TAB) sent first"))
        assertTrue(logs.any { it.startsWith("sequence summary:") })
    }

    @Test
    fun `el resumen de la secuencia no incluye digitos`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val stats = keyboard.sendPinAndEnter("1234")
        val summary = stats.summary()

        assertTrue(summary.contains("4 dígitos"))
        assertTrue(summary.contains("10 reports OK"))
        assertFalse(summary.contains("1234"))
        assertFalse(summary.contains("1e", ignoreCase = true))
        assertEquals(stats, keyboard.lastSequenceStats)
    }

    @Test
    fun `el contador de reports acumula entre secuencias`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val first = keyboard.sendPinAndEnter("1")
        val second = keyboard.sendPinAndEnter("1")

        assertEquals(4, first.reports)
        assertEquals(4, second.reports)
        assertTrue(second.durationMs >= 0)
    }

    // -------------------------------- asentamiento y reintentos (v1.0.6, caso real)

    @Test
    fun `despues del descriptor espera a que el dispositivo acepte eventos`() {
        val transport = FakeTransport()
        val keyboard = newKeyboard(transport)

        keyboard.prepare()

        val ready = logs.indexOf("HID READY")
        val waiting = logs.indexOfFirst { it.startsWith("esperando ${AoaHidKeyboard.DEFAULT_SETTLE_MS}ms") }
        val listo = logs.indexOf("listo para enviar")

        assertTrue(ready >= 0)
        assertTrue(waiting > ready)
        assertTrue(listo > waiting)
    }

    @Test
    fun `un reporte rechazado se reintenta y la secuencia termina bien`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)
        // Reproduce el caso medido: el primer evento se rechaza y el siguiente anda.
        transport.eventFailuresRemaining = 1

        val stats = keyboard.sendPinAndEnter("12")

        assertEquals(2, stats.digits)
        assertEquals(6, stats.reports)          // los reports entregados son los mismos
        assertEquals(1, stats.retries)          // y hubo un reintento de transferencia
        assertTrue(logs.any { it.contains("reintento 1/${AoaHidKeyboard.DEFAULT_REPORT_RETRIES}") })
        assertTrue(logs.any { it.contains("no entrega ninguna tecla") })
        val transfers = transport.sentReports()
        // El transporte vio 7 transferencias: la rechazada + las 6 entregadas.
        assertEquals(7, transfers.size)
        assertEquals(0x1E, transfers[0][2].toInt()) // la rechazada era el primer dígito
        assertEquals(0x1E, transfers[1][2].toInt()) // el reintento mandó exactamente lo mismo
        // Y la secuencia entregada es la esperada, sin corrimientos:
        val delivered = transfers.drop(1).map { it[2].toInt() }
        assertEquals(listOf(0x1E, 0, 0x1F, 0, 0x28, 0), delivered)
    }

    @Test
    fun `si el reporte falla siempre se agota en SEND_REPORT_FAILED`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)
        transport.eventResult = -1

        val error = assertThrows(AoaException::class.java) {
            keyboard.sendPinAndEnter("1")
        }

        assertEquals(AoaError.SEND_REPORT_FAILED, error.aoaError)
        // 1 intento + los reintentos configurados
        val attempts = transport.outTransfers(AoaProtocol.ACCESSORY_SEND_HID_EVENT).size
        assertEquals(1 + AoaHidKeyboard.DEFAULT_REPORT_RETRIES, attempts)
    }

    @Test
    fun `limpiar el campo manda doce backspace antes del pin`() {
        val transport = FakeTransport()
        val keyboard = prepared(transport)

        val stats = keyboard.sendPinAndEnter("12", clearFieldFirst = true)

        assertTrue(stats.clearedField)
        // 12 BACKSPACE + 2 dígitos + ENTER = 15 pulsaciones = 30 reports
        assertEquals(30, stats.reports)
        val reports = transport.sentReports()
        assertEquals(30, reports.size)
        assertEquals(HidKeycodes.BACKSPACE, reports[0][2].toInt())
        assertEquals(HidKeycodes.BACKSPACE, reports[22][2].toInt())
        assertTrue(reports[1].all { it.toInt() == 0 })
        assertEquals(0x1E, reports[24][2].toInt())               // recién ahí el '1'
        assertEquals(HidKeycodes.ENTER, reports[28][2].toInt())
        assertTrue(logs.any { it.contains("limpiando el campo con ${AoaHidKeyboard.CLEAR_FIELD_BACKSPACES} BACKSPACE") })
        assertTrue(stats.summary().contains("campo limpiado antes"))
    }
}
