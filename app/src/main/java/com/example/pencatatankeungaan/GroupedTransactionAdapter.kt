package com.example.pencatatankeungaan

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class GroupedTransactionItem {
    data class Header(val dateLabel: String, val dailyExpenseTotal: Long) : GroupedTransactionItem()
    data class Item(val transaction: Transaction) : GroupedTransactionItem()
}

class GroupedTransactionAdapter(
    private var rawTransactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var groupedItems: List<GroupedTransactionItem> = emptyList()

    init {
        submitList(rawTransactions)
    }

    fun submitList(newList: List<Transaction>) {
        rawTransactions = newList
        val currentTime = System.currentTimeMillis()
        
        // Group by date string "yyyy-MM-dd"
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val groupedByDay = rawTransactions.groupBy { dayKeyFormat.format(Date(it.timestamp)) }
        
        // Sort keys in descending order (newest date first)
        val sortedKeys = groupedByDay.keys.sortedDescending()
        
        val items = mutableListOf<GroupedTransactionItem>()
        
        for (key in sortedKeys) {
            val dayTransactions = groupedByDay[key] ?: continue
            val sortedDayTx = dayTransactions.sortedByDescending { it.timestamp }
            
            // Calculate total expenses for this day
            val totalExpense = sortedDayTx
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            
            // Format date label
            val firstTxTimestamp = sortedDayTx.first().timestamp
            val label = getGroupDateLabel(firstTxTimestamp, currentTime)
            
            items.add(GroupedTransactionItem.Header(label, totalExpense))
            for (tx in sortedDayTx) {
                items.add(GroupedTransactionItem.Item(tx))
            }
        }
        
        groupedItems = items
        notifyDataSetChanged()
    }

    private fun getGroupDateLabel(timestamp: Long, currentTimeMillis: Long): String {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.forLanguageTag("id-ID"))
        
        val txDayStr = dayFormat.format(Date(timestamp))
        val todayStr = dayFormat.format(Date(currentTimeMillis))
        val yesterdayStr = dayFormat.format(Date(currentTimeMillis - 24 * 60 * 60 * 1000L))
        
        val dateStr = dateFormat.format(Date(timestamp))
        return when (txDayStr) {
            todayStr -> "Hari Ini, $dateStr"
            yesterdayStr -> "Kemarin, $dateStr"
            else -> dateStr
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (groupedItems[position]) {
            is GroupedTransactionItem.Header -> VIEW_TYPE_HEADER
            is GroupedTransactionItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_transaction_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_transaction, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = groupedItems[position]
        if (holder is HeaderViewHolder && item is GroupedTransactionItem.Header) {
            holder.bind(item)
        } else if (holder is ItemViewHolder && item is GroupedTransactionItem.Item) {
            holder.bind(item.transaction, onItemClick)
        }
    }

    override fun getItemCount(): Int = groupedItems.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeaderDate: TextView = itemView.findViewById(R.id.tvHeaderDate)
        private val tvHeaderTotal: TextView = itemView.findViewById(R.id.tvHeaderTotal)

        fun bind(header: GroupedTransactionItem.Header) {
            tvHeaderDate.text = header.dateLabel
            
            if (header.dailyExpenseTotal > 0) {
                val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
                numberFormat.maximumFractionDigits = 0
                val formattedAmount = numberFormat.format(header.dailyExpenseTotal).replace("Rp", "Rp ")
                tvHeaderTotal.text = "Pengeluaran: $formattedAmount"
                tvHeaderTotal.visibility = View.VISIBLE
            } else {
                tvHeaderTotal.visibility = View.GONE
            }
        }
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCategoryIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val tvTransactionTitle: TextView = itemView.findViewById(R.id.tvTransactionTitle)
        private val tvTransactionSubtitle: TextView = itemView.findViewById(R.id.tvTransactionSubtitle)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val ivProofBadge: ImageView = itemView.findViewById(R.id.ivProofBadge)

        fun bind(transaction: Transaction, onItemClick: (Transaction) -> Unit) {
            tvTransactionTitle.text = transaction.description
            
            // Format Subtitle: Kategori • Sumber
            val sourceText = when (transaction.source) {
                TransactionSource.VOICE -> "Suara"
                TransactionSource.MANUAL -> "Manual"
                TransactionSource.OCR -> "Struk"
            }
            tvTransactionSubtitle.text = "${transaction.category} • $sourceText"

            // Set Proof Badge visibility
            if (transaction.imagePath != null) {
                ivProofBadge.visibility = View.VISIBLE
            } else {
                ivProofBadge.visibility = View.GONE
            }

            // Format Currency
            val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
            numberFormat.maximumFractionDigits = 0
            val formattedAmount = numberFormat.format(transaction.amount).replace("Rp", "Rp ")

            if (transaction.type == TransactionType.INCOME) {
                tvAmount.text = "+$formattedAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.income_green))
                ivCategoryIcon.setImageResource(R.drawable.ic_trending_up)
                ivCategoryIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.income_green)
                )
                itemView.findViewById<View>(R.id.iconContainer).backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.income_green_light)
                )
            } else {
                tvAmount.text = "-$formattedAmount"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.expense_red))
                ivCategoryIcon.setImageResource(R.drawable.ic_trending_down)
                ivCategoryIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.expense_red)
                )
                itemView.findViewById<View>(R.id.iconContainer).backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, R.color.expense_red_light)
                )
            }

            itemView.setOnClickListener { onItemClick(transaction) }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
