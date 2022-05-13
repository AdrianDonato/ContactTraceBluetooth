package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth

import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Protocol.Bluetrace
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Utils
import java.util.*
import kotlin.properties.Delegates

class GattServer constructor(val context: Context, val serviceUUIDString: String){


    private var bluetoothManager: BluetoothManager by Delegates.notNull()

    private var serviceUUID: UUID by Delegates.notNull()
    var bluetoothGattServer: BluetoothGattServer? = null

    init{
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        this.serviceUUID = UUID.fromString(serviceUUIDString)
    }


    private val gattServerCallback = object : BluetoothGattServerCallback(){

        //create a table which contains id (for testing)
        val readPayloadMap: MutableMap<String, ByteArray> = HashMap()
        val writePayloadMap: MutableMap<String, ByteArray> = HashMap()
        val deviceCharacteristicMap: MutableMap<String, UUID> = HashMap()

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)

            Log.w("GattServerCallback", "Conn state changed")
            Log.w("GattServerCallback", "Gatt Connection Successful")
            if(newState == BluetoothProfile.STATE_CONNECTED){
                Log.w("GattServerCallback", "Successfully connected to ${device?.address}")
                //store bluetooth gatt table

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("GattServerCallback", "Successfully disconnected from ${device?.address}")
                //gatt?.close()
                readPayloadMap.remove(device?.address)
            } else {
                Log.w("GattServerCallback", "State is $newState, Status: $status")
            }

        }

        //as peripheral device
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.w("GattServerCallback", "Requested read")

            if(device == null){
                Log.w("GattServerCallback", "No device")
            }

            device?.let {
                if (Bluetrace.supportsCharUUID(characteristic?.uuid)) {
                    val bluetraceImplementation = Bluetrace.getImplementation(characteristic.uuid)

                    //this is where we put the data to be sent to the client/central
                    characteristic?.uuid.let { charUUID ->
                        val devAddress = device?.address
                        val base = readPayloadMap.getOrPut(devAddress.toString(),
                                {bluetraceImplementation.peripheral.prepareReadRequestData(
                                        bluetraceImplementation.versionInt
                                )
                                })
                        Log.w("GattServerCallback", "Payload: " + readPayloadMap.toString())
                        val sentVal = base.copyOfRange(offset, base.size)
                        Log.w("GattServerCallback", "onCharacteristicReadRequest from ${device.address} - $requestId - $offset - ${String(sentVal, Charsets.UTF_8)}")
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, sentVal)
                    }
                }else{
                    Log.w("GattServerCallback", "Unsupported characteristic from ${device.address}")
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            if(device == null){
                Log.w("GattServerCallback", "No device found! Write operation stopped")
            }

            device?.let {
                Log.w("GattServerCallback", "onCharacteristicWriteRequest - ${device?.address} - preparedWrite: $preparedWrite")
                Log.w("GattServerCallback", "onCharacteristicWriteRequest - ${device?.address} - $requestId - $offset")

                if(Bluetrace.supportsCharUUID(characteristic.uuid)){
                    //putting value on mutable map payload
                    deviceCharacteristicMap[device?.address] = characteristic.uuid
                    var valuePassed = ""
                    value?.let {
                        valuePassed = String(value, Charsets.UTF_8)
                    }
                    Log.w("GattServerCallback", "onCharacteristicWriteRequest - value passed from ${device?.address} - $valuePassed")

                    if(value != null){
                        var dataBuffer = writePayloadMap[device?.address]

                        if(dataBuffer == null){
                            dataBuffer = ByteArray(0)
                        }

                        dataBuffer = dataBuffer.plus(value)
                        writePayloadMap[device?.address] = dataBuffer

                        Log.w("GattServerCallback", "Accumulated Characteristic: ${String(dataBuffer, Charsets.UTF_8)}")

                        if(preparedWrite && responseNeeded){
                            Log.w("GattServerCallback", "Sending response offset: ${dataBuffer.size}")
                            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, dataBuffer.size, value)
                        }
                        //check opentrace code if preparedWrite is false
                        if(!preparedWrite){
                            Log.w("GattServerCallback", "preparedWrite - $preparedWrite")
                            saveDataReceived(device)
                            if(responseNeeded){
                                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, dataBuffer.size, value)
                            }
                        }
                    }
                }else{
                    Log.w("GattServerCallback", "Unsupported Characteristic from ${device.address}")
                    if(responseNeeded){
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            super.onExecuteWrite(device, requestId, execute)
            var data = writePayloadMap[device?.address]

            data?.let { dataBuffer ->
                if(dataBuffer != null){
                    Log.w("GattServerCallback", "onExecuteWrite - $requestId - ${device?.address}" +
                            "- ${String(dataBuffer, Charsets.UTF_8)}")
                    saveDataReceived(device)
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } else {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        fun saveDataReceived(device: BluetoothDevice) {
            var data = writePayloadMap[device.address]
            var charUUID = deviceCharacteristicMap[device.address]

            Log.w("GattServerCallback", "Entering saveDataReceived method")


            charUUID?.let {
                Log.w("GattServerCallback", "Entering charUUID?.let")
                data?.let {
                    Log.w("GattServerCallback", "Entering data?.let")
                    try {
                        device.let {
                            val bluetraceImplementation = Bluetrace.getImplementation(charUUID)

                            val connectionRecord = bluetraceImplementation.peripheral.processWriteRequestDataReceived(data, device.address)
                            connectionRecord?.let {
                                Utils.broadcastStreetPassReceived(context, connectionRecord)
                            }

                            Log.w("GattServerCallback", "Entering device.let")
                            try {
                                val serializedData = BluetoothWritePayload.fromPayload(data)
                                Log.w("GattServerCallback", "fromPayload - Received data - ${serializedData.id}")
                            } catch (e: Throwable) {
                                Log.w("GattServerCallback", "fromPayload - Failed to process write payload - ${e.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w("GattServerCallback", "saveDataReceived - Failed to process write payload - ${e.message}")
                    }
                    writePayloadMap.remove(device?.address)
                    readPayloadMap.remove(device?.address)
                    deviceCharacteristicMap.remove(device?.address)
                }
            }
        }
    }


    fun startServer(): Boolean{
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        bluetoothGattServer?.let{
            it.clearServices()
            return true
        }
        return false
    }

    fun addService(service: GattService){
        bluetoothGattServer?.addService(service.gattService)
    }

    fun stop(){
        try{
            bluetoothGattServer?.clearServices()
            bluetoothGattServer?.close()
        }catch (e: Throwable){
           Log.e("GattServer", "GattServer cannot be closed elegantly ${e.localizedMessage}")
        }
    }
}