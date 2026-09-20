package com.ejair.note10rescue

/**
 * Indicios de desbloqueo, registrados por el usuario y por el monitor USB.
 *
 * AOA-HID es un canal **unidireccional**: el host no recibe ninguna confirmación del
 * target, así que la app no puede "leer" si el Note10 se desbloqueó. Lo que sí puede
 * hacer es juntar los indicios observables desde afuera y dejar un **veredicto
 * explícito**, para que el reporte diga algo concreto en vez de dejarlo a
 * interpretación.
 *
 * Los tres indicios, en orden de fuerza:
 *
 *  1. **MTP en una PC** (decisivo): Android sólo expone el almacenamiento con el
 *     equipo desbloqueado. Si la PC lo ve, está desbloqueado; si no lo ve, sigue
 *     bloqueado.
 *  2. **Vibración de rechazo**: Android vibra cuando el PIN es incorrecto. Si se
 *     sintió la vibración, ese PIN no entró.
 *  3. **Cambio de configuración USB** visto por el host: si el bus cambia después
 *     del envío es un indicio a favor; que no cambie no prueba nada.
 *
 * Ojo: con la vibración del sistema desactivada, "no vibró" no dice nada. Por eso el
 * veredicto nunca afirma desbloqueo sin el dato de MTP.
 */
object UnlockEvidence {

    enum class Vibration(val label: String) {
        SI("vibró (rechazo)"),
        NO("no vibró"),
        SIN_DATO("sin dato")
    }

    enum class Mtp(val label: String) {
        SI("la PC muestra el almacenamiento"),
        NO("la PC no muestra almacenamiento"),
        NO_PROBADO("no probado")
    }

    enum class Usb(val label: String) {
        CAMBIO("cambió la configuración USB"),
        SIN_CAMBIO("sin cambios en la ventana"),
        SIN_DATO("sin dato")
    }

    /** Indicios de un envío concreto. Se completan de a poco (el usuario los marca). */
    data class Observation(
        val attempt: Int,
        val vibration: Vibration = Vibration.SIN_DATO,
        val usb: Usb = Usb.SIN_DATO,
        val mtp: Mtp = Mtp.NO_PROBADO
    )

    const val VERDICT_CONFIRMED = "CONFIRMADO — el Note10 está desbloqueado"
    const val VERDICT_LOCKED = "NO DESBLOQUEADO — la PC no vio el almacenamiento del Note10"
    const val VERDICT_REJECTED = "RECHAZADO — el Note10 vibró (Android avisa así el PIN incorrecto)"
    const val VERDICT_PROBABLE = "PROBABLE — sin vibración de rechazo; falta confirmar con MTP"
    const val VERDICT_UNKNOWN = "INDETERMINADO — falta el dato de vibración"
    const val NO_ATTEMPTS = "SIN ENVÍOS — no hubo ningún envío en esta sesión"

    /**
     * Veredicto a partir de los indicios. El orden importa: MTP manda sobre todo lo
     * demás (es el único indicio objetivo), después la vibración, y el bus USB sólo
     * desempata cuando no hay nada mejor.
     */
    fun verdict(observation: Observation?): String = when {
        observation == null -> NO_ATTEMPTS
        observation.mtp == Mtp.SI -> VERDICT_CONFIRMED
        observation.mtp == Mtp.NO -> VERDICT_LOCKED
        observation.vibration == Vibration.SI -> VERDICT_REJECTED
        observation.vibration == Vibration.NO -> VERDICT_PROBABLE
        else -> VERDICT_UNKNOWN
    }

    /** Bloque del reporte compartible: veredicto + indicios + cómo se interpretan. */
    fun report(observation: Observation?, usbStateNote: String): String = buildString {
        appendLine("veredicto: ${verdict(observation)}")
        if (observation == null) {
            appendLine("(no hubo ningún envío en esta sesión: no hay indicios que juzgar)")
        } else {
            appendLine(
                "intento #${observation.attempt}: vibración=${observation.vibration.label} | " +
                    "USB=${observation.usb.label} | MTP=${observation.mtp.label}"
            )
        }
        appendLine("estado USB del host: $usbStateNote")
        appendLine("AOA-HID es unidireccional: la app NO recibe confirmación del Note10, no hay forma de leerlo del protocolo.")
        appendLine("Fuerza de los indicios: MTP en una PC (decisivo) > vibración de rechazo > cambio en el bus USB (a favor, no concluyente).")
        appendLine("Con la vibración del sistema desactivada, 'no vibró' no significa nada: confirmá siempre con MTP.")
    }
}
