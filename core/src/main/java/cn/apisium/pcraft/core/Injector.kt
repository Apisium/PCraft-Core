package cn.apisium.pcraft.core

import com.eclipsesource.v8.*
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.*

open class Injector(private val v8: V8, private val filter: (name: String) -> Boolean = { _ -> true }) {
  private val list = arrayOf("wait", "notify", "notifyAll", "equals", "getClass", "hashCode", "getHandles",
    "getHandleList", "getRegisteredListeners")
  private val objects = HashMap<Int, WeakReference<Any>>()
  private val define: V8Function

  init {
    val callback = V8Function(v8, fun(_, _args): Any? {
      val hash = _args.getInteger(0)
      val oo = objects[hash]
      val obj = oo?.get()
      if (oo != null && obj == null) objects.remove(hash)
      if (obj == null) return null
      return if (_args.getType(2) == V8Value.V8_ARRAY) {
        val args = getArgs(_args.getArray(2))
        val method = obj.javaClass.getMethod(_args.getString(1), *getTypes(args))
        val result = method.invoke(obj, *args)
        if (method.returnType.isPrimitive) result else translate(result)
      } else {
        val method = obj.javaClass.getMethod(_args.getString(1))
        val result = method.invoke(obj)
        if (method.returnType.isPrimitive) result else translate(result)
      }
    })
    val createObject = V8Function(v8, fun(_, args): Any? {
      val name = args.getString(0)
      return if (name == null || !filter(name)) null
      else try {
        val pars = getArgs(args.getArray(1))
        val result = Class.forName(name).getConstructor(*getTypes(pars)).newInstance(*pars)
        if (result.javaClass.isPrimitive) result else translate(result)
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    })
    val args = V8Array(v8).push(callback).push(createObject)
    val fn = v8.executeObjectScript("""(call, create) => {
      'use strict'
      Object.defineProperty(global, '__createJavaObject', { value: function Create () {
        if (this instanceof Create) return create.apply(null, arguments)
        else throw new TypeError("Constructor Proxy requires 'new'")
      } })
      function f (hash, name, args) {
        if (args) {
          const len = args.length
          for (let i = 0; i < len; i++) if (args[i] != null && args[i].javaHashCode)
            args[i] = { javaHashCode: args[i].javaHashCode }
        }
        return call(hash, name, args)
      }
      function error (v) { throw new Error('The value of the object cannot be set.') }
      return (props, className, hash, supers) => {
        props.javaClass = { value: className }
        props.javaHashCode = { value: hash }
        props.javaSuperClass = { value: Object.freeze(supers) }
        for (const name in props) {
          const obj = props[name]
          if (obj === true) props[name] = { enumerable: true, value () { return f(hash, name, arguments) } }
          else {
            obj.enumerable = true
            const set = obj.set
            const get = obj.get
            if (!('value' in obj)) {
              obj.set = set ? v => f(hash, set, [v]) : error
              if (get) obj.get = () => f(hash, get)
            }
          }
        }
        return Object.defineProperties({}, props)
      }
    }""") as V8Function
    define = fn.call(null, args) as V8Function
    fn.release()
    callback.release()
    createObject.release()
    args.release()
  }

  fun inject(target: V8Object, obj: Any, name: String): Injector {
    target.add(name, translate(obj))
    return this
  }

  fun translate(obj: Any): V8Object {
    val code = obj.hashCode()
    val javaObj = objects[code]
    if (javaObj?.get() == null) objects[code] = WeakReference(obj)
    val clazz = obj.javaClass
    val o = V8Object(v8)
    val methods = clazz.methods.filter { !list.contains(it.name) && !it.isAnnotationPresent(Deprecated::class.java) }
    val sets = ArrayList<Method>()
    methods.filter(fun(it): Boolean {
      if (it.parameterCount == 0) {
        val fnName = it.name
        val preIs = fnName.length > 2 && fnName.startsWith("is")
        if (preIs || (fnName.length > 3 && fnName.startsWith("get"))) {
          val prop = V8Object(v8).add("get", fnName)
          val setter = "set" + fnName.substring(if (preIs) 2 else 3)
          try {
            val setterFn = clazz.getMethod(setter, it.returnType)
            if (setterFn != null) {
              prop.add("set", setter)
              sets.add(setterFn)
            }
          } catch (ignored: Exception) {
          }
          o.add(if (preIs) fnName else fnName[3].toLowerCase() + fnName.substring(4), prop)
          return false
        }
      }
      return true
    }).mapNotNull { if (sets.contains(it)) null else it.name }.distinct().forEach { o.add(it, true) }
    var parent: Class<*>? = clazz.superclass
    val superclass = V8Array(v8)
    while (parent != null) {
      superclass.push(parent.name)
      parent = parent.superclass
    }
    val args = V8Array(v8).push(o).push(clazz.name).push(code).push(superclass)
    val result = this.define.call(null, args) as V8Object
    superclass.release()
    o.release()
    args.release()
    return result
  }

  private fun getArgs(args: V8Array): Array<Any> = Array(args.length(), { i ->
    val arg = args.get(i)
    if (arg.javaClass.isPrimitive || (arg.javaClass.getField("TYPE").get(null) as Class<*>).isPrimitive)
      arg else translate(objects[(arg as V8Object).getInteger("javaHashCode")]!!.get()!!)
  })

  private fun getTypes(args: Array<*>): Array<Class<*>> = args.map {
    val cls = it!!::class.java
    if (cls.isPrimitive) cls else {
      val c = (cls.getField("TYPE").get(null) as Class<*>)
      if (c.isPrimitive) c else cls
    }
  }.toTypedArray()
}
