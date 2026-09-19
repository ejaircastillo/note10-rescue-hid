package com.ejair.note10rescue

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reports HID de 8 bytes: [modifiers][reserved][6 keycodes]
 * y descriptor de reporte igual al de scrcpy.
 */
class HidKeyboardReportsTest {

    /**
     * Descriptor esperado, independiente de la implementación: es el de scrcpy
     * (`app/src/hid/hid_keyboard.c`) con las macros resueltas tal como las
     * resuelve el compilador:
     *   SC_HID_KEYBOARD_KEYS     = 0x66   -> Usage/Logical Maximum = 0x66 - 1 = 0x65
     *   SC_HID_KEYBOARD_MAX_KEYS = 6      -> Report Count de teclas
     * Sirve para que el descriptor no pueda volver a desviarse sin que falle un test.
     */
    private val EXPECTED_DESCRIPTOR = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x75, 0x08, 0x95, 0x01, 0x81, 0x01,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x75, 0x01, 0x95, 0x05, 0x91, 0x02,
        0x75, 0x03, 0x95, 0x01, 0x91, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x15, 0x00, 0x25, 0x65,
        0x75, 0x08, 0x95, 0x06, 0x81, 0x00,
        0xC0
    )

    @Test
    fun `key down coloca el keycode en el primer slot`() {
        val report = HidKeyboardReports.keyDown(HidKeycodes.forDigit('1'))

        assertEquals(HidKeyboardReports.REPORT_SIZE, report.size)
        assertEquals(0x00, report[0].toInt())          // modifiers
        assertEquals(0x00, report[1].toInt())          // reserved
        assertEquals(0x1E, report[2].toInt())          // keycode del '1'
        assertEquals(0x00, report[3].toInt())
        assertEquals(0x00, report[4].toInt())
        assertEquals(0x00, report[5].toInt())
        assertEquals(0x00, report[6].toInt())
        assertEquals(0x00, report[7].toInt())
    }

    @Test
    fun `key down con modificador`() {
        val report = HidKeyboardReports.keyDown(HidKeycodes.forDigit('9'), HidKeyboardReports.MOD_LEFT_SHIFT)
        assertEquals(0x02, report[0].toInt())
        assertEquals(0x26, report[2].toInt())
    }

    @Test
    fun `key release es un report de ceros`() {
        val report = HidKeyboardReports.keyRelease()

        assertEquals(HidKeyboardReports.REPORT_SIZE, report.size)
        assertArrayEquals(ByteArray(HidKeyboardReports.REPORT_SIZE), report)
        assertTrue(report.all { it.toInt() == 0 })
    }

    @Test
    fun `report de ENTER`() {
        val report = HidKeyboardReports.enter()

        assertEquals(HidKeyboardReports.REPORT_SIZE, report.size)
        assertEquals(0x00, report[0].toInt())
        assertEquals(0x00, report[1].toInt())
        assertEquals(HidKeycodes.ENTER, report[2].toInt())
        assertArrayEquals(HidKeyboardReports.keyDown(HidKeycodes.ENTER), report)
    }

    @Test
    fun `reports de TAB y BACKSPACE`() {
        assertEquals(HidKeycodes.TAB, HidKeyboardReports.tab()[2].toInt())
        assertEquals(HidKeycodes.BACKSPACE, HidKeyboardReports.backspace()[2].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `keycode fuera de rango falla`() {
        HidKeyboardReports.keyDown(0x100)
    }

    @Test
    fun `describeSafely no filtra keycodes`() {
        assertEquals(
            "report[mods=0 keys=1]",
            HidKeyboardReports.describeSafely(HidKeyboardReports.keyDown(HidKeycodes.forDigit('1')))
        )
        val text = HidKeyboardReports.describeSafely(HidKeyboardReports.keyDown(0x1E))
        assertFalse(text.contains("1e", ignoreCase = true))
        assertFalse(text.contains("30")) // 0x1E en decimal
    }

    // ---------------------------------------------------------- descriptor

    @Test
    fun `el descriptor coincide byte por byte con scrcpy`() {
        val descriptor = HidKeyboardDescriptor.REPORT_DESCRIPTOR

        assertEquals(63, EXPECTED_DESCRIPTOR.size)
        assertEquals(63, descriptor.size)
        assertEquals(HidKeyboardDescriptor.SIZE, descriptor.size)
        assertArrayEquals(
            "El descriptor debe ser idéntico al de scrcpy hid_keyboard.c",
            ByteArray(EXPECTED_DESCRIPTOR.size) { EXPECTED_DESCRIPTOR[it].toByte() },
            descriptor
        )
    }

    @Test
    fun `los maximos de la lista de teclas son 0x65 y no 0x66`() {
        val descriptor = HidKeyboardDescriptor.REPORT_DESCRIPTOR

        // SC_HID_KEYBOARD_KEYS = 0x66, y scrcpy usa SC_HID_KEYBOARD_KEYS - 1 en
        // LAS DOS: Usage Maximum y Logical Maximum. El bug de v1.0.0 fue poner 0x66.
        assertEquals(0x29, descriptor[50].toInt() and 0xFF)  // Usage Maximum (prefijo)
        assertEquals(0x65, descriptor[51].toInt() and 0xFF)  // 101 == 0x66 - 1
        assertEquals(0x25, descriptor[54].toInt() and 0xFF)  // Logical Maximum (prefijo)
        assertEquals(0x65, descriptor[55].toInt() and 0xFF)  // 101 == 0x66 - 1
    }

    @Test
    fun `el report count de teclas es 6`() {
        val descriptor = HidKeyboardDescriptor.REPORT_DESCRIPTOR

        assertEquals(0x95, descriptor[58].toInt() and 0xFF)  // Report Count (prefijo)
        assertEquals(6, descriptor[59].toInt() and 0xFF)     // SC_HID_KEYBOARD_MAX_KEYS
        assertEquals(0xC0, descriptor[62].toInt() and 0xFF)  // End Collection
    }
}
