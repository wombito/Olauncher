package app.olauncher.ui.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.CalendarEvent
import app.olauncher.databinding.ItemEventsDayBinding
import app.olauncher.databinding.WidgetEventsBinding
import app.olauncher.helper.CalendarRepository
import app.olauncher.helper.dpToPx
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

private const val DAYS_AHEAD = 45
private const val MAX_EVENTS = 40

class EventsWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = WidgetEventsBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(scope: CoroutineScope, onPermissionNeeded: () -> Unit) {
        binding.eventsPermission.setOnClickListener { onPermissionNeeded() }
        binding.eventsEmpty.setOnClickListener { openCalendar(context) }

        val granted = CalendarRepository.hasPermission(context)
        binding.eventsPermission.isVisible = !granted
        binding.eventsContainer.isVisible = granted
        binding.eventsEmpty.isVisible = false
        if (!granted) return

        val start = System.currentTimeMillis()
        val end = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, DAYS_AHEAD) }.timeInMillis

        scope.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarRepository.eventsBetween(context, start, end)
            }
            render(events)
        }
    }

    private fun render(events: List<CalendarEvent>) {
        binding.eventsContainer.removeAllViews()
        binding.eventsEmpty.isVisible = events.isEmpty()

        val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        events.take(MAX_EVENTS).groupBy { startOfDay(it.startMillis) }.toSortedMap().forEach { (dayStart, dayEvents) ->
            val row = ItemEventsDayBinding.inflate(LayoutInflater.from(context), binding.eventsContainer, false)
            val date = Date(dayStart)
            row.root.setOnClickListener { openCalendar(context, dayStart) }
            row.eventDayName.text = dayNameFormat.format(date).uppercase(Locale.getDefault())
            row.eventDayNumber.text = Calendar.getInstance()
                .apply { time = date }.get(Calendar.DAY_OF_MONTH).toString()

            for (event in dayEvents) {
                row.eventDayEvents.addView(createBlock(event, timeFormat))
            }
            binding.eventsContainer.addView(row.root)
        }
    }

    private fun createBlock(event: CalendarEvent, timeFormat: SimpleDateFormat): TextView =
        TextView(context).apply {
            val time = if (event.allDay) context.getString(R.string.all_day)
            else timeFormat.format(Date(event.startMillis))
            text = "${event.title}\n$time"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
            background = ContextCompat.getDrawable(context, R.drawable.rounded_event_chip)?.mutate()
                ?.apply { setColorFilter(event.color, PorterDuff.Mode.SRC_IN) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6.dpToPx() }
            setOnClickListener { openCalendarEvent(context, event.id, event.startMillis) }
        }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        time = Date(millis)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
