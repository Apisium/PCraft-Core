package cn.apisium.pcraft.bukkit.proxies;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;

public class ServerProxy implements InvocationHandler {
  private Object obj;
  private ServerProxy (Object obj) {
    this.obj = obj;
  }

  public static Server create (Server obj) {
    Class clazz = obj.getClass();
    return (Server) Proxy.newProxyInstance(clazz.getClassLoader(),
      clazz.getInterfaces(), new ServerProxy(obj));
  }

  @Override
  public Object invoke (Object proxy, Method method, Object[] args) {
    try {
      final Object result = method.invoke(obj, args);
      switch (method.getName()) {
        case "getOnlinePlayers": return ((Collection<Player>) result).toArray();
      }
      return result;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}

