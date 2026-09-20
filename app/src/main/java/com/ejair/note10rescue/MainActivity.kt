package com.ejair.note10rescue

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pantalla única: detectar dispositivo USB -> permiso -> preparar AOA2 HID ->
 * enviar el PIN (una sola vez) + ENTER.
 *
 * Toda la E/S USB corre en [io] (un solo hilo), nunca en el hilo de UI.
 * El PIN vive sólo en el EditText y en una variable local; no se persiste.
 */
class MainActivity : Activity() {

    private companion object {
        /** Bloqueo obligatorio del botón después de cada envío. */
        const val RESEND_COOLDOWN_MS = 10_000L
        const val MAX_PIN_DIGITS = 12
        const val MAX_LOG_CHARS = 40_000

        /** Cuántos segundos se monitorea el bus USB después de un envío. */
        const val USB_WATCH_SECONDS = 8

        /** Origen del PIN, tal como se registra en la auditoría (nunca el valor). */
        const val PIN_SOURCE_EMBEDDED = "embebido"
        const val PIN_SOURCE_TYPED = "ingresado"
    }

    private lateinit var usb: UsbDeviceManager

    private lateinit var rootScroll: ScrollView
    private lateinit var tvSummary: TextView
    private lateinit var tvAttempts: TextView
    private lateinit var cbWakeKey: CheckBox
    private lateinit var btnOk: Button
    private lateinit var tvOkHint: TextView
    private lateinit var btnShareLog: Button
    private lateinit var btnCopyLog: Button
    private lateinit var tvDevice: TextView
    private lateinit var tvVid: TextView
    private lateinit var tvPid: TextView
    private lateinit var tvProtocol: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var rgDevices: RadioGroup
    private lateinit var panelManual: LinearLayout
    private lateinit var cbForceHid: CheckBox
    private lateinit var etPin: EditText
    private lateinit var btnDetect: Button
    private lateinit var btnPrepare: Button
    private lateinit var btnSendPin: Button
    private lateinit var btnToggleManual: Button
    private lateinit var btnTab: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnEnter: Button
    private lateinit var btnUnregister: Button
    private lateinit var btnClearLog: Button

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var connection: UsbDeviceConnection? = null
    private var keyboard: AoaHidKeyboard? = null
    private var selected: UsbDeviceInfo? = null
    private var cooldownUntil = 0L
    private var manualPanelVisible = false

    /** Copia del registro técnico en memoria, para poder compartirlo. Nunca tiene el PIN. */
    private val logLines = mutableListOf<String>()
    private var attemptsSent = 0
    private var lastSequenceSummary = "—"
    private var lastUsbState = "—"
    private var appVersion = "?"

    /** Bitácora de auditoría en el almacenamiento privado de la app (sin permisos). */
    private lateinit var audit: AuditTrail

    /** true cuando el botón OK pidió preparar el HID y, al lograrlo, debe enviar. */
    private var autoSendPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin screenshots ni preview en el historial de recientes.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_main)

        tvDevice = findViewById(R.id.tvDevice)
        tvVid = findViewById(R.id.tvVid)
        tvPid = findViewById(R.id.tvPid)
        tvProtocol = findViewById(R.id.tvProtocol)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        rootScroll = findViewById(R.id.rootScroll)
        tvSummary = findViewById(R.id.tvSummary)
        tvAttempts = findViewById(R.id.tvAttempts)
        cbWakeKey = findViewById(R.id.cbWakeKey)
        btnOk = findViewById(R.id.btnOk)
        tvOkHint = findViewById(R.id.tvOkHint)
        btnShareLog = findViewById(R.id.btnShareLog)
        btnCopyLog = findViewById(R.id.btnCopyLog)
        rgDevices = findViewById(R.id.rgDevices)
        panelManual = findViewById(R.id.panelManual)
        cbForceHid = findViewById(R.id.cbForceHid)
        etPin = findViewById(R.id.etPin)
        btnDetect = findViewById(R.id.btnDetect)
        btnPrepare = findViewById(R.id.btnPrepare)
        btnSendPin = findViewById(R.id.btnSendPin)
        btnToggleManual = findViewById(R.id.btnToggleManual)
        btnTab = findViewById(R.id.btnTab)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnEnter = findViewById(R.id.btnEnter)
        btnUnregister = findViewById(R.id.btnUnregister)
        btnClearLog = findViewById(R.id.btnClearLog)

        usb = UsbDeviceManager(this)
        usb.onLog = { line -> log(line) }
        usb.onPermissionResult = { device, granted -> onPermissionResult(device, granted) }
        usb.onDeviceAttached = { device ->
            log("USB device detected (attach): ${device.deviceName}")
            refreshDevices(silent = true)
        }
        usb.onDeviceDetached = { device -> onDeviceDetached(device) }

        btnDetect.setOnClickListener { refreshDevices() }
        btnPrepare.setOnClickListener { prepareHid() }
        btnSendPin.setOnClickListener { sendPinOnce() }
        btnToggleManual.setOnClickListener { toggleManualPanel() }
        btnTab.setOnClickListener { sendKeyOnce(HidKeycodes.TAB) }
        btnBackspace.setOnClickListener { sendKeyOnce(HidKeycodes.BACKSPACE) }
        btnEnter.setOnClickListener { sendKeyOnce(HidKeycodes.ENTER) }
        btnUnregister.setOnClickListener { unregisterHid() }
        btnClearLog.setOnClickListener {
            tvLog.text = ""
            logLines.clear()
            audit.clear()
            log(getString(R.string.audit_cleared))
        }
        btnShareLog.setOnClickListener { shareLog() }
        btnCopyLog.setOnClickListener { copyLog() }
        btnOk.setOnClickListener { onOkPressed() }

        audit = AuditTrail(this)

        appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        }.getOrDefault("?")

        if (!usb.isHostSupported()) {
            log("Aviso: el sistema no declara android.hardware.usb.host")
        }
        log("Note10 Rescue HID v$appVersion — todo lo que pasa queda en este registro (nunca el PIN)")
        if (PinVault.hasEmbeddedPin) {
            log(getString(R.string.msg_embedded_pin, PinVault.embeddedPin.length))
        } else {
            log(getString(R.string.msg_no_embedded_pin))
        }
        audit.append(AuditEntry.session("inicio", PinVault.hasEmbeddedPin))
        setKeyboardControlsEnabled(false)
        log("Listo. Conectá el Note10 (USB-C a USB-C) y tocá 'Detectar dispositivos'")
    }

    override fun onStart() {
        super.onStart()
        usb.registerReceivers()
    }

    override fun onStop() {
        usb.unregisterReceivers()
        super.onStop()
    }

    override fun onDestroy() {
        usb.unregisterReceivers()
        // Limpieza de la conexión: desregistrar el HID y cerrar el dispositivo.
        val aoa = keyboard
        val conn = connection
        keyboard = null
        connection = null
        Thread {
            runCatching { aoa?.unregisterHid() }
            runCatching { conn?.close() }
        }.start()
        main.removeCallbacksAndMessages(null)
        io.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- detectar

    private fun refreshDevices(silent: Boolean = false) {
        rgDevices.removeAllViews()
        val devices = usb.listDevices()
        if (devices.isEmpty()) {
            tvDevice.text = getString(R.string.device_none)
            if (!silent) {
                onAoaError(AoaException(AoaError.NO_USB_DEVICE, "UsbManager.deviceList() vacío"))
            }
            return
        }
        log("USB device detected: ${devices.size}")
        devices.forEach { info ->
            log(
                "device: ${info.deviceName} VID=0x%04X PID=0x%04X".format(
                    info.vendorId, info.productId
                )
            )
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = info.label()
                tag = info
            }
            radio.setOnClickListener { onDeviceSelected(info) }
            rgDevices.addView(radio)
        }
        val previous = selected?.deviceName
        if (previous != null) {
            for (index in 0 until rgDevices.childCount) {
                val child = rgDevices.getChildAt(index) as RadioButton
                val deviceInfo = child.tag as? UsbDeviceInfo
                if (deviceInfo?.deviceName == previous) {
                    child.isChecked = true
                    break
                }
            }
        }
    }

    private fun onDeviceSelected(info: UsbDeviceInfo) {
        selected = info
        tvDevice.text = "Dispositivo: ${info.label()}"
        tvVid.text = "VID: 0x%04X".format(info.vendorId)
        tvPid.text = "PID: 0x%04X".format(info.productId)
        log("Dispositivo elegido: ${info.deviceName}")
        if (!info.isSamsung()) {
            log("Aviso: vendor 0x%04X no es Samsung; se permite igual".format(info.vendorId))
        }
        if (usb.hasPermission(info.device)) {
            log("USB permission granted")
            onPermissionGranted()
        } else {
            log("Solicitando permiso USB…")
            try {
                usb.requestPermission(info.device)
            } catch (e: AoaException) {
                onAoaError(e)
            }
        }
    }

    private fun onPermissionResult(device: UsbDevice, granted: Boolean) {
        if (selected?.deviceName != device.deviceName) return
        if (granted) {
            onPermissionGranted()
        } else {
            btnPrepare.isEnabled = false
            onAoaError(AoaException(AoaError.USB_PERMISSION_DENIED, "device=${device.deviceName}"))
        }
    }

    private fun onPermissionGranted() {
        btnPrepare.isEnabled = true
        setStatus(getString(R.string.status_idle))
        // Si el envío directo había quedado esperando el permiso, seguimos acá.
        if (autoSendPending) prepareHid()
    }

    private fun onDeviceDetached(device: UsbDevice) {
        log("DEVICE_DISCONNECTED: ${device.deviceName}")
        if (selected?.deviceName == device.deviceName) {
            closeConnection(getString(R.string.status_disconnected))
        }
        refreshDevices(silent = true)
    }

    // ------------------------------------------------------------------- AOA

    private fun prepareHid() {
        val info = selected
        if (info == null) {
            onAoaError(AoaException(AoaError.NO_USB_DEVICE, "ningún dispositivo seleccionado"))
            return
        }
        if (usb.hasPermission(info.device).not()) {
            onAoaError(AoaException(AoaError.USB_PERMISSION_DENIED, "device=${info.deviceName}"))
            return
        }
        val existing = keyboard
        if (existing != null && existing.isRegistered) {
            log("HID ya estaba preparado (hidId=${AoaProtocol.HID_ID_KEYBOARD})")
            setStatus(getString(R.string.status_ready))
            continueAutoSendIfPending()
            return
        }

        val force = cbForceHid.isChecked
        btnPrepare.isEnabled = false
        setStatus(getString(R.string.status_working))

        io.execute {
            try {
                val conn = connection ?: usb.open(info.device).also { opened ->
                    connection = opened
                    main.post { log("Connection opened") }
                }
                val aoa = AoaHidKeyboard(UsbControlTransport(conn))
                aoa.onEvent = { line -> main.post { log(line) } }
                // GET_PROTOCOL -> REGISTER_HID -> SET_HID_REPORT_DESC (sin loops).
                val version = aoa.prepare(forceIfUnsupported = force)
                keyboard = aoa
                main.post {
                    if (version == AoaHidKeyboard.PROTOCOL_UNKNOWN) {
                        // GET_PROTOCOL no respondió: no mostrar "-1" como versión.
                        tvProtocol.text = getString(R.string.aoa_protocol_forced)
                        setStatus(getString(R.string.status_ready_forced))
                    } else {
                        tvProtocol.text = getString(R.string.aoa_protocol_fmt, version)
                        setStatus(getString(R.string.status_ready))
                    }
                    setKeyboardControlsEnabled(true)
                    btnPrepare.isEnabled = true
                    continueAutoSendIfPending()
                }
            } catch (e: AoaException) {
                main.post {
                    keyboard = null
                    autoSendPending = false
                    btnPrepare.isEnabled = true
                    onAoaError(e)
                }
            } catch (t: Throwable) {
                main.post {
                    keyboard = null
                    autoSendPending = false
                    btnPrepare.isEnabled = true
                    onAoaError(
                        AoaException(
                            AoaError.OPEN_DEVICE_FAILED,
                            "${t.javaClass.simpleName}: ${t.message ?: ""}"
                        )
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------- PIN

    /** Botón manual: usa lo que el usuario escribió en el campo PIN. */
    private fun sendPinOnce() {
        val typed = etPin.text.toString()
        if (typed.isEmpty()) {
            log(getString(R.string.msg_pin_empty))
            return
        }
        if (typed.any { it !in '0'..'9' }) {
            log(getString(R.string.msg_pin_digits_only))
            return
        }
        if (typed.length > MAX_PIN_DIGITS) {
            log(getString(R.string.msg_pin_too_long))
            return
        }
        // El campo se limpia de inmediato: el PIN no se guarda ni se muestra.
        etPin.setText("")
        sendSequence(typed, PIN_SOURCE_TYPED)
    }

    /**
     * Botón OK: hace todo el pipeline (elegir dispositivo, pedir permiso, preparar
     * HID) y al terminar envía **una** secuencia. Un toque = una secuencia, con el
     * mismo cooldown de 10 s: no hay reintentos automáticos ni nada en loop.
     */
    private fun onOkPressed() {
        val pin = resolvePin()
        if (pin.isEmpty()) {
            log(getString(R.string.msg_no_pin_available))
            onAoaError(AoaException(AoaError.INVALID_PIN, "sin PIN (ni embebido ni escrito)"))
            return
        }
        val aoa = keyboard
        if (aoa != null && aoa.isRegistered) {
            sendSequence(pin, pinSourceLabel())
            return
        }

        autoSendPending = true
        log(getString(R.string.msg_auto_preparing))
        val info = selected ?: autoSelectDevice()
        if (info == null) {
            autoSendPending = false
            onAoaError(AoaException(AoaError.NO_USB_DEVICE, "deviceList() vacío"))
            return
        }
        if (!usb.hasPermission(info.device)) {
            log("Envío directo: solicitando permiso USB…")
            try {
                usb.requestPermission(info.device)
            } catch (e: AoaException) {
                autoSendPending = false
                onAoaError(e)
            }
            return
        }
        prepareHid()
    }

    /** Continúa el envío pedido desde OK cuando el HID terminó de prepararse. */
    private fun continueAutoSendIfPending() {
        if (!autoSendPending) return
        autoSendPending = false
        val pin = resolvePin()
        if (pin.isEmpty()) {
            log(getString(R.string.msg_no_pin_available))
            return
        }
        sendSequence(pin, pinSourceLabel())
    }

    /** PIN a usar: el embebido si existe; si no, el que está escrito en el campo. */
    private fun resolvePin(): String =
        if (PinVault.hasEmbeddedPin) PinVault.embeddedPin else etPin.text.toString()

    private fun pinSourceLabel(): String =
        if (PinVault.hasEmbeddedPin) PIN_SOURCE_EMBEDDED else PIN_SOURCE_TYPED

    /** Elige el dispositivo solo: el Samsung si hay uno, si no el primero de la lista. */
    private fun autoSelectDevice(): UsbDeviceInfo? {
        val devices = usb.listDevices()
        if (devices.isEmpty()) return null
        val info = devices.firstOrNull { it.isSamsung() } ?: devices.first()
        selected = info
        tvDevice.text = "Dispositivo: ${info.label()}"
        tvVid.text = "VID: 0x%04X".format(info.vendorId)
        tvPid.text = "PID: 0x%04X".format(info.productId)
        log("${getString(R.string.msg_auto_device)} ${info.deviceName}")
        return info
    }

    /**
     * Envía exactamente UNA secuencia con el PIN resuelto, con cooldown y auditoría.
     * El PIN sólo vive en esta llamada mientras se envía; nunca se registra.
     */
    private fun sendSequence(pin: String, pinSource: String) {
        val aoa = keyboard
        if (aoa == null || !aoa.isRegistered) {
            log(getString(R.string.msg_hid_not_prepared))
            onAoaError(AoaException(AoaError.HID_NOT_PREPARED))
            return
        }
        if (System.currentTimeMillis() < cooldownUntil) {
            log(getString(R.string.msg_cooldown_active))
            return
        }
        if (pin.isEmpty() || pin.any { it !in '0'..'9' }) {
            log(getString(R.string.msg_pin_digits_only))
            onAoaError(AoaException(AoaError.INVALID_PIN, "pin inválido"))
            return
        }

        startCooldown()
        val number = attemptsSent + 1
        val wakeKeyFirst = cbWakeKey.isChecked
        io.execute {
            try {
                // Exactamente UNA secuencia: dígitos + ENTER (+ TAB opcional antes).
                val stats = aoa.sendPinAndEnter(pin, wakeKeyFirst = wakeKeyFirst)
                audit.append(AuditEntry.attempt(number, pinSource, stats))
                main.post {
                    attemptsSent = number
                    updateAttempts()
                    lastSequenceSummary = stats.summary()
                    tvSummary.text = getString(R.string.summary_fmt, lastSequenceSummary)
                    setStatus(getString(R.string.status_ready))
                    log("Envío #$number (PIN $pinSource): ${stats.summary()}")
                    log("Esperá 3-5 s antes de concluir nada (el desbloqueo puede demorar)")
                }
                watchTargetUsbState()
            } catch (e: AoaException) {
                audit.append(AuditEntry.failure(number, e.aoaError, e.detail))
                main.post { onAoaError(e) }
            }
        }
    }

    /**
     * Monitorea el bus USB durante unos segundos después del envío.
     *
     * Es el **único** indicio externo que tenemos del lado del host: cuando un
     * Android se desbloquea suele cambiar su configuración USB y re-enumerar. Que
     * no haya cambios NO confirma que no se haya desbloqueado.
     */
    private fun watchTargetUsbState() {
        val reference = usb.snapshot()
        main.post {
            lastUsbState = UsbDeviceManager.describeSnapshots(reference)
            log("Monitor USB: ${lastUsbState}")
        }
        io.execute {
            var current = reference
            repeat(USB_WATCH_SECONDS) {
                try {
                    Thread.sleep(1000L)
                } catch (e: InterruptedException) {
                    return@execute
                }
                val now = usb.snapshot()
                if (UsbDeviceManager.hasChanges(current, now)) {
                    val detail = UsbDeviceManager.diffSnapshots(current, now)
                    current = now
                    audit.append(AuditEntry.usbObservation(detail))
                    main.post {
                        lastUsbState = detail
                        log("CAMBIO USB: $detail  <- indicio de cambio de estado del Note10")
                    }
                }
            }
            main.post { log("Monitor USB: fin de la ventana de ${USB_WATCH_SECONDS}s") }
        }
    }

    private fun updateAttempts() {
        val base = getString(R.string.attempts_count, attemptsSent)
        tvAttempts.text = if (attemptsSent >= 5) {
            "$base\n${getString(R.string.attempts_warning)}"
        } else {
            base
        }
    }

    /** Bloquea el botón 10 s y lo rehabilita sólo con el estado HID válido. */
    private fun startCooldown() {
        cooldownUntil = System.currentTimeMillis() + RESEND_COOLDOWN_MS
        btnSendPin.isEnabled = false
        tickCooldown()
    }

    private fun tickCooldown() {
        val remaining = cooldownUntil - System.currentTimeMillis()
        if (remaining <= 0L) {
            btnSendPin.text = getString(R.string.send_pin_once)
            setKeyboardControlsEnabled(keyboard?.isRegistered == true)
            return
        }
        btnSendPin.text = getString(R.string.wait_seconds, ((remaining + 999) / 1000).toInt())
        tvOkHint.text = getString(R.string.msg_cooldown_active)
        main.postDelayed({ tickCooldown() }, 250)
    }

    // ------------------------------------------------------- controles manuales

    private fun toggleManualPanel() {
        manualPanelVisible = !manualPanelVisible
        panelManual.visibility = if (manualPanelVisible) View.VISIBLE else View.GONE
        btnToggleManual.text = getString(
            if (manualPanelVisible) R.string.hide_manual_controls else R.string.show_manual_controls
        )
        setKeyboardControlsEnabled(keyboard?.isRegistered == true)
    }

    private fun sendKeyOnce(keycode: Int) {
        val aoa = keyboard
        if (aoa == null || !aoa.isRegistered) {
            log(getString(R.string.msg_hid_not_prepared))
            return
        }
        io.execute {
            try {
                aoa.sendKeyOnce(keycode)
            } catch (e: AoaException) {
                main.post { onAoaError(e) }
            }
        }
    }

    private fun unregisterHid() {
        val aoa = keyboard ?: return
        io.execute {
            val ok = runCatching { aoa.unregisterHid() }.getOrDefault(false)
            main.post {
                keyboard = null
                setKeyboardControlsEnabled(false)
                setStatus(getString(if (ok) R.string.status_unregistered else R.string.status_error))
            }
        }
    }

    // ------------------------------------------------------- reporte / registro

    /** Arma el texto del reporte. El registro nunca contiene el PIN ni keycodes. */
    private fun buildReport(): String {
        val host = "%s %s / Android %s (API %d)".format(
            Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT
        )
        return DiagnosticsReport.build(
            appVersion = appVersion,
            hostSummary = host,
            targetSummary = selected?.label() ?: "sin dispositivo seleccionado",
            protocolSummary = tvProtocol.text.toString(),
            statusSummary = tvStatus.text.toString(),
            attempts = attemptsSent,
            lastSequence = lastSequenceSummary,
            usbState = lastUsbState,
            logLines = logLines.toList(),
            auditTrail = audit.read()
        )
    }

    /** Abre el menú de compartir con el registro técnico (no necesita permisos). */
    private fun shareLog() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_log_subject))
            putExtra(Intent.EXTRA_TEXT, buildReport())
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_log_chooser)))
        } catch (e: Exception) {
            log(getString(R.string.log_share_failed) + " (${e.javaClass.simpleName})")
        }
    }

    /** Copia el registro al portapapeles (sólo el registro: nunca el PIN). */
    private fun copyLog() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            log(getString(R.string.log_clipboard_unavailable))
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.share_log_subject), buildReport())
        )
        log(getString(R.string.log_copied))
    }

    // ------------------------------------------------------------------ común

    private fun setKeyboardControlsEnabled(enabled: Boolean) {
        val ready = enabled && System.currentTimeMillis() >= cooldownUntil
        val okReady = System.currentTimeMillis() >= cooldownUntil
        btnSendPin.isEnabled = ready
        // El botón OK queda disponible aunque el HID no esté preparado: él mismo hace
        // el pipeline (elegir dispositivo, pedir permiso, preparar HID) y recién envía.
        btnOk.isEnabled = okReady
        btnTab.isEnabled = enabled
        btnBackspace.isEnabled = enabled
        btnEnter.isEnabled = enabled
        btnUnregister.isEnabled = enabled
        if (!enabled) {
            btnSendPin.text = getString(R.string.send_pin_once)
        }
        tvOkHint.text = when {
            !okReady -> getString(R.string.msg_cooldown_active)
            ready -> getString(R.string.ok_hint_ready)
            else -> getString(R.string.ok_hint)
        }
    }

    private fun closeConnection(statusText: String) {
        keyboard = null
        runCatching { connection?.close() }
        connection = null
        btnPrepare.isEnabled = false
        setKeyboardControlsEnabled(false)
        setStatus(statusText)
    }

    private fun setStatus(text: String) {
        tvStatus.text = text
    }

    private fun onAoaError(error: AoaException) {
        if (error.aoaError == AoaError.DEVICE_DISCONNECTED) {
            closeConnection(getString(R.string.status_disconnected))
        }
        log("ERROR ${error.message}")
        setStatus("${getString(R.string.status_error)} ${error.aoaError.code}")
    }

    /** Registro técnico. Nunca recibe ni imprime el PIN ni keycodes. */
    private fun log(message: String) {
        if (tvLog.text.length > MAX_LOG_CHARS) {
            tvLog.text = ""
            logLines.clear()
        }
        val line = "${timeFormat.format(Date())}  $message"
        tvLog.append("$line\n")
        logLines.add(line)
        while (logLines.size > DiagnosticsReport.MAX_LOG_LINES) {
            logLines.removeAt(0)
        }
        // Auto-scroll: el registro queda siempre mostrando lo último que pasó.
        rootScroll.post { rootScroll.fullScroll(View.FOCUS_DOWN) }
    }
}
