package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecord
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordDatabase
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordRepository

class ScanResultListActivity: AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanresultlist)

    }
}