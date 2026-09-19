package com.ejair.note10rescue

import android.hardware.usb.UsbDeviceConnection

/**
 * Implementación real de [ControlTransport] sobre `UsbDeviceConnection`.
 *
 * Es la única clase de la app que llama a `controlTransfer()`.
 * Cada transfer registra request, result y duración — nunca el buffer.
 */
class UsbControlTransport(
    private val connection: UsbDeviceConnection
) : ControlTransport {

    override fun transferOut(
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int
    ): TransferResult {
        val start = System.nanoTime()
        val transferred = connection.controlTransfer(
            AoaProtocol.REQUEST_TYPE_OUT_VENDOR,
            request,
            value,
            index,
            data,
            data?.size ?: 0,
            timeoutMs
        )
        return TransferResult(request, value, index, transferred, elapsedMs(start))
    }

    override fun transferIn(
        request: Int,
        value: Int,
        index: Int,
        length: Int,
        timeoutMs: Int
    ): InTransfer {
        val buffer = ByteArray(length)
        val start = System.nanoTime()
        val transferred = connection.controlTransfer(
            AoaProtocol.REQUEST_TYPE_IN_VENDOR,
            request,
            value,
            index,
            buffer,
            length,
            timeoutMs
        )
        val duration = elapsedMs(start)
        val data = if (transferred > 0) buffer.copyOf(transferred) else null
        return InTransfer(TransferResult(request, value, index, transferred, duration), data)
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L
}
