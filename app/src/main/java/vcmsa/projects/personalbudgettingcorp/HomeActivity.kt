package vcmsa.projects.personalbudgettingcorp

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.widget.Button
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityHomeBinding




class HomeActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: FirebaseDatabase

    private var totalBudget = 0.0 // Default to 0
    private var totalExpense = 0.0 // Default to 0
    private var selectedTimeFilter = "Monthly" // Default to Monthly to align with budget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        setupBottomNavigation()
        setupClickListeners()

        // Initial data load for the month
        applyExpenseFilter(selectedTimeFilter) // Loads data for the default filter (Monthly)
        fetchQuickStats() // For "Quick Stats" and "Recent Transactions"
    }

    private fun setupUI() {
        val currentUser = auth.currentUser
        binding.tvUsername.text = currentUser?.email?.substringBefore("@") ?: "User"
        updateTimeFilterButtons(selectedTimeFilter) // Sets initial state for filter buttons

        // hides parts of UI that depend on fetched data or show loading state
        binding.tvTotalBudget.text = "R0.00"
        binding.tvTotalExpense.text = "-R0.00"
        binding.tvRemainingBudget.text = "R0.00"
        binding.progressBudget.progress = 0
        binding.tvProgressPercentage.text = "0%"
        binding.tvBudgetStatus.text = "Loading budget data..."
    }

    private fun setupBottomNavigation() {
        // Inflates the menu for the BottomNavigationView
        binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu)
        binding.bottomNavigation.setOnNavigationItemSelectedListener(this)
        binding.bottomNavigation.selectedItemId = R.id.nav_home // Sets Home as selected
    }

    private fun setupClickListeners() {
        binding.btnDaily.setOnClickListener {
            updateTimeFilterButtons("Daily")
            selectedTimeFilter = "Daily"
            applyExpenseFilter(selectedTimeFilter)
        }
        binding.btnWeekly.setOnClickListener {
            updateTimeFilterButtons("Weekly")
            selectedTimeFilter = "Weekly"
            applyExpenseFilter(selectedTimeFilter)
        }
        binding.btnMonthly.setOnClickListener {
            updateTimeFilterButtons("Monthly")
            selectedTimeFilter = "Monthly"
            applyExpenseFilter(selectedTimeFilter)
        }

        binding.fabAddExpense.setOnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            val currentUserId = auth.currentUser?.uid // Gets the current user's ID

            if (currentUserId != null && currentUserId.isNotEmpty()) {
                intent.putExtra("USER_ID_KEY", currentUserId) // Adds the User ID to the Intent
                startActivity(intent)
            } else {
                Toast.makeText(this, "Error: User not identified. Please try logging in again.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        binding.ivProfile.setOnClickListener {
            showProfileOptions()
        }
    }

    private fun updateTimeFilterButtons(selectedFilter: String) {
        val buttons = listOf(binding.btnDaily, binding.btnWeekly, binding.btnMonthly)
        val filters = listOf("Daily", "Weekly", "Monthly")

        buttons.forEachIndexed { index, button ->
            if (filters[index] == selectedFilter) {
                button.background = ContextCompat.getDrawable(this, R.drawable.button_selected)
                button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                button.background = ContextCompat.getDrawable(this, R.drawable.button_unselected)
                button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateBudgetDisplay() {
        binding.tvTotalBudget.text = "R${String.format(Locale.US, "%.2f", totalBudget)}"
        binding.tvTotalExpense.text = "-R${String.format(Locale.US, "%.2f", totalExpense)}"
        val remaining = totalBudget - totalExpense
        binding.tvRemainingBudget.text = "R${String.format(Locale.US, "%.2f", remaining)}"

        val percentage = if (totalBudget > 0) ((totalExpense / totalBudget) * 100).toInt() else 0
        binding.progressBudget.progress = percentage.coerceIn(0, 100) // Ensures progress is within 0-100
        binding.tvProgressPercentage.text = "$percentage%"

        val statusMessage: String
        val statusColorRes: Int

        when {
            percentage <= 0 && totalBudget <= 0 -> { // When there is no budget set or no expenses
                statusMessage = "Set a budget to get started!"
                statusColorRes = R.color.white // neutral color
            }
            percentage <= 30 -> {
                statusMessage = "📊 $percentage% - Your spending looks good!"
                statusColorRes = R.color.success_color
            }
            percentage <= 70 -> {
                statusMessage = "⚠️ $percentage% - Watch your spending."
                statusColorRes = R.color.warning_color
            }
            percentage <= 100 -> {
                statusMessage = "❗ $percentage% - Nearing your budget limit!"
                statusColorRes = R.color.danger_color
            }
            else -> { // Over budget
                statusMessage = "🚨 Over Budget by ${percentage - 100}%!"
                statusColorRes = R.color.danger_color
            }
        }
        binding.tvBudgetStatus.text = statusMessage
        binding.tvBudgetStatus.setTextColor(ContextCompat.getColor(this, statusColorRes))
    }


    private fun applyExpenseFilter(filter: String) {
        val userId = auth.currentUser?.uid ?: return
        // binding.progressBarHome.visibility = View.VISIBLE

        db.collection("users").document(userId)
            .collection("settings").document("budget")
            .get()
            .addOnSuccessListener { budgetDocument ->
                totalBudget = budgetDocument.getDouble("monthlyGoal") ?: 0.0 // Updates totalBudget

                filterExpensesByRange(filter, userId) // Pass's userId
                    .addOnSuccessListener { expensesSnapshot ->
                        var filteredTotalExpense = 0.0
                        for (doc in expensesSnapshot.documents) {
                            val amount = doc.getDouble("amount") ?: 0.0
                            val category = doc.getString("category") ?: ""
                            if (category.lowercase() != "income") {
                                filteredTotalExpense += amount
                            }
                        }
                        totalExpense = filteredTotalExpense // Updates the main totalExpense
                        updateBudgetDisplay() // Refreshes UI elements for budget overview
                        // binding.progressBarHome.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error filtering expenses: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("HomeActivity", "Error filtering expenses", e)
                        // binding.progressBarHome.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching budget: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "Error fetching budget for filter", e)
                totalBudget = 0.0 // Reset's budget if fetch fails
                totalExpense = 0.0 // Reset's expenses
                updateBudgetDisplay() // Updates UI to reflect error/no data
                // binding.progressBarHome.visibility = View.GONE
            }
    }

    private fun filterExpensesByRange(range: String, userId: String): Task<QuerySnapshot> {
        val now = Calendar.getInstance()
        val startTime: Long

        when (range) {
            "Daily" -> {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startTime = startOfDay.timeInMillis
            }
            "Weekly" -> {
                val startOfWeek = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startTime = startOfWeek.timeInMillis
            }
            "Monthly" -> {
                val startOfMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startTime = startOfMonth.timeInMillis
            }
            else -> return Tasks.forException(IllegalArgumentException("Invalid range: $range"))
        }

        return db.collection("users")
            .document(userId)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", now.timeInMillis) // To cap at current time
            .get()
    }

    private fun fetchQuickStats() {
        val userId = auth.currentUser?.uid ?: return

        // For "Quick Stats Card"
        val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        db.collection("users").document(userId).collection("expenses")
            .whereEqualTo("category", "Transport")
            .whereGreaterThanOrEqualTo("timestamp", oneWeekAgo)
            .get()
            .addOnSuccessListener { documents ->
                var transportTotal = 0.0
                for (doc in documents) {
                    transportTotal += doc.getDouble("amount") ?: 0.0
                }
                // binding.tvTransportAmount.text = "R${String.format(Locale.US, "%.2f", transportTotal)}"
            }
            .addOnFailureListener { Log.e("HomeActivity", "Error fetching transport stats", it) }

        // For "Income Last Week" and "Food Last Week"
        binding.tvIncomeLastWeek.text = "R..."
        binding.tvFoodLastWeek.text = "R..."

        // For "Recent Transactions"
        // Fetches the latest 3-5 transactions.
        db.collection("users").document(userId).collection("expenses")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(3) // Gets latest 3 transactions
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Handle no transactions: show a message in the recent transactions area
                } else {
                    // Manually updates the static CardViews if there are transactions. IDs.
                    // if (documents.size() > 0) {
                    // val firstDoc = documents.documents[0].toObject(Expense::class.java)
                    // binding.tvTransaction1Category.text = firstDoc?.category
                    // binding.tvTransaction1Date.text = formatDate(firstDoc?.timestamp ?: 0L)
                    // binding.tvTransaction1Amount.text = "R${String.format(Locale.US, "%.2f", firstDoc?.amount)}"
                    // if (firstDoc?.amount ?: 0.0 >= 0) { // Assuming positive is income
                    // binding.tvTransaction1Amount.setTextColor(ContextCompat.getColor(this, R.color.income_color))
                    // } else {
                    // binding.tvTransaction1Amount.setTextColor(ContextCompat.getColor(this, R.color.expense_color))
                    // }
                    // }

                }
            }
            .addOnFailureListener { Log.e("HomeActivity", "Error fetching recent transactions", it) }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "N/A"
        val sdf = SimpleDateFormat("HH:mm • MMM dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                return true
            }
            R.id.nav_statistics -> {
                startActivity(Intent(this, StatisticsActivity::class.java))
                return true
            }
            R.id.nav_categories -> {
                startActivity(Intent(this, CategoriesActivity::class.java))
                return true
            }
            R.id.nav_expenses -> {
                startActivity(Intent(this, ExpensesActivity::class.java))
                return true
            }
            R.id.nav_profile -> {
                showProfileOptions()
                return true
            }
        }
        return false
    }

    private fun showProfileOptions() {
        val options = arrayOf("View Profile", "Settings", "Logout")
        AlertDialog.Builder(this)
            .setTitle("Profile Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ProfileActivity::class.java))
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                    2 -> logout()
                }
            }
            .show()
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity() // Finish's all activities in the task
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refreshes data when returning to HomeActivity
        // or changing budget settings.
        applyExpenseFilter(selectedTimeFilter)
        fetchQuickStats()
        // Ensures the correct bottom nav item is selected if user navigates back
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }
}