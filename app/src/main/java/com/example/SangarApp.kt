package com.example

import android.app.Application
import com.example.data.LogManager
import com.example.data.ProfileRepository

class SangarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize singletons
        ProfileRepository.getInstance(this)
        LogManager.info("Sangar initialized. Ready for connections.")
    }
}
