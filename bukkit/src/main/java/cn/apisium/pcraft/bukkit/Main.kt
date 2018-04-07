package cn.apisium.pcraft.bukkit

import cn.apisium.pcraft.bukkit.proxies.ServerProxy
import cn.apisium.pcraft.core.CommandManager
import cn.apisium.pcraft.core.EventManager
import cn.apisium.pcraft.core.PCraft
import com.eclipsesource.v8.*
import org.bukkit.plugin.java.JavaPlugin

class Main: JavaPlugin() {
  private val core = PCraft()
  override fun onEnable () {
    this.logger.info("Loading...")

    try {
      core.init(
        ServerProxy.create(this.server),
        "bukkit",
        BukkitEventManager(this),
        BukkitCommandManager(this.server),
        V8Object(core.v8)
          .registerJavaMethod(fun (_, args): Int {
            try {
              if (args.getType(0) == V8Value.V8_FUNCTION) {
                val callback = args.getObject(0) as V8Function
                val time = if (args.getType(1) == V8Value.INTEGER) (args.getInteger(1) * 0.02).toLong()
                  else 0
                return if (time <= 0) {
                  callback.call(null, null)
                  callback.release()
                  0
                } else {
                  this.server.scheduler.runTaskLater(this, {
                    callback.call(null, null)
                    callback.release()
                  }, time).taskId
                }
              }
            } catch (e: Exception) {
              e.printStackTrace()
            }
            return 0
          }, "setTimeout")
          .registerJavaMethod(fun (_, args) {
            if (args.getType(0) == V8Value.INTEGER) this.server.scheduler.cancelTask(args.getInteger(0))
          }, "clearTimeout")
      )
    } catch (e: Exception) {
      e.printStackTrace()
      this.isEnabled = false
      return
    }

    this.logger.info("Loaded successful!")
  }

  override fun onDisable () {
    core.release()
  }
}
