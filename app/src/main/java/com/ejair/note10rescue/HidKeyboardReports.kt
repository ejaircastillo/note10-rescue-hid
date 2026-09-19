package com.ejair.note10rescue

/**
 * Generación de reports HID de teclado (8 bytes), independiente de Android.
 * Función pura: sin I/O, sin estado, testeable en la JVM.
 */
object HidKeyboardReports {

    const val REPORT_SIZE = 8
    const val INDEX_MODIFIERS = 0
    const val INDEX_RESERVED = 1
    const val INDEX_KEYS = 2
    const val MAX_KEYS = 6

    const val MOD_NONE = 0x00
    const val MOD_LEFT_SHIFT = 0x02

    /** KEY DOWN: un único keycode en el primer slot de la lista de teclas. */
    fun keyDown(keycode: Int, modifiers: Int = MOD_NONE): ByteArray {
        require(keycode in 0x00..0xFF) { "keycode fuera de rango: $keycode" }
        require(modifiers in 0x00..0xFF) { "modifiers fuera de rango: $modifiers" }
        val report = ByteArray(REPORT_SIZE)
        report[INDEX_MODIFIERS] = modifiers.toByte()
        report[INDEX_RESERVED] = 0
        report[INDEX_KEYS] = keycode.toByte()
        return report
    }

    /** KEY RELEASE: report de ceros (ninguna tecla pulsada). */
    fun keyRelease(): ByteArray = ByteArray(REPORT_SIZE)

    /** ENTER como report independiente. */
    fun enter(): ByteArray = keyDown(HidKeycodes.ENTER)

    fun tab(): ByteArray = keyDown(HidKeycodes.TAB)

    fun backspace(): ByteArray = keyDown(HidKeycodes.BACKSPACE)

    /**
     * Resumen seguro para el registro técnico: nunca incluye keycodes
     * (un keycode permite deducir el dígito).
     */
    fun describeSafely(report: ByteArray): String {
        val mods = (report.getOrNull(INDEX_MODIFIERS)?.toInt() ?: 0) and 0xFF
        val keys = report.drop(INDEX_KEYS).count { it.toInt() != 0 }
        return "report[mods=$mods keys=$keys]"
    }
}
