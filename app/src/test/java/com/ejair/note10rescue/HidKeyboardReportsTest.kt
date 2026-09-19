package com.ejair.note10rescue

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reports HID de 8 bytes: [modifiers][reserved][6 keycodes]. */
class HidKeyboardReportsTest {

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

    @Test
    fun `el descriptor de teclado tiene 63 bytes y los valores de scrcpy`() {
        val descriptor = HidKeyboardDescriptor.REPORT_DESCRIPTOR

        assertEquals(63, descriptor.size)
        assertEquals(HidKeyboardDescriptor.SIZE, descriptor.size)
        // Usage Page (Generic Desktop), Usage (Keyboard), Collection (Application)
        assertEquals(0x05, descriptor[0].toInt() and 0xFF)
        assertEquals(0x01, descriptor[1].toInt() and 0xFF)
        assertEquals(0x09, descriptor[2].toInt() and 0xFF)
        assertEquals(0x06, descriptor[3].toInt() and 0xFF)
        assertEquals(0xA1, descriptor[4].toInt() and 0xFF)
        // Usage Minimum / Maximum de los modificadores (224 / 231)
        assertEquals(0xE0, descriptor[9].toInt() and 0xFF)
        assertEquals(0xE7, descriptor[11].toInt() and 0xFF)
        // Usage Maximum (101) == SC_HID_KEYBOARD_KEYS - 1
        assertEquals(0x66, descriptor[51].toInt() and 0xFF)
        // Logical Maximum (101) == SC_HID_KEYBOARD_KEYS
        assertEquals(0x66, descriptor[55].toInt() and 0xFF)
        // Report Count (6 keycodes)
        assertEquals(0x95, descriptor[58].toInt() and 0xFF)
        assertEquals(6, descriptor[59].toInt() and 0xFF)
        // End Collection
        assertEquals(0xC0, descriptor[62].toInt() and 0xFF)
    }
}
