package vcmsa.projects.personalbudgettingcorp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityAddExpenseBinding
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var userId: String
    private var imageUri: Uri? = null

    // ActivityResultLauncher for picking an image
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    // ActivityResultLauncher for requesting permission
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // val expense: String = "Default Value" // This was problematic and should be removed if not used correctly

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        userId = intent.getStringExtra("USER_ID_KEY") ?: ""
        if (userId.isEmpty()) {
            Log.e("AddExpenseActivity", "User ID not found in Intent extras")
            Toast.makeText(this, "Error: User ID missing. Cannot add expense.", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        initializePermissionLauncher()
        initializeImagePickerLauncher()
        setupClickListeners()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Add Expense"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(this, "Permission denied to read external storage.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    imageUri = uri
                    binding.ivReceiptPreview.setImageURI(imageUri)
                    binding.ivReceiptPreview.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectImage.setOnClickListener {
            checkPermissionAndOpenGallery()
        }

        binding.btnSaveExpense.setOnClickListener {
            saveExpense()
        }
    }

    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // Show an explanation to the user *asynchronously*
                // For simplicity here, we'll just request directly.
                // In a real app, show a dialog explaining why you need the permission.
                Toast.makeText(this, "Storage permission is needed to select images.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        // Or: Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        imagePickerLauncher.launch(intent)
    }

    private fun saveExpense() {
        val amountStr = binding.etAmount.text.toString().trim()
        val category = binding.etCategory.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (!validateInput(amountStr, category)) {
            return
        }
        val amount = amountStr.toDouble() // Validation ensures this is safe

        // Show progress indicator (e.g., ProgressBar)
        Toast.makeText(this, "Saving expense...", Toast.LENGTH_SHORT).show()
        binding.btnSaveExpense.isEnabled = false // Disable button to prevent multiple clicks

        if (imageUri != null) {
            uploadImageAndSaveExpense(amount, category, description, imageUri!!)
        } else {
            saveExpenseDataToFirestore(amount, category, description, null)
        }
    }

    private fun uploadImageAndSaveExpense(amount: Double, category: String, description: String, uri: Uri) {
        val fileName = "receipts/${UUID.randomUUID()}.jpg" // Unique filename
        val storageRef = storage.reference.child(fileName)

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveExpenseDataToFirestore(amount, category, description, downloadUrl.toString())
                }.addOnFailureListener { e ->
                    Log.e("AddExpenseActivity", "Failed to get download URL: ${e.message}", e)
                    Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnSaveExpense.isEnabled = true // Re-enable button
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddExpenseActivity", "Image upload failed: ${e.message}", e)
                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveExpense.isEnabled = true // Re-enable button
            }
            .addOnProgressListener { snapshot ->
                val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount)
                // Update progress UI if you have one
                Log.d("AddExpenseActivity", "Upload is $progress% done")
            }
    }


    private fun saveExpenseDataToFirestore(amount: Double, category: String, description: String, imageUrl: String?) {
        val expense = hashMapOf(
            "amount" to amount,
            "category" to category,
            "description" to description,
            "timestamp" to System.currentTimeMillis(),
            "imageUrl" to imageUrl // Can be null if no image
        )

        if (this::userId.isInitialized && userId.isNotEmpty()) {
            db.collection("users")
                .document(this.userId)
                .collection("expenses")
                .add(expense)
                .addOnSuccessListener {
                    Toast.makeText(this, "Expense saved successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Go back to the previous activity
                }
                .addOnFailureListener { e ->
                    Log.e("AddExpenseActivity", "Error saving expense to Firestore: ${e.message}", e)
                    Toast.makeText(this, "Error saving expense: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnSaveExpense.isEnabled = true // Re-enable button
                }

        }
        else {
            Toast.makeText(this, "Error: User ID is missing. Cannot save expense.", Toast.LENGTH_LONG).show()
            Log.e("AddExpenseActivity", "User ID was empty or not initialized during saveExpenseDataToFirestore.")
            binding.btnSaveExpense.isEnabled = true // Re-enable button
        }
    }

    private fun validateInput(amount: String, category: String): Boolean {
        if (amount.isEmpty()) {
            binding.etAmount.error = "Amount is required"
            return false
        }
        try {
            amount.toDouble()
        } catch (e: NumberFormatException) {
            binding.etAmount.error = "Invalid amount"
            return false
        }
        if (category.isEmpty()) {
            binding.etCategory.error = "Category is required"
            return false
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed() // More modern way
        return true
    }
}
