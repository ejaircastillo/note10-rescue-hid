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
    /**
     * Si el transporte es simulado. Es una propiedad del teclado preparado, no una
     * casilla de la UI: el modo real se decide al **preparar** el HID, así que el log,
     * la auditoría y el contador de intentos tienen que leerlo de acá. Cuando esto se
     * leía de la casilla, un "modo de prueba" podía estar escribiendo de verdad en el
     * target (bug real: el log decía "no se toca el Note10" y salieron 10 reports).
     */
    val simulated: Boolean = false,
    private val hidId: Int = AoaProtocol.HID_ID_KEYBOARD,
    private val reportDescriptor: ByteArray = HidKeyboardDescriptor.REPORT_DESCRIPTOR,
    private val keyHoldMs: Long = DEFAULT_KEY_HOLD_MS,
    private val eventIntervalMs: Long = DEFAULT_EVENT_INTERVAL_MS,
    private val timeoutMs: Int = AoaProtocol.DEFAULT_TIMEOUT_MS,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val maxReportRetries: Int = DEFAULT_REPORT_RETRIES,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val slumber: (Long) -> Unit = { ms -> Thread.sleep(ms) }
) {

    companion object {
        /** Tiempo entre KEY DOWN y KEY RELEASE (espera corta). */
        const val DEFAULT_KEY_HOLD_MS = 30L

        /** Tiempo entre eventos HID consecutivos (~60-100 ms recomendado). */
        const val DEFAULT_EVENT_INTERVAL_MS = 80L

        /**
         * Espera después de SET_HID_REPORT_DESC antes de mandar el primer evento.
         *
         * Medido en un Note10 real: si el primer SEND_HID_EVENT sale inmediatamente
         * después del descriptor, el dispositivo lo rechaza (`result=-1`) porque el
         * lado Android todavía no terminó de crear el dispositivo HID. Unos segundos
         * después el mismo evento pasa sin problema.
         */
        const val DEFAULT_SETTLE_MS = 1500L

        /**
         * Reintentos de un report **rechazado** (transferencia fallida). Un control
         * transfer que devuelve -1 no entregó ninguna tecla, así que reintentarlo no
         * duplica pulsaciones ni cuenta como intento de desbloqueo.
         */
        const val DEFAULT_REPORT_RETRIES = 3

        /** Espera entre reintentos de un report rechazado. */
        const val DEFAULT_RETRY_DELAY_MS = 1000L

        /** BACKSPACEs que manda la opción "limpiar campo" antes del PIN. */
        const val CLEAR_FIELD_BACKSPACES = 12

        /**
         * Resultado de `prepare()` cuando ACCESSORY_GET_PROTOCOL falló y el
         * usuario había pedido explícitamente el intento HID forzado. No es una
         * versión de protocolo real: la UI debe mostrarlo como
         * "no disponible / forzado", nunca como `-1`.
         */
        const val PROTOCOL_UNKNOWN = -1

        private const val MAX_RECORDED_TRANSFERS = 64
    }

    /** Resultado de una secuencia, para mostrarlo sin exponer los dígitos. */
    data class SequenceStats(
        val digits: Int,
        val reports: Int,
        val durationMs: Long,
        val wakeKeyFirst: Boolean,
        val retries: Int = 0,
        val clearedField: Boolean = false
    ) {
        fun summary(): String = buildString {
            append("$digits dígitos, $reports reports OK, ${durationMs}ms")
            if (wakeKeyFirst) append(" (con tecla de despertar previa)")
            if (clearedField) append(" (campo limpiado antes)")
            if (retries > 0) append(" [$retries reintentos de transferencia]")
        }
    }

    /** Callback de registro técnico (nunca incluye contenido sensible). */
    var onEvent: ((String) -> Unit)? = null

    private val recorded = mutableListOf<TransferResult>()
    private var registered = false
    private var reportsSent = 0
    private var reportsRetried = 0

    /** Última secuencia enviada (sin dígitos: sólo cantidades y tiempos). */
    var lastSequenceStats: SequenceStats? = null
        private set

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
        // Invariante: antes de REGISTER_HID el teclado no está registrado.
        registered = false
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
     * @param forceIfUnsupported intento HID forzado, sólo si el usuario lo pidió
     *   explícitamente. Cubre los **dos** casos en que la consulta de protocolo
     *   no habilita el camino normal:
     *   - GET_PROTOCOL responde < 2 (se lanzaría `AOA_PROTOCOL_UNSUPPORTED`),
     *   - GET_PROTOCOL falla (se lanzaría `AOA_PROTOCOL_QUERY_FAILED`).
     *   En ambos se intenta registrar el HID **una única vez**: no hay loops ni
     *   reintentos automáticos.
     * @return la versión de protocolo informada por el dispositivo, o
     *   [PROTOCOL_UNKNOWN] si GET_PROTOCOL falló y el intento fue forzado.
     */
    @Synchronized
    fun prepare(forceIfUnsupported: Boolean = false): Int {
        val version = queryProtocolVersionOrUnknown(forceIfUnsupported)

        if (version != PROTOCOL_UNKNOWN && version < 2) {
            if (!forceIfUnsupported) {
                throw AoaException(AoaError.AOA_PROTOCOL_UNSUPPORTED, "protocol=$version")
            }
            log("AOA protocol < 2: forced HID attempt requested")
        }

        registerHid()
        try {
            setReportDescriptor()
        } catch (e: AoaException) {
            // Igual que scrcpy (sc_aoa_setup_hid en aoa_hid.c): si el descriptor
            // falla, se desregistra el HID para no dejar el hidId tomado en el
            // dispositivo. El error del cleanup no debe ocultar el error original.
            runCatching { unregisterHid() }
            throw e
        }
        log("HID READY")
        // El lado Android necesita un instante para aceptar eventos después del
        // descriptor: sin esta espera el primer report puede ser rechazado (-1).
        if (settleMs > 0) {
            log("esperando ${settleMs}ms a que el dispositivo acepte eventos HID")
            slumber(settleMs)
            log("listo para enviar")
        }
        return version
    }

    /**
     * ACCESSORY_GET_PROTOCOL sin abortar el flujo cuando el usuario ya pidió el
     * intento forzado: en ese caso devuelve [PROTOCOL_UNKNOWN] en lugar de
     * propagar el error. Un GET_PROTOCOL fallido sin autorización explícita (o
     * cualquier otro error) se propaga tal cual.
     */
    private fun queryProtocolVersionOrUnknown(forceIfUnsupported: Boolean): Int {
        return try {
            getProtocolVersion()
        } catch (e: AoaException) {
            if (!forceIfUnsupported || e.aoaError != AoaError.AOA_PROTOCOL_QUERY_FAILED) {
                throw e
            }
            log("GET_PROTOCOL failed; forced HID attempt requested")
            PROTOCOL_UNKNOWN
        }
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

    /**
     * ACCESSORY_SEND_HID_EVENT (57): value = hidId, index = 0, data = report.
     *
     * Si el dispositivo rechaza la transferencia (`result=-1`), reintenta el **mismo**
     * report hasta [maxReportRetries] veces. Es seguro: una transferencia rechazada no
     * entregó ninguna tecla, así que no duplica pulsaciones ni consume intentos de
     * desbloqueo. Si se agotan los reintentos, propaga `SEND_REPORT_FAILED`.
     */
    @Synchronized
    fun sendReport(report: ByteArray) {
        require(report.size == HidKeyboardReports.REPORT_SIZE) {
            "El report HID debe tener ${HidKeyboardReports.REPORT_SIZE} bytes"
        }
        requireRegistered()
        var attempt = 0
        while (true) {
            val result = transport.transferOut(
                AoaProtocol.ACCESSORY_SEND_HID_EVENT, hidId, 0, report, timeoutMs
            )
            record("SEND_HID_EVENT", result)
            if (result.ok) {
                reportsSent++
                return
            }
            attempt++
            if (attempt > maxReportRetries) {
                throw AoaException(AoaError.SEND_REPORT_FAILED, result.technicalDetail())
            }
            reportsRetried++
            log(
                "SEND_HID_EVENT rechazado (result=${result.bytesTransferred}); " +
                    "reintento $attempt/$maxReportRetries en ${retryDelayMs}ms " +
                    "(una transferencia rechazada no entrega ninguna tecla)"
            )
            slumber(retryDelayMs)
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
     * Una sola pasada, sin reintentos de secuencia.
     *
     * @param wakeKeyFirst opción manual explícita: manda una TAB antes del PIN.
     *   Sirve para el caso en que la pantalla estaba apagada, porque Android suele
     *   consumir el primer evento de teclado para despertarla (y entonces el PIN
     *   entraría corrido, sin el primer dígito).
     * @param clearFieldFirst opción manual: manda [CLEAR_FIELD_BACKSPACES] BACKSPACE
     *   antes del PIN, para que un intento anterior cortado a mitad de camino no deje
     *   dígitos pegados en el campo del bloqueo.
     * @return [SequenceStats] con cantidades y duración (nunca los dígitos).
     */
    @Synchronized
    fun sendPinAndEnter(
        pin: CharSequence,
        wakeKeyFirst: Boolean = false,
        clearFieldFirst: Boolean = false
    ): SequenceStats {
        val digits = pin.toString()
        if (digits.isEmpty()) {
            throw AoaException(AoaError.INVALID_PIN, "vacío")
        }
        if (digits.any { it !in '0'..'9' }) {
            throw AoaException(AoaError.INVALID_PIN, "sólo se permiten dígitos")
        }
        requireRegistered()

        val started = System.nanoTime()
        val reportsBefore = reportsSent
        val retriesBefore = reportsRetried

        if (wakeKeyFirst) {
            log("wake key (TAB) sent first")
            pressKey(HidKeycodes.TAB)
        }
        if (clearFieldFirst) {
            log("limpiando el campo con $CLEAR_FIELD_BACKSPACES BACKSPACE antes del PIN")
            repeat(CLEAR_FIELD_BACKSPACES) { pressKey(HidKeycodes.BACKSPACE) }
        }
        for (digit in digits) {
            pressKey(HidKeycodes.forDigit(digit))
        }
        pressKey(HidKeycodes.ENTER)

        val stats = SequenceStats(
            digits = digits.length,
            reports = reportsSent - reportsBefore,
            durationMs = (System.nanoTime() - started) / 1_000_000L,
            wakeKeyFirst = wakeKeyFirst,
            retries = reportsRetried - retriesBefore,
            clearedField = clearFieldFirst
        )
        lastSequenceStats = stats
        log("${digits.length} digit sequence sent")
        log("ENTER sent")
        log("sequence summary: ${stats.summary()}")
        return stats
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
