package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.BluetoothAdapter

import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseCallback
import android.os.Handler
import android.os.Looper

import android.os.ParcelUuid
import android.util.Log
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.BluetoothMonitoringService.Companion.infiniteAdvertising
import java.nio.charset.Charset
import java.util.*
import kotlin.properties.Delegates


class BLEAdvertiser constructor(val serviceUUID: String) {
    private var advertiser: BluetoothLeAdvertiser? = BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser
    private var charLength = 3
    var stopRunnable: Runnable = Runnable{
        Log.d("BLEAdvertiser", "Advertising Stopped")
        stopAdvertising()
    }

    private var callback: AdvertiseCallback = object: AdvertiseCallback(){
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d("BLEAdvertiser", "Advertising successful")
            isAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            var reason: String

            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> {
                    reason = "ADVERTISE_FAILED_ALREADY_STARTED"
                    isAdvertising = true
                }
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> {
                    reason = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"
                    isAdvertising = false
                }
                ADVERTISE_FAILED_INTERNAL_ERROR -> {
                    reason = "ADVERTISE_FAILED_INTERNAL_ERROR"
                    isAdvertising = false
                }
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> {
                    reason = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
                    isAdvertising = false
                }
                ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                    reason = "ADVERTISE_FAILED_DATA_TOO_LARGE"
                    isAdvertising = false
                    charLength--
                }

                else -> {
                    reason = "UNDOCUMENTED"
                }
            }

            Log.d("BLEAdvertiser", "Advertising failed: " + reason)
        }
    }

    var isAdvertising = false
    var shouldBeAdvertising = false

    var handler = Handler(Looper.getMainLooper())

    var settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

    val pUuid = ParcelUuid(UUID.fromString(serviceUUID))

    var data: AdvertiseData? = null
    var scanResponseData: AdvertiseData? = null

    fun startAdvertisingLegacy(timeoutInMillis: Long){
        val randomUUID = UUID.randomUUID().toString()
        val finalString = randomUUID.substring(randomUUID.length - charLength, randomUUID.length)
        val serviceDataByteArray = finalString.toByteArray()

        data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(pUuid)
                .addManufacturerData(1023, serviceDataByteArray)
                .build()
        scanResponseData = AdvertiseData.Builder().addServiceUuid(pUuid).build()
        try {
            Log.d("BLEAdvertiser", "Start advertising")
            advertiser = advertiser ?: BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser
            Log.d("BLEAdvertiser", "Advertise Data: ${data.toString()}")
            advertiser?.startAdvertising(settings, data, callback)
        } catch (e: Throwable) {
            Log.e("BLEAdvertiser", "Failed to start advertising legacy: ${e.message}")
        }


        if (!infiniteAdvertising) {
            handler.removeCallbacksAndMessages(stopRunnable)
            handler.postDelayed(stopRunnable, timeoutInMillis)
        }

    }


    fun startAdvertising(timeoutInMillis: Long) {
        startAdvertisingLegacy(timeoutInMillis)
        shouldBeAdvertising = true
        Log.d("BLEAdvertiser", "Advertising starting..")
    }

    fun stopAdvertising() {
        try {
            Log.d("BLEAdvertiser", "Stop Advertising")
            advertiser?.stopAdvertising(callback)
        } catch (e: Throwable) {
            Log.d("BLEAdvertiser", "Failed to stop advertising: ${e.message}")

        }
        shouldBeAdvertising = false
        isAdvertising = false
        handler.removeCallbacksAndMessages(null)
    }
}