package app.olauncher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.olauncher.data.CalendarEvent

object CalendarRepository {

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.EVENT_COLOR_KEY,
        CalendarContract.Instances.CALENDAR_COLOR_KEY,
    )

    // The calendar provider reports the legacy bright palette; the Google Calendar app shows
    // a "modern" palette it computes itself. Re-map by the sync-adapter colour key so events
    // in the widget match what Google Calendar displays.
    private val GOOGLE_EVENT_COLORS = mapOf(
        "1" to 0xFF7986CB.toInt(),  // Lavender
        "2" to 0xFF33B679.toInt(),  // Sage
        "3" to 0xFF8E24AA.toInt(),  // Grape
        "4" to 0xFFE67C73.toInt(),  // Flamingo
        "5" to 0xFFF6BF26.toInt(),  // Banana
        "6" to 0xFFF4511E.toInt(),  // Tangerine
        "7" to 0xFF039BE5.toInt(),  // Peacock
        "8" to 0xFF616161.toInt(),  // Graphite
        "9" to 0xFF3F51B5.toInt(),  // Blueberry
        "10" to 0xFF0B8043.toInt(), // Basil
        "11" to 0xFFD50000.toInt(), // Tomato
    )

    // Google's modern calendar-colour palette (used when an event has no colour of its own).
    private val GOOGLE_CALENDAR_COLORS = mapOf(
        "1" to 0xFF795548.toInt(),  "2" to 0xFFE67C73.toInt(),  "3" to 0xFFD50000.toInt(),
        "4" to 0xFFF4511E.toInt(),  "5" to 0xFFEF6C00.toInt(),  "6" to 0xFFF09300.toInt(),
        "7" to 0xFF009688.toInt(),  "8" to 0xFF0B8043.toInt(),  "9" to 0xFF7CB342.toInt(),
        "10" to 0xFFC0CA33.toInt(), "11" to 0xFFE4C441.toInt(), "12" to 0xFFF6BF26.toInt(),
        "13" to 0xFF33B679.toInt(), "14" to 0xFF039BE5.toInt(), "15" to 0xFF4285F4.toInt(),
        "16" to 0xFF3F51B5.toInt(), "17" to 0xFF7986CB.toInt(), "18" to 0xFFB39DDB.toInt(),
        "19" to 0xFF616161.toInt(), "20" to 0xFFA79B8E.toInt(), "21" to 0xFFAD1457.toInt(),
        "22" to 0xFFD81B60.toInt(), "23" to 0xFF8E24AA.toInt(), "24" to 0xFF9E69AF.toInt(),
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun eventsBetween(context: Context, startMillis: Long, endMillis: Long): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri, PROJECTION, null, null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventColorKey = cursor.getString(6)
                    val calendarColorKey = cursor.getString(7)
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(0),
                            title = cursor.getString(1).orEmpty(),
                            startMillis = cursor.getLong(2),
                            endMillis = cursor.getLong(3),
                            allDay = cursor.getInt(4) == 1,
                            color = GOOGLE_EVENT_COLORS[eventColorKey]
                                ?: GOOGLE_CALENDAR_COLORS[calendarColorKey]
                                ?: cursor.getInt(5),
                        )
                    )
                }
            }
        } catch (exception: Exception) {
            // The provider can throw on locked or restricted profiles.
        }
        return events
    }
}
