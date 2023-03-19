package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.command.NPCLibCommand;
import dev.sergiferry.playernpc.command.global.NPCGlobalCommand;
import dev.sergiferry.playernpc.integration.IntegrationsManager;
import dev.sergiferry.playernpc.nms.minecraft.NMSEntity;
import dev.sergiferry.playernpc.nms.minecraft.NMSNetworkManager;
import dev.sergiferry.playernpc.nms.spigot.NMSFileConfiguration;
import dev.sergiferry.playernpc.utils.EnumUtils;
import dev.sergiferry.spigot.nms.NMSUtils;
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
import java.util.regex.Pattern;
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
    protected final Registry<NPC.Global> globalNPCs;
    private final HashMap<Player, PlayerManager> playerManager;
    private final HashMap<Plugin, PluginManager> pluginManager;
    private FileConfiguration config;
    private boolean useBukkitScoreboards;
    private boolean debug;

    private NPCLib(@Nonnull PlayerNPCPlugin plugin){
        instance = this;
        this.plugin = plugin;
        this.playerManager = new HashMap<>();
        this.globalNPCs = new Registry<>(Registry.ID.playerNPC("globalNPCs"));
        this.pluginManager = new HashMap<>();
        this.debug = false;
        this.useBukkitScoreboards = false;
        registerPlugin(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public PluginManager registerPlugin(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Cannot register plugin manager from a null plugin.");
        Validate.isTrue(!pluginManager.containsKey(plugin), "This plugin is already registered.");
        PluginManager pluginManager = new PluginManager(plugin, this);
        this.pluginManager.put(plugin, pluginManager);
        Bukkit.getConsoleSender().sendMessage(this.plugin.getPrefix()  + "§7Registered §e" + plugin.getName() + " §7plugin into the NPCLib");
        if(plugin != PlayerNPCPlugin.getInstance()){
            if(config == null) return pluginManager;
            if(plugin.getDescription().getAuthors().isEmpty()) return pluginManager;
            if(!plugin.getDescription().getAuthors().get(0).equals(PlayerNPCPlugin.getInstance().getDescription().getAuthors().get(0)))
                PlayerNPCPlugin.getInstance().cancelAutomaticDownload();
        }
        return pluginManager;
    }

    public void unregisterPlugin(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Cannot register plugin manager from a null plugin.");
        Validate.isTrue(pluginManager.containsKey(plugin), "This plugin is not registered.");
        getPluginManager(plugin).onUnregister();
        this.pluginManager.remove(plugin);
        Bukkit.getConsoleSender().sendMessage(this.plugin.getPrefix()  + "§7Unregistered §6" + plugin.getName() + " §7plugin from the NPCLib");
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

    public List<Plugin> getRegisteredPlugins(){ return pluginManager.keySet().stream().toList(); }

    public NPC.Personal generatePersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String simpleID, @Nonnull Location location){
        Validate.notNull(player, "You cannot create an NPC with a null Player");
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.isTrue(isRegistered(plugin), "This plugin is not registered on NPCLib");
        Validate.notNull(simpleID, "You cannot create an NPC with a null simpleID");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        Validate.isTrue(!simpleID.toLowerCase().startsWith("global_"), "You cannot create NPC with global tag");
        return getPluginManager(plugin).generatePlayerPersonalNPC(player, Registry.ID.of(plugin, simpleID), location);
    }

    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String simpleID, @Nonnull NPC.Global.Visibility visibility, @Nonnull Location location){ return generateGlobalNPC(plugin, simpleID, visibility, null, location); }
    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String simpleID, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){ return generateGlobalNPC(plugin, simpleID, NPC.Global.Visibility.EVERYONE, visibilityRequirement, location); }
    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String simpleID, @Nonnull Location location){ return generateGlobalNPC(plugin, simpleID, NPC.Global.Visibility.EVERYONE, null, location); }
    public NPC.Global generateGlobalNPC(@Nonnull Plugin plugin, @Nonnull String simpleID, @Nonnull NPC.Global.Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
        Validate.notNull(plugin, "You cannot create an NPC with a null Plugin");
        Validate.notNull(simpleID, "You cannot create an NPC with a null simple ID");
        Validate.notNull(visibility, "You cannot create an NPC with a null visibility");
        Validate.notNull(location, "You cannot create an NPC with a null Location");
        Validate.notNull(location.getWorld(), "You cannot create NPC with a null world");
        Validate.isTrue(isRegistered(plugin), "This plugin is not registered on NPCLib");
        return getPluginManager(plugin).generatePlayerGlobalNPC(Registry.ID.of(plugin, simpleID), visibility, visibilityRequirement, location);
    }

    @Deprecated public Optional<NPC.Personal> grabPersonalNPC(@Nonnull Player player, @Nonnull String fullID){ return grabPersonalNPC(player, Registry.ID.of(fullID)); }

    public Optional<NPC.Personal> grabPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String simpleID){ return grabPersonalNPC(player, Registry.ID.of(plugin, simpleID)); }

    public Optional<NPC.Personal> grabPersonalNPC(@Nonnull Player player, @Nonnull Registry.ID id){ return getNPCPlayerManager(player).grabNPC(id); }

    @Deprecated @Nullable public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String simpleID){ return getPersonalNPC(player, Registry.ID.of(plugin, simpleID)); }

    @Deprecated @Nullable public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull String fullID){ return getPersonalNPC(player, Registry.ID.of(fullID)); }

    @Deprecated @Nullable public NPC.Personal getPersonalNPC(@Nonnull Player player, @Nonnull Registry.ID id){ return grabPersonalNPC(player, id).orElse(null); }

    @Nonnull
    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Player player, @Nonnull Plugin plugin){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(plugin, "Plugin must not be null");
        return getNPCPlayerManager(player).getNPCs(plugin);
    }

    @Nonnull
    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Plugin must not be null");
        Set<NPC.Personal> set = new HashSet<>(); Bukkit.getOnlinePlayers().forEach(x-> set.addAll(getPersonalNPCs(x, plugin)));
        return set;
    }

    @Nonnull
    public Set<NPC.Personal> getPersonalNPCs(@Nonnull Player player, @Nonnull World world){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(world, "World must not be null");
        return getNPCPlayerManager(player).getNPCs(world);
    }

    @Nonnull public Set<NPC.Personal> getAllPersonalNPCs(@Nonnull Player player){ return getNPCPlayerManager(player).getNPCs(); }

    public boolean hasPersonalNPC(@Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String simpleID){
        Validate.notNull(plugin, "Plugin must not be null");
        Validate.notNull(simpleID, "NPC simpleID must not be null");
        return hasPersonalNPC(player, Registry.ID.of(plugin, simpleID));
    }

    @Deprecated public boolean hasPersonalNPC(@Nonnull Player player, @Nonnull String fullID){ return hasPersonalNPC(player, Registry.ID.of(fullID)); }

    public boolean hasPersonalNPC(@Nonnull Player player, @Nonnull Registry.ID id){
        Validate.notNull(player, "Player must not be null");
        Validate.notNull(id, "NPC id must not be null");
        return getNPCPlayerManager(player).personalNPCs.contains(id);
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
        getNPCPlayerManager(npc.getPlayer()).removeNPC(npc.getID());
    }

    @Deprecated @Nullable public NPC.Global getGlobalNPC(@Nonnull Plugin plugin, @Nonnull String id){ return grabGlobalNPC(plugin, id).orElse(null); }

    @Deprecated @Nullable public NPC.Global getGlobalNPC(@Nonnull Registry.ID id){ return grabGlobalNPC(id).orElse(null); }

    @Deprecated @Nullable public NPC.Global getGlobalNPC(@Nonnull String fullID){ return grabGlobalNPC(fullID).orElse(null); }

    public Optional<NPC.Global> grabGlobalNPC(@Nonnull Plugin plugin, @Nonnull String id){ return grabGlobalNPC(Registry.ID.of(plugin, id)); }

    public Optional<NPC.Global> grabGlobalNPC(@Nonnull Registry.ID id){ return globalNPCs.grab(id); }

    @Deprecated public Optional<NPC.Global> grabGlobalNPC(@Nonnull String fullID){ return grabGlobalNPC(Registry.ID.of(fullID)); }

    public boolean hasGlobalNPC(@Nonnull Plugin plugin, @Nonnull String simpleID){ return hasGlobalNPC(Registry.ID.of(plugin, simpleID)); }

    public boolean hasGlobalNPC(@Nonnull Registry.ID id){ return globalNPCs.contains(id); }

    @Deprecated public boolean hasGlobalNPC(@Nonnull String fullID){ return hasGlobalNPC(Registry.ID.of(fullID)); }

    public Set<NPC.Global> getAllGlobalNPCs(){ return Set.copyOf(globalNPCs.getValues()); }

    public Set<NPC.Global> getAllGlobalNPCs(@Nonnull Plugin plugin){
        Validate.notNull(plugin, "Plugin must not be null");
        Set<NPC.Global> npcs = new HashSet<>();
        globalNPCs.getValues().stream().filter(x-> x.getPlugin().equals(plugin)).forEach(x-> npcs.add(x));
        return npcs;
    }

    public void addGlobalCommand(Plugin plugin, String argument, String arguments, boolean enabled, boolean important, String description, String hover, BiConsumer<NPCGlobalCommand.Command, NPCGlobalCommand.CommandData> execute, BiFunction<NPCGlobalCommand.Command, NPCGlobalCommand.CommandData, List<String>> tabComplete, Command.Color color){
        if(plugin.equals(this.plugin)) throw new IllegalArgumentException("Plugin must be yours.");
        NPCGlobalCommand.addCommand(plugin, argument, arguments, enabled, important, description, hover, execute, tabComplete, color);
    }

    public boolean hasGlobalCommand(String argument){ return getGlobalCommand(argument) != null; }

    public NPCGlobalCommand.Command getGlobalCommand(String argument){ return NPCGlobalCommand.getCommand(argument); }

    public Set<NPCGlobalCommand.Command> getGlobalCommands(Plugin plugin){ return NPCGlobalCommand.getCommands(plugin); }


    public void removeGlobalNPC(@Nonnull NPC.Global npc){
        Validate.notNull(npc, "NPC was not found");
        npc.destroy();
        globalNPCs.remove(npc.getID());
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

    public boolean isDebug() { return debug; }

    public boolean isUsingBukkitScoreboards() { return useBukkitScoreboards; }

    public void setUseBukkitScoreboards(boolean useBukkitScoreboards) {
        if(this.useBukkitScoreboards == useBukkitScoreboards) return;
        this.useBukkitScoreboards = useBukkitScoreboards;
        saveConfig();
    }

    @Deprecated
    public Double getDefaultHideDistance() { return NPC.Attributes.getDefaultHideDistance(); }

    @Deprecated
    public void setDefaultHideDistance(Double hideDistance) { NPC.Attributes.setDefaultHideDistance(hideDistance); }

    public NPC.Attributes getDefaults(){ return NPC.Attributes.getDefault(); }

    protected Plugin getPlugin() { return plugin; }

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
        defaults.put("useBukkitScoreboards", this.useBukkitScoreboards);
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
        NMSFileConfiguration.setComments(config, "useBukkitScoreboards", Arrays.asList("This option will fix \"Team already exists on this scoreboard\" error in some BungeeCord versions.", "⚠ Caution, it's only recommended to enable if you get that error."));
        if(m) { try { config.save(file); } catch (IOException e) { printError(e); } }
        //
        this.debug = config.getBoolean("debug");
        this.useBukkitScoreboards = config.getBoolean("useBukkitScoreboards");
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
        config.set("useBukkitScoreboards", this.useBukkitScoreboards);
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
        playerNPCPlugin.getServer().getOnlinePlayers().forEach(x-> quit(x, true));
    }

    private PlayerNPCPlugin getPlayerNPCPlugin(){ return plugin; }

    protected PlayerManager getNPCPlayerManager(@Nonnull Player player){
        Validate.notNull(player, "Cannot get PlayerManager from a null Player");
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
        Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
            for(NPC.Global global : getAllGlobalNPCs()){
                if(!global.hasPlayer(player)) continue;
                global.forceUpdate(player);
            }
            for(NPC.Personal personal : npcPlayerManager.getPersonalNPCs().getValues().stream().filter(x-> !x.hasGlobal()).collect(Collectors.toSet())){
                if(personal.isCreated() && personal.isShown()) personal.forceUpdate();
            }
        }, 40);
    }

    private void quit(Player player){ quit(player, false); }

    private void quit(Player player, boolean restart){
        for(NPC.Global global : getAllGlobalNPCs()){
            if(!global.hasPlayer(player)) continue;
            global.players.remove(player);
        }
        PlayerManager npcPlayerManager = getNPCPlayerManager(player);
        npcPlayerManager.destroyAll();
        npcPlayerManager.getPacketReader().unInject();
        if(restart && !npcPlayerManager.getClientVersion().equals(ServerVersion.getServerVersion())) player.kickPlayer("Restarting server...");
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
        if(!isRegistered(plugin)) return;
        unregisterPlugin(event.getPlugin());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(!event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && !event.getAction().equals(Action.RIGHT_CLICK_AIR)) return;
        if(!player.isSneaking()) return;
        if(!player.isOp()) return;
        if(event.getItem() == null) return;
        if(!event.getItem().hasItemMeta()) return;
        if(!event.getItem().getItemMeta().getPersistentDataContainer().has(NPCLibCommand.skinKey, PersistentDataType.STRING)) return;
        String skinKey = event.getItem().getItemMeta().getPersistentDataContainer().get(NPCLibCommand.skinKey, PersistentDataType.STRING);
        if(event.getAction().equals(Action.RIGHT_CLICK_AIR)){
            if(!ServerVersion.getServerVersion().isNewerThan(ServerVersion.VERSION_1_18_2)){
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cPlayer skin change only available on 1.18.2+"));
                return;
            }
            if(skinKey.startsWith(NPC.Skin.Type.MINECRAFT.name() + ":")) {
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINECRAFT.name() + ":", "");
                NPC.Skin.Minecraft.fetchSkinAsync(PlayerNPCPlugin.getInstance(), skinName).thenAccept(fetchResult -> {
                    if(fetchResult.hasError()) return;
                    NPC.Skin.Minecraft skin = fetchResult.grabSkin().get();
                    if(!skin.isModifiedByThirdParty()) skin.applyToPlayer(player, p -> p.sendTitle("§a", "§aYour skin has been changed to " + skin.getName(), 5, 20, 5));
                    else NPC.Skin.Minecraft.fetchSkinMojangAsync(player.getName()).thenAccept(mojangFetchResult->{
                        if(!mojangFetchResult.hasFound()) return;
                        NPC.Skin.Minecraft mojangSkin = mojangFetchResult.grabSkin().get();
                        mojangSkin.applyToPlayer(player, p -> p.sendTitle("§a", "§aYour skin has been changed to " + mojangSkin.getName(), 5, 20, 5));
                    });
                });
            }
            else if(skinKey.startsWith(NPC.Skin.Type.MINESKIN.name() + ":")){
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINESKIN.name() + ":", "");
                NPC.Skin.MineSkin.fetchSkinAsync(PlayerNPCPlugin.getInstance(), skinName).thenAccept(result ->{
                    if(result.hasFound()) result.grabSkin().get().applyToPlayer(player, p -> p.sendTitle("§a", "§aYour skin has been changed to MineSkin", 5, 20, 5));
                });
            }
            else if(skinKey.startsWith(NPC.Skin.Type.CUSTOM.name() + ":")) {
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.CUSTOM.name() + ":", "");
                NPC.Skin.Custom.fetchCustomSkinAsync(skinName).thenAccept(result -> {
                    if(result.hasFound()) result.grabSkin().get().applyToPlayer(player, p -> p.sendTitle("§a", "§aYour skin has been changed to " + result.grabSkin().get().getName(), 5, 20, 5));
                });
            }
            return;
        }
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)){
            String simpleID = "preview_" + skinKey.replaceAll("\\.", "_");
            if(hasPersonalNPC(player, Registry.ID.of(plugin, simpleID))) return;
            event.setCancelled(true);
            //
            Registry.ID previewSeconds = Registry.ID.playerNPC("previewSecondsDisappear");
            NPC.Placeholders.addPlaceholderIfNotExists(previewSeconds.getSimpleID(), (n, p) -> {
                Integer a = Integer.valueOf(n.grabCustomData(previewSeconds).orElse("0"));
                String color = "§a";
                if(a < 10) color = "§e";
                if(a < 5) color = "§c";
                return color + a;
            });
            NPC.Personal preview = generatePersonalNPC(player, plugin, simpleID, event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5));
            preview.addCustomClickAction(NPC.Interact.ClickType.LEFT_CLICK, (npc, player1) -> npc.playAnimation(NPC.Animation.TAKE_DAMAGE));
            NPC.Interact.Actions.CustomAction customAction = preview.addCustomClickAction(NPC.Interact.ClickType.LEFT_CLICK, (npc, player1) -> cancelPreview(preview));
            customAction.setDelayTicks(3L);
            preview.setTextLineOpacity(1, NPC.Hologram.Opacity.LOW);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§aPreviewing skin..."));
            if(skinKey.startsWith(NPC.Skin.Type.MINECRAFT.name() + ":")){
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINECRAFT.name() + ":", "");
                preview.setSkin(skinName, fetchResult ->{
                    if(fetchResult.hasError()){
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                        removePersonalNPC(preview);
                        return;
                    }
                    preview.setText("§7Disappears in {" + previewSeconds.getSimpleID() + "} seconds...", "", "§6§lMinecraft Skin Preview", "§e" + fetchResult.grabSkin().get().getName());
                    preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo minecraft " + fetchResult.grabSkin().get().getName());
                    showPreview(preview);
                });
            }
            else if(skinKey.startsWith(NPC.Skin.Type.MINESKIN.name() + ":")){
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.MINESKIN.name() + ":", "");
                preview.setMineSkin(skinName, fetchResult ->{
                    if(fetchResult.hasError()){
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                        removePersonalNPC(preview);
                        return;
                    }
                    preview.setText("§7Disappears in {" + previewSeconds.getSimpleID() + "} seconds...", "", "§6§lMineSkin Preview", "§e" + fetchResult.grabSkin().get().getId());
                    preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo mineskin " + fetchResult.grabSkin().get().getId());
                    showPreview(preview);
                });
            }
            else if(skinKey.startsWith(NPC.Skin.Type.CUSTOM.name() + ":")){
                String skinName = skinKey.replaceFirst(NPC.Skin.Type.CUSTOM.name() + ":", "");
                NPC.Skin.Custom.fetchCustomSkinAsync(skinName).thenAccept(fetchResult -> {
                    if(fetchResult.hasError()){
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cThere was an error fetching that skin."));
                        removePersonalNPC(preview);
                        return;
                    }
                    preview.setSkin(fetchResult.grabSkin().get());
                    preview.setText("§7Disappears in {" + previewSeconds.getSimpleID() + "} seconds...", "", "§6§lCustom Skin Preview", "§e" + fetchResult.grabSkin().get().getID().getFullID());
                    preview.addRunPlayerCommandClickAction(NPC.Interact.ClickType.RIGHT_CLICK, "npclib getskininfo custom " + fetchResult.grabSkin().get().getID().getFullID());
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> showPreview(preview));
                });
            }
            preview.setCustomData(previewSeconds, "15");
            preview.setCustomData(Registry.ID.playerNPC("bukkitTaskID"), "" + Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, ()-> {
                if(!hasPersonalNPC(player, plugin, simpleID)){
                    cancelPreview(preview);
                    return;
                }
                Integer seconds = Integer.valueOf(preview.grabCustomData(previewSeconds).get());
                seconds--;
                preview.setCustomData(previewSeconds, seconds.toString());
                preview.simpleUpdateText();
                if(seconds == 0){ cancelPreview(preview); }
            }, 20, 20));
        }
    }

    private void showPreview(NPC.Personal preview){
        preview.create();
        preview.lookAt(preview.getPlayer());
        preview.show();
    }

    private void cancelPreview(NPC.Personal preview){
        preview.getPlayer().spawnParticle(Particle.CLOUD, preview.getLocation(), 3, 0.2, 0.5, 0.2, 0.1);
        Bukkit.getScheduler().cancelTask(Integer.valueOf(preview.grabCustomData(Registry.ID.playerNPC("bukkitTaskID")).get()));
        removePersonalNPC(preview);
    }

    public static NPCLib getInstance(){
        Validate.notNull(instance, "NPCLib has not been started yet, make sure to have PlayerNPC.jar on your plugins folder, and to add dependency or softdependency to PlayerNPC on your plugin.yml");
        return instance;
    }

    public static void printError(Exception e){
        if(NPCLib.getInstance().isDebug()) e.printStackTrace();
    }

    public enum UpdateGazeType implements EnumUtils.GetName {
        MOVE_EVENT,
        TICKS,
    }

    public static class Registry<V>{

        private ID registryID;
        private HashMap<String, V> registry;

        protected Registry(ID id){
            registryID = id;
            registry = new HashMap<>();
        }

        public ID getRegistryID() { return registryID; }

        public Optional<V> grab(@Nonnull ID id){
            Validate.notNull(id, "Cannot grab from Registry with a null ID");
            return Optional.ofNullable(registry.getOrDefault(id.getFullID(), null));
        }

        @Deprecated @Nullable public V get(@Nonnull ID id){ return grab(id).orElse(null); }

        public void set(@Nonnull ID id, @Nullable V value) {
            Validate.notNull(id, "Cannot set on Registry with a null ID");
            if(value == null) remove(id);
            else registry.put(id.getFullID(), value);
        }

        public void add(@Nonnull ID id, V value) throws IllegalArgumentException{
            Validate.notNull(id, "Cannot add on Registry with a null ID");
            Validate.isTrue(!contains(id), "There's a value for that ID already");
            registry.putIfAbsent(id.getFullID(), value);
        }

        public void remove(@Nonnull ID id) {
            Validate.notNull(id, "Cannot remove from a Registry with a null ID");
            registry.remove(id.getFullID());
        }

        public void clear() { registry.clear(); }

        public Collection<V> getValues() { return registry.values(); }

        public Set<String> getKeys() { return registry.keySet(); }

        public Set<NPCLib.Registry.ID> getKeysID() { return getKeys().stream().map(x-> NPCLib.Registry.ID.of(x)).collect(Collectors.toSet()); }

        public boolean contains(@Nonnull String fullID) { return registry.containsKey(fullID); }

        public boolean contains(@Nonnull ID id) { return contains(id.getFullID()); }

        public boolean isEmpty() { return registry.isEmpty(); }

        public Integer size() { return registry.size(); }

        public Set<Map.Entry<String, V>> entrySet() { return registry.entrySet(); }

        public interface Identified{
            ID getID();
            default String getSimpleID() { return getID().getSimpleID(); }
            default String getFullID() { return getID().getFullID(); }
        }

        public static class ID {

            private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9/:_-]+");
            private static final Integer MAX_LENGTH = 128;

            public static ID of(@Nonnull Plugin plugin, @Nonnull String simpleID) {
                Validate.notNull(plugin, "Cannot create ID from a null plugin");
                Validate.notNull(simpleID, "Cannot create ID from a null simple id");
                return new ID(plugin, simpleID);
            }

            public static ID of(@Nonnull Plugin plugin, @Nonnull Integer simpleID) {
                Validate.notNull(plugin, "Cannot create ID from a null plugin");
                Validate.notNull(simpleID, "Cannot create ID from a null simple id");
                return new ID(plugin, simpleID.toString());
            }

            public static ID of(@Nonnull Plugin plugin, @Nonnull UUID simpleID) {
                Validate.notNull(plugin, "Cannot create ID from a null plugin");
                Validate.notNull(simpleID, "Cannot create ID from a null simple id");
                return new ID(plugin, simpleID.toString());
            }

            public static ID of(String pluginName, String simpleID) { return new ID(pluginName.toLowerCase(), simpleID.toLowerCase()); }

            protected static ID of(@Nonnull String fullID) {
                Validate.notNull(fullID, "Cannot parse Full ID from a null String.");
                Validate.isTrue(fullID.contains("."), "This full ID is incorrect.");
                String[] split = fullID.split("\\.", 2);
                return new ID(split[0], split[1]);
            }

            protected static ID playerNPC(@Nonnull String simpleID) { return new ID(PlayerNPCPlugin.getInstance(), simpleID); }

            public static boolean isValid(Plugin plugin, String simpleID) { return isFullValid(plugin.getName().toLowerCase() + "." + simpleID); }

            public static boolean isSimpleValid(String simpleID){ return VALID_KEY.matcher(simpleID.toLowerCase()).matches(); }

            public static boolean isFullValid(String fullID){
                fullID = fullID.toLowerCase();
                if(!fullID.contains(".")) return false;
                String[] split = fullID.split("\\.", 2);
                if(!isSimpleValid(split[0])) return false;
                if(!isSimpleValid(split[1])) return false;
                return true;
            }

            private final String pluginName;
            private final String simpleID;
            private final String fullID;

            private ID(@Nonnull Plugin plugin, @Nonnull String simpleID) { this(plugin.getName().toLowerCase(), simpleID.toLowerCase()); }

            private ID(@Nonnull String pluginName, @Nonnull String simpleID){
                Validate.notNull(pluginName, "Cannot generate an ID. Plugin must not be null");
                Validate.notNull(simpleID, "Cannot generate an ID. Simple ID must not be null");
                Validate.isTrue(VALID_KEY.matcher(pluginName).matches(), "Invalid Plugin name '" + simpleID + "'. Allowed characters: ('a-z','0-9',':','_','-')");
                Validate.isTrue(VALID_KEY.matcher(simpleID).matches(), "Invalid Simple ID '" + simpleID + "'. Allowed characters: ('a-z','0-9',':','_','-')");
                this.pluginName = pluginName;
                this.simpleID = simpleID;
                this.fullID = pluginName + "." + simpleID;
                Validate.isTrue(fullID.length() <= MAX_LENGTH, "Full ID length cannot be more than " + MAX_LENGTH + " characters");
            }

            public String getPluginName() { return pluginName; }

            public String getSimpleID() { return simpleID; }

            public String getFullID() { return fullID; }

            public boolean equals(String fullID){ return this.fullID.equals(fullID); }

            public boolean equals(ID id){ return equals(id.getFullID()); }

            public boolean equals(Plugin plugin, String simpleID) { return equals(ID.of(plugin, simpleID).getFullID()); }

            public boolean equals(String pluginName, String simpleID) { return equals(ID.of(pluginName, simpleID).getFullID()); }

            public boolean equals(Plugin plugin, UUID simpleID) { return equals(ID.of(plugin, simpleID).getFullID()); }

            protected boolean isPlayerNPC() { return pluginName.equalsIgnoreCase(PlayerNPCPlugin.getInstance().getName()); }

            @Override public String toString() { return getFullID(); }

        }
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
            if(plugin.equals(npcLib.getPlugin())) npcLib.loadConfig();
            loadPersistentNPCs();
        }

        protected void onUnregister(){
            savePersistentNPCs();
            //
            Set<NPC.Global> globals = npcLib.getAllGlobalNPCs(plugin);
            if(!globals.isEmpty()) globals.forEach(x-> npcLib.removeGlobalNPC(x));
            //
            Set<NPC.Personal> npc = npcLib.getPersonalNPCs(plugin);
            if(!npc.isEmpty())  npc.forEach(x-> npcLib.removePersonalNPC(x));
            //
            NPC.Skin.Custom.onUnregisterPlugin(plugin);
        }

        public Optional<NPC.Global> grabGlobalNPC(String simpleCode){ return npcLib.grabGlobalNPC(plugin, simpleCode); }

        public Optional<NPC.Personal> grabPersonalNPC(Player player, String simpleCode) { return npcLib.grabPersonalNPC(player, plugin, simpleCode); }

        @Deprecated public NPC.Personal getPersonalNPC(Player player, String simpleCode){ return npcLib.getPersonalNPC(player, plugin, simpleCode); }

        private void loadPersistentNPCs(){
            if(!plugin.equals(npcLib.getPlugin())) return;
            File folder = new File("plugins/PlayerNPC/persistent/global/" + plugin.getName().toLowerCase() + "/");
            if(!folder.exists()) { folder.mkdirs(); }
            if(folder.listFiles().length == 0) return;
            Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Loading Persistent Global NPCs for plugin §e" + plugin.getName());
            for (File f : folder.listFiles()) {
                if(!f.isDirectory()) continue;
                try{
                    NPC.Global.PersistentManager persistent = new NPC.Global.PersistentManager(plugin, f.getName());
                    persistent.load();
                }
                catch (Exception e){
                    Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Error loading Persistent Global NPC §c" + f.getName());
                    printError(e);
                }
            }
        }

        private void savePersistentNPCs(){ NPC.Global.PersistentManager.forEachPersistentManager(plugin, x-> x.save()); }

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

        public Registry.ID formatID(String simpleID) { return Registry.ID.of(plugin, simpleID); }

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
            if(ticksUntilTabListHide < 0) ticksUntilTabListHide = 0;
            if(ticksUntilTabListHide.equals(this.ticksUntilTabListHide)) return;
            this.ticksUntilTabListHide = ticksUntilTabListHide;
            if(plugin.equals(npcLib.getPlugin())) npcLib.saveConfig();
        }

        protected NPC.Personal generatePlayerPersonalNPC(Player player, Registry.ID id, Location location){
            Validate.isTrue(!npcLib.hasPersonalNPC(player, id), "Personal NPC with ID " + id.getFullID() + " for player " + player.getName() + " already exists.");
            return new NPC.Personal(this, id, player, location);
        }

        protected NPC.Global generatePlayerGlobalNPC(Registry.ID id, NPC.Global.Visibility visibility, Predicate<Player> visibilityRequirement, Location location){
            Validate.isTrue(!npcLib.hasGlobalNPC(id.getFullID()), "Global NPC with ID " + id.getFullID() + " already exists.");
            return new NPC.Global(this, id, visibility, visibilityRequirement, location);
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
        private final Registry<NPC.Personal> personalNPCs;
        private final PlayerManager.PacketReader packetReader;
        private final Map<World, Set<NPC.Personal>> hidden;
        private final Long lastEnter;
        private ServerVersion clientVersion;

        protected PlayerManager(NPCLib npcLib, Player player) {
            this.npcLib = npcLib;
            this.player = player;
            if(IntegrationsManager.isUsingViaVersion()){
                Integer protocolVersion = IntegrationsManager.getViaVersion().getProtocolVersion(player);
                if(protocolVersion != null && protocolVersion > 0 && !ServerVersion.getServerVersion().getProtocolVersion().equals(protocolVersion)){
                    this.clientVersion = ServerVersion.getProtocolVersion(protocolVersion);
                    if(debug) Bukkit.getConsoleSender().sendMessage(player.getName() + " is using client version §a" + clientVersion.getMinecraftVersion() + "§7 (" + clientVersion.getProtocolVersion() + ") vs server version §e" + ServerVersion.getServerVersion().getMinecraftVersion() + " §7(" + ServerVersion.getServerVersion().getProtocolVersion() + ")");
                }
            }
            if(this.clientVersion == null) clientVersion = ServerVersion.getServerVersion();
            this.personalNPCs = new Registry<>(Registry.ID.playerNPC("personalNPCs"));
            this.packetReader = new PlayerManager.PacketReader(this);
            this.hidden = new HashMap<>();
            this.lastEnter = System.currentTimeMillis();
        }

        public ServerVersion getClientVersion() { return clientVersion; }

        protected Registry<NPC.Personal> getPersonalNPCs() { return personalNPCs; }

        protected Optional<NPC.Personal> grabNPC(Integer entityID){ return personalNPCs.getValues().stream().filter(x-> x.isCreated() && NMSEntity.getEntityID(x.getEntity()).equals(entityID)).findAny(); }

        protected void removeNPC(Registry.ID id){ personalNPCs.remove(id); }

        protected void updateMove(Plugin plugin){ getNPCs(getPlayer().getWorld()).stream().filter(x-> x.getPlugin().equals(plugin) && x.isCreated()).forEach(x-> x.updateMove()); }

        protected void destroyWorld(World world){
            Set<NPC.Personal> r = new HashSet<>();
            personalNPCs.getValues().stream().filter(x-> x.getWorld().getName().equals(world.getName())).forEach(x->{
                if(x.getTabListVisibility().equals(NPC.TabListVisibility.SAME_WORLD)) x.removeTabList();
                if(x.isShownOnClient()){
                    x.hideToClient();
                    r.add(x);
                }
            });
            hidden.put(world, r);
        }

        protected void showWorld(World world){
            if(!hidden.containsKey(world)) return;
            hidden.get(world).stream().filter(x-> x.isCreated()).forEach(x-> x.showToClient());
            hidden.remove(world);
            getNPCs(world).stream().filter(x-> x.getTabListVisibility().equals(NPC.TabListVisibility.SAME_WORLD) && !x.isShownOnClientTabList()).forEach(x-> x.addTabList(true));
        }

        protected void changeWorld(NPC.Personal npc, World from, World to){
            if(!hidden.containsKey(from)) return;
            if(!hidden.get(from).contains(npc)) return;
            hidden.get(from).remove(npc);
            npc.show();
        }

        protected void destroyAll(){
            Set<NPC.Personal> destroy = new HashSet<>();
            destroy.addAll(personalNPCs.getValues());
            destroy.stream().filter(x-> x.isCreated()).forEach(x-> x.destroy());
            personalNPCs.clear();
        }

        protected Set<NPC.Personal> getNPCs(World world){
            Validate.notNull(world, "World must be not null");
            return personalNPCs.getValues().stream().filter(x-> x.getWorld().equals(world)).collect(Collectors.toSet());
        }

        protected Set<NPC.Personal> getNPCs(Plugin plugin){
            Validate.notNull(plugin, "Plugin must be not null");
            return personalNPCs.getValues().stream().filter(x-> x.getPlugin().equals(plugin)).collect(Collectors.toSet());
        }

        protected Set<NPC.Personal> getNPCs(){ return personalNPCs.getValues().stream().collect(Collectors.toSet()); }

        protected Optional<NPC.Personal> grabNPC(Registry.ID id){ return personalNPCs.grab(id); }

        protected NPCLib getNPCLib() { return npcLib; }

        protected Player getPlayer() { return player; }

        protected PlayerManager.PacketReader getPacketReader() { return packetReader; }

        protected Long getLastEnter() { return lastEnter; }

        protected Long ticksToAppear() {
            if(System.currentTimeMillis() - lastEnter < 500) return 10L;
            if(System.currentTimeMillis() - lastEnter < 750) return 15L;
            if(System.currentTimeMillis() - lastEnter < 1000) return 20L;
            return 0L;
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
                channel = NMSNetworkManager.getChannel(NMSNetworkManager.getNetworkManager(npcPlayerManager.getPlayer()));
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
                interact(getPlayerManager().grabNPC(id).orElse(null), clickType);
            }

            private void interact(NPC.Personal npc, NPC.Interact.ClickType clickType){
                if(npc == null) return;
                if(lastClick.containsKey(npc) && System.currentTimeMillis() - lastClick.get(npc) < npc.getInteractCooldown()) return;
                lastClick.put(npc, System.currentTimeMillis());
                Bukkit.getScheduler().runTask(getNPCLib().getPlugin(), ()-> npc.interact(npcPlayerManager.getPlayer(), clickType));
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
