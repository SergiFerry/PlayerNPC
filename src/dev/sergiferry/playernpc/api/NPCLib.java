package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.world.EnumHand;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.org.apache.http.annotation.Experimental;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NPCLib is a simple library to create NPCs and customize them.
 * <p>Spigot resource https://www.spigotmc.org/resources/playernpc.93625/
 *
 * @author  SergiFerry
 * @since 2021.1
 */
public class NPCLib implements Listener {

    private static NPCLib instance;

    private final Plugin plugin;
    private final HashMap<Player, PlayerManager> playerManager;
    private UpdateLookType updateLookType;
    private Integer updateLookTicks;
    private Integer taskID;

    /**
     * The {@link NPCLib} instance must be called thought {@link NPCLib#getInstance()}
     *
     * @param plugin is the {@link PlayerNPCPlugin} instance
     *
     * @see NPCLib#getInstance()
     */
    private NPCLib(@Nonnull PlayerNPCPlugin plugin){
        this.plugin = plugin;
        this.playerManager = new HashMap<>();
        this.updateLookType = UpdateLookType.MOVE_EVENT;
        this.updateLookTicks = 5;
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Generates the {@link NPC} instance.
     *
     * @param player the Player who will see the NPC
     * @param code the id of the NPC to access it later
     * @param location the location that the NPC will spawn
     */
    public NPC generateNPC(@Nonnull Player player, @Nonnull String code, @Nonnull Location location){
        Validate.notNull(player, "You cannot create an NPC with a null Player");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        NPC old = getNPCPlayerManager(player).getNPC(code);
        if(old != null) return old;
        return new NPC(this, player, code, location);
    }

    /**
     * Get the {@link NPC} instance of a {@link Player} with specific ID.
     *
     * @return The {@link NPC} instance.
     *
     * @param player the Player who sees the NPC
     * @param id the id of the NPC
     */
    public NPC getNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(id);
    }

    /**
     * Get a {@link Set} of {@link NPC} instances of a {@link Player} with the prefix on the ID.
     *
     * @return A {@link Set} of {@link NPC} instances.
     *
     * @param player the Player who sees the NPCs
     * @param prefix the prefix that have all the NPCs
     */
    public Set<NPC> getNPCs(@Nonnull Player player, @Nonnull String prefix){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(prefix, "Prefix must not be null");
        return getNPCPlayerManager(player).getNPCs(prefix);
    }

    /**
     * Get a {@link Set} of {@link NPC} instances of a {@link Player} that are at on specific {@link World}.
     *
     * @return A {@link Set} of {@link NPC} instances.
     *
     * @param player the Player who sees the NPC
     * @param world the world where are the NPCs
     */
    public Set<NPC> getNPCs(@Nonnull Player player, @Nonnull World world){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(world, "World must not be null");
        return getNPCPlayerManager(player).getNPCs(world);
    }


    /**
     * Detects if the {@link Player} has an {@link NPC} with the specific ID.
     *
     * @return A {@link Boolean} if the {@link Player} has an {@link NPC} with the specific ID.
     *
     * @param player the Player who sees the NPC
     * @param id the id of the NPC
     */
    public boolean hasNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPC(player, id) != null;
    }

    /**
     * Removes the {@link NPC} instance of a {@link Player} with specific ID.
     *
     * @return The {@link NPC} instance.
     *
     * @param player the Player who sees the NPC
     * @param id the id of the NPC
     */
    public NPC removeNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return removeNPC(player, getNPC(player, id));
    }

    /**
     * Removes the {@link NPC} instance of a {@link Player}.
     *
     * @return The {@link NPC} instance.
     *
     * @param player the Player who sees the NPC
     * @param npc the NPC instance
     */
    public NPC removeNPC(@Nonnull Player player, @Nonnull NPC npc){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(npc, "NPC was not found");
        npc.destroy();
        getNPCPlayerManager(player).removeNPC(npc.getCode());
        return npc;
    }

    /**
     * Get a {@link Set} of {@link NPC} instances with the specific ID.
     * This will return all the NPCs even they are not from the same Player.
     *
     * @return A {@link Set} of {@link NPC} instances.
     *
     * @param code the ID of the NPCs
     */
    public Set<NPC> getGlobalNPC(@Nonnull String code){
        Validate.notNull(code, "Code cannot be null");
        Set<NPC> npcs = new HashSet<>();
        Bukkit.getOnlinePlayers().stream().filter(x-> getNPC(x, code) != null).forEach(x-> npcs.add(getNPC(x, code)));
        return npcs;
    }

    /**
     * In order to do some recurrent actions on the {@link NPC} you
     * can choose between doing it every certain ticks or
     * doing it when player is moving listening {@link PlayerMoveEvent}
     *
     * @param updateLookType the type of update
     * @see NPCLib#setUpdateLookTicks(Integer) 
     * @see NPCLib#getUpdateLookType() 
     */
    public void setUpdateLookType(@Nonnull UpdateLookType updateLookType) {
        Validate.notNull(updateLookType, "Update look type must be not null");
        this.updateLookType = updateLookType;
        if(taskID != null) getPlayerNPCPlugin().getServer().getScheduler().cancelTask(taskID);
        if(updateLookType.equals(UpdateLookType.TICKS)){
            taskID = getPlayerNPCPlugin().getServer().getScheduler().runTaskTimerAsynchronously(getPlayerNPCPlugin(), () -> {
                Bukkit.getOnlinePlayers().forEach(x-> getAllNPCs(x).forEach(y-> y.updateMove()));
            }, updateLookTicks, updateLookTicks).getTaskId();
        }
    }

    /**
     * Sets the interval of ticks between every check of movement.
     * By default, is 5 ticks.
     * <p>Less ticks will produce more lag</p>
     *
     * @param ticks the amount of ticks
     * @since 2021.2
     * @see NPCLib#setUpdateLookType(UpdateLookType) 
     */
    public void setUpdateLookTicks(Integer ticks){
        this.updateLookTicks = ticks;
        if(updateLookType.equals(UpdateLookType.TICKS)) setUpdateLookType(UpdateLookType.TICKS);                        // This will update the ticks on the active run task.
    }

    /**
     * Get a {@link Set} of {@link NPC} instances of a {@link Player} with all the NPCs.
     *
     * @return A {@link Set} of {@link NPC} instances.
     *
     * @param player the Player who sees the NPC
     */
    public Set<NPC> getAllNPCs(@Nonnull Player player){
        return getNPCPlayerManager(player).getNPCs();
    }

    /**
     * When the player is far enough, the NPC will temporally hide, in order
     * to be more efficient. And when the player approach, the NPC will be unhidden.
     * You can change each NPC Hide Distance, but by default, will be this value.
     * <p>This method is Deprecated. Use {@link NPC.Attributes#getDefaultHideDistance()}
     *
     * @return {@link Double} the distance in blocks
     * @since 2021.1
     * @see NPCLib#setDefaultHideDistance(Double) 
     * @see NPC#setHideDistance(double) 
     * @see NPC#getHideDistance() 
     */
    @Deprecated
    public Double getDefaultHideDistance() {
        return NPC.Attributes.getDefaultHideDistance();
    }

    /**
     * When the player is far enough, the NPC will temporally hide, in order
     * to be more efficient. And when the player approach, the NPC will be unhidden.
     * You can change each NPC Hide Distance, but by default, will be this value.
     *
     * <p>This method is Deprecated. Use {@link NPC.Attributes#setDefaultHideDistance(double)}
     * @since 2021.1
     *
     * @param hideDistance the distance in blocks
     * @see NPCLib#getDefaultHideDistance()
     * @see NPC#setHideDistance(double)
     * @see NPC#getHideDistance()
     */
    @Deprecated
    public void setDefaultHideDistance(Double hideDistance) {
        NPC.Attributes.setDefaultHideDistance(hideDistance);
    }

    /**
     *
     * @since 2022.1
     * @return {@link NPC.Attributes#getDefault()}
     */
    @Experimental
    public NPC.Attributes getDefaults(){
        return NPC.Attributes.getDefault();
    }

    /**
     * In order to do some recurrent actions on the {@link NPC} you
     * can choose between doing it every certain ticks or
     * doing it when player is moving listening {@link PlayerMoveEvent}
     *
     * @return {@link UpdateLookType} if using ticks or PlayerMoveEvent
     * @since 2021.2
     * @see NPCLib#setUpdateLookType(UpdateLookType) 
     */
    public UpdateLookType getUpdateLookType() {
        return updateLookType;
    }

    protected Plugin getPlugin() {
        return plugin;
    }

    private void onEnable(PlayerNPCPlugin playerNPCPlugin){
        playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> join(x));
    }

    private void onDisable(PlayerNPCPlugin playerNPCPlugin){
        playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> quit(x));
    }

    private PlayerNPCPlugin getPlayerNPCPlugin(){ return (PlayerNPCPlugin) plugin;}

    protected PlayerManager getNPCPlayerManager(Player player){
        if(playerManager.containsKey(player)) return playerManager.get(player);
        PlayerManager npcPlayerManager = new PlayerManager(this, player);
        playerManager.put(player, npcPlayerManager);
        return npcPlayerManager;
    }

    private void join(Player player){
        PlayerManager npcPlayerManager = getNPCPlayerManager(player);
        PlayerManager.PacketReader reader = npcPlayerManager.getPacketReader();
        reader.inject();
    }

    private void quit(Player player){
        PlayerManager npcPlayerManager = getNPCPlayerManager(player);
        npcPlayerManager.destroyAll();
        npcPlayerManager.getPacketReader().unInject();
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event){ join(event.getPlayer()); }

    @EventHandler
    private void onQuit(PlayerQuitEvent event){
        quit(event.getPlayer());
    }

    @EventHandler
    private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        PlayerManager npcPlayerManager = getNPCPlayerManager(player);
        npcPlayerManager.destroyWorld(from);
        npcPlayerManager.showWorld(event.getPlayer().getWorld());
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event){
        if(!getUpdateLookType().equals(UpdateLookType.MOVE_EVENT)) return;
        if(event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        getNPCPlayerManager(player).updateMove();
    }

    public static NPCLib getInstance(){
        return instance;
    }

    public enum UpdateLookType{
        MOVE_EVENT,
        TICKS,
    }

    protected class PlayerManager {

        private final NPCLib npcLib;
        private final Player player;
        private final HashMap<String, NPC> npcs;
        private final PlayerManager.PacketReader packetReader;
        protected final Map<World, Set<NPC>> hidden;
        private final Long lastEnter;

        protected PlayerManager(NPCLib npcLib, Player player) {
            this.npcLib = npcLib;
            this.player = player;
            this.npcs = new HashMap<>();
            this.packetReader = new PlayerManager.PacketReader(this);
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

        protected void changeWorld(NPC npc, World from, World to){
            if(hidden.containsKey(from)){
                if(hidden.get(from).contains(npc)){
                    hidden.get(from).remove(npc);
                    npc.show();
                }
            }
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

        protected PlayerManager.PacketReader getPacketReader() {
            return packetReader;
        }

        protected Long getLastEnter() {
            return lastEnter;
        }

        protected static class PacketReader {

            private HashMap<NPC, Long> lastClick;
            private PlayerManager npcPlayerManager;
            private Channel channel;

            protected PacketReader(PlayerManager npcPlayerManager){
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
                NPC.Interact.ClickType clickType;
                try{
                    Object action = getValue(packet, "b");
                    EnumHand hand = (EnumHand) getValue(action, "a");
                    if(hand != null) clickType = NPC.Interact.ClickType.RIGHT_CLICK;
                    else clickType = NPC.Interact.ClickType.LEFT_CLICK;
                }
                catch (Exception e){ clickType = NPC.Interact.ClickType.LEFT_CLICK; }
                interact(id, clickType);
            }

            private void interact(Integer id, NPC.Interact.ClickType clickType){
                NPC npc = getNPCLib().getNPCPlayerManager(getPlayerManager().getPlayer()).getNPC(id);
                if(npc == null) return;
                interact(npc, clickType);
            }

            private void interact(NPC npc, NPC.Interact.ClickType clickType){
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

            protected PlayerManager getPlayerManager() {
                return npcPlayerManager;
            }

            protected NPCLib getNPCLib(){
                return npcPlayerManager.getNPCLib();
            }

        }

    }

}
