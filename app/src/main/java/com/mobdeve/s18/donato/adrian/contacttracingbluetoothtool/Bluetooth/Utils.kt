package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.Manifest

object Utils {

    fun getRequiredPermissions(): Array<String>{
        return arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

}