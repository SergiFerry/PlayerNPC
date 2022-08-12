package dev.sergiferry.playernpc.api;

import com.google.gson.JsonObject;
import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.command.NPCGlobalCommand;
import dev.sergiferry.playernpc.command.NPCLibCommand;
import dev.sergiferry.playernpc.nms.minecraft.NMSEntity;
import dev.sergiferry.playernpc.nms.minecraft.NMSEntityPlayer;
import dev.sergiferry.playernpc.nms.minecraft.NMSNetworkManager;
import dev.sergiferry.playernpc.nms.spigot.NMSFileConfiguration;
import dev.sergiferry.playernpc.utils.StringUtils;
import dev.sergiferry.spigot.SpigotPlugin;
import dev.sergiferry.spigot.nms.NMSUtils;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.server.ServerVersion;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.world.EnumHand;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.logging.Logger;
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
    private final HashMap<Plugin, PluginManager> pluginManager;
    private FileConfiguration config;
    private boolean debug;

    private NPCLib(@Nonnull PlayerNPCPlugin plugin){
        instance = this;
        this.plugin = plugin;
        this.playerManager = new HashMap<>();
        this.globalNPCs = new HashMap<>();
        this.pluginManager = new HashMap<>();
        this.debug = false;
        registerPlugin(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public PluginManager registerPlugin(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Cannot register plugin manager from a null plugin.");
        Validate.isTrue(!pluginManager.containsKey(plugin), "This plugin is already registered.");
        PluginManager pluginManager = new PluginManager(plugin, this);
        this.pluginManager.put(plugin, pluginManager);
        Bukkit.getConsoleSender().sendMessage(this.plugin.getPrefix()  + "§7Registered §e" + plugin.getName() + " §7plugin on the NPCLib");
        if(plugin != PlayerNPCPlugin.getInstance()){
            if(config == null) return pluginManager;
            if(plugin.getDescription().getAuthors().isEmpty()) return pluginManager;
            if(!plugin.getDescription().getAuthors().get(0).equals(PlayerNPCPlugin.getInstance().getDescription().getAuthors().get(0)))
                PlayerNPCPlugin.getInstance().cancelAutomaticDownload();
        }
        return pluginManager;
    }

    public boolean isRegistered(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Cannot verify plugin manager from a null plugin.");
        return pluginManager.containsKey(plugin);
    }

    public PluginManager getPluginManager(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Cannot get plugin manager from a null plugin.");
        Validate.isTrue(this.pluginManager.containsKey(plugin), "This plugin is not registered.");
        return this.pluginManager.get(plugin);
    }

    public List<Plugin> getRegisteredPlugins(){
        return pluginManager.keySet().stream().toList();
    }

    public NPC.Personal generatePersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Location location){
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.notNull(player, "You cannot create an NPC with a null Player");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        Validate.isTrue(!code.toLowerCase().startsWith("global_"), "You cannot create NPC with global tag");
        Validate.isTrue(isRegistered(plugin), "This plugin is not registered on NPCLib");
        return generatePlayerPersonalNPC(player, plugin, getFormattedName(plugin, code), location);
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String code, @Nonnull NPC.Global.Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.notNull(code, "You cannot create an NPC with a null code");
        Validate.notNull(visibility, "You cannot create an NPC with a null visibility");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        Validate.isTrue(isRegistered(plugin), "This plugin is not registered on NPCLib");
        return generatePlayerGlobalNPC(plugin, getFormattedName(plugin, code), visibility, visibilityRequirement, location);
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
        Validate.isTrue(old == null, "Personal NPC with code " + code + " for player " + player.getName() + " already exists.");
        return new NPC.Personal(this, player, plugin, code, location);
    }

    private NPC.Global generatePlayerGlobalNPC(Plugin plugin, String code, NPC.Global.Visibility visibility, Predicate<Player> visibilityRequirement, Location location){
        Validate.isTrue(!globalNPCs.containsKey(code), "Global NPC with code " + code + " already exists.");
        NPC.Global global = new NPC.Global(this, plugin, code, visibility, visibilityRequirement, location);
        globalNPCs.put(code, global);
        return global;
    }

    public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(getFormattedName(plugin, id));
    }

    public NPC.Global getGlobalNPC(@Nonnull Plugin plugin, @Nonnull String id){
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return globalNPCs.get(getFormattedName(plugin, id));
    }

    @Deprecated
    public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull String id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).getNPC(id.toLowerCase());
    }

    @Deprecated
    public NPC.Global getGlobalNPC(@Nonnull String id){
        Validate.notNull(id, "NPC id must not be null");
        return globalNPCs.get(id.toLowerCase());
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

    public void setDebug(boolean debug) {
        if(this.debug == debug) return;
        this.debug = debug;
        saveConfig();
    }

    public boolean isDebug() {
        return debug;
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


    protected Plugin getPlugin() {
        return plugin;
    }

    protected static String getFormattedName(Plugin plugin, String code){
        String b = plugin.getName().toLowerCase() + ".";
        if(code == null) return b;
        return b + code.toLowerCase();
    }

    private File checkFileExists(){
        File file = new File("plugins/PlayerNPC/config.yml");
        boolean exist = file.exists();
        if(!exist) try{ file.createNewFile();} catch (Exception e){ printError(e); };
        return file;
    }

    public void loadConfig(){
        File file = checkFileExists();
        this.config  = YamlConfiguration.loadConfiguration(file);
        HashMap<String, Object> defaults = new HashMap<>();
        defaults.put("autoUpdate", true);
        defaults.put("debug", this.debug);
        defaults.put("gazeUpdate.ticks", getPluginManager(plugin).updateGazeTicks);
        defaults.put("gazeUpdate.type", getPluginManager(plugin).updateGazeType.name());
        defaults.put("tabListHide.ticks", getPluginManager(plugin).ticksUntilTabListHide);
        defaults.put("skinUpdate.frequency", getPluginManager(plugin).skinUpdateFrequency);
        boolean m = false;
        for(String s : defaults.keySet()){
            if(!config.contains(s)){
                config.set(s, defaults.get(s));
                m = true;
            }
        }
        NMSFileConfiguration.setComments(config, "gazeUpdate.type", Arrays.asList("GazeUpdateType: MOVE_EVENT, TICKS"));
        NMSFileConfiguration.setComments(config, "skinUpdate.frequency", Arrays.asList("TimeUnit: SECONDS, MINUTES, HOURS, DAYS"));
        if(m) { try { config.save(file); } catch (IOException e) { printError(e); } }
        //
        this.debug = config.getBoolean("debug");
        getPluginManager(plugin).ticksUntilTabListHide = config.getInt("tabListHide.ticks");
        getPluginManager(plugin).skinUpdateFrequency = config.getObject("skinUpdate.frequency", SkinUpdateFrequency.class);
        getPluginManager(plugin).updateGazeTicks = config.getInt("gazeUpdate.ticks");
        getPluginManager(plugin).updateGazeType = NPCLib.UpdateGazeType.valueOf(config.getString("gazeUpdate.type"));
        if(config.contains("autoUpdate") && !config.getBoolean("autoUpdate")) PlayerNPCPlugin.getInstance().cancelAutomaticDownload();
        getPluginManager(plugin).runGazeUpdate();
    }

    public void saveConfig(){
        if(config == null) return;
        File file = checkFileExists();
        config.set("debug", this.debug);
        config.set("gazeUpdate.ticks", getPluginManager(plugin).updateGazeTicks);
        config.set("gazeUpdate.type", getPluginManager(plugin).updateGazeType.name());
        config.set("tabListHide.ticks", getPluginManager(plugin).ticksUntilTabListHide);
        config.set("skinUpdate.frequency", getPluginManager(plugin).skinUpdateFrequency);
        try { config.save(file); } catch (IOException e) { printError(e); }
    }

    private void onEnable(PlayerNPCPlugin playerNPCPlugin){
        getPluginManager(playerNPCPlugin).onEnable();
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
        getPluginManager(playerNPCPlugin).onDisable();
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
            if(global.getVisibility().equals(NPC.Global.Visibility.SELECTED_PLAYERS) && !global.getSelectedPlayers().contains(player.getName())) continue;
            global.addPlayer(player);
        }
    }

    private void quit(Player player){
        for(NPC.Global global : getAllGlobalNPCs()){
            if(!global.hasPlayer(player)) continue;
            global.players.remove(player);
        }
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
    private void onPluginDisable(PluginDisableEvent event){
        Plugin plugin = event.getPlugin();
        Set<NPC.Global> globals = getAllGlobalNPCs(plugin);
        if(!globals.isEmpty()) globals.forEach(x-> removeGlobalNPC(x));
        //
        Set<NPC.Personal> npc = getPersonalNPCs(plugin);
        if(!npc.isEmpty())  npc.forEach(x-> removePersonalNPC(x));;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if(!player.isSneaking()) return;
        if(!player.isOp()) return;
        if(event.getItem() == null) return;
        if(!event.getItem().hasItemMeta()) return;
        if(!event.getItem().getItemMeta().getPersistentDataContainer().has(NPCLibCommand.skinKey, PersistentDataType.STRING)) return;
        String skinKey = event.getItem().getItemMeta().getPersistentDataContainer().get(NPCLibCommand.skinKey, PersistentDataType.STRING);
        String npcCode = "preview_" + skinKey;
        if(hasPersonalNPC(player, getFormattedName(plugin, npcCode))) return;
        event.setCancelled(true);
        //
        NPC.Placeholders.addPlaceholderIfNotExists("previewSecondsDisappear", (x, y) -> {
            if(!x.hasCustomData("previewSecondsDisappear")) return "§c0";
            Integer a = Integer.valueOf(x.getCustomData("previewSecondsDisappear"));
            String color = "§a";
            if(a < 10) color = "§e";
            if(a < 5) color = "§c";
            return color + a;
        });
        NPC.Personal preview = generatePersonalNPC(player, plugin, npcCode, event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5));
        preview.addCustomClickAction(NPC.Interact.ClickType.LEFT_CLICK, (npc, player1) -> npc.playAnimation(NPC.Animation.TAKE_DAMAGE));
        NPC.Interact.Actions.CustomAction customAction = preview.addCustomClickAction(NPC.Interact.ClickType.LEFT_CLICK, (npc, player1) -> cancelPreview(preview));
        customAction.setDelayTicks(3L);
        preview.setLineOpacity(1, NPC.Hologram.Opacity.LOW);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aPreviewing skin..."));
        if(skinKey.startsWith(NPC.Skin.Type.MINECRAFT.name() + ":")){
            String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINECRAFT.name() + ":", "");
            preview.setSkin(skinName, skin ->{
                if(skin == null){
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                    removePersonalNPC(preview);
                    return;
                }
                preview.setText("§7Disappear in {previewSecondsDisappear} seconds...", "", "§6§lMinecraft Skin Preview", "§e" + skin.getPlayerName());
                preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo minecraft " + skin.getPlayerName());
                showPreview(preview);
            });
        }
        else if(skinKey.startsWith(NPC.Skin.Type.MINESKIN.name() + ":")){
            String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINESKIN.name() + ":", "");
            preview.setMineSkin(skinName, skin ->{
                if(skin == null){
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                    removePersonalNPC(preview);
                    return;
                }
                preview.setText("§7Disappear in {previewSecondsDisappear} seconds...", "", "§6§lMineSkin Preview", "§e" + skin.getId());
                preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo mineskin " + skin.getId());
                showPreview(preview);
            });
        }
        else if(skinKey.startsWith(NPC.Skin.Type.CUSTOM.name() + ":")){
            String skinName = skinKey.replaceFirst(NPC.Skin.Type.CUSTOM.name() + ":", "");
            NPC.Skin.Custom.fetchCustomSkinAsync(skinName, skin -> {
                if(skin == null){
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                    removePersonalNPC(preview);
                    return;
                }
                preview.setSkin(skin);
                preview.setText("§7Disappear in {previewSecondsDisappear} seconds...", "", "§6§lCustom Skin Preview", "§e" + skin.getFullSkinCode());
                preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo custom " + skin.getFullSkinCode());
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> showPreview(preview));
            });
        }
        preview.setCustomData("previewSecondsDisappear", "15");
        preview.setCustomData("bukkitTaskID", "" + Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, ()-> {
            if(!hasPersonalNPC(player, plugin, npcCode)){
                cancelPreview(preview);
                return;
            }
            Integer seconds = Integer.valueOf(preview.getCustomData("previewSecondsDisappear"));
            seconds--;
            preview.setCustomData("previewSecondsDisappear", "" + seconds);
            preview.updateText();
            if(seconds == 0){ cancelPreview(preview); }
        }, 20, 20));
    }

    private void showPreview(NPC.Personal preview){
        preview.create();
        preview.lookAt(preview.getPlayer());
        preview.show();
    }

    private void cancelPreview(NPC.Personal preview){
        preview.getPlayer().spawnParticle(Particle.CLOUD, preview.getLocation(), 3, 0.2, 0.5, 0.2, 0.1);
        Bukkit.getScheduler().cancelTask(Integer.valueOf(preview.getCustomData("bukkitTaskID")));
        removePersonalNPC(preview);
    }

    private void savePersistentNPCs(){
        NPC.Global.PersistentManager.forEachPersistentManager(x-> x.save());
    }

    public static NPCLib getInstance(){
        Validate.notNull(instance, "NPCLib has not been started yet, make sure to have PlayerNPC.jar on your plugins folder, and to add dependency or softdependency to PlayerNPC on your plugin.yml");
        return instance;
    }

    protected static void printError(Exception e){
        if(NPCLib.getInstance().isDebug()) e.printStackTrace();
    }

    public enum UpdateGazeType{
        MOVE_EVENT,
        TICKS,
    }

    public enum TabListIDType{
        RANDOM_UUID,
        NPC_CODE,
        NPC_SIMPLE_CODE
    }

    public static class PluginManager implements Listener{

        private final Plugin plugin;
        private final NPCLib npcLib;
        protected Command.Color commandColor;
        protected UpdateGazeType updateGazeType;
        protected Integer updateGazeTicks;
        protected Integer ticksUntilTabListHide;
        protected Integer taskID;
        protected SkinUpdateFrequency skinUpdateFrequency;

        protected PluginManager(Plugin plugin, NPCLib npcLib) {
            this.plugin = plugin;
            this.npcLib = npcLib;
            this.updateGazeTicks = 5;
            this.ticksUntilTabListHide = 10;
            this.skinUpdateFrequency = new SkinUpdateFrequency(1, TimeUnit.DAYS);
            this.updateGazeType = UpdateGazeType.MOVE_EVENT;
            this.commandColor = Command.Color.YELLOW;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }

        protected void onEnable(){
            if(plugin.equals(npcLib.getPlugin())){
                npcLib.loadConfig();
                loadPersistentNPCs();
            }
        }

        protected void onDisable(){

        }

        public NPC.Global getGlobalNPC(String simpleCode){
            return npcLib.getGlobalNPC(plugin, simpleCode);
        }

        public NPC.Personal getPersonalNPC(Player player, String simpleCode){
            return npcLib.getPersonalNPC(player, plugin, simpleCode);
        }

        private void loadPersistentNPCs(){
            Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Loading Persistent Global NPCs for plugin §e" + plugin.getName());
            File folder = new File("plugins/PlayerNPC/persistent/global/" + plugin.getName().toLowerCase() + "/");
            if(!folder.exists()) { folder.mkdirs(); }
            boolean empty = true;
            for (File f : folder.listFiles()) {
                if(!f.isDirectory()) continue;
                try{
                    NPC.Global.PersistentManager persistent = new NPC.Global.PersistentManager(plugin, f.getName());
                    persistent.load();
                    empty = false;
                }
                catch (Exception e){
                    Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Error loading Persistent Global NPC §c" + f.getName());
                    printError(e);
                }
            }
            if(empty) Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() +  "§7No Persistent NPC found for plugin §e" + plugin.getName());
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public NPCLib getNPCLib() {
            return npcLib;
        }

        public UpdateGazeType getUpdateGazeType() {
            return updateGazeType;
        }

        public Integer getUpdateGazeTicks() {
            return updateGazeTicks;
        }

        public Integer getTicksUntilTabListHide() {
            return ticksUntilTabListHide;
        }

        public Integer getTaskID() {
            return taskID;
        }

        public String getCode(String simpleCode){
            String b = plugin.getName().toLowerCase() + ".";
            if(simpleCode == null) return b;
            return b + simpleCode;
        }

        public SkinUpdateFrequency getSkinUpdateFrequency() {
            return skinUpdateFrequency;
        }

        public void setCommandColor(Command.Color color){
            if(plugin.equals(PlayerNPCPlugin.getInstance())) return;
            this.commandColor = color;
        }

        public Command.Color getCommandColor(){
            if(commandColor == null) return Command.Color.YELLOW;
            return commandColor;
        }

        public void setUpdateGazeType(@Nonnull UpdateGazeType updateGazeType) {
            Validate.notNull(updateGazeType, "Update gaze type must be not null");
            if(this.updateGazeType.equals(updateGazeType)) return;
            this.updateGazeType = updateGazeType;
            if(plugin.equals(npcLib.getPlugin())) npcLib.saveConfig();
            runGazeUpdate();
        }

        public void setUpdateGazeTicks(Integer ticks){
            if(ticks < 1) ticks = 1;
            if(ticks.equals(this.updateGazeTicks)) return;
            this.updateGazeTicks = ticks;
            if(plugin.equals(npcLib.getPlugin())) npcLib.saveConfig();
            if(this.updateGazeType.equals(UpdateGazeType.TICKS)) runGazeUpdate();                        // This will update the ticks on the active run task.
        }

        private void runGazeUpdate(){
            if(taskID != null) plugin.getServer().getScheduler().cancelTask(taskID);
            if(updateGazeType.equals(UpdateGazeType.TICKS)){
                taskID = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                    Bukkit.getOnlinePlayers().forEach(x-> npcLib.getNPCPlayerManager(x).updateMove(plugin));
                }, updateGazeTicks, updateGazeTicks).getTaskId();
            }
        }

        public void setSkinUpdateFrequency(SkinUpdateFrequency skinUpdateFrequency) {
            this.skinUpdateFrequency = skinUpdateFrequency;
            if(plugin.equals(npcLib.getPlugin())) npcLib.saveConfig();
        }

        public void setTicksUntilTabListHide(Integer ticksUntilTabListHide) {
            if(ticksUntilTabListHide < 1) ticksUntilTabListHide = 1;
            if(ticksUntilTabListHide.equals(this.ticksUntilTabListHide)) return;
            this.ticksUntilTabListHide = ticksUntilTabListHide;
            if(plugin.equals(npcLib.getPlugin())) npcLib.saveConfig();
        }

        @EventHandler
        private void onMove(PlayerMoveEvent event){
            if(!getUpdateGazeType().equals(UpdateGazeType.MOVE_EVENT)) return;
            if(event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
            Player player = event.getPlayer();
            npcLib.getNPCPlayerManager(player).updateMove(plugin);
        }
    }

    public record SkinUpdateFrequency(Integer value, TimeUnit timeUnit) implements ConfigurationSerializable {
        public SkinUpdateFrequency {
            Objects.requireNonNull(value);
            Objects.requireNonNull(timeUnit);
            Validate.isTrue(timeUnit.equals(TimeUnit.SECONDS) || timeUnit.equals(TimeUnit.MINUTES) || timeUnit.equals(TimeUnit.HOURS) || timeUnit.equals(TimeUnit.DAYS), "Time unit must be seconds, minutes, hours or days.");
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> hash = new HashMap<>();
            hash.put("value", value);
            hash.put("timeUnit", timeUnit.name());
            return hash;
        }

        public static SkinUpdateFrequency deserialize(Map<String, Object> map){
            return new SkinUpdateFrequency((Integer) map.get("value"), TimeUnit.valueOf((String) map.get("timeUnit")));
        }
    }

    public static class Command{

        private Command(){}

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
            return npcs.values().stream().filter(x-> x.isCreated() && NMSEntity.getEntityID(x.getEntity()).equals(entityID)).findAny().orElse(null);
        }

        protected void removeNPC(String code){
            npcs.remove(code);
        }

        protected void updateMove(Plugin plugin){
            getNPCs(getPlayer().getWorld()).stream().filter(x-> x.getPlugin().equals(plugin) && x.isCreated()).forEach(x-> x.updateMove());
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
                channel = NMSNetworkManager.getChannel(NMSNetworkManager.getNetworkManager(npcPlayerManager.getPlayer())); //1.18 era .a & 1.19 es .b
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
                Bukkit.getScheduler().runTask(npcPlayerManager.getNPCLib().getPlugin(), ()-> npc.interact(npcPlayerManager.getPlayer(), clickType));
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
