package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El PIN embebido llega ofuscado desde Gradle (XOR 0x5A + hex).
 * Estos tests fijan el formato exacto para que el build y la app no se desincronicen,
 * y verifican que un PIN corto real se decodifique bien.
 */
class PinVaultTest {

    @Test
    fun `vector conocido del build`() {
        // Es exactamente lo que genera obfuscatePin("1234") en app/build.gradle.kts
        assertEquals("6b68696e", PinVault.encode("1234"))
        assertEquals("1234", PinVault.decode("6b68696e"))
    }

    @Test
    fun `ida y vuelta para varios pines`() {
        listOf("0000", "1234", "9876", "0042", "159753").forEach { pin ->
            assertEquals(pin, PinVault.decode(PinVault.encode(pin)))
        }
    }

    @Test
    fun `sin pin embebido el decode devuelve vacio`() {
        assertEquals("", PinVault.decode(""))
        assertFalse(PinVault.decode("").isNotEmpty())
    }

    @Test
    fun `hex invalido no rompe`() {
        assertEquals("", PinVault.decode("6b68696"))
        assertEquals("", PinVault.decode("zz"))
    }

    @Test
    fun `el pin embebido no se expone como texto plano en el codigo fuente`() {
        // En el build público el campo va vacío; en el privado, ofuscado.
        val raw = BuildConfig.EMBEDDED_PIN_OBFUSCATED
        assertEquals(raw, PinVault.encode(PinVault.decode(raw)))
        assertTrue(raw.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
