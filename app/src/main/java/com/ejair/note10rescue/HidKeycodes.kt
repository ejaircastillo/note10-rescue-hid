package com.ejair.note10rescue

/**
 * HID Usage ID (USB HID Usage Tables, usage page 0x07 "Keyboard/Keypad")
 * para las teclas que usa la app.
 *
 * Mapeo de la fila numérica (igual que un teclado USB físico):
 *   1 = 0x1E, 2 = 0x1F, 3 = 0x20, 4 = 0x21, 5 = 0x22,
 *   6 = 0x23, 7 = 0x24, 8 = 0x25, 9 = 0x26, 0 = 0x27
 *   ENTER = 0x28
 */
object HidKeycodes {

    const val NONE = 0x00
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C

    /** Índice = valor del dígito (0..9); valor = HID usage id de la fila numérica. */
    private val DIGIT_KEYCODES = intArrayOf(
        0x27, // 0
        0x1E, // 1
        0x1F, // 2
        0x20, // 3
        0x21, // 4
        0x22, // 5
        0x23, // 6
        0x24, // 7
        0x25, // 8
        0x26  // 9
    )

    /** HID usage id del dígito [digit] (carácter '0'..'9'). */
    fun forDigit(digit: Char): Int {
        require(digit in '0'..'9') { "No es un dígito: '$digit'" }
        return DIGIT_KEYCODES[digit - '0']
    }

    /** HID usage id del dígito [digit] (0..9). */
    fun forDigit(digit: Int): Int {
        require(digit in 0..9) { "No es un dígito: $digit" }
        return DIGIT_KEYCODES[digit]
    }

    /**
     * Etiqueta segura para el registro técnico. Los dígitos devuelven siempre
     * "DIGIT" para que el log nunca permita reconstruir el PIN.
     */
    fun safeLabel(keycode: Int): String = when (keycode) {
        ENTER -> "ENTER"
        TAB -> "TAB"
        BACKSPACE -> "BACKSPACE"
        ESCAPE -> "ESCAPE"
        SPACE -> "SPACE"
        NONE -> "NONE"
        in DIGIT_KEYCODES -> "DIGIT"
        else -> "KEY"
    }
}
