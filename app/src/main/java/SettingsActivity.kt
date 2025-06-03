package vcmsa.projects.personalbudgettingcorp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.personalbudgettingcorp.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    companion object {
        const val PREFS_NAME = "BudgetAppPrefs"
        const val KEY_NOTIFICATIONS_ENABLED = "notificationsEnabled"
        const val KEY_SELECTED_CURRENCY = "selectedCurrency"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupToolbar()
        loadSettings()
        setupClickListeners()
        setupCurrencySpinner()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        binding.switchNotifications.isChecked = notificationsEnabled

        val selectedCurrency = sharedPreferences.getString(KEY_SELECTED_CURRENCY, "ZAR (South African Rand)")
        // Set spinner selection based on loaded currency - see setupCurrencySpinner
    }

    private fun saveSettings() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, binding.switchNotifications.isChecked)
            putString(KEY_SELECTED_CURRENCY, binding.spinnerCurrency.selectedItem.toString())
            apply()
        }
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveSettings() // Save immediately on change or have a "Save" button
        }
        binding.btnSetMonthlyBudget.setOnClickListener {
            showSetBudgetDialog()
        }
    }

    private fun setupCurrencySpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.currency_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCurrency.adapter = adapter
        }

        // Load and set saved currency preference
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCurrency = sharedPreferences.getString(KEY_SELECTED_CURRENCY, "ZAR (South African Rand)")
        val currencyArray = resources.getStringArray(R.array.currency_options)
        val position = currencyArray.indexOf(savedCurrency)
        if (position >= 0) {
            binding.spinnerCurrency.setSelection(position)
        }

        // Consider saving when an item is selected or via a general "Save" button
        // For simplicity, current save is tied to notification switch or can be manual
    }


    private fun showSetBudgetDialog() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You must be logged in to set a budget.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        // Optionally, load current budget to prefill
        db.collection("users").document(userId).collection("settings").document("budget")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val currentGoal = document.getDouble("monthlyGoal")
                    input.setText(currentGoal?.toString() ?: "")
                }
            }

        AlertDialog.Builder(this)
            .setTitle("Set Monthly Budget Goal")
            .setMessage("Enter your total monthly budget")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val budgetStr = input.text.toString()
                val budget = budgetStr.toDoubleOrNull()
                if (budget != null && budget >= 0) {
                    saveBudgetToFirestore(budget, userId)
                } else {
                    Toast.makeText(this, "Invalid budget amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveBudgetToFirestore(budget: Double, userId: String) {
        val budgetData = hashMapOf("monthlyGoal" to budget)
        db.collection("users").document(userId).collection("settings").document("budget")
            .set(budgetData) // Use set to create or overwrite
            .addOnSuccessListener {
                Toast.makeText(this, "Monthly budget saved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save budget: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("SettingsActivity", "Error saving budget", e)
            }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        saveSettings() // Save settings when activity is paused
    }
}