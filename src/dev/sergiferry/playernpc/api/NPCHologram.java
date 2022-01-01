package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.minecraft.network.chat.ChatMessage;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Creado por SergiFerry.
 */
public class NPCHologram {

    private NPC npc;
    private Location location;
    private HashMap<Integer, List<EntityArmorStand>> lines;
    private boolean canSee;

    protected NPCHologram(NPC npc) {
        this.npc = npc;
        this.canSee = false;
        create();
    }

    private void create(){
        this.lines = new HashMap<>();
        this.location = new Location(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ()).add(npc.getTextAlignment());
        for (int i = 1; i <= getText().size(); i++) {
            createLine();
            setLine(i, getText().get(i-1));
        }
    }

    protected void createLine() {
        int line = 1;
        for (int i = 1; i < 100; i++) {
            if(lines.containsKey(i)) continue;
            line = i;
            break;
        }
        NPC.TextOpacity textOpacity = getLinesOpacity().containsKey(line) ? getLinesOpacity().get(line) : npc.getTextOpacity();
        WorldServer world = null;
        try{ world = (WorldServer) NMSCraftWorld.getCraftWorldGetHandle().invoke(NMSCraftWorld.getCraftWorldClass().cast(location.getWorld()), new Object[0]);}catch (Exception e){}
        Validate.notNull(world, "Error at NMSCraftWorld");
        List<EntityArmorStand> armorStands = new ArrayList<>();
        for(int i = 1; i <= textOpacity.getTimes(); i++){
            EntityArmorStand armor = new EntityArmorStand(world, location.getX(), location.getY() + (npc.getLineSpacing() * ((getText().size() - line))), location.getZ());
            armor.n(true); //setCustomNameVisible
            armor.e(true); //setNoGravity
            armor.a(new ChatMessage("§f")); //setCustomName
            armor.j(true); //setInvisible
            armor.t(true); //setMarker
            armorStands.add(armor);
        }
        lines.put(line, armorStands);
    }


    protected void setLine(int line, String text) {
        if(!lines.containsKey(line)) return;
        for(EntityArmorStand as : lines.get(line)){
            as.e(true); //setNoGravity
            as.j(true); //setInvisible
            as.a(new ChatMessage(text)); //setCustomName
            as.n(text != null && text != ""); //setCustomNameVisible
        }
    }

    protected String getLine(int line) {
        if(!lines.containsKey(line)) return "";
        return lines.get(line).get(0).Z().getString(); //Z getCustomName
    }

    protected boolean hasLine(int line){
        return lines.containsKey(line);
    }

    protected void show(){
        if(canSee) return;
        if(npc.isHiddenText()) return;
        if(!npc.isInRange()) return;
        for(Integer line : lines.keySet()){
            for(EntityArmorStand armor : lines.get(line)){
                NMSCraftPlayer.sendPacket(getPlayer(), new PacketPlayOutSpawnEntityLiving(armor));
                NMSCraftPlayer.sendPacket(getPlayer(), new PacketPlayOutEntityMetadata(armor.ae(), armor.ai(), true)); //ae getID //ai getDataWatcher
            }
        }
        canSee = true;
    }

    protected void hide(){
        if(!canSee) return;
        for (Integer in : lines.keySet()) {
            for(EntityArmorStand armor : lines.get(in)){
                NMSCraftPlayer.sendPacket(getPlayer(), NMSPacketPlayOutEntityDestroy.createPacket(armor.ae())); //ae getID
            }
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

    protected HashMap<Integer, NPC.TextOpacity> getLinesOpacity() { return npc.getLinesOpacity(); }

    protected NPC getNpc() {
        return npc;
    }

}

