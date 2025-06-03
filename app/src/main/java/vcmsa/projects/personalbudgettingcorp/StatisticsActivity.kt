package vcmsa.projects.personalbudgettingcorp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityStatisticsBinding

class StatisticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupToolbar()
        loadStatistics()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Statistics"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadStatistics() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("expenses")
            .get()
            .addOnSuccessListener { documents ->
                var totalExpenses = 0.0
                var totalIncome = 0.0

                for (doc in documents) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    val category = doc.getString("category") ?: ""

                    if (category.lowercase() == "income") {
                        totalIncome += amount
                    } else {
                        totalExpenses += amount
                    }
                }

                val netSavings = totalIncome - totalExpenses



            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load stats: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}