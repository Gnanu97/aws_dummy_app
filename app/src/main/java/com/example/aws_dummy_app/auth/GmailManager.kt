package com.example.aws_dummy_app.auth

import android.content.Context

/**
 * Manages Gmail connection state in SharedPreferences.
 * Stores only: connected flag, Gmail email, and serverAuthCode (sent to Lambda).
 * NO Gmail access_token is stored on device — serverAuthCode is exchanged server-side.
 */
object GmailManager {

    private const val PREFS_NAME        = "gmail_prefs"
    private const val KEY_CONNECTED     = "gmail_connected"
    private const val KEY_EMAIL         = "gmail_email"
    private const val KEY_SERVER_CODE   = "gmail_server_auth_code"

    fun isConnected(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTED, false)
    }

    fun saveConnection(context: Context, email: String, serverAuthCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CONNECTED, true)
            .putString(KEY_EMAIL, email)
            .putString(KEY_SERVER_CODE, serverAuthCode)
            .apply()
    }

    fun getConnectedEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
    }

    fun getServerAuthCode(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_CODE, null)
    }

    /** Called on Cognito logout — clears Gmail session to prevent token leakage across accounts */
    fun clearConnection(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
