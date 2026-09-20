package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Líneas de la bitácora de auditoría: tienen que servir para auditar sin filtrar el PIN. */
class AuditEntryTest {

    private val stats = AoaHidKeyboard.SequenceStats(
        digits = 4,
        reports = 10,
        durationMs = 830,
        wakeKeyFirst = true
    )

    @Test
    fun `el registro de un intento dice origen cantidad y resultado`() {
        val line = AuditEntry.attempt(3, "embebido", stats)

        assertTrue(line.contains("INTENTO #3"))
        assertTrue(line.contains("resultado=ENVIADO"))
        assertTrue(line.contains("origen=embebido"))
        assertTrue(line.contains("dígitos=4"))
        assertTrue(line.contains("reports=10"))
        assertTrue(line.contains("duración=830ms"))
        assertTrue(line.contains("teclaDespertar=true"))
    }

    @Test
    fun `el registro de un intento no incluye el pin`() {
        val line = AuditEntry.attempt(1, "ingresado", stats)

        assertFalse(line.contains("1234"))
        assertFalse(line.contains("1e", ignoreCase = true))
        assertFalse(line.contains("1f", ignoreCase = true))
    }

    @Test
    fun `el registro de error usa el codigo y no el pin`() {
        val line = AuditEntry.failure(2, AoaError.SEND_REPORT_FAILED, "request=57 result=-1 duration=1000ms")

        assertTrue(line.contains("INTENTO #2"))
        assertTrue(line.contains("resultado=ERROR"))
        assertTrue(line.contains("SEND_REPORT_FAILED"))
        assertTrue(line.contains("request=57"))
        assertFalse(line.contains("1234"))
    }

    @Test
    fun `la linea de sesion informa si hay pin embebido`() {
        assertEquals(
            "SESION inicio origenDisponible=embebido",
            AuditEntry.session("inicio", embedded = true)
        )
        assertTrue(AuditEntry.session("inicio", embedded = false).contains("origenDisponible=ninguno"))
    }
}
