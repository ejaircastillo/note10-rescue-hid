package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El sondeo del permiso USB es la red de seguridad cuando el broadcast del sistema no
 * llega (PendingIntent sin mutabilidad, receiver no exportado, OEM que lo manda distinto).
 */
class PermissionPollTest {

    @Test
    fun `si ya hay permiso no espera nada`() {
        var sleeps = 0
        val poll = PermissionPoll(slumber = { sleeps++ })

        assertTrue(poll.await { true })
        assertEquals(0, sleeps)
    }

    @Test
    fun `detecta el permiso concedido despues de unos sondeos`() {
        var granted = false
        var sleeps = 0
        val poll = PermissionPoll(
            timeoutMs = 10_000,
            intervalMs = 500,
            slumber = {
                sleeps++
                if (sleeps == 3) granted = true
            }
        )

        assertTrue(poll.await { granted })
        assertEquals(3, sleeps)
    }

    @Test
    fun `acepta el permiso que llega justo en el ultimo sondeo`() {
        var granted = false
        var sleeps = 0
        val poll = PermissionPoll(
            timeoutMs = 2_000,
            intervalMs = 500,
            slumber = {
                sleeps++
                if (sleeps == 4) granted = true
            }
        )

        assertTrue(poll.await { granted })
        assertEquals(4, sleeps)
    }

    @Test
    fun `sin permiso se agota el tiempo y devuelve false`() {
        var sleeps = 0
        val poll = PermissionPoll(timeoutMs = 5_000, intervalMs = 500, slumber = { sleeps++ })

        assertFalse(poll.await { false })
        assertEquals(10, sleeps)
    }

    @Test
    fun `avisa el progreso para poder registrarlo`() {
        val ticks = mutableListOf<Long>()
        val poll = PermissionPoll(
            timeoutMs = 2_000,
            intervalMs = 500,
            slumber = {},
            onTick = { ticks += it }
        )

        poll.await { false }
        assertEquals(listOf(500L, 1_000L, 1_500L, 2_000L), ticks)
    }
}

/**
 * Los códigos de error son la única señal que el usuario puede reportar, así que cada
 * uno tiene que ser distinto y explicar qué hacer.
 */
class AoaErrorTest {

    @Test
    fun `el timeout de permiso es distinto de la denegacion`() {
        assertTrue(AoaError.USB_PERMISSION_TIMEOUT.code != AoaError.USB_PERMISSION_DENIED.code)
        assertTrue(AoaError.USB_PERMISSION_TIMEOUT.description.contains("diálogo"))
        assertTrue(AoaError.USB_PERMISSION_DENIED.description.contains("Denegar"))
    }

    @Test
    fun `todos los codigos son unicos y no vacios`() {
        val codes = AoaError.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(AoaError.values().all { it.code.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun `la excepcion arma el mensaje con codigo y detalle tecnico`() {
        val e = AoaException(AoaError.USB_PERMISSION_TIMEOUT, "device=/dev/bus/usb/001/002")
        assertTrue(e.message!!.contains("USB_PERMISSION_TIMEOUT"))
        assertTrue(e.message!!.contains("device=/dev/bus/usb/001/002"))
    }
}
