package dev.sergiferry.playernpc.api;

import org.apache.commons.lang.Validate;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creado por SergiFerry.
 */
public class NPCPlayerManager {

    private final NPCLib npcLib;
    private final Player player;
    private final HashMap<String, NPC> npcs;
    private final PacketReader packetReader;
    protected final Map<World, Set<NPC>> hidden;
    private final Long lastEnter;

    protected NPCPlayerManager(NPCLib npcLib, Player player) {
        this.npcLib = npcLib;
        this.player = player;
        this.npcs = new HashMap<>();
        this.packetReader = new PacketReader(this);
        this.hidden = new HashMap<>();
        this.lastEnter = System.currentTimeMillis();
    }

    protected void set(String s, NPC npc){
        npcs.put(s, npc);
    }

    protected NPC getNPC(Integer entityID){
        return npcs.values().stream().filter(x-> x.isCreated() && x.getEntityPlayer().ae() == entityID).findAny().orElse(null);
    }

    protected void removeNPC(String code){
        npcs.remove(code);
    }

    protected Set<NPC> getNPCs(String prefix){
        return getNpcs().values().stream().filter(x-> x.getCode().startsWith(prefix)).collect(Collectors.toSet());
    }

    protected void updateMove(){
        getNPCs(getPlayer().getWorld()).forEach(x->{
            if(x.isCreated()) x.updateMove();
        });
    }

    protected void destroyWorld(World world){
        Set<NPC> r = new HashSet<>();
        npcs.values().stream().filter(x-> x.getWorld().getName().equals(world.getName())).forEach(x->{
            if(x.canSee()){
                x.hide();
                r.add(x);
            }
        });
        hidden.put(world, r);
    }

    protected void showWorld(World world){
        if(!hidden.containsKey(world)) return;
        hidden.get(world).forEach(x-> {
            if(x.isCreated()) x.show();
        });
        hidden.remove(world);
    }

    protected void destroyAll(){
        Set<NPC> destroy = new HashSet<>();
        destroy.addAll(npcs.values());
        destroy.stream().forEach(x-> {
            if(x.isCreated()){
                x.destroy();
            }
        });
        npcs.clear();
    }

    protected Set<NPC> getNPCs(World world){
        Validate.notNull(world, "World must be not null");
        return npcs.values().stream().filter(x-> x.getWorld().equals(world)).collect(Collectors.toSet());
    }

    protected Set<NPC> getNPCs(){
        return  npcs.values().stream().collect(Collectors.toSet());
    }

    protected NPC getNPC(String s){
        if(!npcs.containsKey(s)) return null;
        return npcs.get(s);
    }

    private HashMap<String, NPC> getNpcs() {
        return npcs;
    }

    protected NPCLib getNPCLib() {
        return npcLib;
    }

    protected Player getPlayer() {
        return player;
    }

    protected PacketReader getPacketReader() {
        return packetReader;
    }

    protected Long getLastEnter() {
        return lastEnter;
    }

}
