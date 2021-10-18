package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.content.Context
import java.util.*
import java.util.concurrent.PriorityBlockingQueue

class Worker (val context: Context){
    private val workQueue: PriorityBlockingQueue<Work> = PriorityBlockingQueue(5,Collections.reverseOrder<Work>())
}