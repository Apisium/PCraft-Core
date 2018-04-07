package cn.apisium.pcraft.core

interface CommandManager {
  fun register(callback: (sender: Any, args: String, curAlias: String) ->
  Unit, name: String, description: String?, alias: Array<String>?)

  fun unregister(name: String)
  fun unregisterAll()
}
