package vcmsa.projects.personalbudgettingcorp

import Expense
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import vcmsa.projects.personalbudgettingcorp.adapters.ExpensesAdapter
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityExpensesBinding
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ExpensesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpensesBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var expensesAdapter: ExpensesAdapter
    private val expenseList = mutableListOf<Expense>()
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in. Cannot view expenses.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        loadExpenses()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "All Expenses"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        expensesAdapter = ExpensesAdapter(expenseList)
        binding.recyclerExpenses.layoutManager = LinearLayoutManager(this)
        binding.recyclerExpenses.adapter = expensesAdapter

        expensesAdapter.onItemClick = { expense ->
            if (!expense.imageUrl.isNullOrEmpty()) {
                Toast.makeText(this, "Image URL: ${expense.imageUrl}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Clicked: ${expense.description}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadExpenses() {
        binding.progressBarExpenses.visibility = View.VISIBLE // Shows progress bar
        binding.tvNoExpenses.visibility = View.GONE       // Hides no expenses text
        binding.recyclerExpenses.visibility = View.GONE    // Hides RecyclerView

        userId?.let { uid ->
            db.collection("users").document(uid).collection("expenses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    expenseList.clear()
                    for (document in documents) {
                        val expense = document.toObject(Expense::class.java)
                        expenseList.add(expense)
                    }
                    expensesAdapter.updateExpenses(expenseList)
                    binding.progressBarExpenses.visibility = View.GONE // Hides progress bar
                    toggleNoExpensesView() // Updates visibility of RecyclerView and tvNoExpenses
                }
                .addOnFailureListener { e ->
                    binding.progressBarExpenses.visibility = View.GONE // Hide progress bar
                    binding.tvNoExpenses.text = "Error loading expenses: ${e.localizedMessage}"
                    binding.tvNoExpenses.visibility = View.VISIBLE // Shows error message
                    binding.recyclerExpenses.visibility = View.GONE
                    Toast.makeText(this, "Error loading expenses: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("ExpensesActivity", "Error loading expenses", e)
                }
        } ?: run {
            binding.progressBarExpenses.visibility = View.GONE
            binding.tvNoExpenses.text = "User ID not found. Cannot load expenses."
            binding.tvNoExpenses.visibility = View.VISIBLE
            binding.recyclerExpenses.visibility = View.GONE
        }
    }

    private fun toggleNoExpensesView() {
        if (expenseList.isEmpty()) {
                if (binding.tvNoExpenses.visibility == View.GONE) { // Only set "No expenses" if not already showing an error
                binding.tvNoExpenses.text = "No expenses recorded yet."
                binding.tvNoExpenses.visibility = View.VISIBLE
            }
            binding.recyclerExpenses.visibility = View.GONE
        } else {
            binding.tvNoExpenses.visibility = View.GONE
            binding.recyclerExpenses.visibility = View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
