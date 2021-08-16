package dev.sergiferry.playernpc.nms;

import dev.sergiferry.playernpc.nms.craftbukkit.*;
import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;

/**
 * Creado por SergiFerry el 06/07/2021
 */
public class NMSUtils {

    private static String version;

    public static void load() {
        version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        try {
            //CraftBukkit
            NMSCraftPlayer.load();
            NMSCraftWorld.load();
            NMSCraftServer.load();
            NMSCraftItemStack.load();
            NMSCraftScoreboard.load();
            //Minecraft Server
            NMSPacketPlayOutEntityDestroy.load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Class<?> getCraftBukkitClass(String nmsClassString) throws ClassNotFoundException {
        return getNMSClass("org.bukkit.craftbukkit", nmsClassString);
    }

    public static Class<?> getMinecraftClass(String nmsClassString) throws ClassNotFoundException {
        return Class.forName("net.minecraft." + nmsClassString);
    }

    public static Class<?> getNMSClass(String prefix, String nmsClassString) throws ClassNotFoundException {
        String name = prefix + "." + version + "." + nmsClassString;
        return Class.forName(name);
    }

    public static String getVersion() {
        return version;
    }

    public static Object getValue(Object instance, String name) {
        Object result = null;
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            result = field.get(instance);
            field.setAccessible(false);
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    public static void setValue(Object obj,String name,Object value){
        try{
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(obj, value);
        }catch(Exception e){ e.printStackTrace(); }
    }

}
