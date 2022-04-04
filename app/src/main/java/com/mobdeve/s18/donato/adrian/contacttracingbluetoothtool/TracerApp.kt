package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.app.Application
import android.content.Context

class TracerApp: Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext = applicationContext
    }
    companion object {

        lateinit var AppContext: Context

        fun thisDeviceMsg(): String {
            return Preference.getUserID(AppContext)
        }
    }
}