package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Protocol

import android.os.Build
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothPayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothWritePayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.ConnectionRecord
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.MainActivity
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.PeripheralDevice
import java.util.*

object Bluetrace {

    val implementations = mapOf<Int, BluetraceProtocol>(
              2 to BluetraceProtocol(2, V2Central(), V2Peripheral())
    )

    val characteristicToProtocolVersionMap = mapOf<UUID, Int>(
            UUID.fromString("011019d0-8cb6-4804-8b83-1c3348a8940c") to 2
    )

    fun supportsCharUUID(charUUID: UUID?): Boolean{
        if(charUUID == null){
            return false
        }
        characteristicToProtocolVersionMap[charUUID]?.let{
            version -> return implementations[version] != null
        }
        return false
    }
    fun getImplementation(charUUID: UUID): BluetraceProtocol{
        val protocolVersion = characteristicToProtocolVersionMap[charUUID]?:1
        return getImplementation(protocolVersion)
    }

    fun getImplementation(protocolVersion: Int): BluetraceProtocol{
        val impl = implementations[protocolVersion]

        return impl ?: BluetraceProtocol(2, V2Central(), V2Peripheral())
    }


}