package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.prefs.PreferenceChangeEvent

object Scheduler {

    fun scheduleServiceIntent(requestCode: Int, context: Context, intent: Intent, timeFromNowMillis: Long){

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeFromNowMillis,
                    alarmIntent
            )
        }else{
            alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                         SystemClock.elapsedRealtime() + timeFromNowMillis,
                            alarmIntent
            )
        }
    }

    fun scheduleRepeatingServiceIntent(requestCode: Int, context: Context,intent: Intent, intervalMillis: Long){

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        Log.d("Scheduler", "Purging alarm set to ${Preference.getLastPurgeTime(context) + intervalMillis}")
        alarmMgr.setRepeating(AlarmManager.RTC, Preference.getLastPurgeTime(context) + intervalMillis, intervalMillis, alarmIntent)
    }

    fun cancelServiceIntent(requestCode: Int, context: Context, intent: Intent){
        val alarmIntent = PendingIntent.getService(context, requestCode,intent, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmIntent.cancel()
    }
}