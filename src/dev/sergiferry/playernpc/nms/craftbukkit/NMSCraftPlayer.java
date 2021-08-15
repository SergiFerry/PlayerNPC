package dev.sergiferry.playernpc.nms.craftbukkit;

import dev.sergiferry.playernpc.nms.NMSUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.PlayerConnection;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Creado por SergiFerry el 04/07/2021
 */
public class NMSCraftPlayer {

    private static Class<?> craftPlayerClass;
    private static Method craftPlayerGetHandle;
    private static Field playerConnectionField;
    private static Method sendPacketMethod;

    public static void load() throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        craftPlayerClass = NMSUtils.getCraftBukkitClass("entity.CraftPlayer");
        craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle", new Class[0]);
        playerConnectionField = craftPlayerGetHandle.getReturnType().getField("b");
        sendPacketMethod = Arrays.asList(playerConnectionField.getType().getMethods()).stream().filter(each -> each.getName().equals("sendPacket")).findFirst().get();
    }

    public static PlayerConnection getPlayerConnection(Player player){
        try{ return (PlayerConnection) getPlayerConnectionField().get(getEntityPlayer(player)); }
        catch (Exception e){ throw new IllegalArgumentException("Error at NMSCraftPlayer");}
    }

    public static EntityPlayer getEntityPlayer(Player player){
        try { return (EntityPlayer) getCraftPlayerGetHandle().invoke(getCraftPlayerClass().cast(player), new Object[0]); }
        catch (Exception e) { throw new IllegalArgumentException("Error at NMSCraftPlayer"); }
    }

    public static void sendPacket(Player player, Packet packet){
        getPlayerConnection(player).sendPacket(packet);
    }

    public static Class<?> getCraftPlayerClass() {
        return craftPlayerClass;
    }

    public static Method getCraftPlayerGetHandle() {
        return craftPlayerGetHandle;
    }

    public static Field getPlayerConnectionField() {
        return playerConnectionField;
    }

    public static Method getSendPacketMethod() {
        return sendPacketMethod;
    }
}

