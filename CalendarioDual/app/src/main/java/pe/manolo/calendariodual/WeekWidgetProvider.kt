package pe.manolo.calendariodual

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Widget de diagnóstico mínimo para confirmar que One UI dibuja RemoteViews. */
class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.week_widget)
            views.setTextViewText(R.id.widget_test_text, "CALENDARIO DUAL\nPRUEBA DE VISUALIZACIÓN")
            manager.updateAppWidget(id, views)
        }
    }
}
