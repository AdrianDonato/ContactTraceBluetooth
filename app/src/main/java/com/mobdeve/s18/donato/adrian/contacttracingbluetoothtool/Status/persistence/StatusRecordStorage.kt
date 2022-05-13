package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Status.persistence

import android.content.Context
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence.StreetPassRecordDatabase

//Calls statusDao
class StatusRecordStorage(val context: Context) {
    val statusDao = StreetPassRecordDatabase.getDatabase(context).statusDao()

    suspend fun saveRecord(record: StatusRecord) {
        statusDao.insert(record)
    }

    fun nukeDb() {
        statusDao.nukeDb()
    }

    fun getAllRecords(): List<StatusRecord> {
        return statusDao.getCurrentRecords()
    }

    suspend fun purgeOldRecords(before: Long) {
        statusDao.purgeOldRecords(before)
    }
}