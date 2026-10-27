package pe.manolo.calendariodual

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Widget dual: calendario mensual y agenda semanal. */
class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.week_widget)
        val locale = Locale("es", "PE")
        val now = Calendar.getInstance()
        views.setTextViewText(R.id.month_title,
            SimpleDateFormat("MMMM yyyy", locale).format(now.time).uppercase(locale))
        views.setTextViewText(R.id.month_grid, buildMonthGrid(now, locale))
        views.setTextViewText(R.id.agenda_text, buildAgenda(context, now, locale))
        return views
    }

    private fun buildMonthGrid(now: Calendar, locale: Locale): String {
        val cal = now.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstOffset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val out = StringBuilder("Lu Ma Mi Ju Vi Sa Do\n")
        for (cell in 0 until firstOffset) out.append("   ")
        for (day in 1..daysInMonth) {
            out.append(String.format(locale, "%2d ", day))
            if ((firstOffset + day) % 7 == 0) out.append("\n")
        }
        return out.toString()
    }

    private fun buildAgenda(context: Context, now: Calendar, locale: Locale): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return "Concede permiso\npara ver eventos."
        // Semana de lunes a lunes, independiente de la preferencia regional.
        // Usar Calendar.add evita errores en semanas con cambios de horario.
        val startCal = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }
        val start = startCal.timeInMillis
        val end = endCal.timeInMillis
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, start)
        android.content.ContentUris.appendId(builder, end)
        val projection = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY)
        val lines = mutableListOf<String>()
        try {
            context.contentResolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                val beginCol = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val titleCol = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val allDayCol = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val timeFormat = DateFormat.getTimeFormat(context)
                val dayFormat = SimpleDateFormat("EEE", locale)
                while (c.moveToNext() && lines.size < 7) {
                    val begin = c.getLong(beginCol)
                    val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "(Sin título)"
                    val allDay = c.getInt(allDayCol) == 1
                    val day = dayFormat.format(Date(begin)).replaceFirstChar { it.uppercase(locale) }
                    val time = if (allDay) "Todo el día" else timeFormat.format(Date(begin))
                    lines.add("$day  $time\n${title.take(24)}")
                }
            }
        } catch (_: SecurityException) {
            return "Permiso de calendario\npendiente."
        } catch (_: Exception) {
            return "No se pudieron leer\nlos eventos."
        }
        return if (lines.isEmpty()) "Sin eventos esta semana." else lines.joinToString("\n\n")
    }
}