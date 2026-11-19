package com.applens

import android.app.Application

/** Legacy application class; AppLensApplication is registered in the manifest. */
class AppLensLegacyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
