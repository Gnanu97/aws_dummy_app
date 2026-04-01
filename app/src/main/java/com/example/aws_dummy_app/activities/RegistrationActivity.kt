package com.example.aws_dummy_app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.aws_dummy_app.databinding.ActivityRegistrationBinding
import com.example.aws_dummy_app.viewmodel.RegistrationState
import com.example.aws_dummy_app.viewmodel.RegistrationViewModel

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var viewModel: RegistrationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val macAddress = intent.getStringExtra("MAC_ADDRESS") ?: ""
        val randomId = intent.getStringExtra("RANDOM_ID") ?: ""

        binding.tvDeviceInfo.text = "MAC: $macAddress  |  ID: $randomId"
        viewModel = ViewModelProvider(this)[RegistrationViewModel::class.java]

        viewModel.registrationState.observe(this) { state ->
            when (state) {
                is RegistrationState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = false
                    binding.tvError.visibility = View.GONE
                }
                is RegistrationState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    startActivity(Intent(this, ProvisioningActivity::class.java).apply {
                        putExtra("MAC_ADDRESS", macAddress)
                        putExtra("RANDOM_ID", randomId)
                        putExtra("SSID", binding.etSsid.text.toString())
                        putExtra("PASSWORD", binding.etPassword.text.toString())
                    })
                }
                is RegistrationState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = state.message
                }
                is RegistrationState.NotFound -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "❌ Device not found in system"
                }
                is RegistrationState.AlreadyRegistered -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "⚠️ Device already registered"
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            viewModel.registerDevice(
                name = binding.etName.text.toString(),
                phone = binding.etPhone.text.toString(),
                email = binding.etEmail.text.toString(),
                mac = macAddress,
                randomId = randomId,
                ssid = binding.etSsid.text.toString(),        // ← renamed
                wifiPassword = binding.etPassword.text.toString()
            )
        }
    }
}
