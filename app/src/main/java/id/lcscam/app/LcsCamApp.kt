package id.lcscam.app

import android.app.Application
import id.lcscam.app.di.AppContainer

class LcsCamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
