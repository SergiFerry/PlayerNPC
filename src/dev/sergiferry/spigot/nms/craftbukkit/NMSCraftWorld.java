package dev.sergiferry.spigot.nms.craftbukkit;

import dev.sergiferry.spigot.nms.NMSUtils;
import net.minecraft.server.level.WorldServer;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Creado por SergiFerry el 04/07/2021
 */

public class NMSCraftWorld {

    private static Class<?> craftWorldClass;
    private static Method craftWorldGetHandle;

    protected static void load() throws ClassNotFoundException, NoSuchMethodException {
        craftWorldClass = NMSUtils.getCraftBukkitClass("CraftWorld");
        craftWorldGetHandle = craftWorldClass.getMethod("getHandle", new Class[0]);
    }

    public static WorldServer getWorldServer(World world){
        try { return (WorldServer) getCraftWorldGetHandle().invoke(getCraftWorldClass().cast(world), new Object[0]); }
        catch (Exception e) { throw new IllegalArgumentException("Error at NMSCraftWorld"); }
    }

    public static Class<?> getCraftWorldClass() {
        return craftWorldClass;
    }

    public static Method getCraftWorldGetHandle() {
        return craftWorldGetHandle;
    }

}
