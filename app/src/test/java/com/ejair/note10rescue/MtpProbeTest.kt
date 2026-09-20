package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La sonda MTP es el único indicio objetivo de desbloqueo disponible sin una PC: Android
 * sólo expone el almacenamiento con el equipo desbloqueado. Estos tests fijan el
 * protocolo (contenedores, endianness) y la decisión (desbloqueado / bloqueado / sin
 * conclusión) con un canal falso.
 */
class MtpProbeTest {

    /** Canal MTP guionado: entrega los contenedores en orden y graba lo enviado. */
    private class FakeChannel(vararg responses: ByteArray) : MtpProbe.Channel {
        private val queue = ArrayDeque(responses.toList())
        val sent = mutableListOf<ByteArray>()
        var failSend = false

        override fun send(bytes: ByteArray): Boolean {
            if (failSend) return false
            sent += bytes
            return true
        }

        override fun receive(timeoutMs: Int): ByteArray? = queue.removeFirstOrNull()
    }

    private fun deviceInfoData() = MtpProbe.container(
        MtpProbe.CONTAINER_DATA, MtpProbe.OP_GET_DEVICE_INFO, 1, ByteArray(8) { 0x41 }
    )

    private fun okResponse(tx: Int) = MtpProbe.container(MtpProbe.CONTAINER_RESPONSE, MtpProbe.RESP_OK, tx)

    /** Contenedor de datos de GetStorageIDs con [count] almacenamientos. */
    private fun storageIdsData(count: Int, tx: Int): ByteArray {
        val payload = ByteArray(4 + count * 4)
        payload[0] = count.toByte()
        for (i in 0 until count) payload[4 + i * 4] = (i + 1).toByte()
        return MtpProbe.container(MtpProbe.CONTAINER_DATA, MtpProbe.OP_GET_STORAGE_IDS, tx, payload)
    }

    // ------------------------------------------------------------ contenedores

    @Test
    fun `el contenedor arma longitud tipo codigo y transaccion en little endian`() {
        val bytes = MtpProbe.container(MtpProbe.CONTAINER_COMMAND, MtpProbe.OP_GET_STORAGE_IDS, 7)

        assertEquals(12, bytes.size)
        assertEquals(12, bytes[0].toInt())          // longitud total, LE
        assertEquals(0, bytes[1].toInt())
        assertEquals(MtpProbe.CONTAINER_COMMAND, bytes[4].toInt())
        assertEquals(0x04, bytes[6].toInt())        // 0x1004 LE
        assertEquals(0x10, bytes[7].toInt())
        assertEquals(7, bytes[8].toInt())
    }

    @Test
    fun `el contenedor viaja y vuelve igual`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val bytes = MtpProbe.container(MtpProbe.CONTAINER_DATA, MtpProbe.OP_GET_DEVICE_INFO, 3, payload)

        val parsed = MtpProbe.parse(bytes)!!
        assertEquals(MtpProbe.CONTAINER_DATA, parsed.type)
        assertEquals(MtpProbe.OP_GET_DEVICE_INFO, parsed.code)
        assertEquals(3, parsed.transactionId)
        assertTrue(parsed.payload.contentEquals(payload))
    }

    @Test
    fun `un contenedor incompleto o mal formado no se acepta`() {
        assertNull(MtpProbe.parse(ByteArray(5)))
        assertNull(MtpProbe.parse(ByteArray(12)))               // longitud 0 declarada
        val truncated = MtpProbe.container(MtpProbe.CONTAINER_DATA, 1, 1, ByteArray(10)).copyOf(14)
        assertNull(MtpProbe.parse(truncated))
    }

    @Test
    fun `la cantidad de almacenamientos se lee del contenedor de datos`() {
        val data = MtpProbe.parse(storageIdsData(2, 3))!!
        assertEquals(2, MtpProbe.storageCount(data))

        val empty = MtpProbe.parse(storageIdsData(0, 3))!!
        assertEquals(0, MtpProbe.storageCount(empty))

        val wrongOp = MtpProbe.parse(MtpProbe.container(MtpProbe.CONTAINER_DATA, 0x1005, 3, ByteArray(4)))!!
        assertNull(MtpProbe.storageCount(wrongOp))
    }

    @Test
    fun `la sonda junta un contenedor partido en dos lecturas`() {
        val whole = deviceInfoData()
        val half = whole.size / 2
        val channel = object : MtpProbe.Channel {
            private val parts = ArrayDeque(listOf(whole.copyOfRange(0, half), whole.copyOfRange(half, whole.size)))
            override fun send(bytes: ByteArray) = true
            override fun receive(timeoutMs: Int): ByteArray? = parts.removeFirstOrNull()
        }

        val parsed = MtpProbe.readContainer(channel, 500)!!
        assertEquals(MtpProbe.OP_GET_DEVICE_INFO, parsed.code)
    }

    // ------------------------------------------------------------------ flujo

    @Test
    fun `con almacenamientos el target esta desbloqueado`() {
        val channel = FakeChannel(
            deviceInfoData(), okResponse(1),
            okResponse(2),
            storageIdsData(2, 3), okResponse(3)
        )

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Unlocked)
        assertEquals(2, (result as MtpProbe.Result.Unlocked).storages)
        // Mandó las cuatro operaciones, incluido el cierre ordenado de la sesión.
        assertEquals(4, channel.sent.size)
    }

    @Test
    fun `sin almacenamientos el target sigue bloqueado`() {
        val channel = FakeChannel(
            deviceInfoData(), okResponse(1),
            okResponse(2),
            storageIdsData(0, 3), okResponse(3)
        )

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Locked)
    }

    @Test
    fun `access denied tambien significa bloqueado`() {
        val denied = MtpProbe.container(
            MtpProbe.CONTAINER_RESPONSE, MtpProbe.OP_GET_STORAGE_IDS, 3
        ).also { it[6] = (MtpProbe.RESP_ACCESS_DENIED and 0xFF).toByte(); it[7] = (MtpProbe.RESP_ACCESS_DENIED shr 8).toByte() }
        val channel = FakeChannel(
            deviceInfoData(), okResponse(1),
            okResponse(2),
            denied
        )

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Locked)
        assertTrue((result as MtpProbe.Result.Locked).detail.contains("AccessDenied"))
    }

    @Test
    fun `si el target no responde la sonda no concluye`() {
        val channel = FakeChannel()

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Inconclusive)
        assertTrue((result as MtpProbe.Result.Inconclusive).reason.contains("MTP mudo"))
    }

    @Test
    fun `si no se puede escribir en el endpoint no concluye`() {
        val channel = FakeChannel()
        channel.failSend = true

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Inconclusive)
        assertTrue((result as MtpProbe.Result.Inconclusive).reason.contains("bulk OUT"))
    }

    @Test
    fun `una respuesta de error en GetDeviceInfo se informa con su nombre`() {
        val channel = FakeChannel(
            MtpProbe.container(MtpProbe.CONTAINER_RESPONSE, MtpProbe.OP_GET_DEVICE_INFO, 1).also {
                it[6] = (MtpProbe.RESP_OPERATION_NOT_SUPPORTED and 0xFF).toByte()
                it[7] = (MtpProbe.RESP_OPERATION_NOT_SUPPORTED shr 8).toByte()
            }
        )

        val result = MtpProbe.probe(channel)

        assertTrue(result is MtpProbe.Result.Inconclusive)
        assertTrue((result as MtpProbe.Result.Inconclusive).reason.contains("OperationNotSupported"))
    }

    @Test
    fun `los codigos de respuesta se nombran para el reporte`() {
        assertEquals("OK", MtpProbe.responseName(MtpProbe.RESP_OK))
        assertEquals("AccessDenied", MtpProbe.responseName(MtpProbe.RESP_ACCESS_DENIED))
        assertEquals("0x2019", MtpProbe.responseName(0x2019))
    }
}
