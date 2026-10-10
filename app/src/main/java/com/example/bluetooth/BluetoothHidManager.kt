package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.keyboard.KeyboardDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

sealed class HidConnectionState {
    data object Unavailable : HidConnectionState()
    data class NotSupported(val reason: String) : HidConnectionState()
    data object BluetoothDisabled : HidConnectionState()
    data object PermissionRequired : HidConnectionState()
    data object Registering : HidConnectionState()
    data object Disconnected : HidConnectionState() // Registered, waiting for connection
    data class Connecting(val deviceName: String?) : HidConnectionState()
    data class Connected(val device: BluetoothDevice, val deviceName: String) : HidConnectionState()
    data class Error(val message: String) : HidConnectionState()
}

/**
 * Manages the Bluetooth HID Device profile on Android.
 * Communicates directly with the Android Bluetooth stack to register as an HID keyboard peripheral.
 */
class BluetoothHidManager(private val context: Context) {

    private val tag = "BluetoothHidManager"

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private val _isHidRegistered = MutableStateFlow(false)
    val isHidRegistered: StateFlow<Boolean> = _isHidRegistered.asStateFlow()

    private val _connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Disconnected)
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(tag, "onAppStatusChanged: registered=$registered, pluggedDevice=${pluggedDevice?.name}")
            _isHidRegistered.value = registered
            if (registered) {
                if (pluggedDevice != null) {
                    val name = getSafeDeviceName(pluggedDevice)
                    _connectedDevice.value = pluggedDevice
                    _connectionState.value = HidConnectionState.Connected(pluggedDevice, name)
                } else {
                    autoDetectConnectedDevice()
                }
            } else {
                _connectionState.value = HidConnectionState.NotSupported("HID App registration failed or unregistered.")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            val devName = getSafeDeviceName(device)
            Log.d(tag, "onConnectionStateChanged: device=$devName, state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevice.value = device
                    _connectionState.value = HidConnectionState.Connected(device, devName)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = HidConnectionState.Connecting(devName)
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _connectionState.value = HidConnectionState.Disconnected
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_connectedDevice.value?.address == device.address) {
                        _connectedDevice.value = null
                    }
                    _connectionState.value = HidConnectionState.Disconnected
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.d(tag, "onGetReport: type=$type, id=$id, bufferSize=$bufferSize")
            // Send empty report if requested by host
            if (device != null && hidDevice != null) {
                val emptyReport = ByteArray(8) { 0 }
                try {
                    hidDevice?.replyReport(device, type, id, emptyReport)
                } catch (e: SecurityException) {
                    Log.w(tag, "Security exception in onGetReport", e)
                }
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            super.onSetReport(device, type, id, data)
            Log.d(tag, "onSetReport: type=$type, id=$id, dataSize=${data?.size}")
            // Acknowledge report from host (e.g. keyboard LED status)
            if (device != null && hidDevice != null) {
                try {
                    hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS.toByte())
                } catch (e: SecurityException) {
                    Log.w(tag, "Security exception in onSetReport", e)
                }
            }
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            super.onSetProtocol(device, protocol)
            Log.d(tag, "onSetProtocol: protocol=$protocol")
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            super.onInterruptData(device, reportId, data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
            Log.d(tag, "onVirtualCableUnplug")
            _connectedDevice.value = null
            _connectionState.value = HidConnectionState.Disconnected
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(tag, "HID_DEVICE profile proxy connected")
                hidDevice = proxy as? BluetoothHidDevice
                registerHidApp()
                autoDetectConnectedDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(tag, "HID_DEVICE profile proxy disconnected")
                hidDevice = null
                _isHidRegistered.value = false
                _connectionState.value = HidConnectionState.Disconnected
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        _connectionState.value = HidConnectionState.BluetoothDisabled
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        refresh()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    updatePairedDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    /**
     * Checks whether all required Bluetooth permissions are granted.
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Initialize or refresh the Bluetooth HID connection.
     */
    fun refresh() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _connectionState.value = HidConnectionState.Unavailable
            return
        }

        if (!hasBluetoothPermissions()) {
            _connectionState.value = HidConnectionState.PermissionRequired
            return
        }

        if (!adapter.isEnabled) {
            _connectionState.value = HidConnectionState.BluetoothDisabled
            return
        }

        updatePairedDevices()
        autoDetectConnectedDevice()

        if (hidDevice == null) {
            _connectionState.value = HidConnectionState.Registering
            try {
                val success = adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
                if (!success) {
                    _connectionState.value = HidConnectionState.NotSupported(
                        "Device manufacturer or ROM does not expose Bluetooth HID Device profile (Profile 19)."
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to get HID profile proxy", e)
                _connectionState.value = HidConnectionState.NotSupported(
                    "Error requesting Bluetooth HID profile: ${e.message}"
                )
            }
        } else if (!_isHidRegistered.value) {
            registerHidApp()
        }
    }

    @SuppressLint("MissingPermission")
    fun autoDetectConnectedDevice() {
        if (!hasBluetoothPermissions()) return
        val dev = hidDevice ?: return
        try {
            val connectedList = dev.connectedDevices
            val alreadyConnected = connectedList.firstOrNull() ?:
                dev.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTED)).firstOrNull()
            if (alreadyConnected != null) {
                val devName = getSafeDeviceName(alreadyConnected)
                Log.d(tag, "Auto-detected already connected device: $devName (${alreadyConnected.address})")
                _connectedDevice.value = alreadyConnected
                _connectionState.value = HidConnectionState.Connected(alreadyConnected, devName)
                return
            }
            if (_connectedDevice.value == null && _connectionState.value !is HidConnectionState.Connecting) {
                _connectionState.value = HidConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Log.w(tag, "Notice checking connected devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val dev = hidDevice ?: return
        if (!hasBluetoothPermissions()) {
            _connectionState.value = HidConnectionState.PermissionRequired
            return
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "replica_kspp Keyboard",
            "Bluetooth HID Keyboard Peripheral",
            "replica_kspp",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            KeyboardDescriptor.REPORT_DESCRIPTOR
        )

        val qosIn = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val qosOut = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        try {
            _connectionState.value = HidConnectionState.Registering
            val registered = dev.registerApp(sdp, qosIn, qosOut, executor, hidCallback)
            Log.d(tag, "registerApp call returned: $registered")
            if (!registered) {
                _connectionState.value = HidConnectionState.NotSupported(
                    "Bluetooth HID Device registration rejected by Android OS. Ensure Bluetooth is ON and no other HID app is active."
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception registering HID app", e)
            _connectionState.value = HidConnectionState.Error("Registration exception: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun updatePairedDevices() {
        if (!hasBluetoothPermissions()) return
        try {
            val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            _pairedDevices.value = bonded
        } catch (e: Exception) {
            Log.w(tag, "Error reading paired devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        val dev = hidDevice
        if (dev == null) {
            _connectionState.value = HidConnectionState.Error("HID service is not ready yet.")
            return
        }
        if (!hasBluetoothPermissions()) {
            _connectionState.value = HidConnectionState.PermissionRequired
            return
        }

        try {
            val name = getSafeDeviceName(device)
            _connectionState.value = HidConnectionState.Connecting(name)
            val success = dev.connect(device)
            Log.d(tag, "connect to $name returned: $success")
            if (!success) {
                _connectionState.value = HidConnectionState.Error(
                    "Unable to initiate HID connection to $name. Ensure laptop Bluetooth is ON and paired."
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error connecting to device", e)
            _connectionState.value = HidConnectionState.Error("Connection error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        val dev = hidDevice
        val target = _connectedDevice.value
        if (dev != null && target != null && hasBluetoothPermissions()) {
            try {
                dev.disconnect(target)
            } catch (e: Exception) {
                Log.w(tag, "Error disconnecting", e)
            }
        }
        _connectedDevice.value = null
        _connectionState.value = HidConnectionState.Disconnected
    }

    /**
     * Send an 8-byte HID keyboard report to the connected Bluetooth host.
     * Report ID 1 matches the descriptor's REPORT_ID (1).
     */
    @SuppressLint("MissingPermission")
    fun sendReport(reportData: ByteArray): Boolean {
        val dev = hidDevice
        val target = _connectedDevice.value
        if (dev == null || target == null) {
            return false
        }
        if (!hasBluetoothPermissions()) {
            return false
        }

        return try {
            dev.sendReport(target, KeyboardDescriptor.REPORT_ID_KEYBOARD.toInt(), reportData)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send HID report", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getSafeDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothPermissions()) {
                device.name ?: device.address ?: "Unknown Device"
            } else {
                device.address ?: "Bluetooth Device"
            }
        } catch (e: Exception) {
            "Bluetooth Device"
        }
    }

    fun openBluetoothSettings(ctx: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to open Bluetooth settings", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestDiscoverability(ctx: Context, durationSeconds: Int = 120) {
        try {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to request discoverability", e)
        }
    }

    fun getDiagnosticsSummary(): String {
        val adapter = bluetoothAdapter
        val isBtOn = adapter?.isEnabled == true
        val target = _connectedDevice.value
        val targetName = target?.let { getSafeDeviceName(it) } ?: "None"
        val targetAddress = target?.address ?: "N/A"
        val stateName = when (val s = _connectionState.value) {
            is HidConnectionState.Connected -> "Connected (to ${s.deviceName})"
            is HidConnectionState.Connecting -> "Connecting (${s.deviceName ?: "host"})"
            is HidConnectionState.Disconnected -> if (_isHidRegistered.value) "Ready (Profile Registered, Waiting for Host)" else "Disconnected"
            is HidConnectionState.Registering -> "Registering HID Application Profile"
            is HidConnectionState.PermissionRequired -> "Permission Required"
            is HidConnectionState.BluetoothDisabled -> "Bluetooth Disabled"
            is HidConnectionState.Unavailable -> "Bluetooth Hardware Unavailable"
            is HidConnectionState.NotSupported -> "HID Not Supported: ${s.reason}"
            is HidConnectionState.Error -> "Error: ${s.message}"
        }

        return buildString {
            appendLine("=== replica_kspp Bluetooth HID Diagnostics ===")
            appendLine("App: replica_kspp (v1.0.0)")
            appendLine("Android OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine()
            appendLine("[Bluetooth Hardware]")
            appendLine("Adapter Available: ${adapter != null}")
            appendLine("Bluetooth Enabled: $isBtOn")
            appendLine("Permissions Granted: ${hasBluetoothPermissions()}")
            appendLine()
            appendLine("[HID Device Profile (Profile 19)]")
            appendLine("Profile Proxy Attached: ${hidDevice != null}")
            appendLine("HID App Registered: ${_isHidRegistered.value}")
            appendLine("SDP Service Name: replica_kspp Keyboard")
            appendLine("Report Descriptor: 8-byte Standard USB/BT Boot Keyboard (Report ID 1)")
            appendLine("Key Rollover: 6-key array")
            appendLine()
            appendLine("[Connection Status]")
            appendLine("Current State: $stateName")
            appendLine("Connected Host: $targetName")
            appendLine("Host Address: $targetAddress")
            appendLine("Paired Devices Count: ${_pairedDevices.value.size}")
            appendLine("==============================================")
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}

        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {}

        hidDevice = null
        _isHidRegistered.value = false
    }
}
