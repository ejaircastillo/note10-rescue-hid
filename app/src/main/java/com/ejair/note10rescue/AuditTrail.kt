package com.ejair.note10rescue

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitácora de auditoría en el almacenamiento privado de la app (`filesDir`), sin
 * permisos y sin red: una línea por intento, para poder revisar después qué pasó.
 *
 * **Nunca** registra el PIN ni los keycodes de los dígitos: sólo origen
 * ("embebido"/"ingresado"), cantidad de dígitos, reports, duración y resultado.
 */
class AuditTrail(private val context: Context) {

    companion object {
        const val FILE_NAME = "audit.log"
        private const val MAX_LINES = 500
        private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun append(line: String): Boolean = runCatching {
        val stamped = "${TIME_FORMAT.format(Date())}  $line"
        file.appendText("$stamped\n")
        trimIfNeeded()
    }.isSuccess

    fun read(): String = runCatching {
        if (file.exists()) file.readText() else ""
    }.getOrDefault("")

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    private fun trimIfNeeded() {
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }
}

/** Formato de las líneas de auditoría (puro, testeable: se verifica que no filtre el PIN). */
object AuditEntry {

    fun attempt(
        number: Int,
        pinSource: String,
        stats: AoaHidKeyboard.SequenceStats,
        simulated: Boolean = false
    ): String = "INTENTO #$number resultado=ENVIADO origen=$pinSource dígitos=${stats.digits} " +
        "reports=${stats.reports} duración=${stats.durationMs}ms teclaDespertar=${stats.wakeKeyFirst} " +
        "campoLimpiado=${stats.clearedField} reintentos=${stats.retries} " +
        "transporte=${if (simulated) "simulado" else "usb"}" +
        if (simulated) " modo=PRUEBA_sin_envio" else ""

    fun failure(number: Int, error: AoaError, detail: String): String =
        "INTENTO #$number resultado=ERROR codigo=${error.code} detalle=$detail"

    fun usbObservation(detail: String): String = "OBSERVACION-USB $detail"

    /** Indicios de desbloqueo + veredicto: es la línea que responde "¿entró o no?". */
    fun unlockObservation(observation: UnlockEvidence.Observation): String =
        "DESBLOQUEO intento=#${observation.attempt} vibracion=${observation.vibration.name} " +
            "usb=${observation.usb.name} mtp=${observation.mtp.name} " +
            "veredicto=${UnlockEvidence.verdict(observation)}"

    fun session(action: String, embedded: Boolean): String =
        "SESION $action origenDisponible=${if (embedded) "embebido" else "ninguno"}"
}
