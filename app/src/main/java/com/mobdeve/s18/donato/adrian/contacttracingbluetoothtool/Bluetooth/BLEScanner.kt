package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.le.ScanCallback
import android.content.Context
import android.os.ParcelUuid
import java.util.*
import kotlin.properties.Delegates

class BLEScanner constructor(context: Context, val uuid: String, reportDelay: Long){

    private var serviceUUID: String by Delegates.notNull()
    private var context: Context by Delegates.notNull()
    private var scanCallback: ScanCallback? = null
    private var reportDelay: Long by Delegates.notNull()

    val pUuid = ParcelUuid(UUID.fromString(uuid))

    init{
        this.serviceUUID = uuid
    }
}