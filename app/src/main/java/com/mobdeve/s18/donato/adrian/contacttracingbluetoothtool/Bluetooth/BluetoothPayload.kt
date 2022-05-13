package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.PeripheralDevice
import org.apache.commons.text.StringEscapeUtils

class BluetoothPayload (val v: Int, val id: String, peripheral: PeripheralDevice){
    fun getPayload(): ByteArray{
        return gson.toJson(this).toByteArray(Charsets.UTF_8)
    }
    val mp = peripheral.modelP

    companion object {
        //val gson: Gson
        val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        fun fromPayload(databytes: ByteArray): BluetoothPayload{
            val dataString = String(databytes, Charsets.UTF_8)
            return gson.fromJson(dataString, BluetoothPayload::class.java)
        }

        fun removeQuotesAndUnescape(uncleanJson: String): String{
            val noQuotes = uncleanJson.replace("^\"|\"$", "")
            return StringEscapeUtils.unescapeJava(noQuotes)
        }
    }

}