package cn.apisium.pcraft.bukkit

import cn.apisium.pcraft.core.CommandManager
import org.bukkit.Server
import org.bukkit.command.SimpleCommandMap
import org.bukkit.command.defaults.BukkitCommand
import org.bukkit.plugin.Plugin
import org.bukkit.command.CommandSender

internal class BukkitCommandManager (server: Server): CommandManager {
  private val map: SimpleCommandMap
  private val list = HashMap<String, BukkitCommand>()

  init {
    val method = server.javaClass.getDeclaredField("commandMap")
    method.isAccessible = true
    map = method.get(server) as SimpleCommandMap
  }

  override fun register(callback: (sender: Any, args: String, curAlias: String) -> Unit,
    name: String, description: String?, alias: Array<String>?) {
    val cmd = Command(callback, name, description, alias)
    map.register("pcraft", cmd)
    list[name] = cmd
  }

  override fun unregister(name: String) {
    val cmd = list[name]
    if (cmd != null) {
      cmd.unregister(map)
      list.remove(name)
    }
  }

  override fun unregisterAll() {
    list.forEach { it.value.unregister(map) }
    list.clear()
  }

  private inner class Command (private val callback: (sender: Any, args: String, curAlias: String) -> Unit,
    name: String, description: String?, aliases: Array<String>?): BukkitCommand(name) {
    init {
      this.description = description ?: ""
      this.usage = "/$name -h"
      if (aliases != null) this.aliases = aliases.toList()
    }

    override fun execute(sender: CommandSender, alias: String, args: Array<out String>): Boolean {
      callback(sender, args.joinToString(" "), alias)
      return true
    }
  }
}
