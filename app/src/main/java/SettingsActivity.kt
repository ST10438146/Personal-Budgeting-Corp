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
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.SetOptions
import vcmsa.projects.personalbudgettingcorp.databinding.DialogSetSpendingGoalsBinding // For the custom dialog
import vcmsa.projects.personalbudgettingcorp.databinding.ActivitySettingsBinding



class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userId: String? = null

    private lateinit var tvCurrentMinGoal: TextView
    private lateinit var tvCurrentMaxGoal: TextView


    companion object {
        const val TAG = "SettingsActivity"
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
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in. Cannot access settings.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()

        // Initializes UI elements from binding
        tvCurrentMinGoal = binding.tvCurrentMinGoal
        tvCurrentMaxGoal = binding.tvCurrentMaxGoal

        loadAndDisplaySettings()
        setupClickListeners()
        setupCurrencySpinner()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadAndDisplaySettings() {
        // Loads SharedPreferences settings
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
        binding.switchNotifications.isChecked = notificationsEnabled

        // Loads currency from SharedPreferences
        // Loads spending goals from Firestore
        userId?.let { uid ->
            db.collection("users").document(uid).collection("settings").document("budget")
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val minGoal = document.getDouble("minSpendingGoal")
                        val maxGoal = document.getDouble("maxSpendingGoal") // This was likely your 'monthlyGoal'

                        tvCurrentMinGoal.text = "Current Min Goal: ${minGoal?.let { "R%.2f".format(it) } ?: "Not set"}"
                        tvCurrentMaxGoal.text = "Current Max Goal: ${maxGoal?.let { "R%.2f".format(it) } ?: "Not set"}"
                    } else {
                        tvCurrentMinGoal.text = "Current Min Goal: Not set"
                        tvCurrentMaxGoal.text = "Current Max Goal: Not set"
                        Log.d(TAG, "Budget document does not exist.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading spending goals", e)
                    tvCurrentMinGoal.text = "Current Min Goal: Error"
                    tvCurrentMaxGoal.text = "Current Max Goal: Error"
                }
        }
    }

    private fun saveSharedPreferencesSettings() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, binding.switchNotifications.isChecked)
            putString(KEY_SELECTED_CURRENCY, binding.spinnerCurrency.selectedItem.toString())
            apply()
        }
        Toast.makeText(this, "Preferences saved", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, _ ->
            saveSharedPreferencesSettings()
        }

        binding.btnSetSpendingGoals.setOnClickListener {
            showSetSpendingGoalsDialog()
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

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCurrency = sharedPreferences.getString(KEY_SELECTED_CURRENCY, "ZAR (South African Rand)") // Default
        val currencyArray = resources.getStringArray(R.array.currency_options)
        val position = currencyArray.indexOf(savedCurrency)
        if (position >= 0) {
            binding.spinnerCurrency.setSelection(position)
        }

    }

    private fun showSetSpendingGoalsDialog() {
        if (userId == null) {
            Toast.makeText(this, "You must be logged in to set goals.", Toast.LENGTH_SHORT).show()
            return
        }

        // Inflates the custom dialog layout using ViewBinding
        val dialogBinding = DialogSetSpendingGoalsBinding.inflate(LayoutInflater.from(this))
        val etMinGoal = dialogBinding.etMinSpendingGoal
        val etMaxGoal = dialogBinding.etMaxSpendingGoal

        // Pre-fills with current values from Firestore
        db.collection("users").document(userId!!).collection("settings").document("budget")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    document.getDouble("minSpendingGoal")?.let { etMinGoal.setText(it.toString()) }
                    document.getDouble("maxSpendingGoal")?.let { etMaxGoal.setText(it.toString()) }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to pre-fill goals in dialog", e)
            }

        AlertDialog.Builder(this)
            .setTitle("Set Monthly Spending Goals")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val minGoalStr = etMinGoal.text.toString().trim()
                val maxGoalStr = etMaxGoal.text.toString().trim()

                val minGoal = if (minGoalStr.isNotEmpty()) minGoalStr.toDoubleOrNull() else null
                val maxGoal = if (maxGoalStr.isNotEmpty()) maxGoalStr.toDoubleOrNull() else null

                var isValid = true
                if (maxGoalStr.isEmpty()) {
                    dialogBinding.tilMaxSpendingGoal.error = "Max goal (budget) is required"
                    isValid = false
                } else if (maxGoal == null || maxGoal < 0) {
                    dialogBinding.tilMaxSpendingGoal.error = "Invalid max goal amount"
                    isValid = false
                } else {
                    dialogBinding.tilMaxSpendingGoal.error = null
                }


                if (minGoalStr.isNotEmpty() && (minGoal == null || minGoal < 0)) {
                    dialogBinding.tilMinSpendingGoal.error = "Invalid min goal amount"
                    isValid = false
                } else {
                    dialogBinding.tilMinSpendingGoal.error = null
                }


                if (minGoal != null && maxGoal != null && minGoal > maxGoal) {
                    dialogBinding.tilMinSpendingGoal.error = "Min goal cannot exceed max goal"
                    isValid = false
                }


                if (isValid && maxGoal != null) {
                    saveSpendingGoalsToFirestore(minGoal, maxGoal, userId!!)
                } else if (isValid && maxGoalStr.isEmpty()){
                    Toast.makeText(this, "Max spending goal (budget) is required.", Toast.LENGTH_LONG).show()
                } else if (!isValid) {
                    Toast.makeText(this, "Please correct the errors.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun saveSpendingGoalsToFirestore(minGoal: Double?, maxGoal: Double, uid: String) {
        val goalsData = hashMapOf<String, Any>()
        goalsData["maxSpendingGoal"] = maxGoal

        db.collection("users").document(uid).collection("settings").document("budget")
            .set(goalsData, SetOptions.merge()) // Use merge to update only these fields
            .addOnSuccessListener {
                Toast.makeText(this, "Spending goals saved!", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Spending goals successfully written!")
                loadAndDisplaySettings() // Refreshes displayed goals
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save goals: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error writing spending goals", e)
            }
    }

    private fun null_to_delete_field_hack(): Any? {
        return null // Explicitly setting to null.
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
        saveSharedPreferencesSettings() // Saves SharedPreferences settings when activity is paused
    }
}