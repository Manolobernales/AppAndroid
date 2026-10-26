package pe.manolo.calendariodual
import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
class MainActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,42,36,36);setBackgroundColor(Color.WHITE)}
 root.addView(TextView(this).apply{text="Calendario dual";textSize=26f;setTextColor(Color.rgb(23,37,84))})
 root.addView(TextView(this).apply{text="Widget semanal de solo visualización. Google se lee de los calendarios sincronizados en Android. Outlook se lee desde tu enlace público ICS.";textSize=15f;setTextColor(Color.DKGRAY);setPadding(0,18,0,18)})
 val field=EditText(this).apply{hint="Enlace público de Outlook (.ics)";setText(getSharedPreferences("calendar_settings",0).getString("ics_url",""));textSize=12f}
 root.addView(field,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
 root.addView(Button(this).apply{text="Guardar enlace y actualizar";setOnClickListener{
 val url=field.text.toString().trim()
 if(url.isNotEmpty()&&!url.startsWith("https://")){Toast.makeText(this@MainActivity,"Usa un enlace HTTPS válido.",Toast.LENGTH_LONG).show();return@setOnClickListener}
 getSharedPreferences("calendar_settings",0).edit().putString("ics_url",url).apply()
 if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR),91) else refresh()
 Toast.makeText(this@MainActivity,"Configuración guardada.",Toast.LENGTH_SHORT).show()
 }},LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT))
 root.addView(TextView(this).apply{text="Después, añade Calendario dual desde el selector de widgets. No crea, modifica ni elimina eventos.";textSize=13f;setTextColor(Color.GRAY);setPadding(0,24,0,0)})
 setContentView(root)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==91&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)refresh()}
 private fun refresh(){val ids=AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this,WeekWidgetProvider::class.java));sendBroadcast(Intent(this,WeekWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))}
}