package vcmsa.projects.personalbudgettingcorp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityHomeBinding
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.FirebaseDatabaseKtxRegistrar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firestore.admin.v1.Index


class HomeActivity : AppCompatActivity(),
    BottomNavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: FirebaseDatabase


    // Sample data - replace with actual data from database
    private var totalBudget = 8223.00
    private var totalExpense = 1127.40
    private var selectedTimeFilter = "Daily"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        setupBottomNavigation()
        setupClickListeners()
        fetchAndDisplayBudgetData()
        updateBudgetDisplay()
    }

    private fun setupUI() {
        // Set username from Firebase user
        val currentUser = auth.currentUser
        binding.tvUsername.text = currentUser?.email?.substringBefore("@") ?: "Budget Master"

        // Initialize time filter buttons
        updateTimeFilterButtons("Daily")
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnNavigationItemSelectedListener(this)
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun setupClickListeners() {
        // Time filter buttons
        binding.btnDaily.setOnClickListener {
            updateTimeFilterButtons("Daily")
            selectedTimeFilter = "Daily"
            // TODO: Refresh data based on daily filter
        }

        binding.btnWeekly.setOnClickListener {
            updateTimeFilterButtons("Weekly")
            selectedTimeFilter = "Weekly"
            // TODO: Refresh data based on weekly filter
        }

        binding.btnMonthly.setOnClickListener {
            updateTimeFilterButtons("Monthly")
            selectedTimeFilter = "Monthly"
            // TODO: Refresh data based on monthly filter
        }

        // Floating Action Button
        binding.fabAddExpense.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }

        // Profile click
        binding.ivProfile.setOnClickListener {
            showProfileOptions()
        }
    }

    private fun updateTimeFilterButtons(selectedFilter: String) {
        // Reset all buttons
        binding.btnDaily.background = ContextCompat.getDrawable(this, R.drawable.button_unselected)
        binding.btnWeekly.background = ContextCompat.getDrawable(this, R.drawable.button_unselected)
        binding.btnMonthly.background = ContextCompat.getDrawable(this, R.drawable.button_unselected)

        binding.btnDaily.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        binding.btnWeekly.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        binding.btnMonthly.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        // Set selected button
        when (selectedFilter) {
            "Daily" -> {
                binding.btnDaily.background = ContextCompat.getDrawable(this, R.drawable.button_selected)
                binding.btnDaily.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
            "Weekly" -> {
                binding.btnWeekly.background = ContextCompat.getDrawable(this, R.drawable.button_selected)
                binding.btnWeekly.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
            "Monthly" -> {
                binding.btnMonthly.background = ContextCompat.getDrawable(this, R.drawable.button_selected)
                binding.btnMonthly.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
        }
    }

    private fun updateBudgetDisplay() {
        // Update budget information
        binding.tvTotalBudget.text = "R${String.format("%.2f", totalBudget)}"
        binding.tvTotalExpense.text = "-R${String.format("%.2f", totalExpense)}"

        val remaining = totalBudget - totalExpense
        binding.tvRemainingBudget.text = "R${String.format("%.2f", remaining)}"

        val percentage = ((totalExpense / totalBudget) * 100).toInt()
        binding.tvProgressPercentage.text = "$percentage%"
        binding.progressBudget.progress = percentage

        // Update status message based on spending
        val statusMessage = when {
            percentage <= 30 -> "📊 $percentage% Of Your Expenses Looks Good"
            percentage <= 70 -> "⚠️ $percentage% Watch Your Spending"
            else -> "🚨 $percentage% Over Budget Alert!"
        }
        binding.tvBudgetStatus.text = statusMessage

        val statusColor = when {
            percentage <= 30 -> R.color.success_color
            percentage <= 70 -> R.color.warning_color
            else -> R.color.danger_color
        }
        binding.tvBudgetStatus.setTextColor(ContextCompat.getColor(this, statusColor))
    }
    private fun showSetBudgetDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        AlertDialog.Builder(this)
            .setTitle("Set Budget Goal")
            .setMessage("Enter your total monthly budget")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val budget = input.text.toString().toDoubleOrNull()
                if (budget != null) {
                    saveBudgetToFirestore(budget)
                } else {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveBudgetToFirestore(budget: Double) {
        val userId = auth.currentUser?.uid ?: return
        val data = hashMapOf("monthlyGoal" to budget)
        db.collection("users").document(userId).collection("settings")
            .document("budget")
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Budget set successfully!", Toast.LENGTH_SHORT).show()
                fetchAndDisplayBudgetData() // refresh view
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save budget.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Already on home
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
    private fun fetchAndDisplayBudgetData() {
        val userId = auth.currentUser?.uid ?: return

        // Fetch budget first
        db.collection("users").document(userId)
            .collection("settings").document("budget")
            .get()
            .addOnSuccessListener { document ->
                val budget = document.getDouble("monthlyGoal") ?: 0.0

                // Now fetch expenses
                db.collection("users").document(userId)
                    .collection("expenses")
                    .get()
                    .addOnSuccessListener { expensesDocs ->
                        var totalExpense = 0.0

                        for (doc in expensesDocs) {
                            val amount = doc.getDouble("amount") ?: 0.0
                            val category = doc.getString("category") ?: ""
                            if (category.lowercase() != "income") {
                                totalExpense += amount
                            }
                        }

                        val remaining = budget - totalExpense
                        val percentage = if (budget != 0.0) ((totalExpense / budget) * 100).toInt() else 0

                        // Update UI (same as before)
                        binding.tvTotalBudget.text = "R%.2f".format(budget)
                        binding.tvTotalExpense.text = "-R%.2f".format(totalExpense)
                        binding.tvRemainingBudget.text = "R%.2f".format(remaining)
                        binding.tvProgressPercentage.text = "$percentage%"
                        binding.progressBudget.progress = percentage

                        val statusMessage = when {
                            percentage <= 30 -> "      $percentage% Of Your Expenses Looks Good"
                            percentage <= 70 -> "    $percentage% Watch Your Spending"
                            else -> "       $percentage% Over Budget Alert!"
                        }

                        val statusColor = when {
                            percentage <= 30 -> R.color.success_color
                            percentage <= 70 -> R.color.warning_color
                            else -> R.color.danger_color
                        }

                        binding.tvBudgetStatus.text = statusMessage
                        binding.tvBudgetStatus.setTextColor(ContextCompat.getColor(this, statusColor))
                    }
            }
    }
    private fun filterExpensesByRange(range: String): Task<QuerySnapshot> {
        val userId = auth.currentUser?.uid ?: return Tasks.forResult(null)
        val now = System.currentTimeMillis()

        val startTime = when (range) {
            "Daily" -> now - 86400000 // 1 day
            "Weekly" -> now - 7 * 86400000 // 7 days
            "Monthly" -> now - 30L * 86400000 // 30 days
            else -> 0L
        }

        return db.collection("users")
            .document(userId)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .get()
    }


    private fun showProfileOptions() {
        val options = arrayOf("View Profile", "Settings", "Logout")

        AlertDialog.Builder(this)
            .setTitle("Profile Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // TODO: Navigate to ProfileActivity
                        // startActivity(Intent(this, ProfileActivity::class.java))
                    }
                    1 -> {
                        // TODO: Navigate to SettingsActivity
                        // startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    2 -> {
                        logout()
                    }
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
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }
}