package com.applens

import android.app.Application
import com.applens.util.ShizukuManager

class AppLensApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.initialize(this)
    }
}
