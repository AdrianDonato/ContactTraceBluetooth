package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.appcompat.app.ActionBarDrawerToggle
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.ConnectablePeripheral
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Work
import java.util.*
import kotlin.properties.Delegates

class BLEScanner constructor(context: Context, val uuid: String, reportDelay: Long){

    private var serviceUUID: String by Delegates.notNull()
    private var context: Context by Delegates.notNull()
    private var scanCallback: ScanCallback? = null
    private var reportDelay: Long by Delegates.notNull()

    private var scanner: BluetoothLeScanner? = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

    init{
        this.serviceUUID = uuid
        this.context = context
        this.reportDelay = reportDelay
    }

    fun startScan(scanCallback: ScanCallback){
        val filter = ScanFilter.Builder().setServiceUuid(
                ParcelUuid(UUID.fromString(serviceUUID))
        ).build()

        val filters: ArrayList<ScanFilter> = ArrayList()
        filters.add(filter)

        val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build()
        this.scanCallback = scanCallback

        scanner = scanner ?: BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
        scanner?.startScan(filters, scanSettings, scanCallback)
    }

    fun flush(){
        scanCallback?.let {
            scanner?.flushPendingScanResults(scanCallback)
        }
    }

    fun stopScan(){
        try {
            if(scanCallback != null
                    //&& Utils.isBluetoothAvailable()
            ){
                scanner?.stopScan(scanCallback)
                Log.w("BLEScanner", "Stopped Scan")
            }
        }catch (e: Throwable){
            Log.e("BLEScanner", "unable to stop scanning - callback null or bluetooth off? : ${e.localizedMessage}")
        }
    }

}
