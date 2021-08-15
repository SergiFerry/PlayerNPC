package dev.sergiferry.playernpc.nms.minecraft;

import dev.sergiferry.playernpc.nms.NMSUtils;
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

    public static PacketPlayOutEntityDestroy createPacket(int... ids){
        if(isSingleConstructor() && ids.length > 1) throw new IllegalArgumentException("Error at NMSPacketPlayOutEntityDestroy, caused because in 1.17 version cannot be more than one id at the packet.");
        PacketPlayOutEntityDestroy packet;
        try{
            if(isSingleConstructor()) packet = (PacketPlayOutEntityDestroy) constructor.newInstance(ids[0]);
            else packet = (PacketPlayOutEntityDestroy) constructor.newInstance(ids);
        }
        catch (Exception e){ packet = null; }
        return packet;
    }

    public static boolean isSingleConstructor() {
        return singleConstructor;
    }
}
