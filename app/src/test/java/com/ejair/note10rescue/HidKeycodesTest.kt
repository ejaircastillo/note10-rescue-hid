package com.ejair.note10rescue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dígito -> HID keycode (fila numérica de un teclado USB físico). */
class HidKeycodesTest {

    @Test
    fun `mapeo de digitos segun HID Usage Tables`() {
        assertEquals(0x27, HidKeycodes.forDigit('0'))
        assertEquals(0x1E, HidKeycodes.forDigit('1'))
        assertEquals(0x1F, HidKeycodes.forDigit('2'))
        assertEquals(0x20, HidKeycodes.forDigit('3'))
        assertEquals(0x21, HidKeycodes.forDigit('4'))
        assertEquals(0x22, HidKeycodes.forDigit('5'))
        assertEquals(0x23, HidKeycodes.forDigit('6'))
        assertEquals(0x24, HidKeycodes.forDigit('7'))
        assertEquals(0x25, HidKeycodes.forDigit('8'))
        assertEquals(0x26, HidKeycodes.forDigit('9'))
    }

    @Test
    fun `mapeo tambien acepta el digito como Int`() {
        for (digit in 0..9) {
            assertEquals(HidKeycodes.forDigit(digit), HidKeycodes.forDigit('0' + digit))
        }
    }

    @Test
    fun `las diez teclas son distintas`() {
        val keycodes = (0..9).map { HidKeycodes.forDigit(it) }
        assertEquals(10, keycodes.toSet().size)
    }

    @Test
    fun `teclas especiales`() {
        assertEquals(0x28, HidKeycodes.ENTER)
        assertEquals(0x2A, HidKeycodes.BACKSPACE)
        assertEquals(0x2B, HidKeycodes.TAB)
        assertNotEquals(HidKeycodes.ENTER, HidKeycodes.TAB)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un caracter que no es digito falla`() {
        HidKeycodes.forDigit('a')
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un entero fuera de 0-9 falla`() {
        HidKeycodes.forDigit(10)
    }

    @Test
    fun `las etiquetas de log no distinguen digitos`() {
        assertEquals("DIGIT", HidKeycodes.safeLabel(HidKeycodes.forDigit('0')))
        assertEquals("DIGIT", HidKeycodes.safeLabel(HidKeycodes.forDigit('7')))
        assertEquals("ENTER", HidKeycodes.safeLabel(HidKeycodes.ENTER))
        assertEquals("TAB", HidKeycodes.safeLabel(HidKeycodes.TAB))
        assertEquals("BACKSPACE", HidKeycodes.safeLabel(HidKeycodes.BACKSPACE))
        assertTrue(HidKeycodes.safeLabel(HidKeycodes.ENTER).none { it in '0'..'9' })
    }
}
