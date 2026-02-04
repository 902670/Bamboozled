package com.example.bambumonitor

import android.app.Application
import androidx.work.Configuration

class BambuApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
