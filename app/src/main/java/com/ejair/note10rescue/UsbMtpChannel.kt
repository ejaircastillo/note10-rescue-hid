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

        const val BULK_TIMEOUT_MS = 2000

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
        val endpoint = bulkOut ?: return false
        val sent = connection.bulkTransfer(endpoint, bytes, bytes.size, bulkTimeoutMs)
        return sent == bytes.size
    }

    override fun receive(timeoutMs: Int): ByteArray? {
        val endpoint = bulkIn ?: return null
        val buffer = ByteArray(READ_CHUNK)
        val read = connection.bulkTransfer(endpoint, buffer, buffer.size, timeoutMs)
        if (read <= 0) return null
        return buffer.copyOf(read)
    }

    /** Suelta la interfaz (el HID sigue funcionando: va por EP0). */
    override fun close() {
        if (!claimed) return
        runCatching { connection.releaseInterface(iface) }
        claimed = false
    }
}
