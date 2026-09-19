package com.ejair.note10rescue

/**
 * HID report descriptor de teclado USB, portado tal cual de scrcpy
 * (`app/src/hid/hid_keyboard.c`, `SC_HID_KEYBOARD_REPORT_DESC`).
 *
 * En scrcpy las dos últimas constantes del descriptor se escriben como
 * `SC_HID_KEYBOARD_KEYS - 1` y `SC_HID_KEYBOARD_KEYS`, con
 * `SC_HID_KEYBOARD_KEYS = 0x66` (102), o sea Usage Maximum / Logical Maximum
 * = 101 y Report Count = 6. Acá van los valores literales.
 *
 * Formato del report (8 bytes, boot keyboard compatible):
 *   byte 0     : modifiers (bitmap)
 *   byte 1     : reserved
 *   bytes 2-7  : hasta 6 keycodes HID
 *
 * AOA2 no requiere ninguna variación del descriptor: Android lo usa para crear
 * un dispositivo HID virtual igual que un teclado USB físico.
 */
object HidKeyboardDescriptor {

    private val RAW_BYTES = intArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x06, // Usage (Keyboard)
        0xA1, 0x01, // Collection (Application)
        0x05, 0x07, // Usage Page (Key Codes)
        0x19, 0xE0, // Usage Minimum (224)
        0x29, 0xE7, // Usage Maximum (231)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x75, 0x01, // Report Size (1)
        0x95, 0x08, // Report Count (8)
        0x81, 0x02, // Input (Data, Variable, Absolute) - modifier byte
        0x75, 0x08, // Report Size (8)
        0x95, 0x01, // Report Count (1)
        0x81, 0x01, // Input (Constant) - reserved byte
        0x05, 0x08, // Usage Page (LEDs)
        0x19, 0x01, // Usage Minimum (1)
        0x29, 0x05, // Usage Maximum (5)
        0x75, 0x01, // Report Size (1)
        0x95, 0x05, // Report Count (5)
        0x91, 0x02, // Output (Data, Variable, Absolute) - LED report
        0x75, 0x03, // Report Size (3)
        0x95, 0x01, // Report Count (1)
        0x91, 0x01, // Output (Constant) - LED padding
        0x05, 0x07, // Usage Page (Key Codes)
        0x19, 0x00, // Usage Minimum (0)
        0x29, 0x66, // Usage Maximum (101) == SC_HID_KEYBOARD_KEYS - 1
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x66, // Logical Maximum (101) == SC_HID_KEYBOARD_KEYS
        0x75, 0x08, // Report Size (8)
        0x95, 0x06, // Report Count (6)
        0x81, 0x00, // Input (Data, Array) - keys
        0xC0        // End Collection
    )

    /** Descriptor listo para ACCESSORY_SET_HID_REPORT_DESC (63 bytes). */
    val REPORT_DESCRIPTOR: ByteArray = ByteArray(RAW_BYTES.size) { RAW_BYTES[it].toByte() }

    val SIZE: Int get() = REPORT_DESCRIPTOR.size
}
