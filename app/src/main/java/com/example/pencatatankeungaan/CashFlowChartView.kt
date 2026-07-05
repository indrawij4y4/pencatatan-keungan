package com.example.pencatatankeungaan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

class CashFlowChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var incomeData = listOf(30f, 45f, 35f, 60f, 50f, 75f, 90f)
    private var expenseData = listOf(20f, 25f, 40f, 30f, 45f, 35f, 50f)
    private val labels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

    // Animation progress (0f to 1f)
    private var animationProgress = 0f

    // Colors
    private val colorIncome = ContextCompat.getColor(context, R.color.income_green)
    private val colorExpense = ContextCompat.getColor(context, R.color.expense_red)
    private val colorBorder = ContextCompat.getColor(context, R.color.border_gray)
    private val colorText = ContextCompat.getColor(context, R.color.text_muted)

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBorder
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = dpToPx(11f)
        textAlign = Paint.Align.CENTER
    }

    private val incomeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorIncome
        strokeWidth = dpToPx(3f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val incomeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val expenseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorExpense
        strokeWidth = dpToPx(3f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val expenseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        startAnimation()
    }

    fun setData(incomes: List<Float>, expenses: List<Float>) {
        this.incomeData = incomes
        this.expenseData = expenses
        startAnimation()
    }

    private fun startAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = dpToPx(40f)
        val paddingRight = dpToPx(20f)
        val paddingTop = dpToPx(20f)
        val paddingBottom = dpToPx(30f)

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        // Calculate maximum value for scale
        val maxVal = (incomeData.maxOrNull() ?: 100f).coerceAtLeast(expenseData.maxOrNull() ?: 100f).coerceAtLeast(10f) * 1.1f

        // Draw horizontal grid lines & Y-axis labels
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = paddingTop + chartHeight - (chartHeight / gridCount * i)
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint)
            
            // Draw Y-axis text
            val labelVal = (maxVal / gridCount * i).toInt()
            val labelText = if (labelVal >= 1000) "${labelVal / 1000}k" else "$labelVal"
            canvas.drawText(labelText, paddingLeft - dpToPx(10f), y + dpToPx(3.5f), textPaint.apply { textAlign = Paint.Align.RIGHT })
        }

        // Draw X-axis labels
        val stepX = chartWidth / (incomeData.size - 1).coerceAtLeast(1)
        for (i in labels.indices) {
            val x = paddingLeft + i * stepX
            if (i < labels.size) {
                canvas.drawText(labels[i], x, height - dpToPx(8f), textPaint.apply { textAlign = Paint.Align.CENTER })
            }
        }

        // Draw Income curve & fill
        drawDataCurve(canvas, incomeData, maxVal, paddingLeft, paddingTop, chartWidth, chartHeight, stepX, colorIncome, incomeLinePaint, incomeFillPaint)

        // Draw Expense curve & fill
        drawDataCurve(canvas, expenseData, maxVal, paddingLeft, paddingTop, chartWidth, chartHeight, stepX, colorExpense, expenseLinePaint, expenseFillPaint)
    }

    private fun drawDataCurve(
        canvas: Canvas,
        data: List<Float>,
        maxVal: Float,
        paddingLeft: Float,
        paddingTop: Float,
        chartWidth: Float,
        chartHeight: Float,
        stepX: Float,
        baseColor: Int,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        if (data.isEmpty()) return

        val points = mutableListOf<PointF>()
        for (i in data.indices) {
            val x = paddingLeft + i * stepX
            val ratio = data[i] / maxVal
            val y = paddingTop + chartHeight - (chartHeight * ratio * animationProgress)
            points.add(PointF(x, y))
        }

        // Create line path
        val path = Path()
        path.moveTo(points[0].x, points[0].y)

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val controlX1 = p1.x + (p2.x - p1.x) / 2
            val controlY1 = p1.y
            val controlX2 = p1.x + (p2.x - p1.x) / 2
            val controlY2 = p2.y
            path.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
        }

        // Create fill path
        val fillPath = Path(path)
        fillPath.lineTo(points.last().x, paddingTop + chartHeight)
        fillPath.lineTo(points.first().x, paddingTop + chartHeight)
        fillPath.close()

        // Setup fill gradient
        val gradient = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            adjustAlpha(baseColor, 0.25f), adjustAlpha(baseColor, 0.0f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient

        // Draw fill first, then line
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // Draw circles on data points
        for (point in points) {
            pointPaint.color = Color.WHITE
            canvas.drawCircle(point.x, point.y, dpToPx(5f), pointPaint)
            pointPaint.color = baseColor
            canvas.drawCircle(point.x, point.y, dpToPx(3.5f), pointPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
