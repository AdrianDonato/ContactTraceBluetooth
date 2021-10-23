package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BLEAdvertiser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*
import kotlin.coroutines.CoroutineContext

class BluetoothMonitoringService: Service(), CoroutineScope{

    private var mNotifManager: NotificationManager? = null

    //streetpass scanner
    //streetpass server
    private var bleAdvertiser: BLEAdvertiser? = null

    private lateinit var commandHandler: CommandHandler
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var serviceUUID: UUID

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    var worker: Worker? = null

    //streetpassreceiver, bluetoothstatusreceiver, statusreceiver

    override fun onCreate() {
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        setup()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun setup(){
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        Log.d("BTMonitoringService", "Creating Bluetooth Monitoring Service")
        serviceUUID = UUID.fromString(getString(R.string.ble_uuid))

        worker = Worker(this.applicationContext)

        //retrieve temporary id here and save it as broadcast message
    }

    fun teardown(){

    }

    //function to setup notifications
    private fun setupNotifications(){
        mNotifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val mChannel = NotificationChannel(CHANNEL_ID, CHANNEL_SERVICE, NotificationManager.IMPORTANCE_LOW)
            mChannel.enableLights(false)
            mChannel.enableVibration(true)
            mChannel.vibrationPattern = longArrayOf(0L)
            mChannel.setSound(null, null)
            mChannel.setShowBadge(false)

            mNotifManager!!.createNotificationChannel(mChannel)
        }
    }

    //PERMISSIONS

    //check if bluetooth is enabled
    private fun isBluetoothEnabled(): Boolean {
        var btOn = false
        val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }

        bluetoothAdapter?.let {
            btOn = it.isEnabled
        }
        return btOn
    }



    //ADVERTISER METHODS
    private fun actionAdvertise(){
        setupAdvertiser()
        //check if bluetooth is enabled before beginning advertisement
        if(isBluetoothEnabled()){
            bleAdvertiser?.startAdvertising(advertisingDuration)
        } else {
            Log.w("BTMonitoringService", "Unable to start advertisement, BT is off")
        }
    }

    private fun scheduleAdvertisement() {
        if (!infiniteAdvertising) {
            commandHandler.scheduleNextAdvertise(advertisingDuration + advertisingGap)
        }
    }

    //SCANNER METHODS
    fun calcPhaseShift(min: Long, max: Long): Long {
        return (min + (Math.random() * (max - min))).toLong()
    }

    private fun scheduleScan(){
        if(!infiniteScanning){
            commandHandler.scheduleNextScan(scanDuration +
            calcPhaseShift(minScanInterval, maxScanInterval))
        }
    }

    private fun startScan(){
        if(isBluetoothEnabled()){

        } else {
            Log.w("BTMonitoringService", "Unable to start scan, BT is off")
        }
    }

    private fun performScan(){
        setupScanner()
        startScan()
    }

    //SETUPS
    private fun setupAdvertiser(){
        bleAdvertiser = bleAdvertiser ?: BLEAdvertiser(serviceUUID.toString())
    }

    private fun setupScanner(){

    }

    private fun setupCycles(){
        //setup scan cycle
        commandHandler.scheduleNextScan(0)
        //setup advertising cycle
        commandHandler.scheduleNextAdvertise(0)
    }



    //-------
    fun runService(cmd: Command?){

    }

    private fun stopService(){
        //terminate connections worker

        job.cancel()
    }

    //BROADCAST RECEIVERS
    inner class BluetoothStatusReceiver: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val action = intent.action
                if(action == BluetoothAdapter.ACTION_STATE_CHANGED){
                    var state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)

                    when(state){
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            Log.d("BTMonitoringService", "STATE_TURNING_OFF")
                            //teardown
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d("BTMonitoringService", "STATE_OFF")
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            Log.d("BTMonitoringService", "STATE_TURNING_ON")
                        }
                        BluetoothAdapter.STATE_ON -> {
                            Log.d("BTMonitoringService", "STATE_ON")
                            //call utils
                        }
                    }
                }
            }
        }
    }

    inner class StatusReceiver : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {

        }
    }

    //to be used by CommandHandler
    enum class Command(val index: Int, val string: String) {
        INVALID(-1, "INVALID"),
        ACTION_START(0, "START"),
        ACTION_SCAN(1, "SCAN"),
        ACTION_STOP(2, "STOP"),
        ACTION_ADVERTISE(3, "ADVERTISE"),
        ACTION_SELF_CHECK(4, "SELF_CHECK"),
        ACTION_UPDATE_BM(5, "UPDATE_BM"),
        ACTION_PURGE(6, "PURGE");

        companion object {
            private val types = values().associate { it.index to it }
            fun findByValue(value: Int) = types[value]
        }
    }

    //contains different values such as ID, millisecond time, etc.
    companion object{
        private val NOTIFICATION_ID = 771579
        private val CHANNEL_ID = "U-Trace Updates"
        val CHANNEL_SERVICE = "U-Trace Foreground Service"

        val PUSH_NOTIFICATION_ID = 771578

        val PENDING_ACTIVITY = 5
        val PENDING_START = 6
        val PENDING_SCAN_REQ_CODE = 7
        val PENDING_ADVERTISE_REQ_CODE = 8
        val PENDING_HEALTH_CHECK_CODE = 9
        val PENDING_WIZARD_REQ_CODE = 10
        val PENDING_BM_UPDATE = 11
        val PENDING_PURGE_CODE = 12

        //var broadcastMessage: TemporaryID? = null

        //Advertising and Scanning Stuffs
        val scanDuration: Long = 8000
        val minScanInterval: Long = 36000
        val maxScanInterval: Long = 43000

        val advertisingDuration: Long = 180000 // 3 Minutes
        val advertisingGap: Long = 5000

        val maxQueueTime: Long = 7000 // 7 Seconds
        val bmCheckInterval: Long = 540000 // 9 Minutes
        val healthCheckInterval: Long = 900000 // 15 Minutes
        val purgeInterval: Long = 86400000 // 1 Day - 24 Hours
        val purgeTTL: Long = 1814400000 // 21 Days - 3 Weeks (Might have to change to 2 weeks)

        val connectionTimeout: Long = 6000

        val blacklistDuration: Long = 100000 // 1.666... Minutes ??

        //for testing?
        val infiniteScanning = false
        val infiniteAdvertising = false

        val useBlacklist = true
        val bmValidityCheck = false
    }

}