package com.ejair.note10rescue

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build

/** Un dispositivo USB detectado, con lo mínimo para mostrarlo y elegirlo. */
class UsbDeviceInfo(
    val device: UsbDevice,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?
) {
    fun label(): String {
        val name = productName ?: device.productName ?: "USB device"
        val vendor = manufacturerName ?: "fabricante desconocido"
        return "%s — %s (VID 0x%04X, PID 0x%04X)".format(name, vendor, vendorId, productId)
    }

    fun isSamsung(): Boolean = vendorId == SAMSUNG_VENDOR_ID

    companion object {
        /** Samsung suele usar 0x04E8, pero la app no se restringe a ese vendor. */
        const val SAMSUNG_VENDOR_ID = 0x04E8
    }
}

/**
 * Enumeración de dispositivos USB, permiso USB (PendingIntent + BroadcastReceiver),
 * attach/detach y apertura de la conexión.
 *
 * Nada de ADB, nada de red: sólo `UsbManager`.
 */
class UsbDeviceManager(private val context: Context) {

    companion object {
        /**
         * Acción del broadcast de permiso. Es propia de la app (no es una
         * permission de sistema) y se declara con el package name para que
         * sólo nuestra app la reciba.
         */
        const val ACTION_USB_PERMISSION = "com.ejair.note10rescue.USB_PERMISSION"
        private const val REQUEST_CODE_PERMISSION = 1001
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    var onLog: ((String) -> Unit)? = null
    var onPermissionResult: ((UsbDevice, Boolean) -> Unit)? = null
    var onDeviceAttached: ((UsbDevice) -> Unit)? = null
    var onDeviceDetached: ((UsbDevice) -> Unit)? = null

    private var registered = false
    private var pendingDevice: UsbDevice? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val manager = usbManager
            var device = intent.extras?.let { extractDevice(intent) } ?: pendingDevice
            var granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            // El extra puede venir ausente; el estado real lo manda hasPermission().
            if (device != null && manager != null && manager.hasPermission(device)) {
                granted = true
            }
            if (device == null) {
                onLog?.invoke("USB permission result: dispositivo desconocido, granted=$granted")
                return
            }
            if (granted) onLog?.invoke("USB permission granted")
            else onLog?.invoke("USB permission denied")
            onPermissionResult?.invoke(device, granted)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val device = extractDevice(intent) ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> onDeviceAttached?.invoke(device)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> onDeviceDetached?.invoke(device)
            }
        }
    }

    fun isHostSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    /**
     * Registra los receivers mientras la Activity está visible.
     * ATTACHED/DETACHED son broadcasts de sistema (exported); el de permiso es
     * propio de la app (not exported) para que no lo pueda disparar nadie más.
     */
    fun registerReceivers() {
        if (registered) return
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(systemReceiver, systemFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, permissionFilter)
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(systemReceiver, systemFilter)
        }
        registered = true
    }

    fun unregisterReceivers() {
        if (!registered) return
        runCatching { context.unregisterReceiver(permissionReceiver) }
        runCatching { context.unregisterReceiver(systemReceiver) }
        registered = false
    }

    /** Enumeración vía UsbManager.deviceList(). */
    fun listDevices(): List<UsbDeviceInfo> {
        val manager = usbManager ?: return emptyList()
        return manager.deviceList.values
            .map { device ->
                UsbDeviceInfo(
                    device = device,
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = runCatching { device.manufacturerName }.getOrNull(),
                    productName = runCatching { device.productName }.getOrNull()
                )
            }
            .sortedBy { it.deviceName }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager?.hasPermission(device) ?: false

    /** Pide permiso con PendingIntent + BroadcastReceiver (obligatorio en Android). */
    fun requestPermission(device: UsbDevice) {
        val manager = usbManager
            ?: throw AoaException(AoaError.NO_USB_DEVICE, "UsbManager no disponible")
        pendingDevice = device
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_PERMISSION, intent, flags)
        manager.requestPermission(device, pendingIntent)
    }

    /** Abre la conexión USB. Requiere permiso concedido. */
    fun open(device: UsbDevice): UsbDeviceConnection {
        val manager = usbManager
            ?: throw AoaException(AoaError.NO_USB_DEVICE, "UsbManager no disponible")
        if (!manager.hasPermission(device)) {
            throw AoaException(AoaError.USB_PERMISSION_DENIED, "device=${device.deviceName}")
        }
        return manager.openDevice(device)
            ?: throw AoaException(
                AoaError.OPEN_DEVICE_FAILED,
                "UsbManager.openDevice() devolvió null para ${device.deviceName}"
            )
    }

    /** Un dispositivo sigue presente si su deviceName figura en deviceList(). */
    fun isStillAttached(device: UsbDevice): Boolean {
        val manager = usbManager ?: return false
        return manager.deviceList.values.any { it.deviceName == device.deviceName }
    }

    private fun extractDevice(intent: Intent): UsbDevice? {
        val extras = intent.extras ?: return null
        if (!extras.containsKey(UsbManager.EXTRA_DEVICE)) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}
