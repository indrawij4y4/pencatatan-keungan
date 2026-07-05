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

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position], onItemClick)
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCategoryIcon: ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val tvTransactionTitle: TextView = itemView.findViewById(R.id.tvTransactionTitle)
        private val tvTransactionSubtitle: TextView = itemView.findViewById(R.id.tvTransactionSubtitle)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)

        fun bind(transaction: Transaction, onItemClick: (Transaction) -> Unit) {
            tvTransactionTitle.text = transaction.description
            
            // Format Subtitle: Kategori • Sumber
            val sourceText = when (transaction.source) {
                TransactionSource.VOICE -> "Suara"
                TransactionSource.MANUAL -> "Manual"
                TransactionSource.OCR -> "Struk"
            }
            tvTransactionSubtitle.text = "${transaction.category} • $sourceText"

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
}
