package app.olauncher.ui.widgets

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.CalendarEvent
import app.olauncher.databinding.ItemCalendarDayBinding
import app.olauncher.databinding.WidgetCalendarBinding
import app.olauncher.helper.CalendarRepository
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val WEEKS_SHOWN = 6
private const val DAYS_IN_WEEK = 7
private const val MAX_CHIPS_PER_DAY = 3

class CalendarWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = WidgetCalendarBinding.inflate(LayoutInflater.from(context), this, true)

    private var monthOffset = 0
    private var scope: CoroutineScope? = null
    private var onPermissionNeeded: (() -> Unit)? = null

    fun bind(scope: CoroutineScope, onPermissionNeeded: () -> Unit) {
        this.scope = scope
        this.onPermissionNeeded = onPermissionNeeded

        binding.calendarPrevious.setOnClickListener { monthOffset--; render() }
        binding.calendarNext.setOnClickListener { monthOffset++; render() }
        binding.calendarAdd.setOnClickListener { openCalendar(context) }
        binding.calendarMonth.setOnClickListener { openCalendar(context) }
        binding.calendarPermission.setOnClickListener { this.onPermissionNeeded?.invoke() }

        renderWeekdays()
        render()
    }

    private fun firstVisibleDay(): Calendar {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        return calendar
    }

    private fun renderWeekdays() {
        binding.calendarWeekdays.removeAllViews()
        val formatter = SimpleDateFormat("EEEEE", Locale.getDefault())
        val day = firstVisibleDay()
        repeat(DAYS_IN_WEEK) {
            binding.calendarWeekdays.addView(
                TextView(context).apply {
                    text = formatter.format(day.time).take(1).uppercase(Locale.getDefault())
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    alpha = 0.5f
                    setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun render() {
        val start = firstVisibleDay()
        val displayedMonth = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
        binding.calendarMonth.text =
            SimpleDateFormat("MMMM", Locale.getDefault()).format(displayedMonth.time)
                .replaceFirstChar { it.uppercase(Locale.getDefault()) }

        val granted = CalendarRepository.hasPermission(context)
        binding.calendarPermission.isVisible = !granted
        binding.calendarGrid.isVisible = granted
        binding.calendarWeekdays.isVisible = granted
        if (!granted) return

        drawGrid(start, displayedMonth.get(Calendar.MONTH), emptyMap())

        val rangeStart = start.timeInMillis
        val rangeEnd = (start.clone() as Calendar)
            .apply { add(Calendar.DAY_OF_MONTH, WEEKS_SHOWN * DAYS_IN_WEEK) }.timeInMillis

        scope?.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarRepository.eventsBetween(context, rangeStart, rangeEnd)
            }
            drawGrid(start, displayedMonth.get(Calendar.MONTH), events.groupBy { startOfDay(it.startMillis) })
        }
    }

    private fun drawGrid(start: Calendar, month: Int, eventsByDay: Map<Long, List<CalendarEvent>>) {
        binding.calendarGrid.removeAllViews()
        val day = start.clone() as Calendar
        val today = startOfDay(System.currentTimeMillis())

        repeat(WEEKS_SHOWN * DAYS_IN_WEEK) { index ->
            val cell = ItemCalendarDayBinding.inflate(LayoutInflater.from(context), binding.calendarGrid, false)
            val dayStart = startOfDay(day.timeInMillis)
            val dayMillis = day.timeInMillis

            cell.dayNumber.text = day.get(Calendar.DAY_OF_MONTH).toString()
            cell.root.alpha = if (day.get(Calendar.MONTH) == month) 1f else 0.35f
            cell.root.setOnClickListener { openCalendar(context, dayMillis) }

            if (dayStart == today) {
                cell.dayNumber.setBackgroundResource(R.drawable.circle_today)
                cell.dayNumber.setTextColor(context.getColorFromAttr(R.attr.primaryInverseColor))
            }

            addChips(cell.dayEvents, eventsByDay[dayStart].orEmpty())

            cell.root.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % DAYS_IN_WEEK, 1f)
                rowSpec = GridLayout.spec(index / DAYS_IN_WEEK)
            }
            binding.calendarGrid.addView(cell.root)
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun addChips(container: LinearLayout, events: List<CalendarEvent>) {
        container.removeAllViews()
        for (event in events.take(MAX_CHIPS_PER_DAY)) {
            container.addView(
                TextView(context).apply {
                    text = event.title
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(3.dpToPx(), 1.dpToPx(), 3.dpToPx(), 1.dpToPx())
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_event_chip)?.mutate()
                        ?.apply { setColorFilter(event.color, PorterDuff.Mode.SRC_IN) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 1.dpToPx() }
                    setOnClickListener { openCalendarEvent(context, event.id, event.startMillis) }
                }
            )
        }
        if (events.size > MAX_CHIPS_PER_DAY) {
            container.addView(
                TextView(context).apply {
                    text = "..."
                    textSize = 8f
                    gravity = android.view.Gravity.CENTER
                    alpha = 0.6f
                    setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                }
            )
        }
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        time = Date(millis)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
