package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.Status.persistence

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

//Interface for accessing data from status_table
@Dao
interface StatusRecordDao {
    @Query("SELECT * from status_table ORDER BY timestamp ASC")
    fun getRecords(): LiveData<List<StatusRecord>>

    @Query("SELECT * from status_table ORDER BY timestamp ASC")
    fun getCurrentRecords(): List<StatusRecord>

    @Query("SELECT * from status_table where msg = :msg ORDER BY timestamp DESC LIMIT 1")
    fun getMostRecentRecord(msg: String): LiveData<StatusRecord?>

    @Query("DELETE FROM status_table")
    fun nukeDb()

    @Query("DELETE FROM status_table WHERE timestamp < :before")
    fun purgeOldRecords(before: Long)

    @RawQuery
    fun getRecordsViaQuery(query: SupportSQLiteQuery): List<StatusRecord>


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(record: StatusRecord)
}