package com.example.aws_dummy_app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aws_dummy_app.network.RegisterRequest
import com.example.aws_dummy_app.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RegistrationState {
    object Idle : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val message: String) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
    data class AlreadyRegistered(val message: String) : RegistrationState()
    data class NotFound(val message: String) : RegistrationState()
}

class RegistrationViewModel : ViewModel() {

    private val _registrationState = MutableLiveData<RegistrationState>(RegistrationState.Idle)
    val registrationState: LiveData<RegistrationState> = _registrationState

    fun registerDevice(
        name: String,
        phone: String,
        email: String,
        mac: String,
        randomId: String,
        ssid: String,           // ← renamed from wifiSsid
        wifiPassword: String
    ) {
        if (name.isBlank() || phone.isBlank() || email.isBlank() ||
            ssid.isBlank() || wifiPassword.isBlank()
        ) {
            _registrationState.value = RegistrationState.Error("Please fill in all fields")
            return
        }

        _registrationState.value = RegistrationState.Loading

        viewModelScope.launch {
            try {
                val request = RegisterRequest(
                    name = name,
                    phone = phone,
                    email = email,
                    mac = mac,
                    random_id = randomId,
                    ssid = ssid,                    // ← matches RDS column name
                    wifi_password = wifiPassword
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.registerDevice(request)
                }

                if (response.isSuccessful) {
                    when (response.body()?.status) {
                        "AUTH_SUCCESS" ->
                            _registrationState.value = RegistrationState.Success("AUTH_SUCCESS")
                        "DEVICE_ALREADY_REGISTERED" ->
                            _registrationState.value = RegistrationState.AlreadyRegistered("Already registered")
                        "DEVICE_NOT_FOUND" ->
                            _registrationState.value = RegistrationState.NotFound("Not found")
                        else ->
                            _registrationState.value = RegistrationState.Error("Unknown response")
                    }
                } else {
                    _registrationState.value =
                        RegistrationState.Error("Server error: ${response.code()}")
                }

            } catch (e: Exception) {
                _registrationState.value =
                    RegistrationState.Error("Network error: ${e.message}")
            }
        }
    }
}
