package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_FAILURE
import android.bluetooth.le.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BLEAdvertiser
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothPayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothWritePayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Protocol.Bluetrace
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.charset.Charset
import java.util.*
import java.util.Arrays.toString
import java.util.Objects.toString
import java.util.concurrent.PriorityBlockingQueue
import kotlin.properties.Delegates


private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val GATT_MAX_MTU_SIZE = 517

class MainActivity : AppCompatActivity() {
    private lateinit var scanButton : Button
    private lateinit var advertiseButton: Button
    private lateinit var yourID: TextView
    private var serviceUUID: String by Delegates.notNull()

    //CHANGE ONCE MAHANAP ANG MAXQUEUETIME SA OPENTRACE
    private var maxQueueTime: Long = 120000
    private var connTimeout: Long = 180000 //change, imbento lang to for now
    //handlers
    private lateinit var timeoutHandler: Handler
    private lateinit var queueHandler: Handler
    private lateinit var blacklistHandler: Handler

    //queue for works
    private val workQueue: PriorityBlockingQueue<Work> = PriorityBlockingQueue(5, Collections.reverseOrder<Work>())
    //private val blacklist: MutableList<BlacklistEntry> = Collections.synchronizedList(ArrayList())

    //variable to be read in text bluetooth read/write
    private var idNum = (0..100).random().toString()

    //bluetooth service
    private var bluetoothManager: BluetoothManager by Delegates.notNull()

    //work timeout listener
    val onWorkTimeoutListener = object: Work.OnWorkTimeoutListener{
        override fun onWorkTimeout(work: Work) {
            if(!isCurrentlyWorkedOn(work.device.address)){
                Log.w("WorkTimeoutListener", "No longer being worked on.")
            }
            Log.w("WorkTimeoutListener", "Work status: ${work.checklist}")

            //add other logs later - 10/19/21
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
            Log.w("WorkTimeoutListener", "${work.device.address} is currently being worked on. Do not add.")
            return false
        }

        //add blacklist condition
        //---

        //
        if(workQueue.filter { it.device.address == work.device.address }.isEmpty()){
            workQueue.offer(work)
            queueHandler.postDelayed({
                if(workQueue.contains(work)){
                    Log.w("WorkTimeoutListener", "Work for ${work.device.address} removed: ${workQueue.remove(work)}")
                }
            }, maxQueueTime)
            Log.w("WorkTimeoutListener", "Added to work queue: ${work.device.address}")
            return true
        } else {

            Log.w("WorkTimeoutListener", "${work.device.address} is already in queue")

            var prevWork = workQueue.find { it.device.address == work.device.address }
            var removed = workQueue.remove(prevWork)
            var added = workQueue.offer(work)

            Log.w("WorkTimeoutListener", "Queue updated: removed ${removed}, added ${added}")

            return false
        }
    }

    //Work
    private var currentWork: Work? = null

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

            val alreadyConnected = getConnectionStatus(device)
            Log.w("doWork", "Already connected to ${device.address}: $alreadyConnected")

            if(alreadyConnected){
                currentWorkOrder.checklist.skipped.status = true
                currentWorkOrder.checklist.skipped.timePerformed = System.currentTimeMillis()
                finishWork(currentWorkOrder)
            } else {
                currentWorkOrder.let {
                    val workGattCallback = CentralGattCallback(it)
                    Log.w("doWork", "Starting work - connecting to device: ${device.address} @ ${it.connectable.rssi} ${System.currentTimeMillis() - it.timeStamp}ms ago")
                    currentWork = it

                    try {
                        it.checklist.started.status = true
                        it.checklist.started.timePerformed = System.currentTimeMillis()

                        it.startWork(applicationContext, workGattCallback)
                        var connecting = it.gatt?.connect() ?: false

                        if(!connecting){
                            Log.w("doWork", "Hala, not connecting! Moving on to next work")
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

        //work.isCriticalsCompleted

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init bluetooth manager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        scanButton = findViewById(R.id.scan_button)
        advertiseButton = findViewById(R.id.advertise_button)
        yourID = findViewById(R.id.hello)

        yourID.setText("Your ID is " + idNum)

        setupRecyclerView()
        //serviceUUID = getString(R.string.ble_uuid)
        pUuid = ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))
        /*
        var broadcastFilter = IntentFilter()
        broadcastFilter.addAction("BluetoothGattCallback")
        */


        //ilipat to prepare() function once meron na tayo
        timeoutHandler = Handler(Looper.getMainLooper())
        queueHandler = Handler(Looper.getMainLooper())
        blacklistHandler = Handler(Looper.getMainLooper())

        //isScanning is to check if ble is active
        scanButton.setOnClickListener{
            if(isScanning) {
                stopBleScan()
            } else {
                startBleScan()
                //startServer()
            }
        }
        //Add listener to advertise button
        advertiseButton.setOnClickListener{
            if(isAdvertising){
                Log.d("BLEAdvertiser", "Stopped Advertising")
                stopAdvertising()
            } else{
                startAdvertising(180000)
                startServer()
            }
        }
    }


    override fun onResume(){
        super.onResume()
        if(!bluetoothAdapter.isEnabled){
            promptBluetoothEnable()
        }
    }

    private fun promptBluetoothEnable(){
        if(!bluetoothAdapter.isEnabled){
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            getResult.launch(enableIntent)
        }
    }

    private val getResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val value = it.data?.getStringExtra("input")
        }
    }

    val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Location permission required")
            builder.setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices.")
            builder.setCancelable(false)
            //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = ))
            builder.setPositiveButton(android.R.string.ok) {dialog, which
                ->                 requestPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    LOCATION_PERMISSION_REQUEST_CODE
            )
            }
            builder.show()
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    /* BLE Scanner : Should be separate kt file? */
    /* 10/5/2021 */
    // can find a ble device but not sure if this is accurate
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }
    private val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()

    private fun startBleScan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else { /* TODO: Actually perform scan */
            val filter = ScanFilter.Builder().setServiceUuid(
                    ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))
            ).build()

            val filters: ArrayList<ScanFilter> = ArrayList()
            filters.add(filter)

            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(filters, scanSettings, scanCallback)
            isScanning = true
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.w("onScanResult", "Entered onScanResult")
            var rssi = result.rssi
            val device = result.device
            var txPower: Int?= null

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                txPower = result.txPower
                Log.w("onScanResult", "txPower = ${txPower}")
                if(txPower == 127){
                    txPower = null
                }
            }
            var connectable = ConnectablePeripheral("Manufacturer Data", txPower, rssi)
//          Utils.broadcastDeviceScanned(context, device, connectable)
            device?.let {
                connectable?.let {
                    val work = Work(device, connectable, onWorkTimeoutListener)
                    if(addWork(work)){
                        doWork()
                    }
                }
            }

            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                val rssi = result.rssi
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

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

                var connectable = ConnectablePeripheral("Manufacturer Data", txPower, rssi)
                //Utils.broadcastDeviceScanned(context, device, connectable)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e( "ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    //10/5/21 7PM - Stopping BLE Scan

    //sets button text to stop scan while app is currently scanning
    private var isScanning = false
        set (value) {
            field = value
            runOnUiThread{scanButton.text = if (value) "Stop Scan" else "Scan Bluetooth"}
        }

    //stops scanning of ble devices
    private fun stopBleScan(){
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    //bluetoooth gatt server
    private lateinit var bleServer: BluetoothGattServer

    // 10/6/21 6:34PM - Surfacing Scan Results
    //private val results_recyclerView : RecyclerView = findViewById(R.id.scanResult_RecyclerView)

    private fun setupRecyclerView() {
        val results_recyclerView : RecyclerView = findViewById(R.id.scanResult_RecyclerView)
        results_recyclerView.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                    this@MainActivity,
                    RecyclerView.VERTICAL,
                    false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scanResult_RecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    //Oct 6 - Bluetooth GATT (Connecting to BLE Device)
    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            //if(isScanning){stopBleScan()}
            Toast.makeText(applicationContext, "Connecting to device", Toast.LENGTH_SHORT).show()
            with(result.device){
                Log.w("ScanResultAdapter", "Connecting to $address")
                //result.device.connectGatt(applicationContext, false, gattServerCallback, BluetoothDevice.TRANSPORT_LE)

                connectGatt(applicationContext,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                )
            }
        }
    }

    //acting as central (when scanning)
    private val gattCallback = object : BluetoothGattCallback (){

        //used to end connection after writing to device
        fun endConnection(gatt: BluetoothGatt) {
            Log.w("BluetoothGattCallback", "Ending connection with: ${gatt.device.address}")
            gatt.disconnect()
        }


        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt?.device?.address
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    //store bluetooth gatt table
                    gatt?.requestMtu(512)

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    //gatt?.close()
                } else {
                    Log.w("BluetoothGattCallback", "State is $newState, Status: $status")
                }
            } else {
                Log.w("BluetoothGattCallback", "Error! Encountered $status for $deviceAddress. Disconnecting...")
                gatt?.close()
            }
        }

        //check if MTU has changed (added Oct 11)
        //after connection is successful, change MTU
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if(status == BluetoothGatt.GATT_SUCCESS){
                gatt?.discoverServices()
                Log.w("BluetoothGattCallback", "Starting service discovery")
            }
        }

        //after MTU is changed, check if services are discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.w("BluetoothGattCallback", "Discovered ${gatt?.services?.size} service/s on ${gatt?.device?.address}")

            //get service id from gatt
            var discService = gatt?.getService(UUID.fromString(getString(R.string.ble_uuid)))
            discService?.let {
                //get service characteristic
                val discCharacteristic = discService.getCharacteristic(UUID.fromString(getString(R.string.ble_characuuid)))

                //if may nadiscover na read characteristic
                if(discCharacteristic != null){
                    val readSuccess = gatt?.readCharacteristic(discCharacteristic)
                    Log.w("BluetoothGattCallback", "Read characteristic of service: $readSuccess")

                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.w("BluetoothGattCallback", "Characteristic read from ${gatt?.device?.address}: ${characteristic?.getStringValue(0)}")

                var writeData = BluetoothWritePayload(v = 2, id = idNum, central = asCentralDevice(), rs = 0).getPayload()
                characteristic?.value = writeData

                val writeSuccess = gatt?.writeCharacteristic(characteristic)
                Log.w("BluetoothGattback", "Attempt to write characteristic on ${gatt?.device?.address}: $writeSuccess")

            } else {
                Log.w("BluetoothGattCallback", "Failed to read characteristics from ${gatt?.device?.address}: $status")
            }
        }

        //checks for the status of writeCharacteristic on onCharacteristicRead
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.w("BluetoothGattCallback", "Write operation successful!")
            } else {
                Log.w("BluetoothGattCallback", "Failed to write: $status")
            }
        }
    }

    //for bluetoothgattcallback (central)
    fun ByteArray.toHexString(): String =
            joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    //connect to a BLE device (di pa natetest rn)
    //callback for gatt server (advertise ata?)
    private val gattServerCallback = object : BluetoothGattServerCallback (){

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
                        bleServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, sentVal)
                    }
                }else{
                    Log.w("GattServerCallback", "Unsupported characteristic from ${device.address}")
                    bleServer?.sendResponse(device, requestId, GATT_FAILURE, 0, null)
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
                            bleServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, dataBuffer.size, value)
                        }
                        //check opentrace code if preparedWrite is false
                        if(!preparedWrite){
                            Log.w("GattServerCallback", "preparedWrite - $preparedWrite")
                            saveDataReceived(device)
                            if(responseNeeded){
                                bleServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, dataBuffer.size, value)
                            }
                        }
                    }
                }else{
                    Log.w("GattServerCallback", "Unsupported Characteristic from ${device.address}")
                    if(responseNeeded){
                        bleServer?.sendResponse(device, requestId, GATT_FAILURE, 0, null)
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
                    bleServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } else {
                    bleServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        fun saveDataReceived(device: BluetoothDevice){
            var data = writePayloadMap[device.address]
            var charUUID = deviceCharacteristicMap[device.address]

            Log.w("GattServerCallback", "Entering saveDataReceived method")


            charUUID?.let {
                Log.w("GattServerCallback", "Entering charUUID?.let")
                data?.let {
                    Log.w("GattServerCallback", "Entering data?.let")
                    try{
                        device.let {
                            val bluetraceImplementation = Bluetrace.getImplementation(charUUID)

                            val connectionRecord = bluetraceImplementation.peripheral.processWriteRequestDataReceived(data, device.address)
                            Log.w("GattServerCallback", "Entering device.let")
                            try{
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



    // 10/7/2021 - 10/8/2021
    // BLEAdvertising
    //private val advertiser = BLEAdvertiser(getString(R.string.ble_uuid))

    private var advertiser: BluetoothLeAdvertiser? = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()
    private var charLength = 3


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
        set (value) {
            field = value
            runOnUiThread{advertiseButton.text = if (value) "Stop Advertising" else "Advertise"}
        }
    var shouldBeAdvertising = false

    var handler = Handler(Looper.getMainLooper())

    var settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            //.setTimeout(0)
            .build()
    var pUuid: ParcelUuid by Delegates.notNull()

    var data: AdvertiseData? = null
    var scanResponseData: AdvertiseData? = null

    //starts ble gatt server
    fun startServer(): Boolean{
        var startBool = false
        Log.w("BLEGattServer", "Starting server")
        bleServer = bluetoothManager.openGattServer(applicationContext, gattServerCallback)
        bleServer?.let{
            it.clearServices()
            startBool = true
        }

        if(startBool == true){
            val gattService = BluetoothGattService(UUID.fromString(getString(R.string.ble_uuid)), BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val gattCharacteristic = BluetoothGattCharacteristic(UUID.fromString(getString(R.string.ble_characuuid)),
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
            gattService.addCharacteristic(gattCharacteristic)
            bleServer.addService(gattService)
        }

        Log.w("BLEGattServer", "Clear services - $startBool")
        return startBool
    }

    fun stopServer(){
        try {
            bleServer?.clearServices()
            bleServer?.close()
            Log.w("BLEGattServer", "Server stopped")
        } catch (e: Throwable){
            Log.w("BLEGattServer", "Cannot stop server elegantly! - ${e.localizedMessage}")
        }
    }

    fun startAdvertisingLegacy(timeoutInMillis: Long){
        val randomUUID = UUID.randomUUID().toString()
        val finalString = randomUUID.substring(randomUUID.length - charLength, randomUUID.length)
        val serviceDataByteArray = finalString.toByteArray()

        data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(pUuid)
                //.addServiceData(pUuid, "Data".toByteArray(Charset.forName("UTF-8")))
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

        /*
        if (!infiniteAdvertising) {
            handler.removeCallbacksAndMessages(stopRunnable)
            handler.postDelayed(stopRunnable, timeoutInMillis)
        }
         */
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

    fun asPeripheralDevice(): PeripheralDevice {
        return PeripheralDevice(Build.MODEL, "SELF")
    }

    fun asCentralDevice(): CentralDevice {
        return CentralDevice(Build.MODEL, "SELF")
    }
//

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

                var service = gatt.getService(UUID.fromString(getString(R.string.ble_uuid)))

                service?.let {
                    val characteristic = service.getCharacteristic(UUID.fromString(getString(R.string.ble_characuuid)))

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
                val bluetraceImplementation =Bluetrace.getImplementation(characteristic.uuid)

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
}