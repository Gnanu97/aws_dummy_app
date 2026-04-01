package com.example.aws_dummy_app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")
    val MAC_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789013")
    val WIFI_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789014")
}

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onMacRead: ((String) -> Unit)? = null
    var onWifiSent: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Show ALL BLE devices, including unnamed ones
            onDeviceFound?.invoke(device)
        }

        override fun onScanFailed(errorCode: Int) {
            onError?.invoke("BLE scan failed: $errorCode")
        }
    }

    fun startScan() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bleScanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        bleScanner.stopScan(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BLE", "Connected! Discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> onError?.invoke("Disconnected from device")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onConnected?.invoke()
                readMacCharacteristic()
            } else {
                onError?.invoke("Service discovery failed")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                characteristic.uuid == BleConstants.MAC_CHAR_UUID) {
                val mac = characteristic.value?.toString(Charsets.UTF_8) ?: "UNKNOWN"
                onMacRead?.invoke(mac)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) onWifiSent?.invoke()
            else onError?.invoke("Failed to write WiFi credentials")
        }
    }

    fun readMacCharacteristic() {
        val characteristic = bluetoothGatt
            ?.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.MAC_CHAR_UUID)
        if (characteristic != null) bluetoothGatt?.readCharacteristic(characteristic)
        else onError?.invoke("MAC characteristic not found")
    }

    fun sendWifiCredentials(ssid: String, password: String) {
        val characteristic = bluetoothGatt
            ?.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.WIFI_CHAR_UUID)
        if (characteristic != null) {
            characteristic.value = "$ssid,$password".toByteArray(Charsets.UTF_8)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(characteristic)
        } else onError?.invoke("WiFi characteristic not found")
    }

    fun disconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
