package cn.apisium.pcraft.core

import com.eclipsesource.v8.*
import io.alicorn.v8.V8JavaAdapter
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays

class PCraft {
  private val node = NodeJS.createNodeJS()
  private val v8 = node.runtime
  private val root = System.getProperty("user.dir")
  private var app: V8Object? = null
  private var eventManager: EventManager? = null
  private var commandManager: CommandManager? = null

  fun release () {
    eventManager?.unregisterAll()
    commandManager?.unregisterAll()
    app?.executeVoidFunction("disable", null)
    app?.release()
  }

  fun init (server: Any, helpers: V8Object, type: String,
    eventManager: EventManager, commandManager: CommandManager) {
    val config = this
      .write(".npmrc")
      .write("package.json")
      .write("pcraft-setup.js", false)
      .checkModules()
      .getPackage()

    eventManager.setEmitter {
      val args = V8Array(v8)

      val obj = ProxyObject(it)
      val name = "__object_" + obj.hashCode()
      V8JavaAdapter.injectObject(name, obj, v8)
      val o = v8.getObject(name)

      args.push(o)
      v8.addUndefined(name)

      app?.executeVoidFunction("emit", args)
      o.release()
      args.release()

      while (node.isRunning) node.handleMessage()
    }

    val obj = V8Object(v8)
    val args = V8Array(v8)

    val serverObj = ProxyObject(server)
    val objName = "__object_" + serverObj.hashCode()
    V8JavaAdapter.injectObject(objName, serverObj, v8)
    val obj2 = v8.getObject(objName)
    v8.addUndefined(objName)
    args.push(obj
      .add("type", type)
      .add("pkg", config)
      .add("helpers", helpers)
      .add("server", obj2)
      .registerJavaMethod(fun (_, args) {
        for (name in args.getStrings(0, args.length())) {
          try {
            eventManager.register(name)
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }, "registerEvent")
      .registerJavaMethod(fun (_, args) {
        try {
          val arr = args.getArray(3)
          val fn = args.get(0) as V8Function
          val isNull = args.getType(2) == V8Value.UNDEFINED
          commandManager.register(
            fun (sender, args, curAlias) {
              val arg = V8Array(v8)

              val obj3 = ProxyObject(sender)
              val id = "__object_" + obj3.hashCode()
              V8JavaAdapter.injectObject(id, obj3, v8)
              val o = v8.getObject(id)

              arg.push(o).push(args).push(curAlias)
              v8.addUndefined(id)

              fn.call(null, arg)
              o.release()
              arg.release()

              while (node.isRunning) node.handleMessage()
            },
            args.getString(1),
            if (args.getType(2) == V8Value.STRING) args.getString(2) else null,
            if (isNull) null else arr.getStrings(0, arr.length())
          )
          if (! isNull) arr.release()
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, "registerCommand")
    )

    helpers.release()
    obj2.release()

    val app = (node
      .require(File(root, "pcraft-setup.js")) as V8Function)
      .call(null, args) as V8Object

    if (app.isUndefined) throw Exception("Initialization abnormality!")

    obj.release()
    args.release()
    this.eventManager = eventManager
    this.commandManager = commandManager
    this.app = app

    while (node.isRunning) node.handleMessage();
  }


  private fun getPackage (): String {
    return String(Files.readAllBytes(Paths.get(root, "package.json")))
  }

  private fun checkModules (): PCraft {
    if (!File(root, "node_modules/babel-polyfill")
      .isDirectory && !install()) {
      print("Cannot to install all modules!")
    }
    return this
  }

  private fun write (name: String, check: Boolean = true): PCraft {
    val pkg = Paths.get(System.getProperty("user.dir"), name)
    if (!check || !pkg.toFile().isFile) {
      Files.copy(this.javaClass.getResourceAsStream(name), pkg)
    }
    return this
  }
}
