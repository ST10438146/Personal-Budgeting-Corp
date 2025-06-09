package vcmsa.projects.personalbudgettingcorp

import BudgetGoals
import ExpenseItem
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import vcmsa.projects.personalbudgettingcorp.databinding.ActivityStatisticsBinding




class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userId: String? = null

    private lateinit var barChart: BarChart
    private lateinit var tvSelectedPeriod: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoData: TextView

    private var currentPeriodType: String = "Monthly" // Default period
    private var currentMinGoal: Double? = null
    private var currentMaxGoal: Double? = null

    companion object {
        const val TAG = "StatisticsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        initializeUI()
        setupPeriodSelectionListeners()

        // Loads data for the default period
        selectPeriod(currentPeriodType)
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Spending Statistics"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeUI() {
        barChart = binding.barChartSpending
        tvSelectedPeriod = binding.tvSelectedPeriod
        progressBar = binding.progressBarStatistics
        tvNoData = binding.tvNoData
        // Initial chart setup
        setupInitialChartAppearance()
    }

    private fun setupInitialChartAppearance() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)
        barChart.animateY(1000)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        val leftAxis = barChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.setDrawGridLines(true)

        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = true
    }


    private fun setupPeriodSelectionListeners() {
        binding.btnWeekly.setOnClickListener { selectPeriod("Weekly") }
        binding.btnMonthly.setOnClickListener { selectPeriod("Monthly") }
        binding.btnYearly.setOnClickListener { selectPeriod("Yearly") }
    }

    private fun selectPeriod(periodType: String) {
        currentPeriodType = periodType
        val (startDate, endDate, periodName) = getPeriodTimestamps(periodType)
        tvSelectedPeriod.text = "Period: $periodName"
        Log.d(TAG, "Selected period: $periodName, Start: ${Date(startDate)}, End: ${Date(endDate)}")
        loadChartData(startDate, endDate)
    }

    private fun getPeriodTimestamps(periodType: String): Triple<Long, Long, String> {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        var periodName: String

        when (periodType) {
            "Weekly" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                val startOfWeekDate = sdf.format(calendar.time)

                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis
                val endOfWeekDate = sdf.format(calendar.time)
                periodName = "This Week ($startOfWeekDate - $endOfWeekDate)"
                return Triple(startDate, endDate, periodName)
            }
            "Monthly" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                val startOfMonthDate = sdf.format(calendar.time)

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis
                val endOfMonthDate = sdf.format(calendar.time)
                periodName = "${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startDate)} ($startOfMonthDate - $endOfMonthDate)"
                return Triple(startDate, endDate, periodName)
            }
            "Yearly" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                val startOfYearDate = sdf.format(calendar.time)

                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis
                val endOfYearDate = sdf.format(calendar.time)
                periodName = "This Year (${calendar.get(Calendar.YEAR)}) ($startOfYearDate - $endOfYearDate)"
                return Triple(startDate, endDate, periodName)
            }
            else -> { // Default to monthly
                Log.w(TAG, "Unknown period type $periodType, defaulting to Monthly")
                return getPeriodTimestamps("Monthly")
            }
        }
    }


    private fun loadChartData(startDate: Long, endDate: Long) {
        progressBar.visibility = View.VISIBLE
        barChart.visibility = View.GONE
        tvNoData.visibility = View.GONE
        barChart.clear() // Clears previous data and limit lines

        // Fetches goals first
        fetchBudgetGoals { goals ->
            currentMinGoal = goals?.minGoal
            currentMaxGoal = goals?.maxGoal
            Log.d(TAG, "Fetched goals: Min = $currentMinGoal, Max = $currentMaxGoal")

            // Then fetches expenses
            fetchExpensesForPeriod(startDate, endDate) { expenses ->
                progressBar.visibility = View.GONE
                if (expenses.isEmpty()) {
                    tvNoData.visibility = View.VISIBLE
                    barChart.visibility = View.GONE
                    Log.d(TAG, "No expenses found for the period.")
                } else {
                    tvNoData.visibility = View.GONE
                    barChart.visibility = View.VISIBLE
                    Log.d(TAG, "Fetched ${expenses.size} expenses. Processing for chart.")
                    processAndDisplayChart(expenses)
                }
            }
        }
    }

    private fun fetchBudgetGoals(completion: (BudgetGoals?) -> Unit) {
        if (userId == null) {
            completion(null)
            return
        }
             db.collection("users").document(userId!!)
            .collection("settings").document("budget")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minGoal = document.getDouble("minSpendingGoal")
                    val maxGoal = document.getDouble("maxSpendingGoal")
                    completion(BudgetGoals(minGoal, maxGoal))
                } else {
                    Log.d(TAG, "Budget goals document does not exist.")
                    completion(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching budget goals", e)
                Toast.makeText(this, "Failed to load goals: ${e.message}", Toast.LENGTH_SHORT).show()
                completion(null)
            }
    }


    private fun fetchExpensesForPeriod(startDate: Long, endDate: Long, completion: (List<ExpenseItem>) -> Unit) {
        if (userId == null) {
            completion(emptyList())
            return
        }
        db.collection("users").document(userId!!)
            .collection("expenses")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val expenseList = mutableListOf<ExpenseItem>()
                for (document in documents) {
                    try {
                        val category = document.getString("category") ?: "Unknown"
                        // Ensures amount is treated as positive for spending calculation
                        val amount = document.getDouble("amount")?.let { Math.abs(it) } ?: 0.0
                        val timestamp = document.getLong("timestamp") ?: 0L

                        // Filters out income if "category" field is used for that
                        if (category.equals("income", ignoreCase = true)) {
                            // Skip's income items for spending chart
                            continue
                        }
                        expenseList.add(ExpenseItem(category, amount, timestamp))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing expense document ${document.id}", e)
                    }
                }
                completion(expenseList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching expenses", e)
                Toast.makeText(this, "Failed to load expenses: ${e.message}", Toast.LENGTH_SHORT).show()
                completion(emptyList())
            }
    }

    private fun processAndDisplayChart(expenses: List<ExpenseItem>) {
        // Group's expenses by category and sum amounts
        val spendingPerCategory = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList() // Convert's to list of pairs for ordered iteration

        if (spendingPerCategory.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
            barChart.visibility = View.GONE
            return
        }

        val barEntries = ArrayList<BarEntry>()
        val categoryLabels = ArrayList<String>()

        spendingPerCategory.forEachIndexed { index, (category, totalAmount) ->
            barEntries.add(BarEntry(index.toFloat(), totalAmount.toFloat()))
            categoryLabels.add(category)
        }

        val barDataSet = BarDataSet(barEntries, "Spending per Category")
        // Use's a predefined list of colors or generate them
        barDataSet.colors = com.github.mikephil.charting.utils.ColorTemplate.MATERIAL_COLORS.toList()
        barDataSet.valueTextSize = 10f

        val barData = BarData(barDataSet)
        barData.barWidth = 0.6f

        barChart.data = barData

        // Setup's X-axis labels (categories)
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(categoryLabels)
        xAxis.labelCount = categoryLabels.size
        xAxis.setLabelRotationAngle(-45f) // Rotates labels if they overlap

        // Removes previous limit lines before adding new ones
        barChart.axisLeft.removeAllLimitLines()

        // Adds Min/Max Goal LimitLines to Y-axis (axisLeft)
        currentMinGoal?.let { min ->
            val minLimitLine = LimitLine(min.toFloat(), "Min Goal: ${"%.2f".format(min)}")
            minLimitLine.lineWidth = 2f
            minLimitLine.enableDashedLine(10f, 10f, 0f)
            minLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            minLimitLine.textSize = 10f
            minLimitLine.lineColor = Color.GREEN // Chooses appropriate color
            barChart.axisLeft.addLimitLine(minLimitLine)
        }

        currentMaxGoal?.let { max ->
            val maxLimitLine = LimitLine(max.toFloat(), "Max Goal: ${"%.2f".format(max)}")
            maxLimitLine.lineWidth = 2f
            maxLimitLine.enableDashedLine(10f, 10f, 0f)
            maxLimitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            maxLimitLine.textSize = 10f
            maxLimitLine.lineColor = Color.RED // Chooses appropriate color
            barChart.axisLeft.addLimitLine(maxLimitLine)
        }

        // Ensures the Y-axis accommodates the data and limit lines
        var yAxisMax = spendingPerCategory.maxOfOrNull { it.second }?.toFloat() ?: 0f
        currentMaxGoal?.let { yAxisMax = kotlin.math.max(yAxisMax, it.toFloat()) }
        barChart.axisLeft.axisMaximum = yAxisMax * 1.1f // Add some padding

        barChart.invalidate() // Refreshes the chart
        barChart.animateY(1000)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}