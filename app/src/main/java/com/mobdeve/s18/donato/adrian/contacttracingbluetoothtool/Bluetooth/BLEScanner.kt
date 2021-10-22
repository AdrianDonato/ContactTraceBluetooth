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
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.ConnectablePeripheral
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Work
import java.util.*
import kotlin.properties.Delegates

class BLEScanner constructor(context: Context, val uuid: String, reportDelay: Long){

    private var serviceUUID: UUID
    private var context: Context by Delegates.notNull()
    private var reportDelay: Long by Delegates.notNull()
    var scannerCount = 0
    val pUuid = ParcelUuid(UUID.fromString(uuid))
    private var bleScanner: BluetoothLeScanner? = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

    fun isScanning(): Boolean {
        return scannerCount > 0
    }

    private val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()

    private val scanHandler = Handler(Looper.getMainLooper())
    private val infiniteScanning = false

    init{
        serviceUUID = UUID.fromString(Resources.getSystem().getString(R.string.ble_uuid))
    }

    private fun startBleScan(){
        val filter = ScanFilter.Builder().setServiceUuid(
                    ParcelUuid(UUID.fromString(Resources.getSystem().getString(R.string.ble_uuid)))
            ).build()

            val filters: ArrayList<ScanFilter> = ArrayList()
            filters.add(filter)

            bleScanner = bleScanner ?: BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
            bleScanner?.startScan(filters, scanSettings, scanCallback)

            if(!infiniteScanning){
                scanHandler.postDelayed({stopBleScan()}, 8000)
            }

        }
    //stops scanning of ble devices
    private fun stopBleScan(){
        bleScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        //processing of scan result (get rssi, id, etc here?)
        private fun processScanResult(scanResult: ScanResult?){
            scanResult?.let{ result->
                Log.w("onScanResult", "Entered onScanResult")
                Log.w("onScanResult", "Scan Result: ${result.toString()}")
                var rssi = result.rssi
                val device = result.device
                var txPower: Int?= null

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    txPower = result.txPower
                    Log.w("onScanResult", "txPower = ${txPower}")
                    if(txPower == 127){
                        txPower = null
                    }
                }
                var connectable = ConnectablePeripheral("Manufacturer Data", txPower, rssi)
                // Utils.broadcastDeviceScanned(context, device, connectable)
            }
        }
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            processScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e( "ScanCallback", "onScanFailed: code $errorCode")
        }
    }

}
