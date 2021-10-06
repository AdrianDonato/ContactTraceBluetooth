package com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobdeve.s18.donato.adrian.contacttracingbluetoothtool.databinding.ScanresultBinding
import kotlinx.android.synthetic.main.scanresult.view.*

class ScanResultAdapter(
        private val items: List<ScanResult>,
        private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    class ViewHolder(
            private val view: View,
            private val onClickListener: (device: ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(view){
        fun bind(result: ScanResult){
            view.tv_scanResultName.text = result.device.name ?: "Unnamed"
            view.tv_scanResultAddress.text = result.device.address
            view.setOnClickListener{ onClickListener.invoke(result)}
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.scanresult , viewGroup, false)

            return ViewHolder(view, onClickListener)
    }
    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }


}