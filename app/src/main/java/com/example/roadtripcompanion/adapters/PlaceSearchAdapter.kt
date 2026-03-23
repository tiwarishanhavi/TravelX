package com.example.roadtripcompanion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roadtripcompanion.models.PlaceSearchResult

class PlaceSearchAdapter(
    private val onPlaceSelected: (PlaceSearchResult) -> Unit
) : RecyclerView.Adapter<PlaceSearchAdapter.PlaceViewHolder>() {

    private val places = mutableListOf<PlaceSearchResult>()

    fun updatePlaces(newPlaces: List<PlaceSearchResult>) {
        places.clear()
        places.addAll(newPlaces)
        notifyDataSetChanged()
    }
    
    fun setPlaces(newPlaces: List<PlaceSearchResult>) {
        places.clear()
        places.addAll(newPlaces)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(android.R.id.text1)
        private val addressText: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(place: PlaceSearchResult) {
            nameText.text = place.name
            addressText.text = place.address
            
            itemView.setOnClickListener {
                onPlaceSelected(place)
            }
        }
    }
}
