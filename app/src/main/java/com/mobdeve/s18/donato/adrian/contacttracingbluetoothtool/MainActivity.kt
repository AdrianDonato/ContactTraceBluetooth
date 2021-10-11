package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BLEAdvertiser
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.charset.Charset
import java.util.*
import java.util.Arrays.toString
import java.util.Objects.toString
import kotlin.properties.Delegates


private const val LOCATION_PERMISSION_REQUEST_CODE = 2

class MainActivity : AppCompatActivity() {
    private lateinit var scanButton : Button
    private lateinit var advertiseButton: Button
    private var serviceUUID: String by Delegates.notNull()

    //bluetooth service
    private var bluetoothManager: BluetoothManager by Delegates.notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init bluetooth manager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        scanButton = findViewById(R.id.scan_button)
        advertiseButton = findViewById(R.id.advertise_button)
        setupRecyclerView()
        //serviceUUID = getString(R.string.ble_uuid)
        pUuid = ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))
        /*
        var broadcastFilter = IntentFilter()
        broadcastFilter.addAction("BluetoothGattCallback")
        */

        //isScanning is to check if ble is active
        scanButton.setOnClickListener{
            if(isScanning) {
                stopBleScan()
            } else {
                startBleScan()
                startServer()
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
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
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

            /*
                connectGatt(applicationContext,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                )

                 */
                //connect(result.device, false)
                //bleServer?.connect(result.device, false)
                //startServer()
                bleServer.connect(result.device, false)
            }
        }
    }



    //connect to a BLE device (di pa natetest rn)
  //  private val gattCallback = object : BluetoothGattCallback (){
    private val gattServerCallback = object : BluetoothGattServerCallback (){
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
           super.onConnectionStateChange(device, status, newState)
            // super.onConnectionStateChange(gatt, status, newState)
           // val deviceAddress = gatt?.device?.address
            Log.w("BluetoothGattCallback", "Conn state changed")
          //  if(status == BluetoothGatt.GATT_SUCCESS){
                //if(isAdvertising) {stopAdvertising()}
                    Log.w("BluetoothGattCallback", "Gatt Connection Successful")
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully connected to ${device?.address}")
                    //store bluetooth gatt table
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from ${device?.address}")
                    //gatt?.close()
                } else {
                    Log.w("BluetoothGattCallback", "State is $newState, Status: $status")
                }
          //  } else {
            //    Log.w("BluetoothGattCallback", "Error! Encountered $status for $deviceAddress. Disconnecting...")
              //  gatt?.close()
           // }

        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.w("BluetoothGattCallback", "Requested read")
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
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
            gattService.addCharacteristic(gattCharacteristic)
            bleServer.addService(gattService)
        }

        Log.w("BLEGattServer", "Clear services - $startBool")
        return startBool
    }

    fun stopServer(){
        bleServer?.clearServices()
        bleServer?.close()
    }

    //fun addService(service: GattService)

    fun startAdvertisingLegacy(timeoutInMillis: Long){
        val randomUUID = UUID.randomUUID().toString()
        val finalString = randomUUID.substring(randomUUID.length - charLength, randomUUID.length)
        val serviceDataByteArray = finalString.toByteArray()

        data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(pUuid)
                //.addServiceData(pUuid, "Data".toByteArray(Charset.forName("UTF-8")))
                .build()
        scanResponseData = AdvertiseData.Builder().addServiceUuid(pUuid).build()
        try {
            Log.d("BLEAdvertiser", "Start advertising")
            advertiser = advertiser ?: BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser
            advertiser?.startAdvertising(settings, data, scanResponseData, callback)
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
//
}