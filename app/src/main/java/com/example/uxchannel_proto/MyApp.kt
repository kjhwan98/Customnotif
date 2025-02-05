package com.example.uxchannel_proto

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)

            val database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)
            Log.d("MyApp", "Firebase persistence enabled successfully")
        } catch (e: Exception) {
            Log.e("MyApp", "Error initializing Firebase: ${e.message}")
        }
    }
}