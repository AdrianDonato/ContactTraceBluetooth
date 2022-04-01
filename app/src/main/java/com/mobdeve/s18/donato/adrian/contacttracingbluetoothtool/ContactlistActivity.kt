package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecord
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordDatabase
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordRepository


class ContactlistActivity: AppCompatActivity(){

    lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactlist)

        val recordDao = StreetPassRecordDatabase.getDatabase(this.applicationContext).recordDao()


        var repo = StreetPassRecordRepository(recordDao)
        var savedRecords: LiveData<List<StreetPassRecord>> = repo.allRecords
        savedRecords.observe(this, androidx.lifecycle.Observer { records ->
            if(records.size > 0){
                //RECORD LIST:
                //msg, modelc, modelp, rssi, timestamp, v
            }
        })
    }
}