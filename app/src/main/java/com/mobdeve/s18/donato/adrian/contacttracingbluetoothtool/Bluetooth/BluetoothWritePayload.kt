package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.CentralDevice
import org.apache.commons.text.StringEscapeUtils


//this is for the payload of write operations (OnCharacteristicRead for CENTRAL)
class BluetoothWritePayload (val id: String, central: CentralDevice){
    fun getPayload(): ByteArray{
        return gson.toJson(this).toByteArray(Charsets.UTF_8)
    }
    companion object {
        //val gson: Gson
        val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        fun fromPayload(databytes: ByteArray): BluetoothWritePayload{
            val dataString = String(databytes, Charsets.UTF_8)
            return gson.fromJson(dataString, BluetoothWritePayload::class.java)
        }

        fun removeQuotesAndUnescape(uncleanJson: String): String{
            val noQuotes = uncleanJson.replace("^\"|\"$", "")
            return StringEscapeUtils.unescapeJava(noQuotes)
        }
    }

}