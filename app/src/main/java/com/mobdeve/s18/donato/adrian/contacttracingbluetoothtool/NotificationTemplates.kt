package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.BluetoothMonitoringService.Companion.PENDING_WIZARD_REQ_CODE

class NotificationTemplates {
    companion object{
        fun lackingThingsNotification(context: Context, channel: String): Notification{
            //may need to change mainactivity to onboardingactivity
            var intent = Intent(context, MainActivity::class.java)
            intent.putExtra("page", 3)

            val activityPendingIntent = PendingIntent.getActivity(
                    context, PENDING_WIZARD_REQ_CODE,
                    intent, 0
            )

            val builder = NotificationCompat.Builder(context, channel)
                    .setContentTitle("Oh No!")
                    .setContentText("U-Trace is not scanning!")
                    .setOngoing(true)
                    .addAction(
                            R.drawable.ic_launcher_foreground,
                            "Open app now",
                            activityPendingIntent
                    )
                    .setContentIntent(activityPendingIntent)
                    .setWhen(System.currentTimeMillis())

            return builder.build()
        }
    }
}