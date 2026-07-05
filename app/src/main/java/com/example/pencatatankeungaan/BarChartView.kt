package com.example.pencatatankeungaan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var incomeData = listOf(1500000f, 2000000f, 1800000f, 1500000f)
    private var expenseData = listOf(800000f, 1200000f, 600000f, 950000f)
    private val labels = listOf("Mng 1", "Mng 2", "Mng 3", "Mng 4")

    private var animationProgress = 0f

    // Interactivity state
    private var selectedGroupIndex: Int = -1
    private var selectedBarType: Int = -1 // 1 = Income, 2 = Expense

    private val colorIncome = Color.parseColor("#10B981") // Emerald Green
    private val colorExpense = Color.parseColor("#F43F5E") // Rose Red
    private val colorText = Color.parseColor("#6B7280")
    private val colorGrid = Color.parseColor("#E2E8F0")

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = dpToPx(11f)
        textAlign = Paint.Align.CENTER
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E1E1E")
        strokeWidth = dpToPx(1.5f)
    }

    // Tooltip Paints
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
        textSize = dpToPx(10f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val tooltipShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
        style = Paint.Style.FILL
    }

    init {
        startAnimation()
    }

    fun setData(incomes: List<Float>, expenses: List<Float>) {
        this.incomeData = incomes
        this.expenseData = expenses
        this.selectedGroupIndex = -1
        this.selectedBarType = -1
        startAnimation()
    }

    private fun startAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val paddingLeft = dpToPx(48f)
            val paddingRight = dpToPx(16f)
            val paddingTop = dpToPx(20f)
            val paddingBottom = dpToPx(30f)

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            if (chartWidth <= 0 || chartHeight <= 0) return super.onTouchEvent(event)

            val maxVal = (incomeData.maxOrNull() ?: 100f).coerceAtLeast(expenseData.maxOrNull() ?: 100f).coerceAtLeast(10f) * 1.1f

            val groupCount = incomeData.size
            val groupWidth = chartWidth / groupCount
            val barWidth = dpToPx(12f)
            val gap = dpToPx(2f)

            val tx = event.x
            val ty = event.y

            var clickedGroup = -1
            var clickedBarType = -1 // 1 for Income, 2 for Expense

            for (i in 0 until groupCount) {
                val groupCenterX = paddingLeft + (i * groupWidth) + (groupWidth / 2)

                // Income coordinates
                val leftInc = groupCenterX - barWidth - gap
                val rightInc = groupCenterX - gap
                val incomeVal = incomeData.getOrElse(i) { 0f }
                val topInc = paddingTop + chartHeight - (chartHeight * (incomeVal / maxVal))

                // Expense coordinates
                val leftExp = groupCenterX + gap
                val rightExp = groupCenterX + barWidth + gap
                val expenseVal = expenseData.getOrElse(i) { 0f }
                val topExp = paddingTop + chartHeight - (chartHeight * (expenseVal / maxVal))

                // Check tap with vertical margin tolerance
                if (tx in leftInc..rightInc && ty >= topInc - dpToPx(15f) && ty <= paddingTop + chartHeight + dpToPx(10f)) {
                    clickedGroup = i
                    clickedBarType = 1
                    break
                }
                if (tx in leftExp..rightExp && ty >= topExp - dpToPx(15f) && ty <= paddingTop + chartHeight + dpToPx(10f)) {
                    clickedGroup = i
                    clickedBarType = 2
                    break
                }
            }

            if (clickedGroup != -1) {
                if (selectedGroupIndex == clickedGroup && selectedBarType == clickedBarType) {
                    // Deselect if clicked again
                    selectedGroupIndex = -1
                    selectedBarType = -1
                } else {
                    selectedGroupIndex = clickedGroup
                    selectedBarType = clickedBarType
                }
                invalidate()
                return true
            } else {
                // Tapped outside any bar: reset selection
                selectedGroupIndex = -1
                selectedBarType = -1
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = dpToPx(48f)
        val paddingRight = dpToPx(16f)
        val paddingTop = dpToPx(20f)
        val paddingBottom = dpToPx(30f)

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        val maxVal = (incomeData.maxOrNull() ?: 100f).coerceAtLeast(expenseData.maxOrNull() ?: 100f).coerceAtLeast(10f) * 1.1f

        // Draw horizontal grid lines & Y-axis labels
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = paddingTop + chartHeight - (chartHeight / gridCount * i)
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint)

            val labelVal = (maxVal / gridCount * i).toInt()
            val labelText = when {
                labelVal >= 1000000 -> String.format(Locale.US, "%.1fjt", labelVal / 1000000f)
                labelVal >= 1000 -> "${labelVal / 1000}rb"
                else -> "$labelVal"
            }
            canvas.drawText(labelText, paddingLeft - dpToPx(8f), y + dpToPx(4f), textPaint.apply { textAlign = Paint.Align.RIGHT })
        }

        // Draw bars
        val groupCount = incomeData.size
        val groupWidth = chartWidth / groupCount
        val barWidth = dpToPx(12f)
        val gap = dpToPx(2f)

        for (i in 0 until groupCount) {
            val groupCenterX = paddingLeft + (i * groupWidth) + (groupWidth / 2)

            // Income bar
            val incomeVal = incomeData.getOrElse(i) { 0f }
            val incomeBarHeight = (chartHeight * (incomeVal / maxVal)) * animationProgress
            val leftInc = groupCenterX - barWidth - gap
            val topInc = paddingTop + chartHeight - incomeBarHeight
            val rightInc = groupCenterX - gap
            val bottomInc = paddingTop + chartHeight

            barPaint.color = colorIncome
            val incRect = RectF(leftInc, topInc, rightInc, bottomInc)
            canvas.drawRoundRect(incRect, dpToPx(4f), dpToPx(4f), barPaint)
            
            // Draw border: thicker if selected
            val isIncomeSelected = (selectedGroupIndex == i && selectedBarType == 1)
            borderPaint.strokeWidth = if (isIncomeSelected) dpToPx(3.2f) else dpToPx(1.5f)
            canvas.drawRoundRect(incRect, dpToPx(4f), dpToPx(4f), borderPaint)

            // Expense bar
            val expenseVal = expenseData.getOrElse(i) { 0f }
            val expenseBarHeight = (chartHeight * (expenseVal / maxVal)) * animationProgress
            val leftExp = groupCenterX + gap
            val topExp = paddingTop + chartHeight - expenseBarHeight
            val rightExp = groupCenterX + barWidth + gap
            val bottomExp = paddingTop + chartHeight

            barPaint.color = colorExpense
            val expRect = RectF(leftExp, topExp, rightExp, bottomExp)
            canvas.drawRoundRect(expRect, dpToPx(4f), dpToPx(4f), barPaint)
            
            // Draw border: thicker if selected
            val isExpenseSelected = (selectedGroupIndex == i && selectedBarType == 2)
            borderPaint.strokeWidth = if (isExpenseSelected) dpToPx(3.2f) else dpToPx(1.5f)
            canvas.drawRoundRect(expRect, dpToPx(4f), dpToPx(4f), borderPaint)

            // Label
            val label = labels.getOrElse(i) { "" }
            canvas.drawText(label, groupCenterX, height - dpToPx(8f), textPaint.apply { textAlign = Paint.Align.CENTER })
        }

        // Draw Tooltip Box on top of the selected bar
        if (selectedGroupIndex != -1 && selectedBarType != -1) {
            val isIncome = selectedBarType == 1
            val barVal = if (isIncome) {
                incomeData.getOrElse(selectedGroupIndex) { 0f }
            } else {
                expenseData.getOrElse(selectedGroupIndex) { 0f }
            }
            val barHeight = (chartHeight * (barVal / maxVal)) * animationProgress
            val topY = paddingTop + chartHeight - barHeight

            val groupCenterX = paddingLeft + (selectedGroupIndex * groupWidth) + (groupWidth / 2)
            val targetX = if (isIncome) groupCenterX - barWidth / 2f - gap else groupCenterX + barWidth / 2f + gap

            val numberFormat = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
            numberFormat.maximumFractionDigits = 0
            val labelText = (if (isIncome) "Masuk: " else "Keluar: ") + numberFormat.format(barVal.toLong()).replace("Rp", "Rp ")

            val textWidth = tooltipTextPaint.measureText(labelText)
            val tooltipWidth = textWidth + dpToPx(14f)
            val tooltipHeight = dpToPx(24f)
            
            val tooltipRect = RectF(
                targetX - tooltipWidth / 2f,
                topY - tooltipHeight - dpToPx(8f),
                targetX + tooltipWidth / 2f,
                topY - dpToPx(8f)
            )

            // Draw brutalist shadow offset (bottom-right)
            val shadowOffset = dpToPx(3f)
            val shadowRect = RectF(
                tooltipRect.left + shadowOffset,
                tooltipRect.top + shadowOffset,
                tooltipRect.right + shadowOffset,
                tooltipRect.bottom + shadowOffset
            )
            canvas.drawRoundRect(shadowRect, dpToPx(4f), dpToPx(4f), tooltipShadowPaint)

            // Draw card background
            canvas.drawRoundRect(tooltipRect, dpToPx(4f), dpToPx(4f), tooltipBgPaint)
            // Draw card border
            canvas.drawRoundRect(tooltipRect, dpToPx(4f), dpToPx(4f), tooltipBorderPaint)

            // Draw text
            val textY = tooltipRect.centerY() + dpToPx(3.5f)
            canvas.drawText(labelText, targetX, textY, tooltipTextPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
