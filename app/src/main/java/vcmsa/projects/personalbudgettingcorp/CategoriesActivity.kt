package vcmsa.projects.personalbudgettingcorp

import Category
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityCategoriesBinding
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.Query
import vcmsa.projects.personalbudgettingcorp.adapters.CategoryAdapter
import vcmsa.projects.personalbudgettingcorp.databinding.DialogAddCategoryBinding // Import view binding for dialog

class CategoriesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var categoryAdapter: CategoryAdapter
    private val categoryList = mutableListOf<Category>()
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in. Cannot manage categories.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadCategories()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Manage Categories"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(categoryList)
        binding.categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.categoriesRecyclerView.adapter = categoryAdapter

        categoryAdapter.onItemClick = { category ->
            // Handle category click: e.g., open edit/delete dialog
            Toast.makeText(this, "Clicked: ${category.name}", Toast.LENGTH_SHORT).show()
            // showEditDeleteCategoryDialog(category) // Implement this for edit/delete
        }
    }

    private fun setupFab() {
        binding.fabAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        val dialogBinding = DialogAddCategoryBinding.inflate(LayoutInflater.from(this))
        // val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        // val etCategoryName = dialogView.findViewById<EditText>(R.id.etDialogCategoryName)
        // val etCategoryLimit = dialogView.findViewById<EditText>(R.id.etDialogCategoryLimit)

        AlertDialog.Builder(this)
            .setTitle("Add New Category")
            .setView(dialogBinding.root) // Use dialogBinding.root
            .setPositiveButton("Add") { dialog, _ ->
                val name = dialogBinding.etDialogCategoryName.text.toString().trim()
                val limitStr = dialogBinding.etDialogCategoryLimit.text.toString().trim()
                val limit = if (limitStr.isNotEmpty()) limitStr.toDoubleOrNull() ?: 0.0 else 0.0

                if (name.isNotEmpty()) {
                    addNewCategory(name, limit)
                } else {
                    Toast.makeText(this, "Category name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun addNewCategory(name: String, limit: Double) {
        userId?.let { uid ->
            val newCategory = Category(name = name, monthlyLimit = limit)
            db.collection("users").document(uid).collection("categories")
                .add(newCategory) // Firestore generates ID
                .addOnSuccessListener {
                    Toast.makeText(this, "Category '$name' added.", Toast.LENGTH_SHORT).show()
                    loadCategories() // Refresh the list
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error adding category: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("CategoriesActivity", "Error adding category", e)
                }
        }
    }

    private fun loadCategories() {
        userId?.let { uid ->
            db.collection("users").document(uid).collection("categories")
                .orderBy("name", Query.Direction.ASCENDING) // Optional: order by name
                .get()
                .addOnSuccessListener { documents ->
                    categoryList.clear()
                    for (document in documents) {
                        val category = document.toObject(Category::class.java)
                        // category.id = document.id // If you have an 'id' field in Category data class annotated with @DocumentId
                        categoryList.add(category)
                    }
                    categoryAdapter.updateCategories(categoryList)
                    toggleNoCategoriesView()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading categories: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("CategoriesActivity", "Error loading categories", e)
                    toggleNoCategoriesView()
                }
        }
    }

    private fun toggleNoCategoriesView() {
        if (categoryList.isEmpty()) {
            binding.tvNoCategories.visibility = View.VISIBLE
            binding.categoriesRecyclerView.visibility = View.GONE
        } else {
            binding.tvNoCategories.visibility = View.GONE
            binding.categoriesRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

