package com.aicompanion.nativeapp

import android.app.Application
import androidx.room.Room
import com.aicompanion.nativeapp.data.db.AppDatabase

class AiCompanionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
    }
}
