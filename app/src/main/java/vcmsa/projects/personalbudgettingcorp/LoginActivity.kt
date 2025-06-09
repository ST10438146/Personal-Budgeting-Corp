package vcmsa.projects.personalbudgettingcorp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializes Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Checks if user is already logged in
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            loginUser()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Validates input
        if (!validateInput(email, password)) {
            return
        }

        // Shows progress bar
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        // Signs in with Firebase
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                if (task.isSuccessful) {
                    // Login successful
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    // Login failed
                    val errorMessage = when (task.exception?.message) {
                        "The email address is badly formatted." -> "Invalid email format"
                        "The password is invalid or the user does not have a password." -> "Invalid password"
                        "There is no user record corresponding to this identifier. The user may have been deleted." -> "No account found with this email"
                        "A network error (such as timeout, interrupted connection or unreachable host) has occurred." -> "Network error. Please check your connection"
                        else -> "Login failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
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

        return true
    }
}