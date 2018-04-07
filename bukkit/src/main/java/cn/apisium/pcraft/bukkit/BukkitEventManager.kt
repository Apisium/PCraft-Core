package cn.apisium.pcraft.bukkit

import cn.apisium.pcraft.core.EventManager
import org.bukkit.event.HandlerList
import org.bukkit.event.EventPriority
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener
import org.bukkit.plugin.IllegalPluginAccessException

internal class BukkitEventManager (private val plugin: Plugin): EventManager {
  private val packageNames = arrayOf(
    "org.bukkit.event.block.",
    "org.bukkit.event.world.",
    "org.bukkit.event.entity.",
    "org.bukkit.event.player.",
    "org.bukkit.event.server.",
    "org.bukkit.event.vehicle.",
    "org.bukkit.event.weather.",
    "org.bukkit.event.hanging.",
    "org.bukkit.event.inventory.",
    "org.bukkit.event.enchantment."
  )
  private val registered = ArrayList<String>()
  private var handler: ((listener: Listener, event: Event) -> Unit)? = null

  override fun register (name: String) {
    if (name == "") throw IllegalPluginAccessException("Parameter error!")
    if (registered.contains(name)) return
    plugin.server.pluginManager.registerEvent(find(name), Handler(), EventPriority.MONITOR, this.handler, plugin)
    registered.add(name)
  }

  override fun setEmitter (emit: (event: Any) -> Unit) {
    this.handler = fun (_, event) {
      if (event.isAsynchronous) plugin.server.scheduler.runTask(plugin, { emit(event) })
      else emit(event)
    }
  }

  private fun find (name: String): Class<out Event> {
    var clazz: Class<out Event>? = null
    for (pkgName in packageNames) {
      try {
        clazz = Class.forName(pkgName + name + "Event") as Class<out Event>
      } catch (ignored: Exception) { }
    }
    if (clazz == null) throw IllegalPluginAccessException("No such Event: $name")
    return clazz
  }

  private inner class Handler: Listener
}
