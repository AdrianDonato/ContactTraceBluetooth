package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference


class CommandHandler(val service: WeakReference<BluetoothMonitoringService>):  Handler(Looper.getMainLooper()){
    /*
    override fun handleMessage(msg: Message?){
        msg?.let {
            val cmd = msg.what
            service.get()?.runService(BluetoothMonitoringService.Command.findByValue(cmd))
        }
    }
    */

    fun sendCommandMsg(cmd: BluetoothMonitoringService.Command){
        val msg = obtainMessage(cmd.index)
        msg.arg1 = cmd.index
        sendMessage(msg)
    }

    //with delay parameter
    fun sendCommandMsg(cmd: BluetoothMonitoringService.Command, delay: Long) {
        val msg = Message.obtain(this, cmd.index)
        sendMessageDelayed(msg, delay)
    }

    fun startBluetoothMonitoringService(){
        sendCommandMsg(BluetoothMonitoringService.Command.ACTION_START)
    }

    fun scheduleNextScan(timeInMillis: Long) {
        cancelNextScan()
        sendCommandMsg(BluetoothMonitoringService.Command.ACTION_SCAN, timeInMillis)
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
    }

    fun cancelNextAdvertise() {
        removeMessages(BluetoothMonitoringService.Command.ACTION_ADVERTISE.index)
    }

    fun hasAdvertiseScheduled(): Boolean {
        return hasMessages(BluetoothMonitoringService.Command.ACTION_ADVERTISE.index)
    }
}