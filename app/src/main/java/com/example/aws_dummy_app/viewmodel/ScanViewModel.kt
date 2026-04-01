// com/example/aws_dummy_app/viewmodel/ScanViewModel.kt
package com.example.aws_dummy_app.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ScanViewModel : ViewModel() {

    // List of discovered BLE devices
    private val _devices = MutableLiveData<MutableList<BluetoothDevice>>(mutableListOf())
    val devices: LiveData<MutableList<BluetoothDevice>> = _devices

    // Status message shown to user
    private val _statusMessage = MutableLiveData<String>("Press button to start scanning")
    val statusMessage: LiveData<String> = _statusMessage

    // Scanning state (true = currently scanning)
    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    fun addDevice(device: BluetoothDevice) {
        val currentList = _devices.value ?: mutableListOf()
        // Avoid duplicates by checking MAC address
        if (currentList.none { it.address == device.address }) {
            currentList.add(device)
            _devices.postValue(currentList)
        }
    }

    fun clearDevices() {
        _devices.postValue(mutableListOf())
    }

    fun setStatus(message: String) {
        _statusMessage.postValue(message)
    }

    fun setScanningState(scanning: Boolean) {
        _isScanning.postValue(scanning)
    }
}
