package com.ejair.note10rescue

import android.content.Context

/**
 * Recuerda el estado de las **casillas** de la pantalla entre sesiones.
 *
 * Por qué: un update del APK (o reabrir la app) reseteaba "Enviar TAB antes del PIN" a
 * apagado, y enviar sin esa tecla hace que Android se coma el primer dígito si la pantalla
 * estaba apagada → el intento se pierde sin ningún error en el registro. Ya pasó dos veces.
 *
 * Guarda **sólo booleanos de configuración**: nunca el PIN, ni dígitos, ni el campo PIN.
 * (El PIN sigue sin persistirse en ningún lado: ver `PinVault` y "Seguridad del PIN".)
 */
class UiPrefs(context: Context) {

    companion object {
        private const val FILE = "note10rescue_ui"
        private const val KEY_WAKE = "wake_key_first"
        private const val KEY_CLEAR = "clear_field_first"
        private const val KEY_SIMULATED = "simulated_mode"
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** TAB antes del PIN: por defecto **activado** (es la opción segura). */
    var wakeKeyFirst: Boolean
        get() = prefs.getBoolean(KEY_WAKE, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE, value).apply()

    /** Limpiar el campo antes del PIN: por defecto apagado. */
    var clearFieldFirst: Boolean
        get() = prefs.getBoolean(KEY_CLEAR, false)
        set(value) = prefs.edit().putBoolean(KEY_CLEAR, value).apply()

    /** Modo de prueba: por defecto apagado (nunca arrancar simulando sin querer). */
    var simulatedMode: Boolean
        get() = prefs.getBoolean(KEY_SIMULATED, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATED, value).apply()
}
