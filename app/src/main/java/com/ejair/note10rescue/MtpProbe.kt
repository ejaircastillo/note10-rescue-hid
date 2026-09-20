package com.ejair.note10rescue

/**
 * Sonda MTP: le pregunta al target cuántos almacenamientos expone, para saber si está
 * desbloqueado.
 *
 * Por qué sirve: Android sólo expone el almacenamiento por MTP con el equipo
 * **desbloqueado** (es el mismo motivo por el que una PC no ve los archivos de un
 * teléfono bloqueado). Consultar `GetStorageIDs` es entonces el único indicio
 * **objetivo** de desbloqueo que existe, y se puede hacer por el mismo cable
 * USB-C ↔ USB-C que ya se usa para el HID: no hace falta una PC.
 *
 * Es lógica pura (PIMA 15740 / MTP): el canal USB se inyecta, así que se testea sin
 * hardware.
 */
object MtpProbe {

    // Tipos de contenedor MTP.
    const val CONTAINER_COMMAND = 1
    const val CONTAINER_DATA = 2
    const val CONTAINER_RESPONSE = 3
    const val CONTAINER_EVENT = 4

    // Operaciones.
    const val OP_GET_DEVICE_INFO = 0x1001
    const val OP_OPEN_SESSION = 0x1002
    const val OP_CLOSE_SESSION = 0x1003
    const val OP_GET_STORAGE_IDS = 0x1004

    // Códigos de respuesta.
    const val RESP_OK = 0x2001
    const val RESP_SESSION_NOT_OPEN = 0x2003
    const val RESP_OPERATION_NOT_SUPPORTED = 0x2005
    const val RESP_ACCESS_DENIED = 0x200F

    /** Sesión MTP: cualquier número sirve, el host lo elige. */
    const val SESSION_ID = 1

    /** Tope de bytes que se aceptan por contenedor (evita leer basura sin fin). */
    const val MAX_CONTAINER_BYTES = 64 * 1024

    /** Canal MTP: dos operaciones, para poder testear sin USB. */
    interface Channel {
        /** Envía bytes por el endpoint bulk OUT. `true` si salieron todos. */
        fun send(bytes: ByteArray): Boolean

        /** Lee lo que haya (timeout en ms). `null` si no llegó nada. */
        fun receive(timeoutMs: Int): ByteArray?
    }

    /** Contenedor MTP decodificado. */
    data class Container(
        val type: Int,
        val code: Int,
        val transactionId: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean =
            other is Container && type == other.type && code == other.code &&
                transactionId == other.transactionId && payload.contentEquals(other.payload)

        override fun hashCode(): Int =
            ((type * 31 + code) * 31 + transactionId) * 31 + payload.contentHashCode()
    }

    sealed class Result {
        /** MTP expone almacenamiento: el target está desbloqueado. */
        data class Unlocked(val storages: Int) : Result()

        /** MTP no expone almacenamiento: el target sigue bloqueado. */
        data class Locked(val detail: String) : Result()

        /** No se pudo concluir (canal mudo, sin interfaz MTP, etc.). */
        data class Inconclusive(val reason: String) : Result()
    }

    // ------------------------------------------------------------- contenedores

    /** Arma un contenedor (command/data/response) con longitud y endianness correctos. */
    fun container(type: Int, code: Int, transactionId: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val total = 12 + payload.size
        val out = ByteArray(total)
        putU32(out, 0, total)
        putU16(out, 4, type)
        putU16(out, 6, code)
        putU32(out, 8, transactionId)
        payload.copyInto(out, 12)
        return out
    }

    fun command(code: Int, transactionId: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        container(CONTAINER_COMMAND, code, transactionId, payload)

    /** Decodifica un contenedor completo. `null` si está incompleto o mal formado. */
    fun parse(bytes: ByteArray): Container? {
        if (bytes.size < 12) return null
        val total = u32(bytes, 0)
        if (total < 12 || total > bytes.size) return null
        val type = u16(bytes, 4)
        val code = u16(bytes, 6)
        val tx = u32(bytes, 8)
        val payload = bytes.copyOfRange(12, total)
        return Container(type, code, tx, payload)
    }

    /** Cantidad de almacenamientos del contenedor de datos de `GetStorageIDs`. */
    fun storageCount(data: Container): Int? {
        if (data.type != CONTAINER_DATA || data.code != OP_GET_STORAGE_IDS) return null
        val payload = data.payload
        if (payload.size < 4) return null
        val count = u32(payload, 0)
        // Cada storage id ocupa 4 bytes: si el conteo no cierra con el tamaño, no confiar.
        if (count < 0 || 4 + count * 4 > payload.size) return null
        return count
    }

    /** Sesión MTP: id de sesión como uint32 little-endian. */
    fun sessionPayload(sessionId: Int = SESSION_ID): ByteArray = ByteArray(4).also { putU32(it, 0, sessionId) }

    fun responseName(code: Int): String = when (code) {
        RESP_OK -> "OK"
        RESP_SESSION_NOT_OPEN -> "SessionNotOpen"
        RESP_OPERATION_NOT_SUPPORTED -> "OperationNotSupported"
        RESP_ACCESS_DENIED -> "AccessDenied"
        else -> "0x%04X".format(code)
    }

    // -------------------------------------------------------------------- flujo

    /**
     * Corre la sonda completa: `GetDeviceInfo` (comprueba que hay MTP vivo, funciona
     * incluso bloqueado) → `OpenSession` → `GetStorageIDs` (el paso decisivo) →
     * `CloseSession`.
     */
    fun probe(channel: Channel, timeoutMs: Int = 2000): Result {
        var tx = 1
        if (!channel.send(command(OP_GET_DEVICE_INFO, tx))) {
            return Result.Inconclusive("no se pudo enviar GetDeviceInfo (endpoint bulk OUT)")
        }
        val infoResponse = awaitResponse(channel, timeoutMs)
            ?: return Result.Inconclusive("el target no respondió a GetDeviceInfo (MTP mudo)")
        if (infoResponse.code != RESP_OK) {
            return Result.Inconclusive("GetDeviceInfo devolvió ${responseName(infoResponse.code)}")
        }

        tx++
        if (!channel.send(command(OP_OPEN_SESSION, tx, sessionPayload()))) {
            return Result.Inconclusive("no se pudo enviar OpenSession")
        }
        val openResponse = awaitResponse(channel, timeoutMs)
            ?: return Result.Inconclusive("el target no respondió a OpenSession")
        if (openResponse.code != RESP_OK && openResponse.code != RESP_SESSION_NOT_OPEN) {
            return Result.Inconclusive("OpenSession devolvió ${responseName(openResponse.code)}")
        }

        tx++
        if (!channel.send(command(OP_GET_STORAGE_IDS, tx))) {
            return Result.Inconclusive("no se pudo enviar GetStorageIDs")
        }
        val (storageData, storageResponse) = readDataAndResponse(channel, OP_GET_STORAGE_IDS, timeoutMs)

        // AccessDenied explícito: Android no expone el almacenamiento bloqueado.
        if (storageResponse != null && storageResponse.code == RESP_ACCESS_DENIED) {
            return Result.Locked("GetStorageIDs devolvió AccessDenied")
        }
        val count = storageData?.let { storageCount(it) }
        if (count == null) {
            val code = storageResponse?.let { responseName(it.code) } ?: "sin respuesta"
            return Result.Inconclusive("GetStorageIDs no devolvió una lista válida ($code)")
        }
        // Cierre ordenado (si falla, no cambia el veredicto).
        tx++
        channel.send(command(OP_CLOSE_SESSION, tx))

        return if (count > 0) {
            Result.Unlocked(count)
        } else {
            Result.Locked("GetStorageIDs devolvió 0 almacenamientos")
        }
    }

    /** Lee contenedores hasta encontrar una respuesta (saltea eventos y datos). */
    private fun awaitResponse(channel: Channel, timeoutMs: Int, maxContainers: Int = 4): Container? {
        repeat(maxContainers) {
            val container = readContainer(channel, timeoutMs) ?: return null
            if (container.type == CONTAINER_RESPONSE) return container
        }
        return null
    }

    /**
     * Lee contenedores hasta la respuesta de la operación, guardando el contenedor de
     * datos que pueda venir antes. Es el orden real de MTP: Data y después Response
     * (y, si el target niega el acceso, sólo Response).
     */
    private fun readDataAndResponse(
        channel: Channel,
        opCode: Int,
        timeoutMs: Int,
        maxContainers: Int = 4
    ): Pair<Container?, Container?> {
        var data: Container? = null
        repeat(maxContainers) {
            val container = readContainer(channel, timeoutMs) ?: return data to null
            when (container.type) {
                CONTAINER_DATA -> if (container.code == opCode) data = container
                CONTAINER_RESPONSE -> return data to container
            }
        }
        return data to null
    }

    /**
     * Lee un contenedor completo, juntando los pedazos que hagan falta: el header dice
     * cuántos bytes son, así que se lee hasta completarlo.
     */
    fun readContainer(channel: Channel, timeoutMs: Int): Container? {
        val buffer = java.io.ByteArrayOutputStream()
        var expected = -1
        repeat(16) {
            val chunk = channel.receive(timeoutMs) ?: return null
            if (chunk.isEmpty()) return null
            buffer.write(chunk)
            val all = buffer.toByteArray()
            if (expected < 0) {
                if (all.size < 12) return@repeat
                expected = u32(all, 0)
                if (expected < 12 || expected > MAX_CONTAINER_BYTES) return null
            }
            if (all.size >= expected) return parse(all)
        }
        return null
    }

    // -------------------------------------------------------------- little endian

    private fun putU16(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putU32(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        out[offset + 2] = ((value shr 16) and 0xFF).toByte()
        out[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
