package com.example.aws_dummy_app.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aws_dummy_app.auth.GmailManager
import com.example.aws_dummy_app.auth.TokenManager
import com.example.aws_dummy_app.databinding.ActivityConnectGmailBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import net.openid.appauth.AuthState

/**
 * ConnectGmailActivity — shown ONCE after Cognito login succeeds.
 *
 * Flow:
 *  1. Read Cognito email from SharedPreferences (id_token decode)
 *  2. Show "Connect Gmail" UI — explains what access is being requested
 *  3. User taps Allow → Google Sign-In with gmail.readonly scope
 *  4. Google shows: "GarageScan wants to: Read your Gmail messages" (1-tap if session exists)
 *  5. On success: validate returned email == Cognito email → save serverAuthCode → go to MainActivity
 *  6. On mismatch: reject and show error
 *  7. User taps Skip → go straight to MainActivity (can connect later from settings)
 */
class ConnectGmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectGmailBinding

    // ⚠️ Replace with your actual Web Client ID from Google Cloud Console
    // (the same OAuth client used in Cognito's Google identity provider)
    private val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    private val RC_GMAIL_CONNECT = 2001
    private var cognitoEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectGmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read Cognito email from AuthState
        cognitoEmail = getCognitoEmail()

        // Show which account this will connect to
        cognitoEmail?.let {
            binding.tvAccountEmail.text = it
            binding.tvAccountEmail.visibility = View.VISIBLE
        }

        // ── Connect Gmail button ──────────────────────────────────────────
        binding.btnConnectGmail.setOnClickListener {
            setLoading(true)
            startGmailSignIn()
        }

        // ── Skip button ───────────────────────────────────────────────────
        binding.btnSkip.setOnClickListener {
            goToMain()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Google Sign-In with gmail.readonly
    // ─────────────────────────────────────────────────────────────────────

    private fun startGmailSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .requestServerAuthCode(WEB_CLIENT_ID)
            // Pre-fill account picker with Cognito email for seamless SSO
            .apply { cognitoEmail?.let { setAccountName(it) } }
            .build()

        val client = GoogleSignIn.getClient(this, gso)

        // Force account selection + fresh consent every time
        // Uses Google session reuse — no password re-entry if already signed in
        client.signOut().addOnCompleteListener {
            startActivityForResult(client.signInIntent, RC_GMAIL_CONNECT)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GMAIL_CONNECT) return

        setLoading(false)

        try {
            val account: GoogleSignInAccount = GoogleSignIn
                .getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)

            val gmailEmail     = account.email ?: ""
            val serverAuthCode = account.serverAuthCode

            Log.d("GMAIL_CONNECT", "Signed in as: $gmailEmail | serverAuthCode: ${serverAuthCode?.take(20)}...")

            // ── Email mismatch check ──────────────────────────────────────
            if (cognitoEmail != null && gmailEmail.lowercase() != cognitoEmail!!.lowercase()) {
                showEmailMismatchError(gmailEmail)
                return
            }

            // ── Save connection ───────────────────────────────────────────
            if (serverAuthCode != null) {
                GmailManager.saveConnection(this, gmailEmail, serverAuthCode)
                Log.d("GMAIL_CONNECT", "Gmail connected successfully for $gmailEmail")
                Toast.makeText(this, "✅ Gmail connected!", Toast.LENGTH_SHORT).show()

                // TODO: Send serverAuthCode to your Lambda to exchange for
                // long-lived Gmail refresh_token and store in DynamoDB
                // RetrofitClient.userApiService.connectGmail(serverAuthCode)
            } else {
                Log.w("GMAIL_CONNECT", "serverAuthCode is null — WEB_CLIENT_ID may be wrong")
                Toast.makeText(this, "Connection failed: auth code missing", Toast.LENGTH_LONG).show()
                return
            }

            goToMain()

        } catch (e: ApiException) {
            Log.e("GMAIL_CONNECT", "Google Sign-In failed: ${e.statusCode} — ${e.message}")
            if (e.statusCode == 12501) {
                // User cancelled (hit back)
                Toast.makeText(this, "Gmail connection cancelled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Google Sign-In error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun getCognitoEmail(): String? {
        return try {
            val stateJson = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("state", null) ?: return null
            val idToken = AuthState.jsonDeserialize(stateJson).idToken ?: return null
            val payload = idToken.split(".")[1]
            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            org.json.JSONObject(decoded).optString("email", null)
        } catch (e: Exception) {
            Log.e("GMAIL_CONNECT", "Failed to decode Cognito email: ${e.message}")
            null
        }
    }

    private fun showEmailMismatchError(selectedEmail: String) {
        val expected = cognitoEmail ?: "your login email"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Wrong Google Account")
            .setMessage(
                "You selected: $selectedEmail\n\n" +
                "Please use the same account you signed in with:\n$expected"
            )
            .setPositiveButton("Try Again") { _, _ -> startGmailSignIn() }
            .setNegativeButton("Skip") { _, _ -> goToMain() }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConnectGmail.isEnabled = !loading
        binding.btnSkip.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
