package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UpgradeReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        try{
            if(Intent.ACTION_MY_PACKAGE_REPLACED != intent!!.action) return
            context?.let{
                Log.w("UpgradeReceiver", "Starting service from upgrade receiver")
                // Utils.startBluetoothMonitoringService(context)
            }
        }catch (e: Exception){
            Log.e("UpgradeReceiver", "Unable to upgrade: ${e.localizedMessage}")
        }
    }
}