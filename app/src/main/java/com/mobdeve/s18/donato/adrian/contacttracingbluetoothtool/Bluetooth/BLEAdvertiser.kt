package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.BluetoothAdapter

import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseCallback
import android.os.Handler

import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.*


class BLEAdvertiser constructor(val serviceUUID: String) {
    private var advertiser:BluetoothLeAdvertiser? = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()
    private var charLength = 3


    private var callback: AdvertiseCallback = object: AdvertiseCallback(){
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
        }
    }

    var isAdvertising = false
    var shouldBeAdvertising = false
    var handler = Handler()

    var settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
    var pUuid = ParcelUuid(UUID.fromString(serviceUUID))

    var data: AdvertiseData? = null

    fun startAdvertisingLegacy(timeoutInMillis: Long){
        val randomUUID = UUID.randomUUID().toString()
        val finalString = randomUUID.substring(randomUUID.length - charLength, randomUUID.length)
        data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(pUuid)
                .addServiceData(pUuid, "Data".toByteArray(Charset.forName("UTF-8")))
                .build()
    }

    fun startAdvertising(timeoutInMillis: Long) {
        startAdvertisingLegacy(timeoutInMillis)
        shouldBeAdvertising = true
    }

    fun stopAdvertising() {
        try {
            Log.d("BLEAdvertiser", "Stop Advertising")
            advertiser?.stopAdvertising(callback)
        } catch (e: Throwable) {
            Log.d("BLEAdvertiser", "Failed to stop advertising: ${e.message}")

        }
        shouldBeAdvertising = false
        handler.removeCallbacksAndMessages(null)
    }

}