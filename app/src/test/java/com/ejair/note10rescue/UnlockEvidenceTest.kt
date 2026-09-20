package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El veredicto de desbloqueo es lo que hace que el reporte sea interpretable: estos
 * tests fijan la matriz de decisión y que el reporte explique sus propios límites.
 */
class UnlockEvidenceTest {

    private fun obs(
        vibration: UnlockEvidence.Vibration = UnlockEvidence.Vibration.SIN_DATO,
        usb: UnlockEvidence.Usb = UnlockEvidence.Usb.SIN_DATO,
        mtp: UnlockEvidence.Mtp = UnlockEvidence.Mtp.NO_PROBADO,
        attempt: Int = 1
    ) = UnlockEvidence.Observation(attempt, vibration, usb, mtp)

    @Test
    fun `sin envios el veredicto lo dice explicitamente`() {
        assertEquals(UnlockEvidence.NO_ATTEMPTS, UnlockEvidence.verdict(null))
    }

    @Test
    fun `mtp visible confirma el desbloqueo`() {
        val v = UnlockEvidence.verdict(obs(mtp = UnlockEvidence.Mtp.SI))
        assertEquals(UnlockEvidence.VERDICT_CONFIRMED, v)
        assertTrue(v.contains("CONFIRMADO"))
    }

    @Test
    fun `mtp visible manda sobre cualquier otro indicio`() {
        val o = obs(vibration = UnlockEvidence.Vibration.SI, mtp = UnlockEvidence.Mtp.SI)
        assertEquals(UnlockEvidence.VERDICT_CONFIRMED, UnlockEvidence.verdict(o))
    }

    @Test
    fun `si la pc no ve el almacenamiento el telefono sigue bloqueado`() {
        assertEquals(UnlockEvidence.VERDICT_LOCKED, UnlockEvidence.verdict(obs(mtp = UnlockEvidence.Mtp.NO)))
    }

    @Test
    fun `mtp no visible manda sobre la vibracion`() {
        val o = obs(vibration = UnlockEvidence.Vibration.NO, mtp = UnlockEvidence.Mtp.NO)
        assertEquals(UnlockEvidence.VERDICT_LOCKED, UnlockEvidence.verdict(o))
    }

    @Test
    fun `haber sentido la vibracion significa rechazo`() {
        assertEquals(
            UnlockEvidence.VERDICT_REJECTED,
            UnlockEvidence.verdict(obs(vibration = UnlockEvidence.Vibration.SI))
        )
    }

    @Test
    fun `sin vibracion es probable pero nunca confirmado sin mtp`() {
        val v = UnlockEvidence.verdict(obs(vibration = UnlockEvidence.Vibration.NO))
        assertEquals(UnlockEvidence.VERDICT_PROBABLE, v)
        assertTrue(v.contains("MTP"))
        assertFalse(v.contains("CONFIRMADO"))
    }

    @Test
    fun `sin datos el veredicto es indeterminado`() {
        assertEquals(UnlockEvidence.VERDICT_UNKNOWN, UnlockEvidence.verdict(obs()))
    }

    @Test
    fun `el cambio de bus usb no alcanza para confirmar`() {
        val o = obs(usb = UnlockEvidence.Usb.CAMBIO)
        assertEquals(UnlockEvidence.VERDICT_UNKNOWN, UnlockEvidence.verdict(o))
    }

    @Test
    fun `el reporte trae veredicto indicios y sus limites`() {
        val text = UnlockEvidence.report(
            obs(vibration = UnlockEvidence.Vibration.NO, usb = UnlockEvidence.Usb.SIN_CAMBIO),
            "sin cambios"
        )
        assertTrue(text.contains("veredicto: ${UnlockEvidence.VERDICT_PROBABLE}"))
        assertTrue(text.contains("intento #1: vibración=no vibró"))
        assertTrue(text.contains("USB=sin cambios en la ventana"))
        assertTrue(text.contains("MTP=no probado"))
        assertTrue(text.contains("unidireccional"))
        assertTrue(text.contains("vibración del sistema desactivada"))
        assertTrue(text.contains("MTP en una PC (decisivo)"))
    }

    @Test
    fun `el reporte sin intentos no inventa indicios`() {
        val text = UnlockEvidence.report(null, "—")
        assertTrue(text.contains(UnlockEvidence.NO_ATTEMPTS))
        assertTrue(text.contains("no hay indicios que juzgar"))
    }

    @Test
    fun `la linea de auditoria lleva los indicios y el veredicto`() {
        val line = AuditEntry.unlockObservation(obs(mtp = UnlockEvidence.Mtp.SI, attempt = 3))
        assertTrue(line.contains("DESBLOQUEO intento=#3"))
        assertTrue(line.contains("vibracion=SIN_DATO"))
        assertTrue(line.contains("mtp=SI"))
        assertTrue(line.contains("veredicto=${UnlockEvidence.VERDICT_CONFIRMED}"))
    }

    @Test
    fun `el reporte tecnico incluye la seccion de desbloqueo`() {
        val text = DiagnosticsReport.build(
            appVersion = "1.0.7",
            hostSummary = "host",
            targetSummary = "target",
            protocolSummary = "AOA protocol: 2",
            statusSummary = "● HID preparado",
            attempts = 1,
            lastSequence = "4 dígitos, 12 reports OK, 687ms",
            usbState = "sin cambios",
            logLines = listOf("una línea"),
            unlockEvidence = UnlockEvidence.report(obs(mtp = UnlockEvidence.Mtp.NO), "sin cambios")
        )
        assertTrue(text.contains("--- desbloqueo (indicios y veredicto) ---"))
        assertTrue(text.contains("veredicto: ${UnlockEvidence.VERDICT_LOCKED}"))
    }

    @Test
    fun `el reporte tecnico sin indicios no agrega la seccion`() {
        val text = DiagnosticsReport.build(
            appVersion = "1.0.7",
            hostSummary = "host",
            targetSummary = "target",
            protocolSummary = "AOA protocol: 2",
            statusSummary = "● HID preparado",
            attempts = 0,
            lastSequence = "—",
            usbState = "—",
            logLines = listOf("una línea")
        )
        assertFalse(text.contains("--- desbloqueo (indicios y veredicto) ---"))
    }
}
