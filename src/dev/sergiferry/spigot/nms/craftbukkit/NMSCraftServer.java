package dev.sergiferry.spigot.nms.craftbukkit;

import dev.sergiferry.spigot.nms.NMSUtils;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Method;

/**
 * Creado por SergiFerry el 25/09/2021
 */
public class NMSCraftServer {

    private static Class<?> craftServerClass;
    private static Method craftServerGetServer;

    protected static void load() throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        craftServerClass = NMSUtils.getCraftBukkitClass("CraftServer");
        craftServerGetServer = craftServerClass.getMethod("getServer", new Class[0]);
    }

    public static Class<?> getCraftServerClass() {
        return craftServerClass;
    }

    public static Method getCraftServerGetServer() {
        return craftServerGetServer;
    }

    public static MinecraftServer getMinecraftServer(Server server){
        try{ return (MinecraftServer) NMSCraftServer.getCraftServerGetServer().invoke(NMSCraftServer.getCraftServerClass().cast(server), new Object[0]); }
        catch (Exception e){ throw new IllegalArgumentException("Error at NMSCraftServer");}
    }

    public static MinecraftServer getMinecraftServer(){
        return getMinecraftServer(Bukkit.getServer());
    }
}
