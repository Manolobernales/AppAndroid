package pe.manolo.calendariodual

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.week_widget)
            views.setTextViewText(R.id.widget_title, "CALENDARIO DUAL")
            views.setTextViewText(R.id.widget_message, "Prueba visual: el widget está en pantalla.")
            manager.updateAppWidget(id, views)
        }
    }
}
