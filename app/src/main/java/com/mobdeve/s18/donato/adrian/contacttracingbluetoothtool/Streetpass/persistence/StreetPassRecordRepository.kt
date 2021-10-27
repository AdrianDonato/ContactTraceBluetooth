package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Streetpass.persistence

import androidx.lifecycle.LiveData

class StreetPassRecordRepository (private val recordDao: StreetPassRecordDao) {
    val allRecords: LiveData<List<StreetPassRecord>> = recordDao.getRecords()

    suspend fun insert(word: StreetPassRecord) {
        recordDao.insert(word)
    }
}