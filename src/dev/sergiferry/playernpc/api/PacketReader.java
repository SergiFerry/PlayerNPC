package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.world.EnumHand;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

/**
 * Creado por SergiFerry.
 */
public class PacketReader {

    private HashMap<NPC, Long> lastClick;
    private NPCPlayerManager npcPlayerManager;
    private Channel channel;

    protected PacketReader(NPCPlayerManager npcPlayerManager){
        this.npcPlayerManager = npcPlayerManager;
        this.lastClick = new HashMap<>();
    }

    protected void inject() {
        if(channel != null) return;
        channel = NMSCraftPlayer.getPlayerConnection(npcPlayerManager.getPlayer()).a.k;
        if(channel.pipeline() == null) return;
        if(channel.pipeline().get("PacketInjector") != null) return;
        channel.pipeline().addAfter("decoder", "PacketInjector", new MessageToMessageDecoder<PacketPlayInUseEntity>() {
            @Override
            protected void decode(ChannelHandlerContext channel, PacketPlayInUseEntity packet, List<Object> arg) throws Exception {
                arg.add(packet);
                readPacket(packet);
            }
        });
    }

    protected void unInject() {
        if(channel == null) return;
        if(channel.pipeline() == null) return;
        if(channel.pipeline().get("PacketInjector") == null) return;
        channel.pipeline().remove("PacketInjector");
        channel = null;
    }

    private void readPacket(Packet<?> packet) {
        if(packet == null) return;
        if (!packet.getClass().getSimpleName().equalsIgnoreCase("PacketPlayInUseEntity")) return;
        int id = (int) getValue(packet, "a");
        NPCInteractEvent.ClickType clickType;
        try{
            Object action = getValue(packet, "b");
            EnumHand hand = (EnumHand) getValue(action, "a");
            if(hand != null) clickType = NPCInteractEvent.ClickType.RIGHT_CLICK;
            else clickType = NPCInteractEvent.ClickType.LEFT_CLICK;
        }
        catch (Exception e){ clickType = NPCInteractEvent.ClickType.LEFT_CLICK; }
        interact(id, clickType);
    }

    private void interact(Integer id, NPCInteractEvent.ClickType clickType){
        NPC npc = getNPCLib().getNPCPlayerManager(getNpcPlayerManager().getPlayer()).getNPC(id);
        if(npc == null) return;
        interact(npc, clickType);
    }

    private void interact(NPC npc, NPCInteractEvent.ClickType clickType){
        if(npc == null) return;
        if(lastClick.containsKey(npc) && System.currentTimeMillis() - lastClick.get(npc) < npc.getInteractCooldown()) return;
        lastClick.put(npc, System.currentTimeMillis());
        Bukkit.getScheduler().scheduleSyncDelayedTask(npcPlayerManager.getNPCLib().getPlugin(), ()-> {
            npc.interact(npcPlayerManager.getPlayer(), clickType);
        }, 1);
    }

    private Object getValue(Object instance, String name) {
        Object result = null;
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            result = field.get(instance);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {}
        return result;
    }

    protected NPCPlayerManager getNpcPlayerManager() {
        return npcPlayerManager;
    }

    protected NPCLib getNPCLib(){
        return npcPlayerManager.getNPCLib();
    }

}
