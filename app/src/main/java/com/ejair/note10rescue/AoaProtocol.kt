package com.ejair.note10rescue

/**
 * Constantes del protocolo Android Open Accessory 2.0 (AOA2 / AOAv2).
 *
 * Referencia: https://source.android.com/docs/core/interaction/accessories/aoa2
 * Portado de scrcpy `app/src/usb/aoa_hid.c`, que sólo usa estos cuatro comandos
 * para inyectar HID (no manda ACCESSORY_START: registra el HID sobre la
 * conexión USB actual).
 *
 * Los control transfers son de tipo vendor (USB_TYPE_VENDOR):
 *   - OUT (host -> dispositivo): USB_DIR_OUT | USB_TYPE_VENDOR == 0x40
 *   - IN  (dispositivo -> host): USB_DIR_IN  | USB_TYPE_VENDOR == 0xC0
 */
object AoaProtocol {

    /** Accesorio -> Android: pedir la versión de protocolo soportada (uint16 little-endian). */
    const val ACCESSORY_GET_PROTOCOL = 51

    /** Accesorio -> Android: registrar un HID. value = id, index = longitud del report descriptor. */
    const val ACCESSORY_REGISTER_HID = 54

    /** Accesorio -> Android: desregistrar un HID. value = id. */
    const val ACCESSORY_UNREGISTER_HID = 55

    /** Accesorio -> Android: enviar el report descriptor. value = id, index = offset. */
    const val ACCESSORY_SET_HID_REPORT_DESC = 56

    /** Accesorio -> Android: inyectar un report HID. value = id, index = 0. */
    const val ACCESSORY_SEND_HID_EVENT = 57

    const val USB_DIR_OUT = 0x00
    const val USB_DIR_IN = 0x80
    const val USB_TYPE_VENDOR = 0x40
    const val USB_RECIPIENT_DEVICE = 0x00

    /** bmRequestType para comandos OUT de tipo vendor: 0x40. */
    const val REQUEST_TYPE_OUT_VENDOR = USB_DIR_OUT or USB_TYPE_VENDOR or USB_RECIPIENT_DEVICE

    /** bmRequestType para GET_PROTOCOL (IN de tipo vendor): 0xC0. */
    const val REQUEST_TYPE_IN_VENDOR = USB_DIR_IN or USB_TYPE_VENDOR or USB_RECIPIENT_DEVICE

    /** Timeout de cada controlTransfer (scrcpy usa DEFAULT_TIMEOUT 1000 ms). */
    const val DEFAULT_TIMEOUT_MS = 1000

    /** El accesorio elige el id; scrcpy usa 1 para el teclado. */
    const val HID_ID_KEYBOARD = 1

    /** Etiqueta legible del bmRequestType, sólo para el registro técnico. */
    fun requestTypeLabel(requestType: Int): String = "0x%02X".format(requestType and 0xFF)
}
