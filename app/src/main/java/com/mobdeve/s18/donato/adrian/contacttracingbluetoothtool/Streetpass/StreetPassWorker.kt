package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.*
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.ACTION_DEVICE_PROCESSED
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.CONNECTION_DATA
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.DEVICE_ADDRESS
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.STREET_PASS
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Protocol.Bluetrace
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import kotlin.properties.Delegates

const val ACTION_DEVICE_SCANNED = "${BuildConfig.APPLICATION_ID}.ACTION_DEVICE_SCANNED"

class StreetPassWorker (val context: Context){

    //UUIDs used for the bluetooth service and characteristic
    private lateinit var serviceUUID: UUID
    private lateinit var characteristicUUID: UUID

    //queue for works
    private val workQueue: PriorityBlockingQueue<Work> = PriorityBlockingQueue(5,Collections.reverseOrder<Work>())
    private val blacklist: MutableList<BlacklistEntry> = Collections.synchronizedList(ArrayList())

    //CHANGE ONCE MAHANAP ANG MAXQUEUETIME SA OPENTRACE
    private var maxQueueTime: Long = 7000
    private var connTimeout: Long = 6000

    //handlers
    private lateinit var timeoutHandler: Handler
    private lateinit var queueHandler: Handler
    private lateinit var blacklistHandler: Handler

    //Receivers
    private val scannedDeviceReceiver = ScannedDeviceReceiver()
    private val blacklistReceiver = BlacklistReceiver()

    private var localBroadcastManager: LocalBroadcastManager =
            LocalBroadcastManager.getInstance(context)

    //bluetooth service
    private var bluetoothManager: BluetoothManager
    = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    //Work
    private var currentWork: Work? = null


    init{
        prepare()
    }
    private fun prepare(){
        val deviceAvailableFilter = IntentFilter(ACTION_DEVICE_SCANNED)
        localBroadcastManager.registerReceiver(scannedDeviceReceiver, deviceAvailableFilter)

        val deviceProcessedFilter = IntentFilter(ACTION_DEVICE_PROCESSED)
        localBroadcastManager.registerReceiver(blacklistReceiver, deviceProcessedFilter)
        /*
        characteristicUUID = UUID.fromString(Resources.getSystem().getString(R.string.ble_characuuid))
        serviceUUID = UUID.fromString(Resources.getSystem().getString(R.string.ble_uuid))
        */
        //to do - PALITAN TO DAPAT - kunin from resources
        characteristicUUID = UUID.fromString("011019d0-8cb6-4804-8b83-1c3348a8940c")
        serviceUUID = UUID.fromString("61ca2ddb-d5d0-489c-8542-2d1b72e594b4")

        timeoutHandler = Handler(Looper.getMainLooper())
        queueHandler = Handler(Looper.getMainLooper())
        blacklistHandler = Handler(Looper.getMainLooper())

        Log.i("StreetPassWorker", "Finished preparing")
    }
    //work timeout listener
    val onWorkTimeoutListener = object: Work.OnWorkTimeoutListener {
        override fun onWorkTimeout(work: Work) {
            if(!isCurrentlyWorkedOn(work.device.address)){
                Log.i("WorkTimeoutListener", "No longer being worked on.")
            }

            Log.e("WorkTimeoutListener", "Work timed out for ${work.device.address} @ ${work.connectable.rssi} queued for ${work.checklist.started.timePerformed - work.timeStamp}ms")

            Log.e("WorkTimeoutListener", "Work status: ${work.checklist}")

            if(!work.checklist.connected.status){
                Log.e("WorkTimeoutListener", "No connection formed for ${work.device.address}")
                if(work.device.address == currentWork?.device?.address){
                    currentWork = null
                }

                try{
                    work.gatt?.close()
                } catch (e: Exception){
                    Log.e("WorkTimeoutListener", "Unexpected error trying to close connection for ${work.device.address}!" +
                            " ${e.localizedMessage}")
                }
                finishWork(work)
            } else if (work.checklist.connected.status && !work.checklist.disconnected.status){
                if(work.checklist.readCharacteristic.status || work.checklist.writeCharacteristic.status || work.checklist.skipped.status){
                    Log.e("WorkTimeoutListener", "Connected but did not disconnect in time - ${work.device.address}")
                    try{
                        work.gatt?.close()
                        if(work.gatt == null){
                            currentWork = null
                            finishWork(work)
                        }
                    } catch (e: Exception){
                        Log.e("WorkTimeoutListener", "Unexpected error trying to close connection for ${work.device.address}!" +
                                " ${e.localizedMessage}")
                    }
                }
            } else {
                Log.e("WorkTimeoutListener", "Connected but did not do anything - ${work.device.address}")
                try{
                    work.gatt?.close()
                    if(work.gatt == null){
                        currentWork = null
                        finishWork(work)
                    }
                } catch (e: Exception){
                    Log.e("WorkTimeoutListener", "Unexpected error trying to close connection for ${work.device.address}!" +
                            " ${e.localizedMessage}")
                }
            }
        }
    }

    //check if the work is the one being currently used
    fun isCurrentlyWorkedOn(address: String?): Boolean {
        return currentWork?.let {
            it.device.address == address
        } ?: false
    }

    //adding work
    fun addWork(work: Work): Boolean{

        //don't add if the work is being currently processed
        if(isCurrentlyWorkedOn(work.device.address)){
            Log.w("AddWork", "${work.device.address} is currently being worked on. Do not add.")
            return false
        }

        //add blacklist condition
        //if(useBlacklist)

        if(blacklist.filter{it.uniqueIdentifier == work.device.address}.isNotEmpty()){
            Log.w("AddWork", "${work.device.address} is in blacklist. Not adding.")
            return false
        }

        //
        if(workQueue.filter { it.device.address == work.device.address }.isEmpty()){
            workQueue.offer(work)
            queueHandler.postDelayed({
                if(workQueue.contains(work)){
                    Log.w("AddWork", "Work for ${work.device.address} removed: ${workQueue.remove(work)}")
                }
            }, maxQueueTime)
            Log.w("AddWork", "Added to work queue: ${work.device.address}")
            return true
        } else {

            Log.w("AddWork", "${work.device.address} is already in queue")

            var prevWork = workQueue.find { it.device.address == work.device.address }
            var removed = workQueue.remove(prevWork)
            var added = workQueue.offer(work)

            Log.w("WorkTimeoutListener", "Queue updated: removed ${removed}, added ${added}")

            return false
        }
    }

    //doing work
    fun doWork(){
        if(currentWork != null){
            Log.w("doWork", "Already trying to connect to ${currentWork?.device?.address}")

            var timedOut = System.currentTimeMillis() > currentWork?.timeout ?: 0

            if(currentWork?.finished == true || timedOut){
                Log.w("doWork", "Handling erroneous current work for ${currentWork?.device?.address}"
                        + " - finished: ${currentWork?.finished ?: false}, timedout: $timedOut")

                //add extra condition before doing dowork()
                doWork()
            }

            return
        }

        if(workQueue.isEmpty()){
            Log.w("doWork", "Work empty - nothing to do")
        }

        Log.w("doWork", "Work queue size: ${workQueue.size}")
        var workToDo: Work? = null
        val now = System.currentTimeMillis()

        while(workToDo == null && workQueue.isNotEmpty()){
            workToDo = workQueue.poll()

            workToDo?.let{ work ->
                //if(now - work.time)
                if(now - work.timeStamp > maxQueueTime){
                    Log.w("doWork", "Work to do too old: ${work.device.address}")
                    workToDo = null
                }
            }

        }

        workToDo?.let { currentWorkOrder ->
            val device = currentWorkOrder.device

            //ADD BLACKLIST CONDITION
            if(blacklist.filter{it.uniqueIdentifier == device.address}.isNotEmpty()){
                Log.w("doWork", "${device.address} has already been worked on. Skipping.")
                doWork()
                return
            }

            val alreadyConnected = getConnectionStatus(device)
            Log.w("doWork", "Already connected to ${device.address}: $alreadyConnected")

            if(alreadyConnected){
                currentWorkOrder.checklist.skipped.status = true
                currentWorkOrder.checklist.skipped.timePerformed = System.currentTimeMillis()
                finishWork(currentWorkOrder)
            } else {
                currentWorkOrder.let {
                    val workGattCallback = CentralGattCallback(it)
                    Log.w("doWork", "Starting work - connecting to device: ${device.address} @ ${it.connectable.rssi} ${it.connectable.transmissionPower} ${System.currentTimeMillis() - it.timeStamp}ms ago")
                    currentWork = it

                    try {
                        it.checklist.started.status = true
                        it.checklist.started.timePerformed = System.currentTimeMillis()

                        // it.startWork(context, workGattCallback)
                        it.startWork(context, workGattCallback)
                        var connecting = it.gatt?.connect() ?: false

                        if(!connecting){
                            Log.w("doWork", "Hala, not connecting! Moving on to next work: Conn status - $connecting")
                            currentWork = null
                            doWork()
                            return
                        } else {
                            Log.w("doWork", "Connection to ${it.device.address} in progress")
                        }

                        timeoutHandler.postDelayed(it.timeoutRunnable, connTimeout)
                        it.timeout = System.currentTimeMillis() + connTimeout

                        Log.w("doWork", "Timeout scheduled for ${it.device.address}")

                    } catch (e: Throwable){
                        Log.w("doWork", "Unexpected error while attempting to connect to ${device.address}: ${e.localizedMessage}")
                        Log.w("doWork", "Moving on to next work")
                        currentWork = null
                        doWork()
                        return
                    }

                }
            }
        }
        if(workToDo == null){
            Log.w("doWork", "No work to do!")
        }
    }

    private fun finishWork(work: Work){
        if(work.finished){
            Log.w("finishWork", "Work on ${work.device.address} already finished / closed")
            return
        }

        if(work.isCriticalsCompleted()){
            Utils.broadcastDeviceProcessed(context, work.device.address)
        }

        Log.w("finishWork", "Work on ${work.device.address} stopped in ${work.checklist.disconnected.timePerformed}")
        Log.w("finishWork", "Work on ${work.device.address} completed? ") //complete this log

        timeoutHandler.removeCallbacks(work.timeoutRunnable)
        work.finished = true
        doWork()
    }

    private fun getConnectionStatus(device: BluetoothDevice): Boolean {
        val connectedDevices = bluetoothManager.getDevicesMatchingConnectionStates(
                BluetoothProfile.GATT, intArrayOf(BluetoothProfile.STATE_CONNECTED)
        )
        return connectedDevices.contains(device)
    }

    inner class CentralGattCallback(val work: Work): BluetoothGattCallback(){
        fun endWorkConnection(gatt: BluetoothGatt){
            Log.w("CentralGattCallback", "Ending connection with ${gatt.device.address}")
            gatt.disconnect()
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            gatt?.let{
                Log.w("GattServerCallback", "Conn state changed")
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("GattServerCallback", "Successfully connected to ${gatt.device.address}")

                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                    gatt.requestMtu(512)

                    work.checklist.connected.status = true
                    work.checklist.connected.timePerformed = System.currentTimeMillis()

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("GattServerCallback", "Successfully disconnected from ${gatt.device.address}")

                    work.checklist.disconnected.status = true
                    work.checklist.disconnected.timePerformed = System.currentTimeMillis()

                    // timeoutHandler.removeCallbacks(work.timeoutRunnable)
                    if(work.device.address == currentWork?.device?.address){
                        currentWork = null
                    }else{ }

                    gatt.close()
                    // finishWork(work)
                } else {
                    Log.w("GattServerCallback", "State is $newState, Status: $status")
                    endWorkConnection(gatt)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if(!work.checklist.mtuChanged.status){
                work.checklist.mtuChanged.status = true
                work.checklist.mtuChanged.timePerformed = System.currentTimeMillis()

                Log.w("CentralGattCallback", " ${gatt?.device?.address} MTU is $mtu. Status : ${status == BluetoothGatt.GATT_SUCCESS} ")
            }
            gatt?.let{
                val discoveryOn = gatt.discoverServices()
                Log.w("CentralGattCallback", "Attempting to start discovery on ${gatt?.device?.address} : $discoveryOn")
            }
        }
        //comment 6:54pm 10/20/21
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.w("CentralGattCallback", "Discovered ${gatt.services.size} on ${gatt.device.address}")

                var service = gatt.getService(serviceUUID)

                service?.let {
                    val characteristic = service.getCharacteristic(characteristicUUID)

                    if(characteristic != null){
                        val readSuccess = gatt.readCharacteristic(characteristic)

                        Log.w("CentralGattCallback", "Attempt to read characteristic of service on ${gatt.device.address}: $readSuccess")
                    }else{
                        Log.w("CentralGattCallback", "${gatt.device.address} does not have our characteristic")
                        endWorkConnection(gatt)
                    }

                }
                if(service == null){
                    Log.w("CentralGattCallback", "${gatt.device.address} does not have our service")
                    endWorkConnection(gatt)
                }
            }else{
                Log.w("CentralGattCallback", "No services discoverd on ${gatt.device.address}")
                endWorkConnection(gatt)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.w("CentralGattCallback", "Read Status: $status")

            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.w("CentralGattCallback", "Characteristic read from ${gatt.device.address}: ${characteristic.getStringValue(0)}")

                Log.w("CentralGattCallback", "onCharacteristicRead: ${work.device.address} - ${work.connectable.rssi} - ${work.connectable.transmissionPower}")

                if(Bluetrace.supportsCharUUID(characteristic.uuid)) {
                    try {
                        val bluetraceImplementation = Bluetrace.getImplementation(characteristic.uuid)
                        val dataBytes = characteristic.value

                        val connectionRecord = bluetraceImplementation.central.processReadRequestDataReceived(
                                dataRead = dataBytes, peripheralAddress = work.device.address, rssi = work.connectable.rssi, txPower = work.connectable.transmissionPower
                        )

                        //broadcast to utils then send to btmonitoring
                        connectionRecord?.let {
                            Utils.broadcastStreetPassReceived(context, connectionRecord)
                        }
                    } catch (e: Throwable) {
                        Log.w("CentralGattCallback", "Failed to process read payload - ${e.message}")
                    }
                }
                work.checklist.readCharacteristic.status = true
                work.checklist.readCharacteristic.timePerformed = System.currentTimeMillis()
            } else{
                Log.w("CentralGattCallback", "Failed to read characteristic from ${gatt.device.address} : $status")
            }
            if(Bluetrace.supportsCharUUID(characteristic.uuid)){
                val bluetraceImplementation = Bluetrace.getImplementation(characteristic.uuid)

                var writeData = bluetraceImplementation.central.prepareWriteRequestData(
                        bluetraceImplementation.versionInt,
                        work.connectable.rssi,
                        work.connectable.transmissionPower
                )

                characteristic.value = writeData
                val writeSuccess = gatt?.writeCharacteristic(characteristic)
                Log.w("CentralGattCallback", "Attempt to write characteristic to our service on ${gatt.device.address}: $writeSuccess")
            } else{
                Log.w("CentralGattCallback", "Not writint to ${gatt.device.address}. Characteristic ${characteristic.uuid} is not supported")
                endWorkConnection(gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.w("CentralGattCallback", "Characteristic wrote successfully")
                work.checklist.writeCharacteristic.status = true
                work.checklist.writeCharacteristic.timePerformed = System.currentTimeMillis()
            }else{
                Log.w("CentralGattCallback", "Failed to write characteristic $status")
                endWorkConnection(gatt)
            }
        }
    }

    //ends connections
    fun terminateConnections() {
        Log.w("terminateConnections", "Cleaning up worker.")

        currentWork?.gatt?.disconnect()
        currentWork = null

        timeoutHandler.removeCallbacksAndMessages(null)
        queueHandler.removeCallbacksAndMessages(null)
        blacklistHandler.removeCallbacksAndMessages(null)

        workQueue.clear()
        blacklist.clear()
    }

    //RECEIVERS
    fun unregisterReceivers(){
        try{
            localBroadcastManager.unregisterReceiver(blacklistReceiver)
        } catch (e: Throwable){
            Log.e("UnregisterReceivers", "Unable to unregister blacklistReceiver!: ${e.localizedMessage}")
        }

        try{
            localBroadcastManager.unregisterReceiver(scannedDeviceReceiver)
        } catch (e: Throwable){
            Log.e("UnregisterReceivers", "Unable to unregister scannedDeviceReceiver!: ${e.localizedMessage}")
        }
    }

    inner class BlacklistReceiver : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            if(ACTION_DEVICE_PROCESSED == intent.action){
                val deviceAddress = intent.getStringExtra(DEVICE_ADDRESS)
                Log.d("BlacklistReceiver", "Adding $deviceAddress to blacklist")
                val entry = BlacklistEntry(deviceAddress.toString(), System.currentTimeMillis())
                blacklist.add(entry)
                blacklistHandler.postDelayed({
                    Log.w("CentralGattCallback", "Blacklist for ${entry.uniqueIdentifier} removed?: ${blacklist.remove(entry)}")
                }, 100000)
            }
        }
    }

    inner class ScannedDeviceReceiver : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (ACTION_DEVICE_SCANNED == intent.action) {
                    val bluetoothDevice: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val connectable: ConnectablePeripheral? =
                            intent.getParcelableExtra(CONNECTION_DATA)

                    //check if device and connectable are present (thru boolean)
                    val devicePresent = bluetoothDevice != null
                    val connectablePresent = connectable != null
                    Log.i("ScannedDeviceReceiver", "Device received: ${bluetoothDevice?.address}")
                    bluetoothDevice?.let {
                        connectable?.let {
                            val work = Work(bluetoothDevice, connectable, onWorkTimeoutListener)
                            if(addWork(work)){
                                doWork()
                            }
                        }
                    }
                }
            }
        }
    }
}