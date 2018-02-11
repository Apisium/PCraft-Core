package cn.apisium.pcraft.core

interface EventManager {
  fun setEmitter (emit: (event: Any) -> Unit)
  fun register (name: String)
  fun unregister (name: String)
  fun unregisterAll ()
}
