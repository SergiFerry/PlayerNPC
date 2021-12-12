package dev.sergiferry.spigot.nms;

import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Creado por SergiFerry el 04/07/2021
 */
public class NMSUtils {

    private static String version;

    public static void load() {
        version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        try{
            loadNMS(NMSCraftPlayer.class);
            loadNMS(NMSCraftWorld.class);
            loadNMS(NMSCraftServer.class);
        }
        catch (Exception e){
            throw new IllegalStateException("This NMS version (" + version + ") is not supported.");
        }
    }

    public static Class<?> getCraftBukkitClass(String nmsClassString) throws ClassNotFoundException {
        return getNMSClass("org.bukkit.craftbukkit", nmsClassString);
    }

    public static Class<?> getMinecraftClass(String nmsClassString) throws ClassNotFoundException {
        return Class.forName("net.minecraft." + nmsClassString);
    }

    public static Class<?> getClass(String nmsClassString) throws ClassNotFoundException{
        return Class.forName(nmsClassString);
    }

    public static Class<?> getNMSClass(String prefix, String nmsClassString) throws ClassNotFoundException {
        String name = prefix + "." + version + "." + nmsClassString;
        return Class.forName(name);
    }

    public static void loadNMS(Class<?> c){
        try{
            Method method = c.getDeclaredMethod("load", null);
            method.setAccessible(true);
            method.invoke(null, new Object[0]);
        }
        catch (Exception e){
            e.printStackTrace();
            throw new IllegalStateException("Error loading NMS " + c.getName());
        }
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
