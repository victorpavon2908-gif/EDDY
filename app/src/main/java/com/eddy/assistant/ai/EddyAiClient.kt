package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.actions.ActionResult
import com.eddy.assistant.brain.SystemPanel
import com.eddy.assistant.devicecontrol.EddyAccessibilityService
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EddyWebSource(val title: String, val url: String)

data class EddyAiReply(val text: String, val webUsed: Boolean, val sources: List<EddyWebSource>, val evidence: String = "")

class EddyAiClient(private val context: Context, private val baseUrlOverride: String? = null) {
    private val appContext = context.applicationContext
    private val executor by lazy { ActionExecutor(appContext) }
    private val knowledgePrefs by lazy { appContext.getSharedPreferences(KNOWLEDGE_PREFS, Context.MODE_PRIVATE) }
    @Volatile private var pendingActions: JSONArray? = null
    @Volatile private var pendingAt: Long = 0L

    private fun resolvedBaseUrl(): String = baseUrlOverride?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: EddyAiSettings.baseUrl(context)
    val isConfigured: Boolean get() = resolvedBaseUrl().isNotBlank()

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val base = resolvedBaseUrl(); if (base.isBlank()) return@withContext false
        val c = runCatching { URL("$base/health").openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext false
        try { c.requestMethod="GET"; c.connectTimeout=1500; c.readTimeout=2500; c.responseCode in 200..299 } catch (_:Exception){false} finally {c.disconnect()}
    }

    suspend fun reply(message:String, memoryContext:String, forceWeb:Boolean=false): EddyAiReply? {
        val cleaned=message.trim(); if(cleaned.isBlank()) return null
        if(forceWeb) return requestWebSearch(cleaned,memoryContext)
        consumePendingConfirmation(cleaned)?.let{return it}
        findLearnedReply(cleaned)?.let{return it}
        val plan=requestPlan(cleaned,memoryContext)?:return null
        val actions=plan.optJSONArray("actions")?:JSONArray(); val modelReply=plan.optString("reply").trim()
        if(plan.optBoolean("needs_confirmation",false)&&actions.length()>0){pendingActions=JSONArray(actions.toString());pendingAt=System.currentTimeMillis();return EddyAiReply(modelReply.ifBlank{"Esa acción necesita confirmación."}+" Decime sí para hacerla o no para cancelarla.",false,emptyList())}
        val webQueries=extractWebQueries(actions); val directMessages=executePlannedActions(withoutWebActions(actions)); val webReply=if(webQueries.isNotEmpty())requestWebSearch(webQueries.joinToString(" "),memoryContext) else null
        val text=when{webReply!=null->webReply.text;modelReply.isNotBlank()->modelReply;directMessages.isNotEmpty()->directMessages.joinToString(" ");else->"Aquí estoy."}
        val reply=EddyAiReply(text,webReply?.webUsed==true,webReply?.sources.orEmpty(),webReply?.evidence.orEmpty()); if(actions.length()==0&&!reply.webUsed)rememberLearnedReply(cleaned,reply); return reply
    }

    private suspend fun consumePendingConfirmation(message:String):EddyAiReply?{val pending=pendingActions?:return null;if(System.currentTimeMillis()-pendingAt>CONFIRMATION_TTL_MS){clearPending();return null};val n=normalize(message);if(n in NEGATIVE_CONFIRMATIONS){clearPending();return EddyAiReply("Cancelado.",false,emptyList())};if(n !in POSITIVE_CONFIRMATIONS)return null;clearPending();val m=executePlannedActions(pending);return EddyAiReply(m.joinToString(" ").ifBlank{"Listo."},false,emptyList())}
    private fun clearPending(){pendingActions=null;pendingAt=0}

    private suspend fun requestPlan(message:String,memoryContext:String):JSONObject?=withContext(Dispatchers.IO){postJson("${resolvedBaseUrl()}/plan",JSONObject().put("message",message).put("memory_context",memoryContext.takeLast(8000)),900,2200)?.let(::JSONObject)}
    private suspend fun requestWebSearch(query:String,memoryContext:String):EddyAiReply?=withContext(Dispatchers.IO){val body=postJson("${resolvedBaseUrl()}/search",JSONObject().put("message",query).put("force_web",true).put("memory_context",memoryContext.takeLast(8000)),5000,18000)?:return@withContext null;val j=JSONObject(body);val text=j.optString("reply").trim();if(text.isBlank())return@withContext null;val s=j.optJSONArray("sources").toWebSources();EddyAiReply(text,j.optBoolean("web_used",s.isNotEmpty()),s,buildEvidence(j.optJSONArray("evidence")))}
    private fun postJson(endpoint:String,payload:JSONObject,connect:Int,read:Int):String?{if(resolvedBaseUrl().isBlank())return null;val c=(URL(endpoint).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=connect;readTimeout=read;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","application/json");setRequestProperty("User-Agent","EDDY-Android/${BuildConfig.VERSION_NAME}")};return try{c.outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(payload.toString())};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val body=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty();body.takeIf{code in 200..299&&it.isNotBlank()}}catch(_:Exception){null}finally{c.disconnect()}}

    private fun JSONArray?.toWebSources():List<EddyWebSource>=buildList{val a=this@toWebSources?:return@buildList;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val u=o.optString("url").trim();if(u.isNotBlank())add(EddyWebSource(o.optString("title").trim().ifBlank{"Fuente web"},u))}}
    private fun buildEvidence(a:JSONArray?):String=buildString{if(a==null)return@buildString;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val s=o.optString("snippet").trim();if(s.isNotBlank())appendLine("- ${o.optString("title")}: $s")}}.trim()
    private fun extractWebQueries(a:JSONArray):List<String>=buildList{for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;if(o.optString("type").trim().lowercase()!="web_search")continue;val args=o.optJSONObject("args")?:JSONObject();arg(args,"query","text").takeIf{it.isNotBlank()}?.let(::add)}}
    private fun withoutWebActions(a:JSONArray)=JSONArray().also{out->for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;if(o.optString("type").trim().lowercase()!="web_search")out.put(o)}}

    private suspend fun executePlannedActions(actions:JSONArray?):List<String>=withContext(Dispatchers.Main){if(actions==null)return@withContext emptyList();buildList{for(i in 0 until actions.length()){val item=actions.optJSONObject(i)?:continue;val type=item.optString("type").trim().lowercase();val args=item.optJSONObject("args")?:JSONObject();executeAction(type,args)?.spokenMessage?.takeIf{it.isNotBlank()}?.let(::add);if(type in ACCESSIBILITY_ACTIONS)delay(70)}}}
    private suspend fun executeAction(type:String,args:JSONObject):ActionResult?=when(type){"open_app"->executor.openAppByName(arg(args,"app","name"));"torch"->executor.setTorch(boolArg(args,"enabled",true));"dial"->executor.dial(arg(args,"number","phone"));"sms"->executor.composeMessage(arg(args,"number","phone"),arg(args,"message","text"));"whatsapp"->executor.whatsappMessage(arg(args,"number","phone").ifBlank{null},arg(args,"message","text"));"spotify"->executor.playSpotify(arg(args,"query","song"));"maps"->executor.openMaps(arg(args,"query","place","destination"));"volume"->executor.setVolume(intArg(args,"percent",50));"brightness"->executor.setBrightness(intArg(args,"percent",50));"camera"->executor.openCamera();"alarm"->executor.setAlarm(intArg(args,"hour",7),intArg(args,"minute",0),arg(args,"label").ifBlank{null});"timer"->executor.setTimer(intArg(args,"seconds",60),arg(args,"label").ifBlank{null});"system_panel"->executor.openSystemPanel(systemPanel(arg(args,"panel","name")));"back"->accessibilityWithRetry("Atrás."){it.goBack()};"home"->accessibilityWithRetry("Inicio."){it.goHome()};"recents"->accessibilityWithRetry("Recientes."){it.openRecents()};"notifications"->accessibilityWithRetry("Notificaciones."){it.openNotifications()};"quick_settings"->accessibilityWithRetry("Ajustes rápidos."){it.openQuickSettings()};"click_text"->accessibilityWithRetry("Listo."){it.clickText(arg(args,"text","label"))};"type_text"->accessibilityWithRetry("Listo."){it.setTextInFocusedField(arg(args,"text","value"))};"scroll_forward"->accessibilityWithRetry("Listo."){it.scrollForward()};"scroll_backward"->accessibilityWithRetry("Listo."){it.scrollBackward()};else->null}
    private suspend fun accessibilityWithRetry(success:String,action:(EddyAccessibilityService)->Boolean):ActionResult{val s=EddyAccessibilityService.instance?:return ActionResult(false,"Necesito que activés el servicio de accesibilidad de EDDY para hacer eso.");if(runCatching{action(s)}.getOrDefault(false))return ActionResult(true,success);delay(140);val ok=runCatching{action(s)}.getOrDefault(false);return ActionResult(ok,if(ok)success else "No pude completar esa acción en pantalla. Probé dos veces.")}
    private fun systemPanel(v:String)=when(normalize(v)){"wifi","wi fi"->SystemPanel.WIFI;"bluetooth"->SystemPanel.BLUETOOTH;"internet","datos","conectividad"->SystemPanel.INTERNET;"location","ubicacion","gps"->SystemPanel.LOCATION;"nfc"->SystemPanel.NFC;"airplane","modo avion","avion"->SystemPanel.AIRPLANE;else->SystemPanel.SETTINGS}
    private fun arg(j:JSONObject,vararg keys:String):String{for(k in keys)j.optString(k).trim().takeIf{it.isNotBlank()}?.let{return it};return ""}
    private fun intArg(j:JSONObject,key:String,fallback:Int):Int{val raw=j.opt(key)?:return fallback;return when(raw){is Number->raw.toInt();else->raw.toString().filter{it.isDigit()||it=='-'}.toIntOrNull()?:fallback}}
    private fun boolArg(j:JSONObject,key:String,fallback:Boolean):Boolean{if(!j.has(key))return fallback;val raw=j.opt(key)?:return fallback;return when(raw){is Boolean->raw;is Number->raw.toInt()!=0;else->normalize(raw.toString()) in setOf("true","1","on","yes","si","encender","encendido","prender","prendido")}}

    private fun findLearnedReply(message:String):EddyAiReply?{val target=normalize(message);if(target.length<5)return null;val tt=tokens(target);var best:KnowledgeEntry?=null;var score=0.0;val now=System.currentTimeMillis();for(e in readKnowledge()){if(now-e.savedAt>KNOWLEDGE_TTL_MS)continue;val c=normalize(e.question);val s=if(c==target)1.0 else similarity(tt,tokens(c));if(s>score){score=s;best=e}};val hit=best?.takeIf{score>=.88}?:return null;return EddyAiReply(hit.answer,false,emptyList())}
    private fun rememberLearnedReply(q:String,r:EddyAiReply){if(q.length<5||r.text.length<2)return;val e=readKnowledge().toMutableList();val n=normalize(q);e.removeAll{normalize(it.question)==n};e+=KnowledgeEntry(q.take(500),r.text.take(6000),System.currentTimeMillis());while(e.size>120)e.removeAt(0);val a=JSONArray();e.forEach{a.put(JSONObject().put("q",it.question).put("a",it.answer).put("t",it.savedAt))};knowledgePrefs.edit().putString(KEY_KNOWLEDGE,a.toString()).apply()}
    private fun readKnowledge():List<KnowledgeEntry>{val raw=knowledgePrefs.getString(KEY_KNOWLEDGE,null)?:return emptyList();return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val q=o.optString("q").trim();val ans=o.optString("a").trim();if(q.isNotBlank()&&ans.isNotBlank())add(KnowledgeEntry(q,ans,o.optLong("t")))}}}.getOrDefault(emptyList())}
    private fun normalize(v:String)=Normalizer.normalize(v.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9ñ ]+")," ").replace(Regex("\\s+")," ").trim()
    private fun tokens(v:String)=v.split(' ').filter{it.length>=3&&it !in STOP_WORDS}.toSet()
    private fun similarity(a:Set<String>,b:Set<String>)=if(a.isEmpty()||b.isEmpty())0.0 else a.intersect(b).size.toDouble()/a.union(b).size
    private data class KnowledgeEntry(val question:String,val answer:String,val savedAt:Long)
    companion object{private const val KNOWLEDGE_PREFS="eddy_learned_knowledge_v1";private const val KEY_KNOWLEDGE="entries";private const val KNOWLEDGE_TTL_MS=30L*24*60*60*1000;private const val CONFIRMATION_TTL_MS=30_000L;private val POSITIVE_CONFIRMATIONS=setOf("si","sí","dale","confirmo","confirmado","hazlo","hacelo","hacele","procede","adelante","ok","okay");private val NEGATIVE_CONFIRMATIONS=setOf("no","cancela","cancelalo","cancelar","dejalo","mejor no","olvidalo");private val ACCESSIBILITY_ACTIONS=setOf("back","home","recents","notifications","quick_settings","click_text","type_text","scroll_forward","scroll_backward");private val STOP_WORDS=setOf("que","como","para","por","con","una","uno","del","las","los","esto","esta","este","me","mi","es","son","hay","quiero","puedes","puede","favor")}
}