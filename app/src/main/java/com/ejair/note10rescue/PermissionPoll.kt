package com.ejair.note10rescue

/**
 * Sondeo del permiso USB.
 *
 * El resultado del diálogo del sistema llega por broadcast, pero ese broadcast puede
 * perderse (PendingIntent sin mutabilidad, receiver no exportado, un OEM que lo manda
 * distinto, la app en segundo plano). El permiso **real**, en cambio, se puede consultar
 * en cualquier momento con `UsbManager.hasPermission()`: por eso además de esperar el
 * broadcast se sondea ese estado.
 *
 * Es lógica pura: el reloj y la consulta se inyectan, así que se testea sin Android.
 */
class PermissionPoll(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val slumber: (Long) -> Unit = { ms -> Thread.sleep(ms) },
    private val onTick: (Long) -> Unit = {}
) {

    companion object {
        /** Cuánto se espera, como máximo, a que el sistema confirme el permiso. */
        const val DEFAULT_TIMEOUT_MS = 20_000L

        /** Cada cuánto se consulta `hasPermission()`. */
        const val DEFAULT_INTERVAL_MS = 500L
    }

    /**
     * Espera hasta [timeoutMs] a que [hasPermission] devuelva `true`.
     *
     * @return `true` si el permiso quedó concedido (lo haya confirmado el broadcast o
     *   el sondeo). `false` si se agotó el tiempo sin que el sistema concediera nada.
     */
    fun await(hasPermission: () -> Boolean): Boolean {
        if (hasPermission()) return true
        var waited = 0L
        while (waited < timeoutMs) {
            slumber(intervalMs)
            waited += intervalMs
            if (hasPermission()) return true
            onTick(waited)
        }
        return false
    }
}
