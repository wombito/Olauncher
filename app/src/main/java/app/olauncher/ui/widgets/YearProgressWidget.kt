package app.olauncher.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import app.olauncher.databinding.WidgetYearProgressBinding
import java.util.Calendar

class YearProgressWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = WidgetYearProgressBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind() {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val daysInYear = calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
        val weeksInYear = 52

        binding.yearDots.setProgress((dayOfYear * weeksInYear) / daysInYear, weeksInYear)
        binding.yearPercent.text = "${(dayOfYear * 100) / daysInYear}%"
    }
}
