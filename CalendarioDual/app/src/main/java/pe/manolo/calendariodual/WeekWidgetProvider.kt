package pe.manolo.calendariodual
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.RemoteViews
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
data class Ev(val time:Long,val title:String,val source:String)
class WeekWidgetProvider:AppWidgetProvider(){
 override fun onUpdate(ctx:Context,mgr:AppWidgetManager,ids:IntArray){ids.forEach{id->
 val v=RemoteViews(ctx.packageName,R.layout.week_widget)
 val pi=PendingIntent.getActivity(ctx,0,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 v.setOnClickPendingIntent(R.id.week_title,pi);v.setTextViewText(R.id.week_title,"ACTUALIZANDO…");mgr.updateAppWidget(id,v)
 thread{
 val now=Calendar.getInstance();val sc=(now.clone() as Calendar).apply{firstDayOfWeek=Calendar.MONDAY;set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
 val start=sc.timeInMillis;val end=(sc.clone() as Calendar).apply{add(Calendar.DAY_OF_YEAR,7)}.timeInMillis;val es=mutableListOf<Ev>()
 if(ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)==android.content.pm.PackageManager.PERMISSION_GRANTED)try{
 val u=CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(start.toString()).appendPath(end.toString()).build()
 ctx.contentResolver.query(u,arrayOf(CalendarContract.Instances.BEGIN,CalendarContract.Instances.TITLE),null,null,CalendarContract.Instances.BEGIN+" ASC")?.use{c->while(c.moveToNext())es.add(Ev(c.getLong(0),c.getString(1)?:"(Sin título)","Google"))}
 }catch(_:Exception){}
 val ics=ctx.getSharedPreferences("calendar_settings",0).getString("ics_url",null)
 if(!ics.isNullOrBlank())try{es.addAll(readIcs(ics,start,end))}catch(_:Exception){}
 val df=SimpleDateFormat("EEE dd",Locale("es","PE"));val tf=SimpleDateFormat("HH:mm",Locale("es","PE"));val mf=SimpleDateFormat("MMMM yyyy",Locale("es","PE"))
 val lines=es.sortedBy{it.time}.take(14).joinToString("\n"){(if(it.source=="Google")"🔵 " else "🟢 ")+df.format(Date(it.time)).uppercase(Locale("es","PE"))+" "+tf.format(Date(it.time))+" "+it.title.take(30)}
 val o=RemoteViews(ctx.packageName,R.layout.week_widget);o.setTextViewText(R.id.month_title,mf.format(now.time).uppercase(Locale("es","PE")));o.setTextViewText(R.id.month_grid,monthGrid(now));o.setTextViewText(R.id.events,if(lines.isBlank())"No hay eventos esta semana." else lines);o.setTextViewText(R.id.week_title,"SEMANA "+df.format(Date(start))+" – "+df.format(Date(end-86400000)));o.setOnClickPendingIntent(R.id.week_title,pi);mgr.updateAppWidget(id,o)
 }}
 }
 private fun readIcs(url:String,start:Long,end:Long):List<Ev>{
 val raw=URL(url).openConnection().apply{connectTimeout=8000;readTimeout=10000}.getInputStream().bufferedReader(Charsets.UTF_8).use{it.readLines()}
 val ls=mutableListOf<String>();raw.forEach{if((it.startsWith(" ")||it.startsWith("\t"))&&ls.isNotEmpty())ls[ls.lastIndex]+=it.trim() else ls.add(it)}
 val out=mutableListOf<Ev>();var inside=false;var title="";var s:Long?=null;var e:Long?=null
 for(line in ls){if(line=="BEGIN:VEVENT"){inside=true;title="";s=null;e=null;continue};if(line=="END:VEVENT"){if(inside&&s!=null&&s!!<end&&(e?:s!!)>=start)out.add(Ev(s!!,title.ifBlank{"(Sin título)"},"Outlook"));inside=false;continue};if(!inside)continue
 val k=line.substringBefore(':',"");val value=line.substringAfter(':',"");when(k.substringBefore(';')){"SUMMARY"->title=value.replace("\\,",",").replace("\\n"," ").replace("\\;",";");"DTSTART"->s=parseDate(value);"DTEND"->e=parseDate(value)}
 };return out}
 private fun parseDate(v:String):Long?{val ps=listOf("yyyyMMdd'T'HHmmss'Z'","yyyyMMdd'T'HHmmss","yyyyMMdd'T'HHmm'Z'","yyyyMMdd'T'HHmm","yyyyMMdd");for(p in ps)try{val f=SimpleDateFormat(p,Locale.US);if(p.endsWith("'Z'"))f.timeZone=TimeZone.getTimeZone("UTC");return f.parse(v)?.time}catch(_:Exception){};return null}
 private fun monthGrid(n:Calendar):String{val c=(n.clone() as Calendar).apply{set(Calendar.DAY_OF_MONTH,1)};val off=(c.get(Calendar.DAY_OF_WEEK)+5)%7;val max=c.getActualMaximum(Calendar.DAY_OF_MONTH);val b=StringBuilder("L M X J V S D\n");repeat(off){b.append("   ")};for(d in 1..max)b.append(String.format(Locale.US,"%2d",d)).append(if((off+d)%7==0)"\n" else " ");return b.toString()}
}