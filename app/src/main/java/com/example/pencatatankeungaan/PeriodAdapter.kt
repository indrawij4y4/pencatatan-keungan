package com.example.pencatatankeungaan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PeriodAdapter(
    private val periods: List<String>,
    private val activePeriod: String,
    private val emptyPeriods: Set<String>,
    private val onPeriodSelected: (String) -> Unit
) : RecyclerView.Adapter<PeriodAdapter.PeriodViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeriodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_period_picker, parent, false)
        return PeriodViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeriodViewHolder, position: Int) {
        holder.bind(periods[position], activePeriod, emptyPeriods, onPeriodSelected)
    }

    override fun getItemCount(): Int = periods.size

    class PeriodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPeriodName: TextView = itemView.findViewById(R.id.tvPeriodName)
        private val tvPeriodStatusBadge: TextView = itemView.findViewById(R.id.tvPeriodStatusBadge)

        fun bind(
            period: String,
            activePeriod: String,
            emptyPeriods: Set<String>,
            onPeriodSelected: (String) -> Unit
        ) {
            val context = itemView.context
            tvPeriodName.text = period

            when {
                period == activePeriod -> {
                    // Active Period style
                    tvPeriodName.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    tvPeriodName.setTypeface(null, android.graphics.Typeface.BOLD)

                    tvPeriodStatusBadge.visibility = View.VISIBLE
                    tvPeriodStatusBadge.text = "AKTIF"
                    tvPeriodStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_white))
                    tvPeriodStatusBadge.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
                    tvPeriodStatusBadge.isSelected = true
                }
                emptyPeriods.contains(period) -> {
                    // Empty Period style
                    tvPeriodName.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    tvPeriodName.setTypeface(null, android.graphics.Typeface.NORMAL)

                    tvPeriodStatusBadge.visibility = View.VISIBLE
                    tvPeriodStatusBadge.text = "KOSONG"
                    tvPeriodStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    tvPeriodStatusBadge.setBackgroundResource(R.drawable.bg_brutal_chip_filter)
                    tvPeriodStatusBadge.isSelected = false
                }
                else -> {
                    // Regular Period (has transactions) style
                    tvPeriodName.setTextColor(ContextCompat.getColor(context, R.color.text_main))
                    tvPeriodName.setTypeface(null, android.graphics.Typeface.NORMAL)

                    tvPeriodStatusBadge.visibility = View.GONE
                }
            }

            itemView.setOnClickListener {
                onPeriodSelected(period)
            }
        }
    }
}
