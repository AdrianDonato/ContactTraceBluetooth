package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import java.lang.ref.WeakReference


class CommandHandler(val service: WeakReference<BluetoothMonitoringService>):  Handler(Looper.getMainLooper()){

    override fun handleMessage(msg: Message){
        msg?.let {
            val cmd = msg.what
            service.get()?.runService(BluetoothMonitoringService.Command.findByValue(cmd))
        }
    }

    fun sendCommandMsg(cmd: BluetoothMonitoringService.Command){
        val msg = obtainMessage(cmd.index)
        msg.arg1 = cmd.index
        Log.d("CommandHandler", "Sending message: ${msg.arg1}")

        if(sendMessage(msg)){
            Log.d("CommandHandler", "Message successfully sent")
        } else {Log.d("CommandHandler", "Message not sent?")}

    }

    //with delay parameter
    fun sendCommandMsg(cmd: BluetoothMonitoringService.Command, delay: Long) {
        val msg = Message.obtain(this, cmd.index)
        Log.d("CommandHandler", "Sending message: ${msg.toString()}, delay $delay")
        if(sendMessageDelayed(msg, delay)){
            Log.d("CommandHandler", "Message successfully sent")
        } else {Log.d("CommandHandler", "Message not sent?")}
    }

    fun startBluetoothMonitoringService(){
        sendCommandMsg(BluetoothMonitoringService.Command.ACTION_START)
    }

    fun scheduleNextScan(timeInMillis: Long) {
        cancelNextScan()
        sendCommandMsg(BluetoothMonitoringService.Command.ACTION_SCAN, timeInMillis)
        Log.d("CommandHandler", "Scheduled next scan")
    }

    fun cancelNextScan(){
        removeMessages(BluetoothMonitoringService.Command.ACTION_SCAN.index)
    }

    fun hasScanScheduled(): Boolean {
        return hasMessages(BluetoothMonitoringService.Command.ACTION_SCAN.index)
    }

    fun scheduleNextAdvertise(timeInMillis: Long) {
        cancelNextAdvertise()
        sendCommandMsg(BluetoothMonitoringService.Command.ACTION_ADVERTISE, timeInMillis)
        Log.d("CommandHandler", "Scheduled next advertise")
    }

    fun cancelNextAdvertise() {
        removeMessages(BluetoothMonitoringService.Command.ACTION_ADVERTISE.index)
    }

    fun hasAdvertiseScheduled(): Boolean {
        return hasMessages(BluetoothMonitoringService.Command.ACTION_ADVERTISE.index)
    }
}