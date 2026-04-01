package com.example.aws_dummy_app.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aws_dummy_app.databinding.ActivityProvisioningBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProvisioningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProvisioningBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvisioningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Only need SSID and PASSWORD — API already succeeded in RegistrationActivity
        val ssid = intent.getStringExtra("SSID") ?: ""
        val password = intent.getStringExtra("PASSWORD") ?: ""

        startProvisioning(ssid, password)
    }

    private fun startProvisioning(ssid: String, password: String) {
        lifecycleScope.launch {

            updateStatus("🔵 Connecting to BLE device...")
            binding.progressBar.progress = 25
            delay(1000)

            updateStatus("✅ Device already validated by server")
            binding.progressBar.progress = 50
            delay(800)

            updateStatus("📡 Sending WiFi credentials to device...")
            binding.progressBar.progress = 75
            delay(500)

            sendWifiViaBle(ssid, password)
        }
    }

    private fun sendWifiViaBle(ssid: String, password: String) {
        val bleManager = BleHolder.bleManager

        if (bleManager == null) {
            updateStatus("❌ BLE not connected. Please go back and reconnect.")
            binding.progressBar.progress = 0
            return
        }

        bleManager.onWifiSent = {
            runOnUiThread {
                binding.progressBar.progress = 100
                updateStatus("🎉 Provisioning Complete!")
                updateStatus("📶 Device is now connecting to WiFi...")
                Toast.makeText(
                    this,
                    "Device provisioned successfully!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        bleManager.onError = { msg ->
            runOnUiThread {
                updateStatus("❌ BLE Error: $msg")
                binding.progressBar.progress = 0
            }
        }

        bleManager.sendWifiCredentials(ssid, password)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            binding.tvStatus.text = message
            val current = binding.tvLog.text.toString()
            binding.tvLog.text = if (current.isEmpty()) message else "$current\n$message"
        }
    }
}
