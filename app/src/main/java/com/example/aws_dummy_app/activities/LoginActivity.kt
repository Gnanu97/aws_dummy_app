package com.example.aws_dummy_app.activities

import com.example.aws_dummy_app.network.RetrofitClient
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aws_dummy_app.databinding.ActivityLoginBinding
import com.example.aws_dummy_app.db.AppDatabase
import com.example.aws_dummy_app.db.AuthLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authService: AuthorizationService

    private val CLIENT_ID    = "3e65elf9sf15setmt4biiprbie"
    private val REDIRECT_URI = "myapp://callback/"
    private val ISSUER_URL   = "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_ajBbv72Go"
    private val RC_AUTH      = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authService = AuthorizationService(this)
        if (isLoggedIn()) { goToMain(); return }
        binding.btnLogin.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false
            fetchCognitoConfig()
        }
    }

    private fun fetchCognitoConfig() {
        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(ISSUER_URL)) { config, ex ->
            if (ex != null || config == null) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, "Config error: ${ex?.message}", Toast.LENGTH_LONG).show()
                }
                return@fetchFromIssuer
            }
            runOnUiThread { startLogin(config) }
        }
    }

    private fun startLogin(config: AuthorizationServiceConfiguration) {
        val authRequest = AuthorizationRequest.Builder(
            config, CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(REDIRECT_URI)
        ).setScope("openid email").build()
        startActivityForResult(authService.getAuthorizationRequestIntent(authRequest), RC_AUTH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_AUTH) {
            val response = AuthorizationResponse.fromIntent(data!!)
            val ex       = AuthorizationException.fromIntent(data)
            if (response != null) {
                // ✅ Show loading immediately so login UI is hidden
                binding.progressBar.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = false
                exchangeCodeForTokens(response)
            } else {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                Toast.makeText(this, "Login cancelled: ${ex?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exchangeCodeForTokens(authResponse: AuthorizationResponse) {
        authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse, ex ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
            if (tokenResponse != null) {
                saveAuthState(authResponse, tokenResponse)
                saveToRoomDb(tokenResponse)         // ✅ saves sub, email, loginTime only

                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val resp = RetrofitClient.userApiService.registerUser()  // ✅ correct service
                        Log.d("AUTH_CLOUD", "registerUser: ${resp.code()} | ${resp.body()?.message}")
                    } catch (e: Exception) {
                        Log.e("AUTH_CLOUD", "registerUser failed: ${e.message}")
                    }
                    withContext(Dispatchers.Main) { goToMain() }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Token exchange failed: ${ex?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToRoomDb(tokenResponse: TokenResponse) {
        var sub   = "unknown"
        var email = "unknown"
        try {
            val idToken = tokenResponse.idToken
            if (idToken != null) {
                val payload = idToken.split(".")[1]
                val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING))
                val json    = JSONObject(decoded)
                sub   = json.optString("sub",   "unknown")
                email = json.optString("email", "unknown")
            }
        } catch (e: Exception) {
            Log.e("AUTH", "id_token decode failed: ${e.message}")
        }

        // ✅ Store ONLY sub, email, loginTime — no tokens in Room
        val logEntry = AuthLogEntity(
            sub       = sub,
            email     = email,
            loginTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        GlobalScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).authLogDao().insert(logEntry)
            Log.d("AUTH_DB", "Saved → sub: $sub | email: $email")
        }
    }

    private fun saveAuthState(authResponse: AuthorizationResponse, tokenResponse: TokenResponse) {
        val authState = AuthState(authResponse, null).apply { update(tokenResponse, null) }
        getSharedPreferences("auth", MODE_PRIVATE)
            .edit().putString("state", authState.jsonSerializeString()).apply()
    }

    private fun isLoggedIn(): Boolean {
        val json = getSharedPreferences("auth", MODE_PRIVATE)
            .getString("state", null) ?: return false
        return AuthState.jsonDeserialize(json).isAuthorized
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }
}