package dev.sergiferry.playernpc.nms.minecraft;

import dev.sergiferry.spigot.nms.NMSUtils;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;

import java.lang.reflect.Constructor;

public class NMSPacketPlayOutEntityDestroy {

    private static Class<?> packetPlayOutEntityDestroyClass;
    private static boolean singleConstructor;
    private static Constructor constructor;

    public static void load() throws ClassNotFoundException, NoSuchMethodException {
        packetPlayOutEntityDestroyClass = NMSUtils.getMinecraftClass("network.protocol.game.PacketPlayOutEntityDestroy");
        try{
            constructor = packetPlayOutEntityDestroyClass.getConstructor(int[].class);
            singleConstructor = false;
        }
        catch (Exception e){
            constructor = packetPlayOutEntityDestroyClass.getConstructor(int.class);
            singleConstructor = true;
        }
    }

    public static PacketPlayOutEntityDestroy createPacket(int id){
        PacketPlayOutEntityDestroy packet;
        try{
            if(isSingleConstructor()) packet = (PacketPlayOutEntityDestroy) constructor.newInstance(id);
            else packet = (PacketPlayOutEntityDestroy) constructor.newInstance(new int[]{id});
        }
        catch (Exception e){ packet = null; }
        return packet;
    }

    public static boolean isSingleConstructor() {
        return singleConstructor;
    }
}
