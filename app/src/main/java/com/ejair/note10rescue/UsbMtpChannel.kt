package com.ejair.note10rescue

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Canal MTP sobre el USB host: reclama la interfaz MTP del target y usa sus endpoints
 * bulk.
 *
 * Reutiliza la conexión que ya está abierta para el HID: los control transfers del HID
 * van por EP0 y no chocan con los endpoints bulk de MTP. Así la comprobación de
 * desbloqueo viaja por el **mismo cable** que ya se usa para escribir el PIN (no hace
 * falta una PC).
 *
 * Nada de esto escribe en el target: sólo lee el resultado de `GetStorageIDs`.
 */
class UsbMtpChannel(
    device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val bulkTimeoutMs: Int = BULK_TIMEOUT_MS
) : MtpProbe.Channel, AutoCloseable {

    companion object {
        /** Clase 6 = Still Imaging (PTP/MTP). */
        const val MTP_CLASS = 6
        const val MTP_SUBCLASS = 1

        /** Protocolo 1 = MTP (el 2 es PTP puro, el que usan las cámaras). */
        const val MTP_PROTOCOL = 1

        const val BULK_TIMEOUT_MS = 3000

        /** CLEAR_FEATURE(ENDPOINT_HALT): 0x02 = endpoint, 0x01 = CLEAR_FEATURE, 0x0000 = halt. */
        private const val REQUEST_TYPE_CLEAR_FEATURE = 0x02
        private const val REQUEST_CLEAR_FEATURE = 0x01
        private const val FEATURE_ENDPOINT_HALT = 0x0000
        private const val CLEAR_HALT_TIMEOUT_MS = 500

        /** Tamaño de lectura: alcanza para cualquier contenedor de MTP razonable. */
        private const val READ_CHUNK = 16 * 1024

        /**
         * Interfaz MTP del dispositivo. Si no hay ninguna, el target está en modo
         * "sólo cargar" (o no expone MTP) y la sonda no puede concluir nada.
         */
        fun findMtpInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == MTP_CLASS &&
                    iface.interfaceSubclass == MTP_SUBCLASS &&
                    iface.interfaceProtocol == MTP_PROTOCOL
                ) {
                    return iface
                }
            }
            // Algunos equipos exponen PTP en lugar de MTP: sirve igual para la sonda.
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == MTP_CLASS && iface.interfaceSubclass == MTP_SUBCLASS) {
                    return iface
                }
            }
            return null
        }
    }

    private val iface: UsbInterface = findMtpInterface(device)
        ?: throw AoaException(
            AoaError.MTP_INTERFACE_NOT_FOUND,
            "el target no expone interfaz MTP (clase 6, subclase 1)"
        )

    private val bulkOut: UsbEndpoint?
    private val bulkIn: UsbEndpoint?
    private var claimed = false

    /** Último error técnico de la sonda: va al registro para saber dónde se trabó. */
    var lastError: String = ""
        private set

    val hasBulkOut: Boolean get() = bulkOut != null
    val hasBulkIn: Boolean get() = bulkIn != null

    /**
     * Topología que se está usando: interfaz, altsetting y endpoints. Va al registro
     * antes de la sonda, para poder distinguir "no hay endpoint" de "el target no
     * responde".
     */
    fun describe(): String {
        val endpoints = (0 until iface.endpointCount).joinToString(", ") { i ->
            val ep = iface.getEndpoint(i)
            val direction = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
            val kind = if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) "bulk" else "tipo${ep.type}"
            "0x%02X %s %s %dB".format(ep.address, direction, kind, ep.maxPacketSize)
        }
        return "MTP: interfaz %d (altsetting %d, clase %d/%d/%d, %d endpoints) -> %s".format(
            iface.id,
            iface.alternateSetting,
            iface.interfaceClass,
            iface.interfaceSubclass,
            iface.interfaceProtocol,
            iface.endpointCount,
            endpoints
        )
    }

    init {
        var out: UsbEndpoint? = null
        var incoming: UsbEndpoint? = null
        for (e in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(e)
            if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) out = endpoint else incoming = endpoint
        }
        bulkOut = out
        bulkIn = incoming
    }

    /** Reclama la interfaz. Hay que llamarlo antes de usar el canal. */
    fun open() {
        if (claimed) return
        if (!connection.claimInterface(iface, true)) {
            throw AoaException(
                AoaError.MTP_CLAIM_FAILED,
                "claimInterface() devolvió false para la interfaz ${iface.id}"
            )
        }
        claimed = true
    }

    override fun send(bytes: ByteArray): Boolean {
        val endpoint = bulkOut
        if (endpoint == null) {
            lastError = "la interfaz MTP no tiene endpoint bulk OUT (${describe()})"
            return false
        }
        val first = connection.bulkTransfer(endpoint, bytes, bytes.size, bulkTimeoutMs)
        if (first == bytes.size) return true
        // Un endpoint bulk puede quedar en halt y rechazar todo hasta que se limpia:
        // CLEAR_FEATURE(ENDPOINT_HALT) y un reintento.
        clearHalt(endpoint)
        val second = connection.bulkTransfer(endpoint, bytes, bytes.size, bulkTimeoutMs)
        lastError = "bulk OUT 0x%02X devolvió %d bytes y tras CLEAR_HALT %d (se esperaban %d)".format(
            endpoint.address, first, second, bytes.size
        )
        return second == bytes.size
    }

    override fun receive(timeoutMs: Int): ByteArray? {
        val endpoint = bulkIn
        if (endpoint == null) {
            lastError = "la interfaz MTP no tiene endpoint bulk IN"
            return null
        }
        val buffer = ByteArray(READ_CHUNK)
        val read = connection.bulkTransfer(endpoint, buffer, buffer.size, timeoutMs)
        if (read <= 0) {
            clearHalt(endpoint)
            lastError = "bulk IN 0x%02X devolvió %d (sin datos del target)".format(endpoint.address, read)
            return null
        }
        return buffer.copyOf(read)
    }

    /**
     * CLEAR_FEATURE(ENDPOINT_HALT): limpia el halt de un endpoint bulk. Es la causa
     * típica de un `bulkTransfer` que devuelve -1 sin que el dispositivo esté roto.
     */
    private fun clearHalt(endpoint: UsbEndpoint) {
        runCatching {
            connection.controlTransfer(
                REQUEST_TYPE_CLEAR_FEATURE,
                REQUEST_CLEAR_FEATURE,
                FEATURE_ENDPOINT_HALT,
                endpoint.address,
                null,
                0,
                CLEAR_HALT_TIMEOUT_MS
            )
        }
    }

    /** Suelta la interfaz (el HID sigue funcionando: va por EP0). */
    override fun close() {
        if (!claimed) return
        runCatching { connection.releaseInterface(iface) }
        claimed = false
    }
}
