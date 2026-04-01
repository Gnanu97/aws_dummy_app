package com.example.aws_dummy_app.network

import com.example.aws_dummy_app.auth.TokenManager          // ← ADD import
import okhttp3.Interceptor                                   // ← ADD import
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch                   // ← ADD import
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEVICE_API_URL = "https://rj05wleeb1.execute-api.ap-south-1.amazonaws.com"
    private const val QR_API_URL     = "https://q1j2hg7ux1.execute-api.ap-south-1.amazonaws.com"

    private const val USER_API_URL      = "https://k51vq7es51.execute-api.ap-south-1.amazonaws.com/" // ✅ NEW — register-user only

    // ── ADD: context reference set once from MyApp ──────────────────────
    var appContext: android.content.Context? = null

    private val authInterceptor = Interceptor { chain ->
        val ctx = appContext
        var token: String? = null
        if (ctx != null) {
            val latch = CountDownLatch(1)
            TokenManager.getAccessToken(ctx,
                onSuccess = { token = it; latch.countDown() },
                onError   = { latch.countDown() }
            )
            latch.await(5, TimeUnit.SECONDS)
        }
        val request = chain.request().newBuilder()
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .build()
        chain.proceed(request)
    }
    // ────────────────────────────────────────────────────────────────────

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)          // ← ADD this line
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEVICE_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    val userApiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(USER_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    val qrApiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(QR_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }
}