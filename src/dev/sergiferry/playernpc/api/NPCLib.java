package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import net.minecraft.world.entity.animal.axolotl.ValidatePlayDead;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.org.apache.http.annotation.Experimental;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Creado por SergiFerry.
 */
public class NPCLib implements Listener {

    private static NPCLib instance;

    private final Plugin plugin;
    private final HashMap<Player, NPCPlayerManager> playerManager;
    private Double hideDistance;
    private UpdateLookType updateLookType;
    private Integer updateLookTicks;

    public NPCLib(PlayerNPCPlugin plugin){
        this.plugin = plugin;
        this.playerManager = new HashMap<>();
        instance = this;
        hideDistance = 50.0;
        updateLookType = UpdateLookType.MOVE_EVENT;
        updateLookTicks = 5;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public NPC generateNPC(@Nonnull Player player, @Nonnull String code, @Nonnull Location location){
        Validate.notNull(player, "You cannot create an NPC with a null Player");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        NPC old = getNPCPlayerManager(player).getNPC(code);
        if(old != null) return old;
        return new NPC(this, player, code, location);
    }

    public NPC getNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(id);
    }

    public boolean hasNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPC(player, id) != null;
    }

    public NPC removeNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return removeNPC(player, getNPC(player, id));
    }

    public NPC removeNPC(@Nonnull Player player, @Nonnull NPC npc){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(npc, "NPC was not found");
        npc.destroy();
        getNPCPlayerManager(player).removeNPC(npc.getCode());
        return npc;
    }

    public Set<NPC> getGlobalNPC(@Nonnull String code){
        Validate.notNull(code, "Code cannot be null");
        Set<NPC> npcs = new HashSet<>();
        Bukkit.getOnlinePlayers().stream().filter(x-> getNPC(x, code) != null).forEach(x-> npcs.add(getNPC(x, code)));
        return npcs;
    }

    public Set<NPC> getAllNPCs(@Nonnull Player player){
        return getNPCPlayerManager(player).getNPCs();
    }

    public Double getDefaultHideDistance() {
        return hideDistance;
    }

    public void setDefaultHideDistance(Double hideDistance) {
        this.hideDistance = hideDistance;
    }

    public void onEnable(PlayerNPCPlugin playerNPCPlugin){
        playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> join(x));
    }

    public void onDisable(PlayerNPCPlugin playerNPCPlugin){
        playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> quit(x));
    }

    public static NPCLib getInstance(){
        return instance;
    }

    private PlayerNPCPlugin getPlayerNPCPlugin(){ return (PlayerNPCPlugin) plugin;}

    protected NPCPlayerManager getNPCPlayerManager(Player player){
        if(playerManager.containsKey(player)) return playerManager.get(player);
        NPCPlayerManager npcPlayerManager = new NPCPlayerManager(this, player);
        playerManager.put(player, npcPlayerManager);
        return npcPlayerManager;
    }

    private void join(Player player){
        NPCPlayerManager npcPlayerManager = getNPCPlayerManager(player);
        PacketReader reader = npcPlayerManager.getPacketReader();
        reader.inject();
    }

    private void quit(Player player){
        NPCPlayerManager npcPlayerManager = getNPCPlayerManager(player);
        npcPlayerManager.destroyAll();
        npcPlayerManager.getPacketReader().unInject();
    }

    public UpdateLookType getUpdateLookType() {
        return updateLookType;
    }

    private Integer taskID;

    public void setUpdateLookTicks(Integer ticks){
        this.updateLookTicks = ticks;
        setUpdateLookType(UpdateLookType.TICKS);
    }

    public void setUpdateLookType(UpdateLookType updateLookType) {
        this.updateLookType = updateLookType;
        if(taskID != null) getPlayerNPCPlugin().getServer().getScheduler().cancelTask(taskID);
        if(updateLookType.equals(UpdateLookType.TICKS)){
            taskID = getPlayerNPCPlugin().getServer().getScheduler().runTaskTimerAsynchronously(getPlayerNPCPlugin(), () -> {
                Bukkit.getOnlinePlayers().forEach(x-> getAllNPCs(x).forEach(y-> y.updateMove()));
            }, updateLookTicks, updateLookTicks).getTaskId();
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event){
        join(event.getPlayer());
        if(!event.getPlayer().isOp()) return;
        if(getPlayerNPCPlugin().getLastSpigotVersion() == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () ->{
            event.getPlayer().sendMessage(getPlayerNPCPlugin().getPrefix() + "§7" + plugin.getDescription().getName() + " version §e" + getPlayerNPCPlugin().getLastSpigotVersion() + "§7 is available (currently running " + plugin.getDescription().getVersion() + "). Please download it at: §e" + getPlayerNPCPlugin().getSpigotResource());
        }, 20);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event){
        quit(event.getPlayer());
    }

    @EventHandler
    private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        NPCPlayerManager npcPlayerManager = getNPCPlayerManager(player);
        npcPlayerManager.destroyWorld(from);
        npcPlayerManager.showWorld(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(event.getAction().equals(Action.PHYSICAL)) return;
        getNPCPlayerManager(player).setLastClick(NPCInteractEvent.ClickType.getAction(event.getAction()));
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event){
        if(!getUpdateLookType().equals(UpdateLookType.MOVE_EVENT)) return;
        if(event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        getNPCPlayerManager(player).updateMove();
    }

    public enum UpdateLookType{
        MOVE_EVENT,
        TICKS,
        ;
    }

}
