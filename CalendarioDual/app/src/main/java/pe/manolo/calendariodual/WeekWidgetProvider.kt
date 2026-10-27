package pe.manolo.calendariodual

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.RemoteViews
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

private data class Ev(val time: Long, val title: String, val source: String)

class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        ids.forEach { id ->
            val loading = RemoteViews(ctx.packageName, R.layout.week_widget)
            loading.setTextViewText(R.id.events, "Cargando tus calendarios…")
            mgr.updateAppWidget(id, loading)
        }
        thread(name = "CalendarDualWidgetUpdate") {
            try {
                val now = Calendar.getInstance()
                val startCal = (now.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = startCal.timeInMillis
                val end = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
                val events = mutableListOf<Ev>()
                var googleStatus: String? = null

                if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                            .appendPath(start.toString()).appendPath(end.toString()).build()
                        ctx.contentResolver.query(
                            uri,
                            arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE),
                            null, null, CalendarContract.Instances.BEGIN + " ASC"
                        )?.use { cursor ->
                            val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                            val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                            while (cursor.moveToNext()) {
                                if (beginIndex >= 0) {
                                    val time = cursor.getLong(beginIndex)
                                    val title = if (titleIndex >= 0) cursor.getString(titleIndex) else null
                                    events.add(Ev(time, title?.takeIf { it.isNotBlank() } ?: "(Sin título)", "Google"))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        googleStatus = "No pude leer Google Calendar: revisa el permiso de Calendario."
                    }
                } else {
                    googleStatus = "Abre la app y concede permiso para leer el calendario del teléfono."
                }

                val ics = ctx.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE)
                    .getString("ics_url", null)
                var outlookStatus: String? = null
                if (!ics.isNullOrBlank()) {
                    try {
                        events.addAll(readIcs(ics, start, end))
                    } catch (_: Exception) {
                        outlookStatus = "No se pudo leer Outlook. Revisa el enlace ICS y la conexión."
                    }
                } else {
                    outlookStatus = "Añade tu enlace ICS de Outlook en la app."
                }

                val locale = Locale("es", "PE")
                val dayFmt = SimpleDateFormat("EEE dd", locale)
                val timeFmt = SimpleDateFormat("HH:mm", locale)
                val monthFmt = SimpleDateFormat("MMMM yyyy", locale)
                val eventLines = events.sortedBy { it.time }.take(12).joinToString("\n") {
                    val marker = if (it.source == "Google") "●" else "○"
                    "${marker} ${dayFmt.format(Date(it.time)).uppercase(locale)}  ${timeFmt.format(Date(it.time))}  ${it.title.take(36)}"
                }
                val message = when {
                    events.isNotEmpty() -> eventLines
                    googleStatus != null -> "${googleStatus}\n${outlookStatus ?: "Sin eventos esta semana."}"
                    outlookStatus != null -> "${outlookStatus}\nNo hay eventos de Google esta semana."
                    else -> "No hay eventos esta semana."
                }

                ids.forEach { id ->
                    val views = RemoteViews(ctx.packageName, R.layout.week_widget)
                    views.setTextViewText(R.id.month_title, monthFmt.format(now.time).uppercase(locale))
                    views.setTextViewText(R.id.month_grid, monthGrid(now))
                    views.setTextViewText(
                        R.id.week_title,
                        "SEMANA ${dayFmt.format(Date(start)).uppercase(locale)} – ${dayFmt.format(Date(end - 1)).uppercase(locale)}"
                    )
                    views.setTextViewText(R.id.events, message)
                    val open = PendingIntent.getActivity(
                        ctx, id, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.week_title, open)
                    views.setOnClickPendingIntent(R.id.month_title, open)
                    mgr.updateAppWidget(id, views)
                }
            } catch (_: Exception) {
                ids.forEach { id ->
                    val views = RemoteViews(ctx.packageName, R.layout.week_widget)
                    views.setTextViewText(R.id.events, "No se pudo actualizar. Abre Calendario dual y vuelve a intentarlo.")
                    mgr.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun readIcs(url: String, start: Long, end: Long): List<Ev> {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "CalendarioDual/1.0")
        }
        val raw = connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readLines() }
        val lines = mutableListOf<String>()
        raw.forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] += line.trim()
            } else lines.add(line)
        }
        val out = mutableListOf<Ev>()
        var inside = false
        var title = ""
        var startTime: Long? = null
        var endTime: Long? = null
        for (line in lines) {
            if (line == "BEGIN:VEVENT") {
                inside = true; title = ""; startTime = null; endTime = null
                continue
            }
            if (line == "END:VEVENT") {
                val s = startTime
                if (inside && s != null && s < end && (endTime ?: s) >= start) {
                    out.add(Ev(s, title.ifBlank { "(Sin título)" }, "Outlook"))
                }
                inside = false
                continue
            }
            if (!inside) continue
            val key = line.substringBefore(':', "")
            val value = line.substringAfter(':', "")
            when (key.substringBefore(';')) {
                "SUMMARY" -> title = value.replace("\\,", ",").replace("\\n", " ").replace("\\;", ";")
                "DTSTART" -> startTime = parseDate(value)
                "DTEND" -> endTime = parseDate(value)
            }
        }
        return out
    }

    private fun parseDate(value: String): Long? {
        val patterns = listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm'Z'", "yyyyMMdd'T'HHmm", "yyyyMMdd")
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(value)?.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun monthGrid(now: Calendar): String {
        val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val maxDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)
        val out = StringBuilder("L  M  X  J  V  S  D\n")
        repeat(offset) { out.append("   ") }
        for (day in 1..maxDay) {
            val cell = if (day == today) "[${day}]" else String.format(Locale.US, "%2d ", day)
            out.append(cell)
            if ((offset + day) % 7 == 0) out.append("\n")
        }
        return out.toString()
    }
}
