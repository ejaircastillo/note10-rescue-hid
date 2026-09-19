package com.ejair.note10rescue

/**
 * Resultado de un control transfer, apto para el registro técnico.
 *
 * Lleva sólo metadatos (request, value, index, result, duración).
 * NUNCA lleva el contenido del buffer: los reports HID pueden contener el PIN.
 */
data class TransferResult(
    val request: Int,
    val value: Int,
    val index: Int,
    val bytesTransferred: Int,
    val durationMs: Long
) {
    /** Un controlTransfer devuelve >= 0 si salió bien (0 es válido para OUT sin data). */
    val ok: Boolean get() = bytesTransferred >= 0

    fun technicalDetail(): String =
        "request=$request result=$bytesTransferred duration=${durationMs}ms"
}

/** Resultado de un control transfer IN: el [data] puede ser null si falló o vino vacío. */
class InTransfer(val result: TransferResult, val data: ByteArray?)

/**
 * Abstracción mínima sobre `UsbDeviceConnection.controlTransfer()`.
 *
 * Existe para que [AoaHidKeyboard] sea testeable en la JVM sin USB ni Android
 * (ver `FakeTransport` en los unit tests).
 */
interface ControlTransport {
    /** Control transfer OUT de tipo vendor (bmRequestType 0x40). */
    fun transferOut(request: Int, value: Int, index: Int, data: ByteArray?, timeoutMs: Int): TransferResult

    /** Control transfer IN de tipo vendor (bmRequestType 0xC0). */
    fun transferIn(request: Int, value: Int, index: Int, length: Int, timeoutMs: Int): InTransfer
}
