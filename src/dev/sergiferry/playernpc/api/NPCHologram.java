package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.minecraft.network.chat.ChatMessage;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

/**
 * Creado por SergiFerry.
 */
public class NPCHologram {

    private NPC npc;
    private Location location;
    private HashMap<Integer, EntityArmorStand> lines;
    private List<String> text;
    private boolean canSee;

    protected NPCHologram(NPC npc, List<String> text) {
        this.npc = npc;
        this.canSee = false;
        this.text = text;
        create();
    }

    private void create(){
        this.lines = new HashMap<>();
        this.location = new Location(npc.getWorld(), npc.getX(), npc.getY() + (text.size() * 0.27) - 0.27, npc.getZ()).add(0, 2, 0);
        for (int i = 1; i <= text.size(); i++) {
            createLine();
            setLine(i, text.get(i-1));
        }
    }

    protected void setLine(int line, String text) {
        if(!lines.containsKey(line)) return;
        EntityArmorStand as = lines.get(line);
        as.e(true); //setNoGravity
        as.j(true); //setInvisible
        as.a(new ChatMessage(text)); //setCustomName
        as.n(true); //setCustomNameVisible
        if (text == "") as.n(false);
    }

    protected String getLine(int line) {
        if(!lines.containsKey(line)) return "";
        return lines.get(line).Z().getString();
    }

    protected boolean hasLine(int line){
        return lines.containsKey(line);
    }

    protected void createLine() {
        int line = 1;
        for (int i = 1; i < 100; i++) {
            if(lines.containsKey(i)) continue;
            line = i;
            break;
        }
        WorldServer world = null;
        try{ world = (WorldServer) NMSCraftWorld.getCraftWorldGetHandle().invoke(NMSCraftWorld.getCraftWorldClass().cast(location.getWorld()), new Object[0]);}catch (Exception e){}
        Validate.notNull(world, "Error at NMSCraftWorld");
        EntityArmorStand armor = new EntityArmorStand(world, location.getX(), location.getY() - 0.27 * line, location.getZ());
        armor.n(true); //setCustomNameVisible
        armor.e(true); //setNoGravity
        armor.a(new ChatMessage("§f")); //setCustomName
        armor.j(true); //setInvisible
        armor.t(true); //setMarker
        lines.put(line, armor);
    }

    protected void show(){
        if(canSee) return;
        if(npc.isHiddenText()) return;
        if(!npc.isInRange()) return;
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(getPlayer());
        for(Integer line : lines.keySet()){
            EntityArmorStand armor = lines.get(line);
            NMSCraftPlayer.sendPacket(getPlayer(), new PacketPlayOutSpawnEntityLiving(armor));
            NMSCraftPlayer.sendPacket(getPlayer(), new PacketPlayOutEntityMetadata(armor.ae(), armor.ai(), true));
        }
        canSee = true;
    }

    protected void hide(){
        if(!canSee) return;
        for (Integer in : lines.keySet()) {
            NMSCraftPlayer.sendPacket(getPlayer(), NMSPacketPlayOutEntityDestroy.createPacket(lines.get(in).ae()));
        }
        canSee = false;
    }

    protected void update(){
        hide();
        show();
    }

    protected void forceUpdate(){
        hide();
        create();
        show();
    }

    protected void removeHologram() {
        hide();
        lines.clear();
    }

    protected void setText(List<String> text){
        this.text = text;
    }

    protected boolean isCreatedLine(Integer line){
        return lines.containsKey(line);
    }

    protected boolean canSee(){
        return canSee;
    }

    protected Player getPlayer(){
        return npc.getPlayer();
    }

    protected List<String> getText(){
        return npc.getText();
    }

    protected NPC getNpc() {
        return npc;
    }
}

