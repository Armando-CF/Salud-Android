package app.savia

import android.app.Application

class SaviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Savia.init(this)
    }
}
