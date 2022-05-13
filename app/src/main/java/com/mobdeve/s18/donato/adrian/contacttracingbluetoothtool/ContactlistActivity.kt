package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecord
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordStorage
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class ContactlistActivity: AppCompatActivity(){

    private var disposableObj: Disposable? = null //used to read SQLite Records
    private lateinit var contactData: ArrayList<ContactRVData>
    private lateinit var rvCloseContacts: RecyclerView

    private lateinit var btnBack: Button
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactlist)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        btnRefresh = findViewById(R.id.btnRefresh)
        btnRefresh.setOnClickListener {
            finish()
            startActivity(intent)
        }

        //recyclerview setup
        rvCloseContacts = findViewById(R.id.rvCloseContacts)
        rvCloseContacts.layoutManager = LinearLayoutManager(applicationContext)
        contactData = arrayListOf()


        //observable retrieval sql
        var observableContacts = Observable.create<List<StreetPassRecord>>{
            val result = StreetPassRecordStorage(applicationContext).getAllRecords()
            it.onNext(result)
        }

        disposableObj = observableContacts.observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io())
            .subscribe{ records ->
                if(records.size > 0){
                    for(retRecord in records){
                        contactData.add(ContactRVData(retRecord.msg,
                            retRecord.modelP, retRecord.modelC, retRecord.rssi, getDate(retRecord.timestamp)))
                    }
                    rvCloseContacts.adapter = ContactRVAdapter(contactData)
                }
            }

        //retrieve records from sql (non-observable version)
        /*
        val recordDao = StreetPassRecordDatabase.getDatabase(this.applicationContext).recordDao()

        var repo = StreetPassRecordRepository(recordDao)
        var savedRecords: LiveData<List<StreetPassRecord>> = repo.allRecords
        savedRecords.observe(this, androidx.lifecycle.Observer { records ->
            if(records.size > 0){
                for(retRecord in records){
                  contactData.add(ContactRVData(retRecord.msg,
                      retRecord.modelP, retRecord.modelC, retRecord.rssi, getDate(retRecord.timestamp)))
                }
                rvCloseContacts.adapter = ContactRVAdapter(contactData)
            }
        })*/
    }

    override fun onDestroy() {
        super.onDestroy()
        disposableObj?.dispose()
    }

    fun getDate(milliSeconds: Long): String {
        val dateFormat = "dd/MM/yyyy HH:mm:ss"
        // Create a DateFormatter object for displaying date in specified format.
        val formatter = SimpleDateFormat(dateFormat)

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return formatter.format(calendar.time)
    }
}