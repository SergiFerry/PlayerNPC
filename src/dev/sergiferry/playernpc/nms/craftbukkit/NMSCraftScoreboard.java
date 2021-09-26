package dev.sergiferry.playernpc.nms.craftbukkit;

import dev.sergiferry.spigot.nms.NMSUtils;

import java.lang.reflect.Method;

/**
 * Creado por SergiFerry el 06/07/2021
 */
public class NMSCraftScoreboard {

    private static Class<?> craftScoreBoardClass;
    private static Method craftScoreBoardGetHandle;

    public static void load() throws ClassNotFoundException, NoSuchMethodException {
        craftScoreBoardClass = NMSUtils.getCraftBukkitClass("scoreboard.CraftScoreboard");
        craftScoreBoardGetHandle = craftScoreBoardClass.getMethod("getHandle", new Class[0]);
    }

    public static Class<?> getCraftScoreBoardClass() {
        return craftScoreBoardClass;
    }

    public static Method getCraftScoreBoardGetHandle() {
        return craftScoreBoardGetHandle;
    }
}
