package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.res.Resources
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import java.util.*
import kotlin.properties.Delegates

class GattService constructor(val context: Context, serviceUUIDString: String) {

    private var serviceUUID = UUID.fromString(serviceUUIDString)

    var gattService: BluetoothGattService by Delegates.notNull()

    private var characteristic: BluetoothGattCharacteristic

    init{
        gattService = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristic = BluetoothGattCharacteristic(/*UUID.fromString(Resources.getSystem().getString(R.string.ble_characuuid))*/
                UUID.fromString("011019d0-8cb6-4804-8b83-1c3348a8940c"),
                                            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                                           BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
        gattService.addCharacteristic(characteristic)
    }
}