data class Budget(
    val userId: String = "",
    val monthlyGoal: Double = 0.0,
    val categories: Map<String, Double> = mapOf() // categoryId to limit
)
