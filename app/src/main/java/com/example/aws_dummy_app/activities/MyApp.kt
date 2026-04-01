package com.example.aws_dummy_app

import android.app.Application
import com.example.aws_dummy_app.network.RetrofitClient

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.appContext = this
    }
}