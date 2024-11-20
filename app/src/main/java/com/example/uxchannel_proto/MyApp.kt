package com.example.uxchannel_proto

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase persistence 활성화
        val database = FirebaseDatabase.getInstance()
        database.setPersistenceEnabled(true)
    }
}