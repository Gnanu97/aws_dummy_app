package com.example.aws_dummy_app.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aws_dummy_app.ble.BleManager
import com.example.aws_dummy_app.databinding.ActivityDeviceConnectionBinding

@SuppressLint("MissingPermission")
class DeviceConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceConnectionBinding
    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS") ?: return
        val deviceName = intent.getStringExtra("DEVICE_NAME") ?: "ESP32"

        binding.tvDeviceName.text = "Connecting to: $deviceName"
        binding.tvStatus.text = "⏳ Connecting to device..."

        bleManager = BleManager(this)

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

        bleManager.onConnected = {
            runOnUiThread {
                binding.tvStatus.text = "✅ Connected! Reading device info..."
            }
        }

        bleManager.onMacRead = { mac ->
            runOnUiThread {
                binding.tvStatus.text = "✅ MAC Address read: $mac"
                BleHolder.bleManager = bleManager
                val intent = Intent(this, QRScannerActivity::class.java).apply {
                    putExtra("MAC_ADDRESS", mac)
                }
                startActivity(intent)
            }
        }

        bleManager.onError = { msg ->
            runOnUiThread {
                binding.tvStatus.text = "❌ Error: $msg"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        bleManager.connectToDevice(device)
    }
}

object BleHolder {
    var bleManager: BleManager? = null
}