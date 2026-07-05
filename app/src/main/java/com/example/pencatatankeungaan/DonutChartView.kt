package com.example.pencatatankeungaan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.text.NumberFormat
import java.util.Locale

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data = listOf(45f, 30f, 25f) // percentages
    private var categories = listOf("Bahan Baku", "Operasional", "Lainnya")
    private var totalExpense: Long = 2550000
    private var selectedSliceIndex: Int = -1

    private val colors = listOf(
        Color.parseColor("#8B5CF6"), // Violet (Primary Theme Color)
        Color.parseColor("#38BDF8"), // Sky Blue
        Color.parseColor("#FBBF24"), // Amber
        Color.parseColor("#F43F5E"), // Rose Red
        Color.parseColor("#F97316")  // Orange
    )

    private var animationProgress = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = dpToPx(11f)
        textAlign = Paint.Align.CENTER
    }

    private val centerAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
        textSize = dpToPx(15f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val centerPercentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(11f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E1E1E")
        strokeWidth = dpToPx(1.5f)
    }

    init {
        startAnimation()
    }

    fun setData(percentages: List<Float>, categoryNames: List<String>, total: Long) {
        this.data = percentages
        this.categories = categoryNames
        this.totalExpense = total
        this.selectedSliceIndex = -1 // Reset selection on data change
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

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val centerX = width / 2f
            val centerY = height / 2f
            val dx = event.x - centerX
            val dy = event.y - centerY
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())

            val strokeWidth = dpToPx(24f)
            val size = Math.min(width, height)
            val radius = (size - strokeWidth - dpToPx(16f)) / 2f

            // Add margin for easier touch target detection
            val innerLimit = radius - strokeWidth / 2f - dpToPx(16f)
            val outerLimit = radius + strokeWidth / 2f + dpToPx(16f)

            if (distance in innerLimit..outerLimit) {
                val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                // Normalize angle: Math.atan2 is -180 to 180. We start at -90 degrees.
                var degrees = angle + 90f
                if (degrees < 0f) {
                    degrees += 360f
                }

                val totalSum = data.sum()
                if (totalSum > 0f) {
                    var cumulativeAngle = 0f
                    var clickedIndex = -1
                    for (i in data.indices) {
                        val sweep = (data[i] / totalSum) * 360f
                        if (degrees >= cumulativeAngle && degrees < cumulativeAngle + sweep) {
                            clickedIndex = i
                            break
                        }
                        cumulativeAngle += sweep
                    }

                    if (clickedIndex != -1) {
                        selectedSliceIndex = if (selectedSliceIndex == clickedIndex) -1 else clickedIndex
                        invalidate()
                        return true
                    }
                }
            } else {
                // Tapped outside/inside circle: reset selection
                selectedSliceIndex = -1
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = Math.min(width, height)
        val centerX = width / 2f
        val centerY = height / 2f

        val strokeWidth = dpToPx(24f)
        val radius = (size - strokeWidth - dpToPx(16f)) / 2f

        if (radius <= 0) return

        val rectF = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        var startAngle = -90f
        val totalSum = data.sum()
        
        if (totalSum <= 0f) {
            paint.strokeWidth = strokeWidth
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawArc(rectF, 0f, 360f, false, paint)
        } else {
            val activeSegmentsCount = data.filter { it > 0f }.size
            val gapAngle = if (activeSegmentsCount > 1) 2.5f else 0f

            for (i in data.indices) {
                val percentage = data[i]
                val sweepAngle = (percentage / totalSum * 360f) * animationProgress
                val isSelected = selectedSliceIndex == i
                
                // Pop-out highlight effect for selected segment
                paint.strokeWidth = if (isSelected) strokeWidth + dpToPx(6f) else strokeWidth
                
                val categoryName = categories.getOrNull(i) ?: ""
                val segmentColor = when (categoryName) {
                    "Pemasukan" -> Color.parseColor("#10B981")
                    "Pengeluaran" -> Color.parseColor("#F43F5E")
                    else -> colors[i % colors.size]
                }
                paint.color = segmentColor

                if (sweepAngle > gapAngle) {
                    canvas.drawArc(
                        rectF,
                        startAngle + (gapAngle / 2f),
                        sweepAngle - gapAngle,
                        false,
                        paint
                    )
                }

                startAngle += percentage / totalSum * 360f
            }
        }

        // Draw inner and outer borders for brutalist look
        canvas.drawCircle(centerX, centerY, radius + strokeWidth / 2f, borderPaint)
        canvas.drawCircle(centerX, centerY, radius - strokeWidth / 2f, borderPaint)

        val numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        numberFormat.maximumFractionDigits = 0

        if (selectedSliceIndex != -1 && selectedSliceIndex < categories.size && totalSum > 0f) {
            val categoryName = categories[selectedSliceIndex]
            val percentage = data[selectedSliceIndex]
            val categorySum = Math.round((percentage / totalSum) * totalExpense)
            val percentageStr = String.format(Locale.US, "%.1f%%", (percentage / totalSum) * 100f)
            
            val themeColor = when (categoryName) {
                "Pemasukan" -> Color.parseColor("#10B981")
                "Pengeluaran" -> Color.parseColor("#F43F5E")
                else -> colors[selectedSliceIndex % colors.size]
            }

            // Draw Center Category Name (in theme color)
            val labelY = centerY - dpToPx(14f)
            centerTextPaint.color = themeColor
            centerTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(categoryName, centerX, labelY, centerTextPaint)

            // Draw Center Amount
            val formattedAmount = numberFormat.format(categorySum).replace("Rp", "Rp ")
            val amountY = centerY + dpToPx(6f)
            canvas.drawText(formattedAmount, centerX, amountY, centerAmountPaint)

            // Draw Center Percentage
            val percentY = centerY + dpToPx(22f)
            centerPercentPaint.color = themeColor
            canvas.drawText(percentageStr, centerX, percentY, centerPercentPaint)
        } else {
            // Draw default center text
            centerTextPaint.color = Color.parseColor("#6B7280")
            centerTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val labelY = centerY - dpToPx(4f)
            canvas.drawText("Total Transaksi", centerX, labelY, centerTextPaint)

            val formattedAmount = numberFormat.format(totalExpense).replace("Rp", "Rp ")
            val amountY = centerY + dpToPx(16f)
            canvas.drawText(formattedAmount, centerX, amountY, centerAmountPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
