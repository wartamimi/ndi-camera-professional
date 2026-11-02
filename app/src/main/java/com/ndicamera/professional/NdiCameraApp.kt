package com.ndicamera.professional

import android.app.Application
import com.ndicamera.professional.di.AppContainer

class NdiCameraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
