package com.ejair.note10rescue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reporte que el usuario comparte o copia para informar un problema.
 * Requisito duro: **nunca** puede contener el PIN.
 */
class DiagnosticsReportTest {

    private fun report(logLines: List<String>, attempts: Int = 1) = DiagnosticsReport.build(
        appVersion = "1.0.2",
        hostSummary = "samsung SM-G991B / Android 14 (API 34)",
        targetSummary = "Galaxy Note10 — Samsung (VID 0x04E8, PID 0x6860)",
        protocolSummary = "AOA protocol: 2",
        statusSummary = "● HID preparado",
        attempts = attempts,
        lastSequence = "6 dígitos, 14 reports OK, 1240ms",
        usbState = "sin cambios en el bus USB",
        logLines = logLines
    )

    @Test
    fun `incluye el contexto necesario para diagnosticar`() {
        val text = report(
            logLines = listOf(
                "00:00:01  GET_PROTOCOL: request=51 result=2 duration=3ms",
                "00:00:02  6 digit sequence sent",
                "00:00:02  ENTER sent"
            )
        )

        assertTrue(text.contains("Note10 Rescue HID — registro técnico"))
        assertTrue(text.contains("app: 1.0.2"))
        assertTrue(text.contains("host: samsung SM-G991B"))
        assertTrue(text.contains("target: Galaxy Note10"))
        assertTrue(text.contains("AOA protocol: 2"))
        assertTrue(text.contains("estado: ● HID preparado"))
        assertTrue(text.contains("intentos enviados (contador de esta sesión): 1"))
        assertTrue(text.contains("último envío: 6 dígitos, 14 reports OK, 1240ms"))
        assertTrue(text.contains("estado USB observado: sin cambios en el bus USB"))
        assertTrue(text.contains("GET_PROTOCOL: request=51 result=2"))
        assertTrue(text.contains("--- fin ---"))
    }

    @Test
    fun `nunca filtra el pin ni keycodes`() {
        // Simula un registro real después de enviar el PIN 123456: el registro
        // técnico sólo tiene cantidades, nunca los dígitos ni sus keycodes.
        val text = report(
            logLines = listOf(
                "00:00:01  SEND_HID_EVENT: request=57 result=8 duration=31ms",
                "00:00:02  6 digit sequence sent",
                "00:00:02  ENTER sent",
                "00:00:02  sequence summary: 6 dígitos, 14 reports OK, 1240ms"
            )
        )

        assertFalse(text.contains("123456"))
        assertFalse(text.contains("1e", ignoreCase = true))
        assertFalse(text.contains("1f", ignoreCase = true))
    }

    @Test
    fun `recorta el registro largo conservando lo ultimo`() {
        val long = (1..(DiagnosticsReport.MAX_LOG_LINES + 50)).map { "linea $it" }
        val text = report(logLines = long)

        assertTrue(text.contains("50 omitidas"))
        assertTrue(text.contains("linea ${DiagnosticsReport.MAX_LOG_LINES + 50}"))
        assertFalse(text.contains("linea 1\n"))
    }
}
