package com.ejair.note10rescue

/**
 * Teclado AOA2 HID: habla el protocolo Android Open Accessory 2.0 sobre un
 * [ControlTransport] inyectado.
 *
 * **No depende de Android ni del USB**: se puede testear con un transporte falso
 * (ver `AoaHidKeyboardTest`).
 *
 * Secuencia por tecla (igual que un teclado USB físico):
 *   KEY DOWN -> espera corta ([keyHoldMs]) -> KEY RELEASE (report de ceros)
 * y luego [eventIntervalMs] antes del siguiente evento.
 *
 * Nunca automatiza nada: cada llamada envía exactamente lo que se le pide.
 * Nunca registra dígitos ni keycodes.
 */
class AoaHidKeyboard(
    private val transport: ControlTransport,
    private val hidId: Int = AoaProtocol.HID_ID_KEYBOARD,
    private val reportDescriptor: ByteArray = HidKeyboardDescriptor.REPORT_DESCRIPTOR,
    private val keyHoldMs: Long = DEFAULT_KEY_HOLD_MS,
    private val eventIntervalMs: Long = DEFAULT_EVENT_INTERVAL_MS,
    private val timeoutMs: Int = AoaProtocol.DEFAULT_TIMEOUT_MS,
    private val slumber: (Long) -> Unit = { ms -> Thread.sleep(ms) }
) {

    companion object {
        /** Tiempo entre KEY DOWN y KEY RELEASE (espera corta). */
        const val DEFAULT_KEY_HOLD_MS = 30L

        /** Tiempo entre eventos HID consecutivos (~60-100 ms recomendado). */
        const val DEFAULT_EVENT_INTERVAL_MS = 80L

        private const val MAX_RECORDED_TRANSFERS = 64
    }

    /** Callback de registro técnico (nunca incluye contenido sensible). */
    var onEvent: ((String) -> Unit)? = null

    private val recorded = mutableListOf<TransferResult>()
    private var registered = false

    var lastProtocolVersion: Int = -1
        private set

    val isRegistered: Boolean get() = registered

    /** Últimos control transfers: request / result / duración. */
    val transfers: List<TransferResult>
        @Synchronized get() = recorded.toList()

    /**
     * ACCESSORY_GET_PROTOCOL (51) — control transfer IN (0xC0), 2 bytes,
     * uint16 little-endian.
     */
    @Synchronized
    fun getProtocolVersion(): Int {
        val inTransfer = transport.transferIn(
            AoaProtocol.ACCESSORY_GET_PROTOCOL, 0, 0, 2, timeoutMs
        )
        record("GET_PROTOCOL", inTransfer.result)
        val data = inTransfer.data
        if (!inTransfer.result.ok || data == null || data.size < 2) {
            throw AoaException(
                AoaError.AOA_PROTOCOL_QUERY_FAILED,
                inTransfer.result.technicalDetail()
            )
        }
        // uint16 little-endian: byte bajo primero.
        val version = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        lastProtocolVersion = version
        log("AOA protocol: $version")
        return version
    }

    /**
     * ACCESSORY_REGISTER_HID (54): value = hidId,
     * index = longitud del report descriptor, sin data.
     */
    @Synchronized
    fun registerHid() {
        val result = transport.transferOut(
            AoaProtocol.ACCESSORY_REGISTER_HID, hidId, reportDescriptor.size, null, timeoutMs
        )
        record("REGISTER_HID", result)
        if (!result.ok) {
            throw AoaException(AoaError.REGISTER_HID_FAILED, result.technicalDetail())
        }
        registered = true
        log("REGISTER_HID: OK")
    }

    /**
     * ACCESSORY_SET_HID_REPORT_DESC (56): value = hidId, index = 0,
     * data = descriptor completo. scrcpy manda el descriptor entero en un solo
     * control transfer y deja la fragmentación en paquetes a la capa USB.
     */
    @Synchronized
    fun setReportDescriptor() {
        val result = transport.transferOut(
            AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC, hidId, 0, reportDescriptor, timeoutMs
        )
        record("SET_HID_REPORT_DESC", result)
        if (!result.ok) {
            throw AoaException(AoaError.SET_DESCRIPTOR_FAILED, result.technicalDetail())
        }
        log("SET_HID_REPORT_DESC: OK")
    }

    /**
     * Flujo completo: GET_PROTOCOL -> REGISTER_HID -> SET_HID_REPORT_DESC.
     *
     * @param forceIfUnsupported fallback manual del usuario: si GET_PROTOCOL
     *   devuelve < 2, igual intenta registrar el HID. Es un único intento, no hay
     *   loop ni reintento automático.
     * @return la versión de protocolo informada por el dispositivo.
     */
    @Synchronized
    fun prepare(forceIfUnsupported: Boolean = false): Int {
        val version = getProtocolVersion()
        if (version < 2 && !forceIfUnsupported) {
            throw AoaException(AoaError.AOA_PROTOCOL_UNSUPPORTED, "protocol=$version")
        }
        if (version < 2) {
            log("AOA protocol < 2: intento HID forzado por el usuario")
        }
        registerHid()
        setReportDescriptor()
        log("HID READY")
        return version
    }

    /** ACCESSORY_UNREGISTER_HID (55): value = hidId, index = 0. */
    @Synchronized
    fun unregisterHid(): Boolean {
        val result = transport.transferOut(
            AoaProtocol.ACCESSORY_UNREGISTER_HID, hidId, 0, null, timeoutMs
        )
        record("UNREGISTER_HID", result)
        registered = false
        if (!result.ok) {
            log("UNREGISTER_HID: FAILED (${result.technicalDetail()})")
            return false
        }
        log("UNREGISTER_HID: OK")
        return true
    }

    /** ACCESSORY_SEND_HID_EVENT (57): value = hidId, index = 0, data = report. */
    @Synchronized
    fun sendReport(report: ByteArray) {
        require(report.size == HidKeyboardReports.REPORT_SIZE) {
            "El report HID debe tener ${HidKeyboardReports.REPORT_SIZE} bytes"
        }
        requireRegistered()
        val result = transport.transferOut(
            AoaProtocol.ACCESSORY_SEND_HID_EVENT, hidId, 0, report, timeoutMs
        )
        record("SEND_HID_EVENT", result)
        if (!result.ok) {
            throw AoaException(AoaError.SEND_REPORT_FAILED, result.technicalDetail())
        }
    }

    /** Una pulsación completa: KEY DOWN -> KEY RELEASE -> pausa entre eventos. */
    @Synchronized
    fun pressKey(keycode: Int, modifiers: Int = HidKeyboardReports.MOD_NONE) {
        sendReport(HidKeyboardReports.keyDown(keycode, modifiers))
        slumber(keyHoldMs)
        sendReport(HidKeyboardReports.keyRelease())
        slumber(eventIntervalMs)
    }

    /**
     * Envía la secuencia de [pin] (sólo dígitos) seguida de ENTER.
     * Una sola pasada, sin reintentos.
     *
     * @return la cantidad de dígitos enviados (nunca los dígitos).
     */
    @Synchronized
    fun sendPinAndEnter(pin: CharSequence): Int {
        val digits = pin.toString()
        if (digits.isEmpty()) {
            throw AoaException(AoaError.INVALID_PIN, "vacío")
        }
        if (digits.any { it !in '0'..'9' }) {
            throw AoaException(AoaError.INVALID_PIN, "sólo se permiten dígitos")
        }
        requireRegistered()
        for (digit in digits) {
            pressKey(HidKeycodes.forDigit(digit))
        }
        pressKey(HidKeycodes.ENTER)
        log("${digits.length} digit sequence sent")
        log("ENTER sent")
        return digits.length
    }

    /** Tecla suelta de los controles manuales (TAB, BACKSPACE, ENTER). */
    @Synchronized
    fun sendKeyOnce(keycode: Int) {
        requireRegistered()
        pressKey(keycode)
        log("KEY sent: ${HidKeycodes.safeLabel(keycode)}")
    }

    private fun requireRegistered() {
        if (!registered) {
            throw AoaException(AoaError.HID_NOT_PREPARED)
        }
    }

    private fun record(operation: String, result: TransferResult) {
        synchronized(recorded) {
            if (recorded.size >= MAX_RECORDED_TRANSFERS) recorded.removeAt(0)
            recorded.add(result)
        }
        log("$operation: request=${result.request} result=${result.bytesTransferred} duration=${result.durationMs}ms")
    }

    private fun log(message: String) {
        onEvent?.invoke(message)
    }
}
