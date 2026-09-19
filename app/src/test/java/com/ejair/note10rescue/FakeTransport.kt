package com.ejair.note10rescue

/**
 * Transporte falso: permite testear [AoaHidKeyboard] sin USB ni Android.
 * Registra cada control transfer (request/value/index/tamaño de data) y permite
 * forzar resultados de error.
 */
class FakeTransport : ControlTransport {

    data class Recorded(
        val request: Int,
        val value: Int,
        val index: Int,
        val data: ByteArray?,
        val directionIn: Boolean
    ) {
        val dataSize: Int get() = data?.size ?: 0
    }

    val recorded = mutableListOf<Recorded>()

    /** Respuesta de ACCESSORY_GET_PROTOCOL (uint16 little-endian). */
    var protocolResponse: ByteArray? = byteArrayOf(0x02, 0x00)
    var protocolResult: Int = 2
    var registerResult: Int = 0
    var descriptorResult: Int = HidKeyboardDescriptor.SIZE
    var eventResult: Int = HidKeyboardReports.REPORT_SIZE
    var unregisterResult: Int = 0

    override fun transferOut(
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int
    ): TransferResult {
        recorded += Recorded(request, value, index, data?.copyOf(), false)
        val result = when (request) {
            AoaProtocol.ACCESSORY_REGISTER_HID -> registerResult
            AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC -> descriptorResult
            AoaProtocol.ACCESSORY_SEND_HID_EVENT -> eventResult
            AoaProtocol.ACCESSORY_UNREGISTER_HID -> unregisterResult
            else -> -1
        }
        return TransferResult(request, value, index, result, 0L)
    }

    override fun transferIn(
        request: Int,
        value: Int,
        index: Int,
        length: Int,
        timeoutMs: Int
    ): InTransfer {
        recorded += Recorded(request, value, index, null, true)
        return InTransfer(
            TransferResult(request, value, index, protocolResult, 0L),
            protocolResponse?.copyOf()
        )
    }

    fun requests(): List<Int> = recorded.map { it.request }

    fun outTransfers(request: Int): List<Recorded> =
        recorded.filter { !it.directionIn && it.request == request }

    fun sentReports(): List<ByteArray> =
        outTransfers(AoaProtocol.ACCESSORY_SEND_HID_EVENT).mapNotNull { it.data }
}
