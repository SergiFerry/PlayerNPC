package dev.sergiferry.playernpc;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.NPCLib;
import dev.sergiferry.playernpc.command.NPCCommand;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftItemStack;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftScoreboard;
import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.spigot.SpigotPlugin;
import dev.sergiferry.spigot.metrics.Metrics;
import dev.sergiferry.spigot.nms.NMSUtils;
import dev.sergiferry.spigot.server.ServerVersion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class PlayerNPCPlugin extends SpigotPlugin {

    private static PlayerNPCPlugin instance;

    private NPCLib npcLib;

    public PlayerNPCPlugin() {
        //Last 1.17-1.17.1 supported version 2021.4
        super(93625, ServerVersion.VERSION_1_18, ServerVersion.VERSION_1_18_1);
        instance = this;
    }

    @Override
    public void enable() {
        NMSUtils.loadNMS(NMSCraftItemStack.class);
        NMSUtils.loadNMS(NMSCraftScoreboard.class);
        NMSUtils.loadNMS(NMSPacketPlayOutEntityDestroy.class);
        setPrefix("§6§lPlayerNPC §8| §7");
        try{
            Constructor<NPCLib> constructor = NPCLib.class.getDeclaredConstructor(PlayerNPCPlugin.class);
            constructor.setAccessible(true);
            this.npcLib = constructor.newInstance(this);
            constructor.setAccessible(false);
        }catch (Exception e){ e.printStackTrace(); }
        new NPCCommand(this);
        callPrivate("onEnable");
        setupMetrics(11918);
        super.getMetrics().addCustomChart(new Metrics.SingleLineChart("npcs", () -> {
            Set<NPC> npcSet = new HashSet<>();
            getServer().getOnlinePlayers().forEach(x-> npcSet.addAll(npcLib.getAllNPCs(x)));
            return npcSet.size();
        }));
    }

    @Override
    public void disable() {
        callPrivate("onDisable");
    }

    private void callPrivate(String m){
        try{
            Method method = NPCLib.class.getDeclaredMethod(m, PlayerNPCPlugin.class);
            method.setAccessible(true);
            method.invoke(npcLib, this);
            method.setAccessible(false);
        }
        catch (Exception e){}
    }

    public NPCLib getNPCLib() {
        return npcLib;
    }

    public static PlayerNPCPlugin getInstance() {
        return instance;
    }

}
