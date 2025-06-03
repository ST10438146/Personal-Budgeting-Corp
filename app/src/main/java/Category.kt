data class Category(
    val id: String = "",
    val name: String = "",
    val monthlyLimit: Double = 0.0,
    val iconResId: Int = 0
)
{
    constructor() : this("", 0.0.toString())
}