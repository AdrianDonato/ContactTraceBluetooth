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
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BLEAdvertiser
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothPayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Bluetooth.BluetoothWritePayload
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Protocol.Bluetrace
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.R
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecord
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordDatabase
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordRepository
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.charset.Charset
import java.util.*
import java.util.Arrays.toString
import java.util.Objects.toString
import java.util.concurrent.PriorityBlockingQueue
import kotlin.properties.Delegates


private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val BATTERY_OPTIMISER = 789

class MainActivity : AppCompatActivity() {
    private lateinit var scanButton : Button
    private lateinit var advertiseButton: Button
    private lateinit var savedRecsButton: Button
    private lateinit var yourID: TextView

    //variable to be read in text bluetooth read/write
    private var idNum = (0..100).random().toString()

    //bluetooth service
    private var bluetoothManager: BluetoothManager by Delegates.notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Preference.putUserID(applicationContext, idNum)

        val recordDao = StreetPassRecordDatabase.getDatabase(this.applicationContext).recordDao()
        scanButton = findViewById(R.id.scan_button)
        savedRecsButton = findViewById(R.id.saved_records_button)
        advertiseButton = findViewById(R.id.advertise_button)
        yourID = findViewById(R.id.hello)

        yourID.setText("Your ID is " + TracerApp.thisDeviceMsg())

        if(!isLocationPermissionGranted){
            requestLocationPermission()
        } else {
            Utils.startBluetoothMonitoringService(this)
        }

        //TESTING: FOR RETRIEVING DB RECORDS
        scanButton.setOnClickListener{

            var repo = StreetPassRecordRepository(recordDao)
            var savedRecords: LiveData<List<StreetPassRecord>> = repo.allRecords
            savedRecords.observe(this, androidx.lifecycle.Observer { records ->
                Log.d("DBMainActivity", "Saved Records: ${records.get(0).modelP}, ${records.get(0).rssi}, ${records.get(0).msg}")
                Toast.makeText(this.applicationContext, "Saved Records: ${records.get(0).modelP}, ${records.get(0).rssi}, ${records.get(0).msg}",
                    Toast.LENGTH_SHORT).show()
            })
        }

        savedRecsButton.setOnClickListener {
            startActivity(Intent(this, ContactlistActivity::class.java))
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

    private fun excludeFromBatteryOptimization() {
        Log.d("MainActivityLog", "[excludeFromBatteryOptimization] ")
        val powerManager =
            this.getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        val packageName = this.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent =
                Utils.getBatteryOptimizerExemptionIntent(
                    packageName
                )

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivityLog", "Not on Battery Optimization whitelist")
                //check if there's any activity that can handle this
                if (Utils.canHandleIntent(
                        intent,
                        packageManager
                    )
                ) {
                    getResult.launch(intent)
                } else {
                    Log.d("MainActivityLog", "No way of handling optimizer")
                }
            } else {
                Log.d("MainActivityLog", "On Battery Optimization whitelist")
            }
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
                    excludeFromBatteryOptimization()
                } else {
                    excludeFromBatteryOptimization()
                    Utils.startBluetoothMonitoringService(this)
                }
            }
        }
    }
}