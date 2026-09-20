package com.ejair.note10rescue

/**
 * Transporte simulado para el "modo de prueba".
 *
 * Responde exactamente como responde un Android con AOA2 (protocolo 2, REGISTER_HID
 * 0 bytes, descriptor aceptado, reports de 8 bytes) pero **no abre ni toca ningún
 * dispositivo**. Sirve para validar todo el flujo de la app —botón OK, pipeline,
 * cooldown, resumen, auditoría— sin gastar intentos de desbloqueo en el target.
 */
class SimulatedTransport : ControlTransport {

    /** Igual que un dispositivo real: protocolo 2 en little-endian. */
    val protocolVersion: Int = 2

    override fun transferOut(
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int
    ): TransferResult {
        val transferred = when (request) {
            AoaProtocol.ACCESSORY_REGISTER_HID -> 0
            AoaProtocol.ACCESSORY_SET_HID_REPORT_DESC -> data?.size ?: 0
            AoaProtocol.ACCESSORY_SEND_HID_EVENT -> data?.size ?: 0
            AoaProtocol.ACCESSORY_UNREGISTER_HID -> 0
            else -> -1
        }
        return TransferResult(request, value, index, transferred, 0L)
    }

    override fun transferIn(
        request: Int,
        value: Int,
        index: Int,
        length: Int,
        timeoutMs: Int
    ): InTransfer {
        val payload = byteArrayOf(
            (protocolVersion and 0xFF).toByte(),
            ((protocolVersion shr 8) and 0xFF).toByte()
        )
        return InTransfer(TransferResult(request, value, index, payload.size, 0L), payload)
    }
}
