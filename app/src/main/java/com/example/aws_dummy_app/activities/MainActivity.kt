package com.example.aws_dummy_app.activities

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aws_dummy_app.auth.TokenManager
import com.example.aws_dummy_app.ble.BleManager
import com.example.aws_dummy_app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startBleScan()
        else Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleManager(this)
        deviceAdapter = DeviceAdapter(deviceList) { device -> onDeviceTapped(device) }

        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter

        bleManager.onDeviceFound = { device ->
            runOnUiThread {
                if (deviceList.none { it.address == device.address }) {
                    deviceList.add(device)
                    deviceAdapter.notifyItemInserted(deviceList.size - 1)
                }
            }
        }

        bleManager.onError = { msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        }

        // â”€â”€ Door Access â”€â”€
        binding.btnGenerateQR.setOnClickListener {
            startActivity(Intent(this, GenerateQRActivity::class.java))
        }
        binding.btnValidateQR.setOnClickListener {
            startActivity(Intent(this, QRValidatorActivity::class.java))
        }

        // â”€â”€ Auth Info Button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        binding.btnAuthInfo.setOnClickListener {
            val stateJson = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("state", null)

            if (stateJson == null) {
                showAuthDialog("Not logged in", "No auth state found.")
                return@setOnClickListener
            }

            val authState = AuthState.jsonDeserialize(stateJson)
            val accessToken = authState.accessToken ?: "null"
            val idToken     = authState.idToken ?: "null"

            // Decode sub + email from id_token
            var sub   = "unknown"
            var email = "unknown"
            try {
                val payload = idToken.split(".")[1]
                val decoded = String(
                    android.util.Base64.decode(
                        payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                    )
                )
                val json = org.json.JSONObject(decoded)
                sub   = json.optString("sub", "unknown")
                email = json.optString("email", "unknown")
            } catch (e: Exception) {
                sub = "decode error"
            }

            showAuthDialog(
                title   = "Auth Info",
                message = """
                    âœ… Logged In: ${authState.isAuthorized}
                    
                    ðŸ‘¤ Sub (User ID):
                    $sub
                    
                    ðŸ“§ Email:
                    $email
                    
                    ðŸ”‘ Access Token:
                    ${accessToken.take(80)}...
                    
                    ðŸªª ID Token:
                    ${idToken.take(80)}...
                """.trimIndent()
            )
        }
        // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        // â”€â”€ BLE Provisioning â”€â”€
        binding.btnScan.setOnClickListener { requestPermissionsAndScan() }

        // â”€â”€ Logout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    // Clear auth state
                    TokenManager.clearSession(this)
                    // Clear Room DB log
                    GlobalScope.launch(Dispatchers.IO) {
                        com.example.aws_dummy_app.db.AppDatabase
                            .getInstance(applicationContext)
                            .authLogDao()
                            .deleteAll()
                    }
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    }

    private fun showAuthDialog(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun requestPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val allGranted = permissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startBleScan() else permissionLauncher.launch(permissions)
    }

    private fun startBleScan() {
        deviceList.clear()
        deviceAdapter.notifyDataSetChanged()
        binding.tvStatus.text = "Scanning for ESP32 devices..."
        binding.btnScan.isEnabled = false
        bleManager.startScan()
        binding.rvDevices.postDelayed({
            bleManager.stopScan()
            binding.btnScan.isEnabled = true
            binding.tvStatus.text = "Scan complete. Found ${deviceList.size} device(s)."
        }, 10000)
    }

    private fun onDeviceTapped(device: BluetoothDevice) {
        bleManager.stopScan()
        binding.btnScan.isEnabled = true
        startActivity(Intent(this, DeviceConnectionActivity::class.java).apply {
            putExtra("DEVICE_ADDRESS", device.address)
            putExtra("DEVICE_NAME", device.name ?: "ESP32_SETUP")
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
    }
}