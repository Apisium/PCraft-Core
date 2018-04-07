package cn.apisium.pcraft.core

import java.io.InputStreamReader
import java.io.BufferedReader

fun install(): Boolean {
  try {
    val p = try {
      val pb = ProcessBuilder("npm", "install", "--production")
      pb.redirectErrorStream()
      pb.start()
    } catch (ignored: Exception) {
      val pb = ProcessBuilder("npm.cmd", "install", "--production")
      pb.redirectErrorStream()
      pb.start()
    }

    p.waitFor()
    BufferedReader(InputStreamReader(p.inputStream)).forEachLine { print(it) }
    return p.exitValue() == 0
  } catch (e: Exception) {
    e.printStackTrace()
    return false
  }

}