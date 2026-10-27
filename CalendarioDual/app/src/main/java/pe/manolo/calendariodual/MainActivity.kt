package pe.manolo.calendariodual

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var linkField: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
            setBackgroundColor(Color.rgb(248, 250, 252))
        }

        root.addView(TextView(this).apply {
            text = "Calendario dual"
            textSize = 28f
            setTextColor(Color.rgb(23, 59, 112))
        })

        root.addView(TextView(this).apply {
            text = "Tu agenda semanal en un widget: mini calendario mensual a la izquierda y eventos a la derecha."
            textSize = 16f
            setTextColor(Color.rgb(51, 65, 85))
            setPadding(0, 12, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "1. GOOGLE CALENDAR"
            textSize = 14f
            setTextColor(Color.rgb(23, 59, 112))
        })

        root.addView(TextView(this).apply {
            text = "Da permiso para leer los calendarios sincronizados en Android. No modifica ni crea eventos."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 6, 0, 8)
        })

        root.addView(Button(this).apply {
            text = "Dar permiso a Google Calendar"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 91)
                } else {
                    Toast.makeText(this@MainActivity, "El permiso ya está concedido.", Toast.LENGTH_SHORT).show()
                    refreshWidget()
                }
            }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "2. OUTLOOK CALENDAR"
            textSize = 14f
            setTextColor(Color.rgb(23, 59, 112))
            setPadding(0, 18, 0, 0)
        })

        root.addView(TextView(this).apply {
            text = "Pega aquí el enlace de suscripción ICS de Outlook. Mantén ese enlace privado."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 6, 0, 8)
        })

        linkField = EditText(this).apply {
            hint = "https://…/calendar.ics"
            setText(getSharedPreferences("calendar_settings", 0).getString("ics_url", ""))
            textSize = 13f
            setSingleLine(true)
        }
        root.addView(linkField, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(Button(this).apply {
            text = "Guardar enlace de Outlook"
            setOnClickListener {
                val url = linkField.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("https://")) {
                    Toast.makeText(this@MainActivity, "El enlace debe empezar por https://", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                getSharedPreferences("calendar_settings", 0).edit().putString("ics_url", url).apply()
                Toast.makeText(this@MainActivity, "Enlace guardado. Actualizando widget…", Toast.LENGTH_SHORT).show()
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 91)
                } else {
                    refreshWidget()
                }
            }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(Button(this).apply {
            text = "Actualizar widget ahora"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 91)
                } else refreshWidget()
            }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusText = TextView(this).apply {
            text = if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                "Permiso de calendario: concedido."
            else "Permiso de calendario: pendiente."
            textSize = 12f
            setTextColor(Color.rgb(71, 85, 105))
            setPadding(0, 14, 0, 0)
        }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "IMPORTANTE: el widget se añade aparte desde la pantalla de inicio del teléfono. Mantén pulsado un espacio vacío, entra en Widgets y busca “Calendario dual”. Después, agrándalo para que se vean las dos columnas."
            textSize = 13f
            setTextColor(Color.rgb(51, 65, 85))
            setPadding(0, 18, 0, 0)
        })

        setContentView(root)
        publishWidgetPreview()
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            refreshWidget()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 91) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            statusText.text = if (granted) "Permiso de calendario: concedido." else "Permiso denegado. Google Calendar no podrá mostrarse."
            if (granted) refreshWidget()
        }
    }

    @Suppress("NewApi")
    private fun publishWidgetPreview() {
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            try {
                AppWidgetManager.getInstance(this).setWidgetPreview(
                    ComponentName(this, WeekWidgetProvider::class.java),
                    android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    RemoteViews(packageName, R.layout.week_widget_preview)
                )
            } catch (_: Exception) {
                // El widget sigue funcionando en dispositivos que no aceptan la vista previa generada.
            }
        }
    }

    private fun refreshWidget() {
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, WeekWidgetProvider::class.java))
        if (ids.isEmpty()) {
            Toast.makeText(this, "Ahora añade el widget desde Widgets en la pantalla de inicio.", Toast.LENGTH_LONG).show()
            return
        }
        sendBroadcast(
            Intent(this, WeekWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
        Toast.makeText(this, "Actualización solicitada.", Toast.LENGTH_SHORT).show()
    }
}
