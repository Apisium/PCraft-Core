package cn.apisium.pcraft.bukkit

import java.io.File
import java.lang.reflect.Method
import java.util.ArrayList

fun getDefines() {
  val list = arrayOf("wait", "notify", "notifyAll", "equals", "getClass", "hashCode", "getHandles",
    "getHandleList", "getRegisteredListeners", "toString", "spigot")
  val notTranslate = arrayOf("java.io.Serializable", "java.lang.Comparable", "java.lang.Cloneable",
    "java.lang.Class", "java.lang.Enum", "java.lang.Object", "java.util.Map", "java.util.Set", "java.util.List")
  val map = mapOf(Pair("java.lang.String", "string"), Pair("java.lang.Integer", "number"),
    Pair("double", "number"), Pair("float", "number"), Pair("byte", "number"),
    Pair("short", "number"), Pair("int", "number"), Pair("java.lang.Object", "any"),
    Pair("char", "string"), Pair("void", "void"), Pair("boolean", "boolean"),
    Pair("long", "number"))
  val list2 = arrayOf("[", "sun.", "java.", "javax.")
  val classes = ArrayList<String>()
  fun translate(clazz: Class<*>?) {
    if (clazz == null || clazz.isAnnotationPresent(Deprecated::class.java) ||
      notTranslate.contains(clazz.name) || clazz.isAssignableFrom(Exception::class.java)) return
    fun getType(clazz: Class<*>?, imports: HashMap<String, String>): String {
      if (clazz == null || notTranslate.contains(clazz.name)) return "void"
      var name = clazz.name
      val isArray = clazz.isArray
      if (isArray) name = name.removePrefix("[L").removeSuffix(";")
      if (clazz.isAssignableFrom(Exception::class.java) || notTranslate.contains(name))
        return if (isArray) "Set<any>" else "any"
      if (clazz.isPrimitive || clazz == String::class.java) {
        val t = map[name]!!
        return if (isArray) "Set<$t>" else t
      }
      try {
        val c = clazz.getField("TYPE").get(null) as Class<*>
        if (c.isPrimitive) {
          val t = map[c.name]!!
          return if (isArray) "Set<$t>" else t
        }
      } catch (ignored: Exception) {}
      var result = imports[name]
      if (result == null) {
        if (!classes.contains(name)) {
          classes.add(name)
          translate(clazz)
        }
        result = clazz.simpleName!!.removeSuffix("[]")
        val re = map[result]
        if (re == null) imports[name] = result else result = re
      }
      return if (isArray) "Set<$result>" else result
    }
    val nn = clazz.name
    var parent: Class<*>? = clazz.superclass
    val imports = hashMapOf<String, String>()
    val exts = ArrayList<String>()
    if (parent != null && parent != Object::class.java) {
      exts.add(getType(parent, imports))
    }
    val supers = HashSet<String>()
    clazz.interfaces.forEach { exts.add(getType(it, imports)) }
    while (parent != null && parent != Object::class.java) {
      parent = parent.superclass
    }

    fun getSupers(clazz: Class<*>?, supers: HashSet<String>, first: Boolean = false) {
      if (clazz == null) return
      clazz.interfaces.forEach { getSupers(it, supers) }
      getSupers(clazz.superclass, supers)
      supers.add(clazz.name)
      if (!first) translate(clazz)
    }
    getSupers(clazz, supers, true)

    val methods = clazz.methods.filter { !list.contains(it.name) && !it.isAnnotationPresent(Deprecated::class.java) }
    val sets = ArrayList<Method>()
    val readonly = StringBuilder()
    val fields = StringBuilder()
    val mes = StringBuilder()
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
              fields.append(field, ": ").appendln(getType(it.returnType, imports))
              sets.add(setterFn)
            }
          } catch (ignored: Exception) {
            readonly.append("readonly ", field, ": ").appendln(getType(it.returnType, imports))
          }
          return false
        }
      }
      return true
    }).filter { !sets.contains(it) }.forEach {
      mes.append(it.name, " (",
        it.parameters.joinToString(", ", transform = { it.name + ": " + getType(it.type, imports) }),
        "): ").appendln(getType(it.returnType, imports))
    }
    if (list2.any { nn.startsWith(it) }) return
    val fileName = nn.replace(".", "/")
    val f = File("classes", fileName + ".d.ts")
    f.parentFile.mkdirs()
    val sb = StringBuilder()
    imports.filter { !notTranslate.contains(it.key) }
      .forEach { sb.append("import ", it.value, " from '", it.key).appendln("'") }
    sb.append("export default interface ${clazz.simpleName}")
    val ee = exts.filter { !map.containsKey(it) }
    if (ee.isNotEmpty()) sb.append(" extends ").append(ee.joinToString(", "))
    sb.appendln(" {").append(readonly, fields, mes, "}")
    f.writeText(sb.toString())

    File("classes", fileName + ".js").writeText("module.exports = [" +
      supers.joinToString(", ", transform = { "'$it'" }) + "]\n")
  }

  PackageUtil
    .getClassName("org.bukkit.event")
    .forEach { translate(Class.forName(it)) }
}