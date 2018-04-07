package cn.apisium.pcraft.bukkit.proxies;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class BlockProxy implements InvocationHandler {
  private Object obj;
  private BlockProxy (Object obj) {
    this.obj = obj;
  }

  public static Block create (Block obj) {
    Class clazz = obj.getClass();
    return (Block) Proxy.newProxyInstance(clazz.getClassLoader(),
      clazz.getInterfaces(), new BlockProxy(obj));
  }

  @Override
  public Object invoke (Object proxy, Method method, Object[] args) {
    try {
      switch (method.getName()) {
        case "setType": return method.invoke(obj, Material.getMaterial((String) args[0]));
        case "getType": return ((Material) method.invoke(obj, args)).name();
      }
      return method.invoke(obj, args);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}

