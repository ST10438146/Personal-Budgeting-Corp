package vcmsa.projects.personalbudgettingcorp

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser

        setupToolbar()
        displayUserInfo()
        setupClickListeners()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun displayUserInfo() {
        if (currentUser != null) {
            binding.tvProfileEmail.text = "Email: ${currentUser!!.email}"
        } else {
            binding.tvProfileEmail.text = "Email: Not available"
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            finish() // Or redirect to login
        }
    }

    private fun setupClickListeners() {
        binding.btnChangePassword.setOnClickListener {
            handleChangePassword()
        }
    }

    private fun handleChangePassword() {
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmNewPassword = binding.etConfirmNewPassword.text.toString().trim()

        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "New password is required"
            binding.etNewPassword.requestFocus()
            return
        }
        if (newPassword.length < 6) {
            binding.etNewPassword.error = "Password must be at least 6 characters"
            binding.etNewPassword.requestFocus()
            return
        }
        if (confirmNewPassword.isEmpty()) {
            binding.etConfirmNewPassword.error = "Please confirm your new password"
            binding.etConfirmNewPassword.requestFocus()
            return
        }
        if (newPassword != confirmNewPassword) {
            binding.etConfirmNewPassword.error = "Passwords do not match"
            binding.etConfirmNewPassword.requestFocus()
            return
        }

        binding.progressBarProfile.visibility = View.VISIBLE
        binding.btnChangePassword.isEnabled = false

        currentUser?.updatePassword(newPassword)
            ?.addOnCompleteListener { task ->
                binding.progressBarProfile.visibility = View.GONE
                binding.btnChangePassword.isEnabled = true
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    binding.etNewPassword.text?.clear()
                    binding.etConfirmNewPassword.text?.clear()
                } else {
                    Log.e("ProfileActivity", "Password update failed", task.exception)
                    // Handle specific errors, e.g., re-authentication needed
                    if (task.exception?.message?.contains("RECENT_LOGIN_REQUIRED") == true) {
                        Toast.makeText(this, "Re-authentication required to change password. Please log out and log back in.", Toast.LENGTH_LONG).show()
                        // Ideally, you would prompt for current password and re-authenticate.
                        // For simplicity here, we ask user to re-login.
                    } else {
                        Toast.makeText(this, "Failed to update password: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}