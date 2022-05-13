package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactRVAdapter (private val contactRVList: ArrayList<ContactRVData>): RecyclerView.Adapter<ContactRVAdapter.ContactRVViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactRVViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.closecontactlist, parent, false)
        contactRVList.sortedByDescending { it.timestamp }
        return ContactRVViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ContactRVViewHolder, position: Int) {
        val currItem = contactRVList[position]
        holder.contactMessage.text = currItem.msg
        holder.contactTimestamp.text = currItem.timestamp
        holder.contactModelP.text = currItem.modelP
        holder.contactModelC.text = currItem.modelC
        holder.contactRSSI.text = currItem.rssi.toString()
    }

    override fun getItemCount(): Int {
        return contactRVList.size
    }

    class ContactRVViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val contactMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val contactTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val contactModelP: TextView = itemView.findViewById(R.id.tvModelP)
        val contactModelC: TextView = itemView.findViewById(R.id.tvModelC)
        val contactRSSI: TextView = itemView.findViewById(R.id.tvRSSI)
    }
}