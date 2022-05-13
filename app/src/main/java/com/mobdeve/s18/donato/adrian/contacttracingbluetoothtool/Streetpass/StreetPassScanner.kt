package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BLEScanner
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.BluetoothMonitoringService.Companion.infiniteScanning
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.ConnectablePeripheral
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Status.Status
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Utils
import kotlin.properties.Delegates

class StreetPassScanner constructor(context: Context, serviceUUIDString: String, private val scanDurationMillis: Long){

    private var scanner: BLEScanner by Delegates.notNull()

    private var context: Context by Delegates.notNull()

    private var handler: Handler = Handler(Looper.getMainLooper())

    var scannerCount = 0

   var scanCallback = BLEScanCallBack()

    init {
        scanner = BLEScanner(context, serviceUUIDString, 0)
        this.context = context
    }

    fun startScan(){
        var statusRecord = Status("Scanning Started")
        Log.i("StreetPassScanner", "startScan - Scanning Started")
        Utils.broadcastStatusReceived(context, statusRecord)
        scanner.startScan(scanCallback)
        scannerCount++

        if(!infiniteScanning){
            handler.postDelayed({stopScan()}, scanDurationMillis)
        }
    }

    fun stopScan(){

        //check if successfully scanned before stopping
        if(scannerCount > 0){
            var statusRecord = Status("Scanning Stopped")
            Utils.broadcastStatusReceived(context, statusRecord)
            scannerCount--
            scanner.stopScan()
        }
    }

    fun isScanning(): Boolean{
        return scannerCount > 0
    }


    inner class BLEScanCallBack: ScanCallback(){
        //processing of scan result (get rssi, id, etc here?)
        private fun processScanResult(scanResult: ScanResult?){

            scanResult?.let { result ->
                var rssi = result.rssi
                val device = result.device
                var txPower: Int?= null

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    txPower = result.txPower
                    if(txPower == 127){
                        txPower = null
                    }
                }

                var manuData: ByteArray =
                        scanResult.scanRecord?.getManufacturerSpecificData(1023) ?: "N.A".toByteArray()
                var manuString = String(manuData, Charsets.UTF_8)


                var connectable = ConnectablePeripheral(manuString, txPower, rssi)

                Log.w("StreetPassScanner", "Scanned ${device.address}")
                Log.w("StreetPassScanner", "ManuString: $manuString")

                Utils.broadcastDeviceScanned(context, device, connectable)
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            processScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "$errorCode - SCAN_FAILED_ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "$errorCode - SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "$errorCode - SCAN_FAILED_FEATURE_UNSUPPORTED"
                SCAN_FAILED_INTERNAL_ERROR -> "$errorCode - SCAN_FAILED_INTERNAL_ERROR"
                else -> {
                    "$errorCode - UNDOCUMENTED"
                }
            }
            Log.e("ScanCallback", "onScanFailed; $reason")
            if (scannerCount > 0) {
                scannerCount--
            }
        }
    }
}