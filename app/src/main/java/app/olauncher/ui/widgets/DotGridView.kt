package app.olauncher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr
import kotlin.math.ceil

class DotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val columns = 13
    private var total = 52
    private var progress = 0

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColorTrans50)
    }

    fun setProgress(progress: Int, total: Int) {
        this.progress = progress
        this.total = total
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cell = width / columns.toFloat()
        val rows = ceil(total / columns.toFloat()).toInt()
        setMeasuredDimension(width, (cell * rows).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = width / columns.toFloat()
        val radius = cell * 0.22f
        for (index in 0 until total) {
            val column = index % columns
            val row = index / columns
            val centerX = cell * column + cell / 2f
            val centerY = cell * row + cell / 2f
            canvas.drawCircle(centerX, centerY, radius, if (index < progress) filledPaint else emptyPaint)
        }
    }
}
