package io.github.eslamasabry.opencode_mobile
import android.content.Context
import java.io.File
fun main(args: Array<String>) {
 var phase="started"
 var fixture: File?=null
 try {
  android.os.Looper.prepare()
  phase="thread"
  val klass=Class.forName("android.app.ActivityThread")
  val thread=klass.getDeclaredMethod("systemMain").invoke(null)
  phase="systemContext"
  val system=klass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
  phase="packageContext"
  val context=system.createPackageContext("io.github.eslamasabry.opencode_mobile", Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
  phase="loader"
  val loader=dalvik.system.PathClassLoader(args[3],args[2],Thread.currentThread().contextClassLoader)
  phase="hostClass"
  val type=loader.loadClass("ab")
  phase="construct"
  val host=type.getConstructor(Context::class.java).newInstance(context)
  if(args[4]=="fixture") {
   phase="projectControl"
   val base=File(context.cacheDir,"ba-private-project-control")
   check(!base.exists() && base.mkdir());fixture=base
   val storage=type.getDeclaredField("d").apply { isAccessible=true }.get(host)
   val paths=mapOf("V" to base,"W" to File(base,"ubuntu-base.tar.gz"),"X" to File(base,"projects"),
     "Y" to File(base,"linux/ubuntu"),"Z" to File(base,"linux"),"a0" to File(base,"linux/ubuntu/root"),"b0" to File(base,"linux/ubuntu/root/projects"))
   for((field,path) in paths) storage.javaClass.getDeclaredField(field).apply {isAccessible=true}.set(storage,path)
  } else check(args[4]=="real")
  phase="probe"
  val request=mapOf("profileId" to args[0],"agentId" to "claude","action" to "probe","script" to File(args[1]).readText(),"timeoutSeconds" to 10)
  val callType=loader.loadClass("ke0")
  val call=callType.getConstructor(Int::class.javaPrimitiveType,Any::class.java,Any::class.java).newInstance(5,"agentAuthProbe",request)
  val routeType=loader.loadClass("pi0")
  val route=routeType.getConstructor(type,callType,Int::class.javaPrimitiveType).newInstance(host,call,0)
  val result=routeType.getMethod("a").invoke(route) as Map<*,*>
  val state=result["state"];check(state in setOf("signedIn","signedOut","error"));println("PROBE_STATE=$state")
  val error=result["error"];if(error!=null){check(error in setOf("probeUnsupported","invalidResponse","timedOut","hostUnavailable","notInstalled","invalidContext","signInExpired","signOutFailed"));println("PROBE_ERROR=$error")}
 } catch(e: Throwable) {
  println("PROBE_DIAGNOSTIC=$phase")
  var cause=e;while(cause.cause!=null)cause=cause.cause!!
  val kind=when(cause){is SecurityException->"Security";is IllegalStateException->"State";is java.io.IOException->"IO";is ClassNotFoundException->"Class";is NullPointerException->"Null";is IllegalAccessError->"Access";else->"Unavailable"}
  println("PROBE_EXCEPTION=$kind")
 } finally { fixture?.deleteRecursively() }
}
