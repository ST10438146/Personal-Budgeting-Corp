package vcmsa.projects.personalbudgettingcorp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializes Firebase Auth
        auth = FirebaseAuth.getInstance()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        binding.tvLogin.setOnClickListener {
            finish() // Goes back to login activity
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Validates input
        if (!validateInput(email, password, confirmPassword)) {
            return
        }

        // Shows progress bar
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false

        // Creates user with Firebase
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                if (task.isSuccessful) {
                    // Registration successful
                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    // Registration failed
                    val errorMessage = when (task.exception?.message) {
                        "The email address is already in use by another account." -> "Email already registered"
                        "The email address is badly formatted." -> "Invalid email format"
                        "The given password is invalid. [ Password should be at least 6 characters ]" -> "Password must be at least 6 characters"
                        "A network error (such as timeout, interrupted connection or unreachable host) has occurred." -> "Network error. Please check your connection"
                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            binding.etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return false
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            binding.etConfirmPassword.requestFocus()
            return false
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return false
        }

        return true
    }
}