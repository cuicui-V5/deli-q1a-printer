package com.deli.printer

import android.app.Application
import com.deli.printer.ble.DeliBleManager

class DeliApp : Application() {

    lateinit var bleManager: DeliBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = DeliBleManager(this)
    }

    companion object {
        lateinit var instance: DeliApp
            private set
    }
}
