package cn.apisium.pcraft.core

import com.eclipsesource.v8.*
import io.alicorn.v8.V8JavaAdapter
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays

class PCraft {
  val node = NodeJS.createNodeJS()
  val v8 = node.runtime

  private val root = System.getProperty("user.dir")
  private var app: V8Object? = null
  private var eventManager: EventManager? = null
  private var commandManager: CommandManager? = null

  fun inject (obj: Any): V8Object {
    val name = "__object_" + obj.hashCode()
    V8JavaAdapter.injectObject(name, obj, v8)
    val obj2 = v8.getObject(name)
    v8.addUndefined(name)
    return obj2
  }

  fun release () {
    eventManager?.unregisterAll()
    commandManager?.unregisterAll()
    app?.executeVoidFunction("disable", null)
    app?.release()
  }

  fun init (server: Any, type: String, eventManager: EventManager,
    commandManager: CommandManager, helpers: V8Object?) {
    val config = this
      .write("/.npmrc")
      .write("/package.json")
      .write("/pcraft-setup.js", false)
      .checkModules()
      .getPackage()

    eventManager.setEmitter {
      val args = V8Array(v8)
      val obj = inject(it)

      app?.executeVoidFunction("emit", args.push(obj))
      obj.release()
      args.release()

      while (node.isRunning) node.handleMessage()
    }

    val obj = V8Object(v8)
    val args = V8Array(v8)

    val serverObj = inject(server)
    args.push(obj
      .add("type", type)
      .add("pkg", config)
      .add("helpers", helpers)
      .add("server", serverObj)
      .registerJavaMethod(fun (_, args) {
        args.getStrings(0, args.length()).forEach { eventManager.register(it) }
      }, "registerEvent")
      .registerJavaMethod(fun (_, args) {
        args.getStrings(0, args.length()).forEach { eventManager.unregister(it) }
      }, "unregisterEvent")
      .registerJavaMethod(fun (_, args) {
        val arr = args.getArray(3)
        val fn = args.get(0) as V8Function
        val isNull = args.getType(2) == V8Value.UNDEFINED
        commandManager.register(
          fun (sender, args, curAlias) {
            val arg = V8Array(v8)
            val obj3 = inject(sender)

            fn.call(null, arg.push(obj3).push(args).push(curAlias))
            obj3.release()
            arg.release()

            while (node.isRunning) node.handleMessage()
          },
          args.getString(1),
          if (args.getType(2) == V8Value.STRING) args.getString(2) else null,
          if (isNull) null else arr.getStrings(0, arr.length())
        )
        if (!isNull) arr.release()
      }, "registerCommand")
      .registerJavaMethod(fun (_, args) {
        args.getStrings(0, args.length()).forEach { commandManager.unregister(it) }
      }, "unregisterCommand")
    )

    helpers?.release()
    serverObj.release()

    val app = (node
      .require(File(root, "pcraft-setup.js")) as V8Function)
      .call(null, args) as V8Object

    if (app.isUndefined) throw Exception("Initialization abnormality!")

    obj.release()
    args.release()
    this.eventManager = eventManager
    this.commandManager = commandManager
    this.app = app

    while (node.isRunning) node.handleMessage()
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
