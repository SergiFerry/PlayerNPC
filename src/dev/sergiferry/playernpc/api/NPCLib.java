package dev.sergiferry.playernpc.api;

import com.google.gson.JsonObject;
import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.command.NPCGlobalCommand;
import dev.sergiferry.playernpc.nms.minecraft.NMSEntityPlayer;
import dev.sergiferry.playernpc.nms.minecraft.NMSNetworkManager;
import dev.sergiferry.spigot.nms.NMSUtils;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.world.EnumHand;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.*;
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

    private final PlayerNPCPlugin plugin;
    private final HashMap<Player, PlayerManager> playerManager;
    private final HashMap<String, NPC.Global> globalNPCs;
    private UpdateLookType updateLookType;
    private Integer updateLookTicks;
    private Integer ticksUntilTabListHide;
    private Integer taskID;

    private NPCLib(@Nonnull PlayerNPCPlugin plugin){
        instance = this;
        this.plugin = plugin;
        this.playerManager = new HashMap<>();
        this.globalNPCs = new HashMap<>();
        this.updateLookTicks = 5;
        this.ticksUntilTabListHide = 10;
        setUpdateLookType(UpdateLookType.MOVE_EVENT);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public NPC.Personal generatePersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Location location){
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.notNull(player, "You cannot create an NPC with a null Player");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        Validate.isTrue(!code.toLowerCase().startsWith("global_"), "You cannot create NPC with global tag");
        return generatePlayerPersonalNPC(player, plugin, a(plugin, code), location);
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String code, @Nonnull NPC.Global.Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(visibility, "You cannot create an NPC with a null visibility");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        return generatePlayerGlobalNPC(plugin, a(plugin, code), visibility, visibilityRequirement, location);
    }

    @Deprecated
    public NPC.Global generatePersistentGlobalNPC(@Nonnull String code, @Nonnull NPC.Global.Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
        NPC.Global persistent = generateGlobalNPC(plugin, code, visibility, visibilityRequirement, location);
        //persistent.setPersistent(true);
        return persistent;
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String code, @Nonnull NPC.Global.Visibility visibility, @Nonnull Location location){
        return generateGlobalNPC(plugin, code, visibility, null, location);
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String code, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
        return generateGlobalNPC(plugin, code, NPC.Global.Visibility.EVERYONE, visibilityRequirement, location);
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String code, @Nonnull Location location){
        return generateGlobalNPC(plugin, code, NPC.Global.Visibility.EVERYONE, null, location);
    }

    protected NPC.Personal generatePlayerPersonalNPC(Player player, Plugin plugin, String code, Location location){
        NPC.Personal old = getNPCPlayerManager(player).getNPC(code);
        if(old != null) return old;
        return new NPC.Personal(this, player, plugin, code, location);
    }

    private NPC.Global generatePlayerGlobalNPC(Plugin plugin, String code, NPC.Global.Visibility visibility, Predicate<Player> visibilityRequirement, Location location){
        NPC.Global old = globalNPCs.get(code);
        if(old != null) return old;
        NPC.Global global = new NPC.Global(this, plugin, code, visibility, visibilityRequirement, location);
        globalNPCs.put(code, global);
        return global;
    }

    public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(a(plugin, id));
    }

    public NPC.Global getGlobalNPC(@Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return globalNPCs.get(a(plugin, id));
    }

    @Deprecated
    public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(id);
    }

    @Deprecated
    public NPC.Global getGlobalNPC(@Nonnull String id){
        Validate.notNull(id, "NPC id must not be null");
        return globalNPCs.get(id);
    }

    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Player player, @Nonnull Plugin plugin){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(plugin, "Plugin must not be null");
        return getNPCPlayerManager(player).getNPCs(plugin);
    }

    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Plugin must not be null");
        Set<NPC.Personal> npcs = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(x-> npcs.addAll(getPersonalNPCs(x, plugin)));
        return npcs;
    }

    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Player player, @Nonnull World world){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(world, "World must not be null");
        return getNPCPlayerManager(player).getNPCs(world);
    }

    public Set<NPC.Personal> getAllPersonalNPCs(@Nonnull Player player){
        return getNPCPlayerManager(player).getNPCs();
    }

    public Set<NPC.Global> getAllGlobalNPCs(){
        return Set.copyOf(globalNPCs.values());
    }

    public Set<NPC.Global> getAllGlobalNPCs(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Plugin must not be null");
        Set<NPC.Global> npcs = new HashSet<>();
        globalNPCs.keySet().stream().filter(x-> x.startsWith(plugin.getName().toLowerCase() + ".")).forEach(x-> npcs.add(getGlobalNPC(x)));
        return npcs;
    }

    public void addGlobalCommand(Plugin plugin, String argument, String arguments, boolean enabled, boolean important, String description, String hover, BiConsumer<NPCGlobalCommand.Command, NPCGlobalCommand.CommandData> execute, BiFunction<NPCGlobalCommand.Command, NPCGlobalCommand.CommandData, List<String>> tabComplete, Command.Color color){
        if(plugin.equals(this.plugin)) throw new IllegalArgumentException("Plugin must be yours.");
        NPCGlobalCommand.addCommand(plugin, argument, arguments, enabled, important, description, hover, execute, tabComplete, color);
    }

    public boolean hasGlobalCommand(String argument){
        return getGlobalCommand(argument) != null;
    }

    public NPCGlobalCommand.Command getGlobalCommand(String argument){
        return NPCGlobalCommand.getCommand(argument);
    }

    public Set<NPCGlobalCommand.Command> getGlobalCommands(Plugin plugin){
        return NPCGlobalCommand.getCommands(plugin);
    }

    public boolean hasPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getPersonalNPC(player, plugin, id) != null;
    }

    @Deprecated
    public boolean hasPersonalNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getPersonalNPC(player, id) != null;
    }

    public void removePersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(player, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        removePersonalNPC(getPersonalNPC(player, plugin, id));
    }

    public void removePersonalNPC(@Nonnull NPC.Personal npc){
        Validate.notNull(npc, "NPC was not found");
        if(npc.hasGlobal() && npc.getGlobal().hasPlayer(npc.getPlayer())){
            npc.getGlobal().removePlayer(npc.getPlayer());
            return;
        }
        npc.destroy();
        getNPCPlayerManager(npc.getPlayer()).removeNPC(npc.getCode());
    }

    public void removeGlobalNPC(@Nonnull NPC.Global npc){
        Validate.notNull(npc, "NPC was not found");
        npc.destroy();
        globalNPCs.remove(npc.getCode());
    }

    public void removeNPC(@Nonnull NPC npc){
        Validate.notNull(npc, "NPC was not found");
        if(npc instanceof NPC.Personal) removePersonalNPC((NPC.Personal) npc);
        else if(npc instanceof NPC.Global) removeGlobalNPC((NPC.Global) npc);
    }

    public void setUpdateLookType(@Nonnull UpdateLookType updateLookType) {
        Validate.notNull(updateLookType, "Update look type must be not null");
        this.updateLookType = updateLookType;
        if(taskID != null) getPlayerNPCPlugin().getServer().getScheduler().cancelTask(taskID);
        if(updateLookType.equals(UpdateLookType.TICKS)){
            taskID = getPlayerNPCPlugin().getServer().getScheduler().runTaskTimerAsynchronously(getPlayerNPCPlugin(), () -> {
                Bukkit.getOnlinePlayers().forEach(x-> getAllPersonalNPCs(x).forEach(y-> y.updateMove()));
            }, updateLookTicks, updateLookTicks).getTaskId();
        }
    }

    public void setUpdateLookTicks(Integer ticks){
        if(ticks < 1) ticks = 1;
        this.updateLookTicks = ticks;
        if(updateLookType.equals(UpdateLookType.TICKS)) setUpdateLookType(UpdateLookType.TICKS);                        // This will update the ticks on the active run task.
    }

    public Integer getTicksUntilTabListHide() {
        return ticksUntilTabListHide;
    }

    public void setTicksUntilTabListHide(Integer ticksUntilTabListHide) {
        if(ticksUntilTabListHide < 1) ticksUntilTabListHide = 1;
        this.ticksUntilTabListHide = ticksUntilTabListHide;
    }

    @Deprecated
    public Double getDefaultHideDistance() {
        return NPC.Attributes.getDefaultHideDistance();
    }

    @Deprecated
    public void setDefaultHideDistance(Double hideDistance) {
        NPC.Attributes.setDefaultHideDistance(hideDistance);
    }

    public NPC.Attributes getDefaults(){
        return NPC.Attributes.getDefault();
    }

    public UpdateLookType getUpdateLookType() {
        return updateLookType;
    }

    protected Plugin getPlugin() {
        return plugin;
    }

    private String a(Plugin plugin, String code){
        String b = plugin.getName().toLowerCase() + ".";
        if(code == null) return b;
        return b + code;
    }

    private void onEnable(PlayerNPCPlugin playerNPCPlugin){
        loadPersistentNPCs();
        Bukkit.getScheduler().runTaskLater(getPlugin(), ()-> { playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> {
            join(x);
            for(NPC.Global global : getAllGlobalNPCs()){
                if(global.isActive(x)) global.forceUpdate();
            }
        }); }, 1L);
    }

    private void onDisable(PlayerNPCPlugin playerNPCPlugin){
        savePersistentNPCs();
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
        for(NPC.Global global : getAllGlobalNPCs()){
            if(!global.getVisibility().equals(NPC.Global.Visibility.EVERYONE)) continue;
            global.addPlayer(player);
        }
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
    
    @EventHandler
    private void onPluginDisable(PluginDisableEvent event){
        Plugin plugin = event.getPlugin();
        Set<NPC.Global> globals = getAllGlobalNPCs(plugin);
        if(!globals.isEmpty()) globals.forEach(x-> removeGlobalNPC(x));
        //
        Set<NPC.Personal> npc = getPersonalNPCs(plugin);
        if(!npc.isEmpty())  npc.forEach(x-> removePersonalNPC(x));;
    }

    private void loadPersistentNPCs(){
        File folder = new File("plugins/PlayerNPC/persistent/global/");
        if(!folder.exists()) { folder.mkdirs(); }
        for (File f : folder.listFiles()) {
            if(!f.getName().contains(".yml")) continue;
            String id = f.getName().replaceAll(".yml", "");
            GlobalPersistentData globalPersistentData = new GlobalPersistentData(id);
            globalPersistentData.load();
        }
    }

    private void savePersistentNPCs(){
        //getAllGlobalNPCs().stream().filter(x-> x.isPersistent()).forEach(x-> x.savePersistent());
    }

    protected static class GlobalPersistentData{

        private static HashMap<String, GlobalPersistentData> PERSISTENT_DATA;

        static{
            PERSISTENT_DATA = new HashMap<>();
        }

        public static GlobalPersistentData getPersistentData(String id) {
            if(!PERSISTENT_DATA.containsKey(id)) return null;
            return PERSISTENT_DATA.get(id);
        }

        private static void setPersistentData(String id, GlobalPersistentData globalPersistentData) {
            PERSISTENT_DATA.put(id, globalPersistentData);
        }

        public static void forEachPersistent(Consumer<NPC.Global> action){

        }

        private String id;
        private NPC.Global global;
        private File file;
        private FileConfiguration config;

        protected GlobalPersistentData(String simpleID) {
            this.id = simpleID;
            setPersistentData(id, this);
        }

        protected void load(){
            if(file == null){
                this.file = new File(getFilePath());
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile();} catch (Exception e){};
            }
            this.config = YamlConfiguration.loadConfiguration(file);
            if(global != null) NPCLib.getInstance().removeGlobalNPC(global);
            global = getInstance().generatePersistentGlobalNPC(id, NPC.Global.Visibility.EVERYONE, null, null);
        }

        public void save(){
            if(global == null) return;
            try{ config.save(file); }catch (Exception ignored){}
        }

        public void remove(){
            file.delete();
        }

        public String getFilePath(){
            return "plugins/PlayerNPC/persistent/global/" + id + ".yml";
        }

        public NPC.Global getGlobal() {
            return global;
        }

        public boolean isLoaded(){
            return config != null;
        }

        public Object get(String s){
            if(config.contains(s)){
                return config.get(s);
            }
            return null;
        }

        public boolean containsKey(String s){
            if(config == null) return false;
            return config.contains(s);
        }

        public FileConfiguration getConfig(){
            return this.config;
        }

        public void set(String s, Object o){
            if(config == null) return;
            config.set(s, o);
        }

    }

    public static NPCLib getInstance(){
        return instance;
    }

    public enum UpdateLookType{
        MOVE_EVENT,
        TICKS,
    }

    public enum TabListIDType{
        RANDOM_UUID,
        NPC_CODE,
        NPC_SIMPLE_CODE
    }

    public static class Command{

        private static Map<Plugin, Color> colorMap;

        private Command(){}

        static{
            colorMap = new HashMap<>();
            colorMap.put(PlayerNPCPlugin.getInstance(), Color.YELLOW);
        }

        public static void setColor(Plugin plugin, Color color){
            if(plugin.equals(PlayerNPCPlugin.getInstance())) return;
            colorMap.put(plugin, color);
        }

        public static Color getColor(Plugin plugin){
            if(!colorMap.containsKey(plugin)) return Color.YELLOW;
            return colorMap.get(plugin);
        }

        public enum Color{

            YELLOW(ChatColor.YELLOW, ChatColor.GOLD),
            BLUE(ChatColor.AQUA, ChatColor.DARK_AQUA),
            GREEN(ChatColor.GREEN, ChatColor.DARK_GREEN),
            ;

            private ChatColor normal;
            private ChatColor important;

            Color(ChatColor normal, ChatColor important){
                this.normal = normal;
                this.important = important;
            }

            public ChatColor getNormal() {
                return normal;
            }

            public String getImportant(){
                return "" + important + ChatColor.BOLD;
            }

            public ChatColor getImportantSimple() {
                return important;
            }
        }

    }

    protected class PlayerManager {

        private final NPCLib npcLib;
        private final Player player;
        private final HashMap<String, NPC.Personal> npcs;
        private final PlayerManager.PacketReader packetReader;
        private final Map<World, Set<NPC.Personal>> hidden;
        private final Long lastEnter;

        protected PlayerManager(NPCLib npcLib, Player player) {
            this.npcLib = npcLib;
            this.player = player;
            this.npcs = new HashMap<>();
            this.packetReader = new PlayerManager.PacketReader(this);
            this.hidden = new HashMap<>();
            this.lastEnter = System.currentTimeMillis();
        }

        protected void set(String s, NPC.Personal npc){
            npcs.put(s, npc);
        }

        protected NPC.Personal getNPC(Integer entityID){
            return npcs.values().stream().filter(x-> x.isCreated() && NMSEntityPlayer.getEntityID(x.getEntity()).equals(entityID)).findAny().orElse(null);
        }

        protected void removeNPC(String code){
            npcs.remove(code);
        }

        protected void updateMove(){
            getNPCs(getPlayer().getWorld()).forEach(x->{
                if(x.isCreated()) x.updateMove();
            });
        }

        protected void destroyWorld(World world){
            Set<NPC.Personal> r = new HashSet<>();
            npcs.values().stream().filter(x-> x.canSee() && x.getWorld().getName().equals(world.getName())).forEach(x->{
                x.hide();
                r.add(x);
            });
            hidden.put(world, r);
        }

        protected void showWorld(World world){
            if(!hidden.containsKey(world)) return;
            hidden.get(world).stream().filter(x-> x.isCreated()).forEach(x-> x.show());
            hidden.remove(world);
        }

        protected void changeWorld(NPC.Personal npc, World from, World to){
            if(!hidden.containsKey(from)) return;
            if(!hidden.get(from).contains(npc)) return;
            hidden.get(from).remove(npc);
            npc.show();
        }

        protected void destroyAll(){
            Set<NPC.Personal> destroy = new HashSet<>();
            destroy.addAll(npcs.values());
            destroy.stream().filter(x-> x.isCreated()).forEach(x-> x.destroy());
            npcs.clear();
        }

        protected Set<NPC.Personal> getNPCs(World world){
            Validate.notNull(world, "World must be not null");
            return npcs.values().stream().filter(x-> x.getWorld().equals(world)).collect(Collectors.toSet());
        }

        protected Set<NPC.Personal> getNPCs(Plugin plugin){
            Validate.notNull(plugin, "Plugin must be not null");
            return npcs.values().stream().filter(x-> x.getPlugin().equals(plugin)).collect(Collectors.toSet());
        }

        protected Set<NPC.Personal> getNPCs(){
            return  npcs.values().stream().collect(Collectors.toSet());
        }

        protected NPC.Personal getNPC(String s){
            if(!npcs.containsKey(s)) return null;
            return npcs.get(s);
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
                channel = NMSNetworkManager.getChannel(NMSCraftPlayer.getPlayerConnection(npcPlayerManager.getPlayer()).b); //1.18 era .a & 1.19 es .b
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
                int id = (int) NMSUtils.getValue(packet, "a");
                NPC.Interact.ClickType clickType;
                try{
                    Object action = NMSUtils.getValue(packet, "b");
                    EnumHand hand = (EnumHand) NMSUtils.getValue(action, "a");
                    if(hand != null) clickType = NPC.Interact.ClickType.RIGHT_CLICK;
                    else clickType = NPC.Interact.ClickType.LEFT_CLICK;
                }
                catch (Exception e){ clickType = NPC.Interact.ClickType.LEFT_CLICK; }
                interact(id, clickType);
            }

            private void interact(Integer id, NPC.Interact.ClickType clickType){
                NPC.Personal npc = getNPCLib().getNPCPlayerManager(getPlayerManager().getPlayer()).getNPC(id);
                if(npc == null) return;
                interact(npc, clickType);
            }

            private void interact(NPC.Personal npc, NPC.Interact.ClickType clickType){
                if(npc == null) return;
                if(lastClick.containsKey(npc) && System.currentTimeMillis() - lastClick.get(npc) < npc.getInteractCooldown()) return;
                lastClick.put(npc, System.currentTimeMillis());
                Bukkit.getScheduler().scheduleSyncDelayedTask(npcPlayerManager.getNPCLib().getPlugin(), ()-> {
                    npc.interact(npcPlayerManager.getPlayer(), clickType);
                }, 1);
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
