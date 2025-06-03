package vcmsa.projects.personalbudgettingcorp.adapters

import Category
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vcmsa.projects.personalbudgettingcorp.R
import java.util.Locale

class CategoryAdapter(private var categories: List<Category>) :
    RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    // Listener for item clicks (optional, for editing/deleting)
    var onItemClick: ((Category) -> Unit)? = null

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val tvCategoryLimit: TextView = itemView.findViewById(R.id.tvCategoryLimit)

        init {
            itemView.setOnClickListener {
                onItemClick?.invoke(categories[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.tvCategoryName.text = category.name
        if (category.monthlyLimit > 0) {
            holder.tvCategoryLimit.text = String.format(Locale.getDefault(), "Limit: R%.2f", category.monthlyLimit)
            holder.tvCategoryLimit.visibility = View.VISIBLE
        } else {
            holder.tvCategoryLimit.text = "No Limit Set"
            // Or hide it: holder.tvCategoryLimit.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<Category>) {
        this.categories = newCategories
        notifyDataSetChanged() // Or use DiffUtil for better performance
    }
}
