package com.ejair.note10rescue

/**
 * PIN embebido en el build (opcional, sólo para builds privados).
 *
 * El valor llega por `BuildConfig.EMBEDDED_PIN_OBFUSCATED`, generado por Gradle a
 * partir de `pin-local.properties` (archivo **no versionado**: ver .gitignore). Si
 * ese archivo no existe, el campo queda vacío, `hasEmbeddedPin` es `false` y la app
 * funciona igual, pidiendo el PIN a mano.
 *
 * La codificación es XOR 0x5A + hex, la misma que aplica `app/build.gradle.kts`.
 * Es **ofuscación, no criptografía**: evita que el PIN aparezca como texto plano en
 * el .dex, pero quien tenga el APK puede recuperarlo. Por eso el APK con PIN
 * embebido es privado y no se publica.
 */
object PinVault {

    private const val XOR_KEY = 0x5A

    /** Decodifica el hex ofuscado que genera Gradle. Devuelve "" si no es válido. */
    fun decode(hexEncoded: String): String {
        if (hexEncoded.isEmpty() || hexEncoded.length % 2 != 0) return ""
        return buildString {
            var index = 0
            while (index < hexEncoded.length) {
                val byte = hexEncoded.substring(index, index + 2).toIntOrNull(16) ?: return ""
                append(((byte xor XOR_KEY) and 0xFF).toChar())
                index += 2
            }
        }
    }

    /** Misma codificación que usa el build (para tests y para documentar el formato). */
    fun encode(pin: String): String =
        pin.map { ((it.code xor XOR_KEY) and 0xFF).toString(16).padStart(2, '0') }.joinToString("")

    /** PIN embebido, ya decodificado. Vacío si este build es el público. */
    val embeddedPin: String by lazy { decode(BuildConfig.EMBEDDED_PIN_OBFUSCATED) }

    /** true sólo si hay un PIN embebido y son todos dígitos. */
    val hasEmbeddedPin: Boolean
        get() = embeddedPin.isNotEmpty() && embeddedPin.all { it in '0'..'9' }
}
