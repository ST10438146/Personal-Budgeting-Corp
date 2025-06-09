package vcmsa.projects.personalbudgettingcorp.adapters

import Expense
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vcmsa.projects.personalbudgettingcorp.R
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.util.Date
import java.util.Locale

class ExpensesAdapter(
    private var expenses: List<Expense>
) : RecyclerView.Adapter<ExpensesAdapter.ExpenseViewHolder>() {

    // Listener for item clicks ( for viewing full image or details)
    var onItemClick: ((Expense) -> Unit)? = null

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val ivReceiptThumbnail: ImageView = itemView.findViewById(R.id.ivReceiptThumbnail)

        init {
            itemView.setOnClickListener {
                // Ensures adapterPosition is valid
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(expenses[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]

        holder.tvCategory.text = expense.category
        holder.tvAmount.text = String.format(Locale.getDefault(), "R%.2f", expense.amount)
        holder.tvDate.text = formatDate(expense.timestamp)
        holder.tvDescription.text = expense.description

        if (!expense.imageUrl.isNullOrEmpty()) {
            holder.ivReceiptThumbnail.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(expense.imageUrl)
                .placeholder(R.drawable.ic_placeholder_image) // Replaces with your placeholder
                .error(R.drawable.ic_placeholder_image) // Replaces with an error image
                .centerCrop()
                .into(holder.ivReceiptThumbnail)
        } else {
            holder.ivReceiptThumbnail.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = expenses.size

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun updateExpenses(newExpenses: List<Expense>) {
        this.expenses = newExpenses
        notifyDataSetChanged()
    }
}