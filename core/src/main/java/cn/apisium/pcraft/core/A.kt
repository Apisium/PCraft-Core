package cn.apisium.pcraft.core

import com.eclipsesource.v8.NodeJS
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import java.io.File
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.ArrayList

fun main(args: Array<String>) {
  val list = arrayOf("wait", "notify", "notifyAll", "equals", "getClass", "hashCode", "getHandles",
    "getHandleList", "getRegisteredListeners", "toString")
  val notTranslate = arrayOf("java.io.Serializable", "java.lang.Comparable", "java.lang.Cloneable",
    "java.lang.Class", "java.lang.Enum")
  val map = mapOf(Pair("java.lang.String", "string"), Pair("java.lang.Integer", "number"),
    Pair("double", "number"), Pair("float", "number"), Pair("byte", "number"),
    Pair("short", "number"), Pair("int", "number"), Pair("java.lang.Object", "any"),
    Pair("char", "string"))
  val n = NodeJS
    .createNodeJS()
  val r = n.runtime
  val s = n.require(File("C:\\node\\PCraft\\t.js")) as V8Array
  val extendsTpl = s.get(0) as V8Function
  val typeTpl = s.get(1) as V8Function
  val classes = arrayListOf<String>()
  fun translate(clazz: Class<*>?) {
    if (clazz == null || clazz.isAnnotationPresent(Deprecated::class.java) ||
      notTranslate.contains(clazz.name)) return
    fun getType(clazz: Class<*>?, imports: HashMap<String, String>): String {
      if (clazz == null || notTranslate.contains(clazz.name)) return "void"
      val name = clazz.name
      if (notTranslate.contains(name)) return "any"
      if (clazz.isPrimitive || clazz == String::class.java) return map[name]!!
      try {
        val c = clazz.getField("TYPE").get(null) as Class<*>
        if (c.isPrimitive) return map[c.name]!!
      } catch (ignored: Exception) {}
      var result = imports[name]
      if (result == null) {
        if (!classes.contains(name)) {
          classes.add(name)
          translate(clazz)
        }
        result = clazz.simpleName + Math.abs(clazz.hashCode()).toString().substring(0, 2)
        imports[name] = result
      }
      return result
    }
    var parent: Class<*>? = clazz.superclass
    val imports = hashMapOf<String, String>()
    val exts = V8Array(r)
    if (parent != null && parent != Object::class.java) {
      exts.push(getType(parent, imports))
    }
    val supers = V8Array(r)
    clazz.interfaces.forEach { exts.push(getType(it, imports)) }
    while (parent != null && parent != Object::class.java) {
      supers.push(parent.name)
      translate(parent)
      parent = clazz.superclass
    }

    val methods = clazz.methods.filter { !list.contains(it.name) && !it.isAnnotationPresent(Deprecated::class.java) }
    val sets = ArrayList<Method>()
    val readonly = V8Array(r)
    val fields = V8Array(r)
    val mes = V8Array(r)
    methods.filter(fun(it): Boolean {
      if (it.parameterCount == 0) {
        val fnName = it.name
        val preIs = fnName.length > 2 && fnName.startsWith("is")
        if (preIs || (fnName.length > 3 && fnName.startsWith("get"))) {
          val field = if (preIs) fnName else fnName[3].toLowerCase() + fnName.substring(4)
          val setter = "set" + fnName.substring(if (preIs) 2 else 3)
          try {
            val setterFn = clazz.getMethod(setter, it.returnType)
            if (setterFn != null) {
              fields.push(V8Object(r).add("name", field).add("type", getType(it.returnType, imports)))
              sets.add(setterFn)
            }
          } catch (ignored: Exception) {
            readonly.push(V8Object(r).add("name", field).add("type", getType(it.returnType, imports)))
          }
          return false
        }
      }
      return true
    }).filter { !sets.contains(it) }.forEach {
      val args = V8Array(r)
      it.parameters.forEach { args.push(V8Object(r)
        .add("name", it.name).add("type", getType(it.type, imports)))}
      mes.push(V8Object(r).add("name", it.name).add("type", getType(it.returnType, imports))
        .add("args", args))
    }
    val imps = V8Array(r)
    imports.filter { !notTranslate.contains(it.key) }
      .forEach { imps.push(V8Object(r).add("name", it.value).add("path", it.key)) }
    val arg = V8Array(r).push(
      V8Object(r).add("name", clazz.simpleName).add("imports", imps).add("exts", exts)
        .add("readonlys", readonly).add("fields", fields).add("methods",mes))
    val f = File("classes", clazz.name.replace(".", "/") + ".d.ts")
    f.parentFile.mkdirs()
    f.writeText(typeTpl.call(null, arg).toString())
    arg.release()
  }

//  PackageUtil
//    .getClassName("org.bukkit.event")
//    .forEach { translate(Class.forName(it)) }
  translate(Class.forName("org.bukkit.entity.Player"))
}