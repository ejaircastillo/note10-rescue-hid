package com.ejair.note10rescue

/**
 * Arma el texto del "registro técnico" que el usuario puede compartir o copiar
 * para reportar un problema.
 *
 * Es una función pura (sin Android) para poder testear que **nunca** incluya el
 * PIN: el log jamás contiene dígitos ni keycodes, y acá sólo se concatenan
 * líneas que ya pasaron por ese filtro.
 */
object DiagnosticsReport {

    /** Tope de líneas que se incluyen en el reporte (las últimas). */
    const val MAX_LOG_LINES = 300

    fun build(
        appVersion: String,
        hostSummary: String,
        targetSummary: String,
        protocolSummary: String,
        statusSummary: String,
        attempts: Int,
        lastSequence: String,
        usbState: String,
        logLines: List<String>,
        auditTrail: String = ""
    ): String {
        val included = if (logLines.size > MAX_LOG_LINES) logLines.takeLast(MAX_LOG_LINES) else logLines
        val omitted = logLines.size - included.size
        return buildString {
            appendLine("Note10 Rescue HID — registro técnico")
            appendLine("app: $appVersion")
            appendLine("host: $hostSummary")
            appendLine("target: $targetSummary")
            appendLine(protocolSummary)
            appendLine("estado: $statusSummary")
            appendLine("intentos enviados (contador de esta sesión): $attempts")
            appendLine("último envío: $lastSequence")
            appendLine("estado USB observado: $usbState")
            appendLine()
            appendLine("--- registro (${included.size} líneas${if (omitted > 0) ", $omitted omitidas" else ""}) ---")
            included.forEach { appendLine(it) }
            appendLine("--- fin ---")
            if (auditTrail.isNotBlank()) {
                appendLine()
                appendLine("--- bitácora de auditoría (audit.log) ---")
                appendLine(auditTrail.trimEnd())
            }
        }
    }
}
