package com.example.aws_dummy_app.auth

import android.content.Context
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService

object TokenManager {

    fun getAccessToken(
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val json = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("state", null)

        if (json == null) { onError("Not logged in"); return }

        val authState   = AuthState.jsonDeserialize(json)
        val authService = AuthorizationService(context)

        authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            // Persist updated state (refresh token may have rotated)
            context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .edit().putString("state", authState.jsonSerializeString()).apply()
            authService.dispose()

            if (accessToken != null) onSuccess(accessToken)
            else onError(ex?.message ?: "Token error")
        }
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}