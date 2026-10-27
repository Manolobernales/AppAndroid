package pe.manolo.calendariodual

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * Widget de prueba visual. Los eventos son datos ficticios; la conexión con
 * Google Calendar y Outlook se incorporará después de validar la presentación.
 */
class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.week_widget)
            views.setTextViewText(R.id.month_title, "OCTUBRE 2026")
            views.setTextViewText(
                R.id.month_grid,
                "L M X J V S D\n      1 2 3 4\n5 6 7 8 9 10 11\n12 13 14 15 16 17 18\n19 20 21 22 23 24 25\n26 27 28 29 30 31"
            )
            views.setTextViewText(R.id.month_legend, "● Google  ○ Outlook")
            views.setTextViewText(R.id.week_title, "AGENDA SEMANAL")
            views.setTextViewText(
                R.id.week_events,
                "LUN 09:00 Reunión\nMAR 11:30 Llamada\nMIÉ 16:00 Proyecto\nJUE 10:00 Seguimiento\nVIE 12:00 Revisión"
            )
            views.setTextViewText(R.id.widget_footer, "Vista de prueba · datos de ejemplo")
            manager.updateAppWidget(id, views)
        }
    }
}
