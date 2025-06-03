import com.google.firebase.firestore.DocumentId

data class Expense(
    @DocumentId var id: String = "", // Firestore document ID
    var amount: Double = 0.0,
    var category: String = "",
    var description: String = "",
    var timestamp: Long = 0L,
    var imageUrl: String? = null // To store the URL of the receipt image
)