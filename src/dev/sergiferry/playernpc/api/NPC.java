package dev.sergiferry.playernpc.api;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.integration.IntegrationsManager;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftItemStack;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftScoreboard;
import dev.sergiferry.playernpc.nms.minecraft.*;
import dev.sergiferry.playernpc.nms.spigot.NMSFileConfiguration;
import dev.sergiferry.playernpc.nms.spigot.NMSFileUtils;
import dev.sergiferry.playernpc.nms.spigot.NMSPlayer;
import dev.sergiferry.playernpc.utils.*;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import dev.sergiferry.spigot.server.ServerVersion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.mineskin.MineskinClient;
import org.mineskin.SkinOptions;
import org.mineskin.Variant;
import org.mineskin.Visibility;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * NPC instance per player. An NPC can only be seen by one player. This is because of personalization purposes.
 * With this instance you can create customizable Player NPCs that can be interacted with.
 * NPCs will be only visible to players after creating the EntityPlayer, and show it to the player.
 *
 * @since 2021.1
 * @author  SergiFerry
 */
public abstract class NPC implements NPCLib.Registry.Identified {

    protected final NPCLib.PluginManager pluginManager;
    private final NPCLib.Registry.ID id;
    private final NPCLib.Registry<String> customData;
    private World world;
    private Double x, y, z;
    private Float yaw, pitch;
    private List<NPC.Interact.ClickAction> clickActions;
    private NPC.Move.Task moveTask;
    private NPC.Move.Behaviour moveBehaviour;
    protected Updatable.PendingUpdates pendingUpdates;

    // NPC.Attributes
    private NPC.Attributes attributes;

    protected NPC(@Nonnull NPCLib.PluginManager pluginManager, @Nonnull NPCLib.Registry.ID id, @Nonnull World world, double x, double y, double z, float yaw, float pitch){
        Validate.notNull(pluginManager, "Cannot generate NPC instance, PluginManager cannot be null.");
        Validate.notNull(id, "Cannot generate NPC instance, ID cannot be null.");
        Validate.notNull(world, "Cannot generate NPC instance, World cannot be null.");
        Validate.isTrue(id.getPluginName().equalsIgnoreCase(pluginManager.getPlugin().getName()), "Cannot generate NPC instance, ID Plugin (" + id.getPluginName() + ") do not match with PluginManager (" + pluginManager.getPlugin().getName() + ")");
        this.pluginManager = pluginManager;
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.clickActions = new ArrayList<>();
        this.moveTask = null;
        this.moveBehaviour = new NPC.Move.Behaviour(this);
        this.customData = new NPCLib.Registry<>(NPCLib.Registry.ID.of(getPlugin(), id.getSimpleID() + "-customData"));
        this.pendingUpdates = new Updatable.PendingUpdates(this);

        //NPC Attributes
        this.attributes = NPC.Attributes.copyOf(Attributes.DEFAULT);
    }

    protected abstract void simpleUpdate();

    protected abstract void forceUpdate();

    protected abstract void simpleUpdateText();

    protected abstract void forceUpdateText();

    protected Updatable needUpdate(Updatable.Type type) {
        return switch (type){
            case NONE -> noNeedUpdate();
            case SIMPLE_UPDATE -> needSimpleUpdate();
            case FORCE_UPDATE -> needForceUpdate();
            case SIMPLE_UPDATE_TEXT -> needSimpleUpdateText();
            case FORCE_UPDATE_TEXT -> needForceUpdateText();
            case SYNCHRONIZE_GLOBAL_ATTRIBUTES -> null;
        };
    }

    protected Updatable noNeedUpdate() { return new Updatable.InGeneral(); }

    protected Updatable needSimpleUpdate() { return new Updatable.InGeneral(this, Updatable.Type.SIMPLE_UPDATE); }

    protected Updatable needForceUpdate() { return new Updatable.InGeneral(this, Updatable.Type.FORCE_UPDATE); }

    protected Updatable needSimpleUpdateText() { return new Updatable.InGeneral(this, Updatable.Type.SIMPLE_UPDATE_TEXT); }

    protected Updatable needForceUpdateText() { return new Updatable.InGeneral(this, Updatable.Type.FORCE_UPDATE_TEXT); }

    protected Updatable needUpdateOrForce(boolean forceUpdate) { return forceUpdate ? needForceUpdate() : needSimpleUpdate(); }

    protected Updatable needUpdateTextOrForce(boolean forceUpdate) { return forceUpdate ? needForceUpdateText() : needSimpleUpdateText(); }

    public boolean hasPendingUpdates() { return pendingUpdates.hasPending(); }

    public boolean hasPendingUpdate(Updatable.Type type) { return pendingUpdates.containsType(type); }

    public void update() { pendingUpdates.update(); }

    protected abstract void destroy();

    protected abstract void teleport(World world, double x, double y, double z, float yaw, float pitch);

    public void teleport(@Nonnull Entity entity){
        Validate.notNull(entity, "Entity must be not null.");
        teleport(entity.getLocation());
    }

    public void teleport(@Nonnull Location location){
        Validate.notNull(location, "Location must be not null.");
        teleport(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void teleport(double x, double y, double z){ teleport(world, x, y, z); }

    public void teleport(World world, double x, double y, double z){ teleport(world, x, y, z, yaw, pitch); }

    public void teleport(double x, double y, double z, float yaw, float pitch){ teleport(this.world, x, y, z, yaw, pitch); }

    public Updatable setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
        Validate.notNull(slot, "Failed to set item, NPC.Slot cannot be null");
        if(itemStack == null) itemStack = new ItemStack(Material.AIR);
        return needUpdate(attributes.setItem(slot, itemStack));
    }

    public Updatable setHelmet(@Nullable ItemStack itemStack){ return setItem(Slot.HEAD, itemStack); }

    public Updatable setChestplate(@Nullable ItemStack itemStack){ return setItem(Slot.CHEST, itemStack); }

    public Updatable setLeggings(@Nullable ItemStack itemStack){ return setItem(Slot.LEGS, itemStack); }

    public Updatable setBoots(@Nullable ItemStack itemStack){ return setItem(Slot.FEET, itemStack); }

    public Updatable setItemInMainHand(@Nullable ItemStack itemStack){ return setItem(Slot.MAINHAND, itemStack); }

    public Updatable setItemInOffHand(@Nullable ItemStack itemStack){ return setItem(Slot.OFFHAND, itemStack); }

    @Deprecated public Updatable setItemInRightHand(@Nullable ItemStack itemStack){ return setItemInMainHand(itemStack); }

    @Deprecated public Updatable setItemInLeftHand(@Nullable ItemStack itemStack){ return setItemInOffHand(itemStack); }

    public Updatable clearEquipment(@Nonnull NPC.Slot slot){ return setItem(slot, null); }

    public Updatable clearEquipment(){
        Arrays.stream(NPC.Slot.values()).forEach(x-> clearEquipment(x));
        return needSimpleUpdate();
    }

    public Updatable lookAt(@Nonnull Entity entity){
        Validate.notNull(entity, "Failed to set look direction. The entity cannot be null");
        return lookAt(entity.getLocation());
    }

    public Updatable lookAt(@Nonnull Location location){
        Validate.notNull(location, "Failed to set look direction. The location cannot be null.");
        Validate.isTrue(location.getWorld().getName().equals(getWorld().getName()), "The location must be in the same world as NPC");
        Location npcLocation = new Location(world, x, y, z, yaw, pitch);
        Vector dirBetweenLocations = location.toVector().subtract(npcLocation.toVector());
        npcLocation.setDirection(dirBetweenLocations);
        return lookAt(npcLocation.getYaw(), npcLocation.getPitch());
    }

    public abstract Updatable lookAt(float yaw, float pitch);

    public Updatable setCollidable(boolean collidable) { return needUpdate(attributes.setCollidable(collidable)); }

    public Updatable setSkin(@Nonnull Skin.SignedTexture signedTexture) { return setSkin(null, signedTexture.texture, signedTexture.signature); }

    public Updatable setSkin(@Nullable String skinName, @Nonnull Skin.SignedTexture signedTexture) { return setSkin(skinName, signedTexture.texture, signedTexture.signature); }

    public Updatable setSkin(@Nonnull String texture, @Nonnull String signature){ return setSkin(null, texture, signature); }

    public Updatable setSkin(@Nullable String skinName, @Nonnull String texture, @Nonnull String signature){
        if(skinName == null) return setSkin(NPC.Skin.Custom.createCustomSkin(getPlugin(), texture, signature));
        else return setSkin(NPC.Skin.Custom.createCustomSkin(getPlugin(), skinName, texture, signature));
    }

    public CompletableFuture<Updatable> setSkin(@Nullable String playerName, @Nullable Consumer<NPC.Skin.Minecraft.FetchResult> finishAction){
        if(playerName == null) return CompletableFuture.completedFuture(setSkin(Skin.Minecraft.STEVE));
        CompletableFuture<Updatable> completableFuture = new CompletableFuture<>();
        NPC.Skin.Minecraft.fetchSkinAsync(getPlugin(), playerName).thenAccept(fetchResult -> {
            completableFuture.complete(setSkin(fetchResult.grabSkin().orElse(null)));
            if(finishAction != null) getPlugin().getServer().getScheduler().runTask(getPlugin(), ()-> finishAction.accept(fetchResult));
        });
        return completableFuture;
    }

    public CompletableFuture<Updatable> setMineSkin(@Nonnull String id, boolean forceDownload, Consumer<Skin.MineSkin.FetchResult> finishAction){
        Validate.notNull(id, "Cannot fetch null identifier of a MineSkin");
        CompletableFuture<Updatable> completableFuture = new CompletableFuture<>();
        NPC.Skin.MineSkin.fetchSkinAsync(getPlugin(), id, forceDownload).thenAccept(fetchResult -> {
            completableFuture.complete(setSkin(fetchResult.grabSkin().orElse(null)));
            if(finishAction != null) getPlugin().getServer().getScheduler().runTask(getPlugin(), ()-> finishAction.accept(fetchResult));
        });
        return completableFuture;
    }

    public CompletableFuture<Updatable> setMineSkin(@Nonnull String id, Consumer<Skin.MineSkin.FetchResult> finishAction){ return setMineSkin(id, false, finishAction); }

    public CompletableFuture<Updatable> setSkin(@Nullable String playerName){ return setSkin(playerName, (Consumer<Skin.Minecraft.FetchResult>) null); }

    public CompletableFuture<Updatable> setSkin(@Nullable Player playerSkin){
        if(playerSkin == null) return CompletableFuture.completedFuture(setSkin(Skin.Minecraft.STEVE));
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        return setSkin(playerSkin.getName(), (Consumer<Skin.Minecraft.FetchResult>) null);
    }

    public CompletableFuture<Updatable> setSkin(@Nullable Player playerSkin, Consumer<Skin.Minecraft.FetchResult> finishAction){
        if(playerSkin == null) return CompletableFuture.completedFuture(setSkin(Skin.Minecraft.STEVE));
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        return setSkin(playerSkin.getName(), finishAction);
    }

    public Updatable setInGameSkinOf(@Nonnull Player player){ return setSkin(NPC.Skin.Minecraft.getSkinGameProfile(player).orElse(Skin.Minecraft.getSteveSkin().getSignedTexture())); }

    public Updatable setSkin(@Nullable NPC.Skin npcSkin){ return needUpdate(attributes.setSkin(npcSkin)); }

    protected Updatable setSkinVisibleLayers(Skin.VisibleLayers skinVisibleLayers){ return needUpdate(attributes.setSkinVisibleLayers(skinVisibleLayers)); }

    public Updatable clearSkin(){ return setSkin((NPC.Skin) null); }

    public Updatable setSkinLayerVisibility(Skin.Layer layer, boolean visible){ return needUpdate(attributes.skinVisibleLayers.setVisibility(layer, visible)); }

    public Updatable setPose(@Nullable NPC.Pose pose){ return needUpdate(attributes.setPose(pose)); }

    public Updatable setCrouching(boolean b){
        if(b) return setPose(NPC.Pose.CROUCHING);
        else if(getPose().equals(NPC.Pose.CROUCHING)) return resetPose();
        return noNeedUpdate();
    }

    public Updatable setSwimming(boolean b){
        if(b) return setPose(Pose.SWIMMING);
        else if(getPose().equals(Pose.SWIMMING)) return resetPose();
        return noNeedUpdate();
    }

    public Updatable setSleeping(boolean b){
        if(b) return setPose(Pose.SLEEPING);
        else if(getPose().equals(Pose.SLEEPING)) return resetPose();
        return noNeedUpdate();
    }

    public Updatable resetPose(){ return setPose(NPC.Pose.STANDING); }

    public Updatable clearText(){ return setText(new ArrayList<>()); }

    public Updatable setText(@Nonnull List<String> text){ return needUpdate(attributes.setText(text)); }

    public Updatable setText(@Nonnull String... text){ return setText(Arrays.asList(text)); }

    public Updatable setText(@Nonnull String text){
        List<String> list = new ArrayList<>();
        if(text.contains("\\n")) for(String s : text.split("\\\\n")) list.add(s);
        else list.add(text);
        return setText(list);
    }

    public Updatable resetTextLinesOpacity(){
        attributes.resetTextLinesOpacity();
        return needForceUpdateText();
    }

    public Updatable resetTextLineOpacity(int line){
        attributes.resetTextLineOpacity(line);
        return needForceUpdateText();
    }

    public Updatable setTextLineOpacity(int line, @Nullable NPC.Hologram.Opacity textOpacity){ return needUpdate(attributes.setTextLineOpacity(line, textOpacity)); }

    protected void setTextLinesOpacity(HashMap<Integer, NPC.Hologram.Opacity> linesOpacity){ attributes.setTextLinesOpacity(linesOpacity); }

    public Updatable setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity){ return needUpdate(attributes.setTextOpacity(textOpacity)); }

    public Updatable resetTextOpacity(){ return setTextOpacity(NPC.Hologram.Opacity.LOWEST); }

    public Updatable setTextItem(@Nullable ItemStack item){ return needUpdate(attributes.setTextItem(item)); }

    public Updatable setTextItemGlowing(boolean glowing){ return needUpdate(attributes.setTextItemGlowing(glowing)); }

    public Updatable setTextItemGlowingColor(@Nullable NPC.Color color){ return needUpdate(attributes.setTextItemGlowingColor(color)); }

    public Updatable setTextItemGlowingColor(@Nullable ChatColor chatColor){ return setTextItemGlowingColor(chatColor != null ? NPC.Color.of(chatColor).orElse(null) : null); }

    public Updatable setGlowingColor(@Nullable ChatColor color){ return setGlowingColor(color != null ? NPC.Color.of(color).orElse(null) : null); }

    public Updatable setGlowingColor(@Nullable Color color){ return needUpdate(attributes.setGlowingColor(color)); }

    public Updatable setGlowing(boolean glowing, @Nullable ChatColor color){ return setGlowing(glowing, NPC.Color.of(color).orElse(null)); }

    public Updatable setGlowing(boolean glowing, @Nullable Color color){
        setGlowing(glowing);
        setGlowingColor(color);
        return needSimpleUpdate();
    }

    public Updatable setGlowing(boolean glowing){ return needUpdate(attributes.setGlowing(glowing)); }

    public Updatable setPotionParticlesColor(@Nullable java.awt.Color color){ return needUpdate(attributes.setPotionParticlesColor(color)); }

    public Updatable setPotionParticlesType(@Nullable PotionParticlesType potionParticlesType){ return needUpdate(attributes.setPotionParticlesType(potionParticlesType)); }

    public Updatable setInvisible(boolean invisible){ return needUpdate(attributes.setInvisible(invisible)); }

    public Move.Behaviour follow(NPC npc){
        Validate.isTrue(!npc.equals(this), "NPC cannot follow himself.");
        return moveBehaviour.setFollowNPC(npc);
    }

    public Move.Behaviour follow(NPC npc, double min, double max){
        Validate.isTrue(!npc.equals(this), "NPC cannot follow himself.");
        return moveBehaviour.setFollowNPC(npc, min, max);
    }

    public Move.Behaviour follow(Entity entity, double min, double max){ return moveBehaviour.setFollowEntity(entity, min, max); }

    public Move.Behaviour follow(Entity entity, double min){ return moveBehaviour.setFollowEntity(entity, min); }

    public Move.Behaviour follow(Entity entity){ return moveBehaviour.setFollowEntity(entity); }

    public void cancelMoveBehaviour(){ moveBehaviour.cancel(); }

    public NPC.Move.Path setPath(Move.Path.Type type, List<Location> locations){ return getMoveBehaviour().setPath(locations, type).start(); }

    public NPC.Move.Path setPath(Move.Path.Type type, Location... locations){ return setPath(type, Arrays.stream(locations).toList()); }

    public NPC.Move.Path setRepetitivePath(List<Location> locations){ return setPath(Move.Path.Type.REPETITIVE, locations); }

    public NPC.Move.Path setRepetitivePath(Location... locations){ return setRepetitivePath(Arrays.stream(locations).toList()); }

    public void setGazeTrackingType(@Nullable GazeTrackingType followLookType) { attributes.setGazeTrackingType(followLookType); }

    public void setHideDistance(double hideDistance) { attributes.setHideDistance(hideDistance); }

    @Deprecated public void setTextHideDistance(double textHideDistance) { attributes.setTextHideDistance(textHideDistance); }

    public Updatable setTextLineSpacing(double lineSpacing){ return needUpdate(attributes.setTextLineSpacing(lineSpacing)); }

    public Updatable resetTextLineSpacing(){ return setTextLineSpacing(NPC.Attributes.getDefault().getTextLineSpacing()); }

    public Updatable setTextAlignment(@Nonnull Vector vector){ return needUpdate(attributes.setTextAlignment(vector)); }

    public Updatable resetTextAlignment(){ return setTextAlignment(null); }

    public void setInteractCooldown(long milliseconds){ attributes.setInteractCooldown(milliseconds); }

    public void resetInteractCooldown(){ setInteractCooldown(NPC.Attributes.getDefaultInteractCooldown()); }

    public Interact.Actions.CustomAction addCustomClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull BiConsumer<NPC, Player> customAction){ return (Interact.Actions.CustomAction) addClickAction(new Interact.Actions.CustomAction(this, clickType,customAction)); }

    public Interact.Actions.CustomAction addCustomClickAction(@Nonnull BiConsumer<NPC, Player> customAction){ return addCustomClickAction(Interact.ClickType.EITHER, customAction); }

    public Interact.Actions.Player.SendChatMessage addMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String... message){ return (Interact.Actions.Player.SendChatMessage) addClickAction(new Interact.Actions.Player.SendChatMessage(this, clickType, message)); }

    public Interact.Actions.Player.SendChatMessage addMessageClickAction(@Nonnull String... message){ return addMessageClickAction(Interact.ClickType.EITHER, message); }

    public Interact.Actions.Player.PerformCommand addRunPlayerCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){ return (Interact.Actions.Player.PerformCommand) addClickAction(new Interact.Actions.Player.PerformCommand(this, clickType, command)); }

    public Interact.Actions.Player.PerformCommand addRunPlayerCommandClickAction(@Nonnull String command){ return addRunPlayerCommandClickAction(Interact.ClickType.EITHER, command); }

    public Interact.Actions.Console.PerformCommand addRunConsoleCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){ return (Interact.Actions.Console.PerformCommand) addClickAction(new Interact.Actions.Console.PerformCommand(this, clickType, command)); }

    public Interact.Actions.Console.PerformCommand addRunConsoleCommandClickAction(@Nonnull String command){ return addRunConsoleCommandClickAction(Interact.ClickType.EITHER, command); }

    public Interact.Actions.Player.ConnectBungeeServer addConnectBungeeServerClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String server){ return (Interact.Actions.Player.ConnectBungeeServer) addClickAction(new Interact.Actions.Player.ConnectBungeeServer(this, clickType, server)); }

    public Interact.Actions.Player.ConnectBungeeServer addConnectBungeeServerClickAction(@Nonnull String server){ return addConnectBungeeServerClickAction(Interact.ClickType.EITHER, server); }

    public Interact.Actions.Player.SendActionBarMessage addActionBarMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String message){ return (Interact.Actions.Player.SendActionBarMessage) addClickAction(new Interact.Actions.Player.SendActionBarMessage(this, clickType, message)); }

    public Interact.Actions.Player.SendActionBarMessage addActionBarMessageClickAction(@Nonnull String message){ return addActionBarMessageClickAction(Interact.ClickType.EITHER, message); }

    public Interact.Actions.Player.SendTitleMessage addTitleMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){ return (Interact.Actions.Player.SendTitleMessage) addClickAction(new Interact.Actions.Player.SendTitleMessage(this, clickType, title, subtitle, fadeIn, stay, fadeOut)); }

    public Interact.Actions.Player.SendTitleMessage addTitleMessageClickAction(@Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){ return addTitleMessageClickAction(Interact.ClickType.EITHER, title, subtitle, fadeIn, stay, fadeOut); }

    public Interact.Actions.Player.TeleportToLocation addTeleportToLocationClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Location location){ return (Interact.Actions.Player.TeleportToLocation) addClickAction(new Interact.Actions.Player.TeleportToLocation(this, clickType, location)); }

    public Interact.Actions.Player.TeleportToLocation addTeleportToLocationClickAction(@Nonnull Location location){ return addTeleportToLocationClickAction(Interact.ClickType.EITHER, location); }

    public Interact.Actions.SetCustomData addSetCustomDataClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Plugin plugin, @Nonnull String key, @Nullable String value) { return (Interact.Actions.SetCustomData) addClickAction(new Interact.Actions.SetCustomData(this, clickType, plugin, key, value)); }

    public Interact.Actions.SetCustomData addSetCustomDataClickAction(@Nonnull Plugin plugin, @Nonnull String key, @Nullable String value) { return addSetCustomDataClickAction(Interact.ClickType.EITHER, plugin, key, value); }

    public Interact.Actions.Player.GiveItem addGivePlayerItemClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull ItemStack itemStack) { return (Interact.Actions.Player.GiveItem) addClickAction(new Interact.Actions.Player.GiveItem(this, clickType, itemStack)); }

    public Interact.Actions.Player.GiveItem addGivePlayerItemClickAction(@Nonnull ItemStack itemStack) { return addGivePlayerItemClickAction(Interact.ClickType.EITHER, itemStack); }

    public Interact.Actions.Player.OpenWorkbench addOpenWorkbenchClickAction(@Nullable NPC.Interact.ClickType clickType) { return (Interact.Actions.Player.OpenWorkbench) addClickAction(new Interact.Actions.Player.OpenWorkbench(this, clickType)); }

    public Interact.Actions.Player.OpenWorkbench addOpenWorkbenchClickAction() { return addOpenWorkbenchClickAction(Interact.ClickType.EITHER); }

    @Deprecated public Interact.Actions.Player.OpenEnchanting addOpenEnchantingClickAction(@Nullable NPC.Interact.ClickType clickType) { return (Interact.Actions.Player.OpenEnchanting) addClickAction(new Interact.Actions.Player.OpenEnchanting(this, clickType)); }

    @Deprecated public Interact.Actions.Player.OpenEnchanting addOpenEnchantingClickAction() { return addOpenEnchantingClickAction(Interact.ClickType.EITHER); }

    public Interact.Actions.Player.OpenBook addOpenBookClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull ItemStack book) { return (Interact.Actions.Player.OpenBook) addClickAction(new Interact.Actions.Player.OpenBook(this, clickType, book)); }

    public Interact.Actions.Player.OpenBook addOpenBookClickAction(@Nonnull ItemStack book) { return addOpenBookClickAction(Interact.ClickType.EITHER, book); }

    public Interact.Actions.PlayAnimation addPlayAnimationClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Animation animation) { return (Interact.Actions.PlayAnimation) addClickAction(new Interact.Actions.PlayAnimation(this, clickType, animation)); }

    public Interact.Actions.PlayAnimation addPlayAnimationClickAction(@Nonnull Animation animation) { return addPlayAnimationClickAction(Interact.ClickType.EITHER, animation); }

    public Interact.Actions.Player.PlaySound addPlayerPlaySoundClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Sound sound) { return (Interact.Actions.Player.PlaySound) addClickAction(new Interact.Actions.Player.PlaySound(this, clickType, sound)); }

    public Interact.Actions.Player.PlaySound addPlayerPlaySoundClickAction(@Nonnull Sound sound) { return addPlayerPlaySoundClickAction(Interact.ClickType.EITHER, sound); }

    public Interact.Actions.Player.WithdrawMoney addPlayerWithdrawMoneyClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Double balance) { return (Interact.Actions.Player.WithdrawMoney) addClickAction(new Interact.Actions.Player.WithdrawMoney(this, clickType, balance)); }

    public Interact.Actions.Player.WithdrawMoney addPlayerWithdrawMoneyClickAction(@Nonnull Double balance) { return addPlayerWithdrawMoneyClickAction(Interact.ClickType.EITHER, balance); }

    public Interact.Actions.Player.GiveMoney addPlayerGiveMoneyClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Double balance) { return (Interact.Actions.Player.GiveMoney) addClickAction(new Interact.Actions.Player.GiveMoney(this, clickType, balance)); }

    public Interact.Actions.Player.GiveMoney addPlayerGiveMoneyClickAction(@Nonnull Double balance) { return addPlayerGiveMoneyClickAction(Interact.ClickType.EITHER, balance); }

    public void resetClickActions(@Nonnull NPC.Interact.ClickType clickType){
        Validate.notNull(clickType, "Click type cannot be null");
        List<NPC.Interact.ClickAction> remove = this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType) || clickType.equals(Interact.ClickType.EITHER)).collect(Collectors.toList());
        clickActions.removeAll(remove);
        if(clickType != Interact.ClickType.EITHER){
            Interact.ClickType inverse = null;
            if(clickType.equals(Interact.ClickType.RIGHT_CLICK)) inverse = Interact.ClickType.LEFT_CLICK;
            if(clickType.equals(Interact.ClickType.LEFT_CLICK)) inverse = Interact.ClickType.RIGHT_CLICK;
            final Interact.ClickType inverseFinal = inverse;
            this.clickActions.stream().filter(x-> x.getClickType().equals(Interact.ClickType.EITHER)).forEach(x-> x.clickType = inverseFinal);
        }
    }

    public void removeClickAction(NPC.Interact.ClickAction clickAction){ if(this.clickActions.contains(clickAction)) clickActions.remove(clickAction); }

    public void resetClickActions(){ this.clickActions = new ArrayList<>(); }

    public Move.Task goTo(@Nonnull Location end, boolean lookToEnd){
        Validate.notNull(end, "Cannot move NPC to a null location.");
        Validate.isTrue(end.getWorld().getName().equals(world.getName()), "Cannot move NPC to another world.");
        if(this.moveTask == null){
            this.moveTask = new Move.Task(this, end, lookToEnd);
            return this.moveTask.start();
        }
        return null;
    }

    public Move.Task goTo(@Nonnull Location end){
        return goTo(end, true);
    }

    public Move.Task goTo(@Nonnull Location end, boolean lookToEnd, @Nullable Move.Speed moveSpeed){
        setMoveSpeed(moveSpeed);
        return goTo(end, lookToEnd);
    }

    public Move.Task goTo(@Nonnull Location end, @Nullable Move.Speed moveSpeed){
        setMoveSpeed(moveSpeed);
        return goTo(end, true);
    }

    public void cancelMove(){
        if(this.moveTask != null) moveTask.cancel(Move.Task.CancelCause.CANCELLED);
        clearMoveTask();
    }

    public Updatable setTabListVisibility(TabListVisibility tabListVisibility){ return needUpdate(attributes.setTabListVisibility(tabListVisibility)); }

    @Deprecated public Updatable setShowOnTabList(boolean show){ return setTabListVisibility(show ? TabListVisibility.ALWAYS : TabListVisibility.NEVER); }

    public Updatable setShowNameTag(boolean show){ return needUpdate(attributes.setShowNameTag(show)); }

    public void setMoveSpeed(@Nullable Move.Speed moveSpeed){
        moveSpeed = moveSpeed != null ? moveSpeed : Move.Speed.NORMAL;
        setMoveSpeed(moveSpeed.doubleValue());
    }

    public void setMoveSpeed(double moveSpeed){ attributes.setMoveSpeed(moveSpeed); }

    public abstract void playAnimation(NPC.Animation animation);

    public void playParticle(@Nonnull Particle particle, @Nullable Vector regarding, @Nullable Vector offset, int count, double speed) { playParticle(particle, regarding, offset, count, speed, null); }

    public abstract <T> void playParticle(@Nonnull Particle particle, @Nullable Vector regarding, @Nullable Vector offset, int count, double speed, @Nullable T data);

    public abstract void hit();

    public Updatable setOnFire(boolean onFire) { return needUpdate(attributes.setOnFire(onFire)); }

    public Updatable setGroundParticles(boolean groundParticles) { return needUpdate(attributes.setGroundParticles(groundParticles)); }

    public Updatable setArrowsInBody(int arrowsInBody) { return needUpdate(attributes.setArrowsInBody(arrowsInBody)); }

    public Updatable setBeeStingersInBody(int beeStingersInBody) { return needUpdate(attributes.setBeeStingersInBody(beeStingersInBody)); }

    public Updatable setShaking(boolean shaking) { return needUpdate(attributes.setShaking(shaking)); }

    public void setFireTicks(@Nonnull Integer ticks){
        setOnFire(true).update();
        Bukkit.getScheduler().runTaskLater(pluginManager.getPlugin(), ()->{ if(isOnFire()) setOnFire(false).update(); }, ticks.longValue());
    }

    public Updatable setTabListName(@Nullable String tabListName, boolean show){
        setTabListName(tabListName);
        setShowOnTabList(show);
        return needSimpleUpdate();
    }

    public Updatable resetTabListName(){ return setTabListName(null); }

    public Updatable setTabListName(@Nullable String name) { return needUpdate(attributes.setTabListName(name)); }

    public Updatable resetNameTag(){ return setTabListName(null); }

    public Updatable setNameTag(@Nullable String prefix, @Nullable String name, @Nullable String suffix) { return setNameTag(new NameTag(prefix, name, suffix)); }

    public Updatable setNameTag(@Nullable NameTag nameTag) { return needUpdate(attributes.setNameTag(nameTag)); }

    public Updatable setNameTag(@Nullable NameTag nameTag, boolean showNameTag) {
        setNameTag(nameTag);
        setShowNameTag(showNameTag);
        return needForceUpdate();
    }

    public void setCustomData(@Nonnull Plugin plugin, @Nonnull String simpleKey, @Nullable String value){ setCustomData(NPCLib.Registry.ID.of(plugin, simpleKey), value); }

    @Deprecated public void setCustomData(@Nonnull String fullKey, @Nonnull String value){ setCustomData(NPCLib.Registry.ID.of(fullKey), value); }

    public void setCustomData(@Nonnull NPCLib.Registry.ID key, @Nullable String value){
        if(customData.contains(key) && value == null){
            customData.remove(key);
            return;
        }
        customData.set(key, value);
    }

    public void clearCustomData(){ customData.clear(); }

    public void clearCustomData(@Nonnull NPCLib.Registry.ID key){ setCustomData(key, null);}

    public void clearCustomData(@Nonnull Plugin plugin, @Nonnull String simpleKey){ clearCustomData(NPCLib.Registry.ID.of(plugin, simpleKey)); }

    @Deprecated public void clearCustomData(@Nonnull String fullKey){ clearCustomData(NPCLib.Registry.ID.of(fullKey)); }


    /*
                Protected and private access methods
    */

    protected abstract void move(double moveX, double moveY, double moveZ);

    protected abstract void updatePlayerRotation();

    protected void setClickActions(@Nonnull List<NPC.Interact.ClickAction> clickActions){ this.clickActions = clickActions; }

    protected void setEquipment(HashMap<NPC.Slot, ItemStack> slots){ attributes.setEquipment(slots); }

    protected abstract void updateLocation();

    protected abstract void updateMove();

    protected Interact.ClickAction addClickAction(@Nonnull NPC.Interact.ClickAction clickAction){
        this.clickActions.add(clickAction);
        return clickAction;
    }

    protected void clearMoveTask(){ this.moveTask = null; }

    /*
                             Getters
    */

    public Location getLocation(){ return new Location(getWorld(), getX(), getY(), getZ(), getYaw(), getPitch()); }

    public Location getEyeLocation() { return getLocation().add(0, 1.625, 0); }

    protected HashMap<NPC.Slot, ItemStack> getEquipment(){ return attributes.equipment; }

    public ItemStack getEquipment(NPC.Slot npcSlot){ return attributes.equipment.get(npcSlot); }

    public double getMoveSpeed() { return attributes.moveSpeed; }

    public Move.Task getMoveTask() { return moveTask; }

    protected Move.Behaviour getMoveBehaviour(){ return moveBehaviour; }

    public Move.Behaviour.Type getMoveBehaviourType(){ return moveBehaviour.getType(); }

    public World getWorld() { return world; }

    public Double getX() { return x; }

    public Double getY() { return y; }

    public Double getZ() { return z; }

    public Float getYaw() { return yaw; }

    public Float getPitch() { return pitch; }

    public NPCLib getNPCLib() { return pluginManager.getNPCLib(); }

    @Override public NPCLib.Registry.ID getID() { return id; }

    public List<String> getText() { return attributes.text; }

    public ItemStack getTextItem() { return attributes.textItem; }

    public boolean isTextItemGlowing() { return attributes.textItemGlowing; }

    public NPC.Color getTextItemGlowingColor() { return attributes.textItemGlowingColor; }

    public NPC.Skin getSkin() { return attributes.skin; }

    public boolean isCollidable() { return attributes.collidable; }

    public Double getHideDistance() { return attributes.hideDistance; }

    public Double getTextLineSpacing(){ return attributes.textLineSpacing; }

    public Vector getTextAlignment() { return attributes.textAlignment; }

    public Long getInteractCooldown() { return attributes.interactCooldown; }

    public NPC.Color getGlowingColor() { return attributes.glowingColor; }

    public java.awt.Color getPotionParticlesColor() { return attributes.potionParticlesColor; }

    public PotionParticlesType getPotionParticlesType() { return attributes.potionParticlesType; }

    protected HashMap<NPC.Slot, ItemStack> getSlots() { return attributes.equipment; }

    public TabListVisibility getTabListVisibility() { return attributes.tabListVisibility; }

    public String getTabListName() { return attributes.tabListName; }

    public boolean isShowNameTag() { return attributes.showNameTag; }

    public NameTag getNameTag() { return attributes.nameTag; }

    public boolean isGlowing() { return attributes.glowing; }

    public GazeTrackingType getGazeTrackingType() { return attributes.gazeTrackingType; }

    public NPC.Pose getPose() { return attributes.pose; }

    public Skin.VisibleLayers getSkinVisibleLayers() { return attributes.skinVisibleLayers; }

    public NPC.Hologram.Opacity getLineOpacity(int line){ return attributes.getTextLineOpacity(line); }

    protected HashMap<Integer, NPC.Hologram.Opacity> getLinesOpacity() { return attributes.textLinesOpacity; }

    public NPC.Hologram.Opacity getTextOpacity() { return attributes.textOpacity; }

    public boolean isOnFire() { return attributes.onFire; }

    public boolean isGroundParticles() { return attributes.groundParticles; }

    public boolean isInvisible() { return attributes.invisible; }

    public Integer getArrowsInBody() { return attributes.arrowsInBody; }

    public Integer getBeeStingersInBody() { return attributes.beeStingersInBody; }

    public Boolean isShaking() { return attributes.shaking; }

    public NPC.Attributes getAttributes() { return attributes; }

    public Plugin getPlugin() { return pluginManager.getPlugin(); }

    public NPCLib.PluginManager getPluginManager() { return pluginManager; }

    public List<NPC.Interact.ClickAction> getClickActions() { return clickActions.stream().collect(Collectors.toList()); }

    public Integer getClickActionsSize() { return clickActions.size(); }

    public List<NPC.Interact.ClickAction> getClickActions(@Nonnull NPC.Interact.ClickType clickType){ return this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType)).collect(Collectors.toList()); }

    public Optional<String> grabCustomData(@Nonnull Plugin plugin, @Nonnull String simpleKey){ return grabCustomData(NPCLib.Registry.ID.of(plugin, simpleKey)); }

    public Optional<String> grabCustomData(@Nonnull NPCLib.Registry.ID id){ return customData.grab(id); }

    @Deprecated public Optional<String> grabCustomData(@Nonnull String fullKey) {
        if(NPCLib.Registry.ID.isFullValid(fullKey)) return grabCustomData(NPCLib.Registry.ID.of(fullKey));
        else if(NPCLib.Registry.ID.isSimpleValid(fullKey)) return grabCustomData(NPCLib.Registry.ID.playerNPC(fullKey));
        else return Optional.empty();
    }

    @Deprecated @Nullable public String getCustomData(@Nonnull Plugin plugin, @Nonnull String simpleKey){ return grabCustomData(plugin, simpleKey).orElse(null); }

    @Deprecated @Nullable public String getCustomData(@Nonnull NPCLib.Registry.ID id){ return grabCustomData(id).orElse(null); }

    @Deprecated @Nullable public String getCustomData(@Nonnull String fullKey) { return grabCustomData(fullKey).orElse(null); }

    public Set<String> getCustomDataKeys(){ return customData.getKeys(); }

    public Set<NPCLib.Registry.ID> getCustomDataKeysID() { return customData.getKeysID(); }

    @Deprecated public boolean hasCustomData(@Nonnull String fullKey){ return customData.contains(fullKey.toLowerCase()); }

    public boolean hasCustomData(@Nonnull NPCLib.Registry.ID key){ return customData.contains(key); }

    public boolean hasCustomData(@Nonnull Plugin plugin, @Nonnull String simpleKey) { return customData.contains(NPCLib.Registry.ID.of(plugin, simpleKey)); }

    public static class Personal extends NPC{

        private final Player player;
        private UUID gameProfileID;
        private EntityPlayer entityPlayer;
        private NPC.Hologram npcHologram;
        private boolean canShow;
        private boolean hiddenText;
        private boolean hiddenToClient;
        private boolean shownOnTabList;
        private NPC.Global global;
        private Updatable.PendingUpdates<NPC.Global> globalPendingUpdates;

        protected Personal(@Nonnull NPCLib.PluginManager pluginManager, @Nonnull NPCLib.Registry.ID id, @Nonnull Player player, @Nonnull World world, double x, double y, double z, float yaw, float pitch){
            super(pluginManager, id, world, x, y, z, yaw, pitch);
            Validate.notNull(player, "Cannot generate NPC instance, Player cannot be null.");
            this.player = player;
            this.gameProfileID = UUID.randomUUID();
            this.canShow = false;
            this.npcHologram = null;
            this.shownOnTabList = false;
            this.hiddenToClient = true;
            this.hiddenText = false;
            this.global = null;
            this.globalPendingUpdates = null;
            getNPCLib().getNPCPlayerManager(player).getPersonalNPCs().set(id, this);
        }

        protected Personal(@Nonnull NPCLib.PluginManager pluginManager, @Nonnull NPCLib.Registry.ID id, @Nonnull Player player, @Nonnull Location location){
            this(pluginManager, id, player, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public void create(){
            Validate.notNull(super.attributes.skin, "Failed to create the NPC. The NPC.Skin has not been configured.");
            Validate.isTrue(entityPlayer == null, "Failed to create the NPC. This NPC has already been created before.");
            MinecraftServer server = NMSCraftServer.getMinecraftServer();
            WorldServer worldServer = NMSCraftWorld.getWorldServer(super.world);
            try{
                GameProfile gameProfile = new GameProfile(gameProfileID, getReplacedNameTag());
                entityPlayer = NMSEntityPlayer.newEntityPlayer(server, worldServer, gameProfile);
            } catch (Exception e){
                GameProfile gameProfile = new GameProfile(gameProfileID, ChatColor.stripColor(getReplacedNameTag()));
                try { entityPlayer = NMSEntityPlayer.newEntityPlayer(server, worldServer, gameProfile); } catch (Exception ex) { NPCLib.printError(ex); }
            }
            Validate.notNull(entityPlayer, "Error at NMSEntityPlayer");
            NMSEntity.setLocation(entityPlayer, super.x, super.y, super.z, super.yaw, super.pitch);
            updateSkin();
            updatePose();
            updateScoreboard();
            if(getTabListVisibility().equals(TabListVisibility.ALWAYS) || (getTabListVisibility().equals(TabListVisibility.SAME_WORLD) && getWorld().getName().equals(player.getWorld().getName())))
                addTabList(true);
            updateTabList();
            this.npcHologram = new NPC.Hologram(this, player);
        }

        public void simpleUpdate(){
            Validate.notNull(entityPlayer, "Failed to update the NPC. The NPC has not been created yet.");
            if(!canShow) return;
            if(!hiddenToClient && !isInRange()){
                hideToClient();
                return;
            }
            if(hiddenToClient && isInRange() && canBeShownInView()){
                showToClient();
                return;
            }
            updatePose();
            updateLook();
            updateSkin();
            updatePlayerRotation();
            updateEquipment();
            updateMetadata();
            updateScoreboard();
            updateTabList();
            onExecuteUpdate(Updatable.Type.SIMPLE_UPDATE);
        }

        public void forceUpdate(){
            Validate.notNull(entityPlayer, "Failed to force update the NPC. The NPC has not been created yet.");
            reCreate();
            simpleUpdate();
            forceUpdateText();
            onExecuteUpdate(Updatable.Type.FORCE_UPDATE);
        }

        public void teleport(World world, double x, double y, double z, float yaw, float pitch){
            Validate.notNull(entityPlayer, "Failed to move the NPC. The NPC has not been created yet.");
            NPC.Events.Teleport npcTeleportEvent = new NPC.Events.Teleport(this, new Location(world, x, y, z, yaw, pitch));
            if(npcTeleportEvent.isCancelled()) return;
            super.x = x;
            super.y = y;
            super.z = z;
            super.yaw = yaw;
            super.pitch = pitch;
            if(!super.world.equals(world)) changeWorld(world);
            boolean show = canShow;
            if(npcHologram != null) npcHologram.hide();
            reCreate();
            if(npcHologram != null) forceUpdateText();
            if(show) show();
            else if(npcHologram != null) hideText();
        }

        public void simpleUpdateText(){
            if(npcHologram == null) return;
            int i = 1;
            for(String s : super.attributes.text){
                npcHologram.setLine(i, s);
                i++;
            }
            npcHologram.simpleUpdate();
            onExecuteUpdate(Updatable.Type.SIMPLE_UPDATE_TEXT);
        }

        public void forceUpdateText(){
            if(npcHologram == null) return;
            npcHologram.forceUpdate();
            onExecuteUpdate(Updatable.Type.FORCE_UPDATE_TEXT);
        }

        public void destroy(){
            cancelMove();
            if(entityPlayer != null){
                if(canShow) hide();
                entityPlayer = null;
            }
            if(npcHologram != null) npcHologram.removeHologram();
        }

        public void show(){ show(10); }

        protected void show(int ticksToAppear){
            Validate.notNull(entityPlayer, "Failed to show NPC. The NPC has not been created yet.");
            if(canShow && !hiddenToClient) return;
            NPC.Events.Show npcShowEvent = new NPC.Events.Show(getPlayer(), this);
            if(npcShowEvent.isCancelled()) return;
            canShow = true;
            if(!isInRange() || !canBeShownInView()){
                hiddenToClient = true;
                return;
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), ()-> {
                showToClient();
            },Math.max(getPlayerManager().ticksToAppear(), ticksToAppear));
        }

        public void hide(){
            Validate.notNull(entityPlayer, "Failed to hide the NPC. The NPC has not been created yet.");
            if(!canShow) return;
            NPC.Events.Hide npcHideEvent = new NPC.Events.Hide(getPlayer(), this);
            if(npcHideEvent.isCancelled()) return;
            hideToClient();
            if(shownOnTabList) removeTabList();
            canShow = false;
        }

        public void setHideText(boolean hide){
            boolean a = hiddenText;
            this.hiddenText = hide;
            if(a == hide) return;
            if(npcHologram == null) return;
            if(hide) hideText();
            else showText();
        }

        private void onExecuteUpdate(Updatable.Type type){
            pendingUpdates.onExecute(type);
            if(globalPendingUpdates != null) globalPendingUpdates.onExecute(type);
        }

        public Move.Behaviour followPlayer(){
            return super.moveBehaviour.setFollowPlayer();
        }

        public Move.Behaviour followPlayer(double min, double max){ return super.moveBehaviour.setFollowPlayer(min, max); }

        public void playAnimation(@Nonnull NPC.Animation animation){
            Validate.notNull(animation, "Cannot play a null animation.");
            if(!isCreated()) return;
            if(animation.isDeprecated() && !getNPCLib().isDebug()) return;
            if(animation.equals(Animation.TAKE_DAMAGE) && getClientVersion().isNewerThanOrEqual(ServerVersion.VERSION_1_19_4) && getServerVersion().isNewerThanOrEqual(ServerVersion.VERSION_1_19_4)){
                NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutAnimation.getHurtAnimationPacket(entityPlayer));
                return;
            }
            NMSCraftPlayer.sendPacket(player, animation.createPacket(entityPlayer));
        }

        @Override
        public <T> void playParticle(Particle particle, Vector regarding, Vector offset, int count, double speed, @Nullable T data) {
            player.spawnParticle(particle, getLocation().add(regarding), count, offset != null ? offset.getX() : 0.0, offset != null ? offset.getY() : 0.0, offset != null ? offset.getZ() : 0.0, speed, data);
        }

        @Override
        public void hit() {
            if(!isCreated()) return;
            player.playSound(getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
            playAnimation(Animation.TAKE_DAMAGE);
        }

        public Updatable lookAt(float yaw, float pitch){
            Validate.notNull(entityPlayer, "Failed to set look direction. The NPC has not been created yet.");
            float a = Math.abs(yaw - super.yaw);
            super.yaw = yaw; //yRot
            super.pitch = pitch; //xRot
            NMSEntity.setYRot(entityPlayer, yaw);
            NMSEntity.setXRot(entityPlayer, pitch);
            return needUpdateOrForce(a > 45);
        }

        public Updatable setInGameSkin() { return setInGameSkinOf(player); }

    /*
                Protected and private access methods
    */

        protected void reCreate(){
            Validate.notNull(entityPlayer, "Failed to re-create the NPC. The NPC has not been created yet.");
            boolean show = canShow;
            hide();
            entityPlayer = null;
            create();
            if(show) show(0);
        }

        protected void interact(@Nonnull Player player, @Nonnull NPC.Interact.ClickType clickType){
            NPC.Events.Interact npcInteractEvent = new NPC.Events.Interact(player, this, clickType);
            if(npcInteractEvent.isCancelled()) return;
            if(hasGlobal()){
                getGlobal().getClickActions(clickType).forEach(x-> x.execute(player));
                getGlobal().getClickActions(Interact.ClickType.EITHER).forEach(x-> x.execute(player));
            }
            getClickActions(clickType).forEach(x-> x.execute(player));
            getClickActions(Interact.ClickType.EITHER).forEach(x-> x.execute(player));
        }

        protected void updateGlobalLocation(){
            if(!hasGlobal()) return;
            NPC.Global global = getGlobal();
            super.x = global.getX();
            super.y = global.getY();
            super.z = global.getZ();
            if(getGazeTrackingType().equals(GazeTrackingType.NONE)){
                super.yaw = global.getYaw();
                super.pitch = global.getPitch();
            }
        }

        protected void updateMove(){
            if(player == null) return;
            if(entityPlayer == null) return;
            if(!canShow) return;
            if(!hiddenToClient && !isInRange()){
                hideToClient();
                return;
            }
            if(hiddenToClient && isInRange() && canBeShownInView()){
                showToClient();
                return;
            }
            updateLook();
            updatePlayerRotation();
        }

        private void updateLook(){
            Validate.notNull(entityPlayer, "Failed to update look the NPC. The NPC has not been created yet.");
            if(!player.getWorld().getName().equals(getWorld().getName())) return;
            if(getGazeTrackingType().equals(GazeTrackingType.PLAYER)) lookAt(player);
            else if(getGazeTrackingType().equals(GazeTrackingType.NEAREST_PLAYER) || getGazeTrackingType().equals(GazeTrackingType.NEAREST_ENTITY)){
                final boolean var3 = getGazeTrackingType().equals(GazeTrackingType.NEAREST_PLAYER);
                if(hasGlobal()){
                    Entity near;
                    if(var3) near = getGlobal().np();
                    else near = getGlobal().ne();
                    if(near != null){
                        lookAt(near);
                        return;
                    }
                }
                Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), ()-> {
                    Entity near = null;
                    double var0 = getHideDistance();
                    final Location npcLocation = getLocation();
                    for(Entity entities : super.world.getNearbyEntities(npcLocation, getHideDistance(), getHideDistance(), getHideDistance())){
                        if(var3 && !(entities instanceof Player)) continue;
                        double var1 = entities.getLocation().distance(npcLocation);
                        if(var1 > var0) continue;
                        near = entities;
                        var0 = var1;
                    }
                    if(near == null) return;
                    lookAt(near);
                    if(hasGlobal()){
                        if(var3) getGlobal().np(near);
                        else getGlobal().ne(near);
                    }
                });
            }
        }

        @Override
        protected void updateLocation(){
            if(entityPlayer == null) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityTeleport(entityPlayer));
        }

        protected void updateScoreboard(){
            if(getNPCLib().isUsingBukkitScoreboards()){
                updateBukkitScoreboard();
                return;
            }
            Scoreboard scoreboard = NMSCraftScoreboard.getScoreboard(player);
            String teamName = getScoreboardTeamName("npc");
            boolean existsTeam = NMSScoreboard.getTeam(scoreboard, teamName) != null;
            ScoreboardTeam scoreboardTeam = existsTeam ? NMSScoreboard.getTeam(scoreboard, teamName) : new ScoreboardTeam(scoreboard, teamName);
            NMSScoreboard.setNameTagVisibility(scoreboardTeam, isShowNameTag() ? NMSScoreboard.nameTagVisibility_ALWAYS : NMSScoreboard.nameTagVisibility_NEVER);
            NMSScoreboard.setTeamColor(scoreboardTeam, getGlowingColor().asMinecraftEnumChatFormat());
            NMSScoreboard.setTeamPush(scoreboardTeam, isCollidable() ? NMSScoreboard.teamPush_ALWAYS : NMSScoreboard.teamPush_NEVER);
            NMSScoreboard.setPlayerTeam(scoreboard, getGameProfile().getName(), scoreboardTeam);
            NMSScoreboard.setPrefix(scoreboardTeam, IChatBaseComponent.a(getNameTag().getPrefix() != null ? getNameTag().getPrefix() : null));
            NMSScoreboard.setSuffix(scoreboardTeam, IChatBaseComponent.a(getNameTag().getSuffix() != null ? getNameTag().getSuffix() : null));
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, !existsTeam));
        }

        protected void updateBukkitScoreboard(){
            if(player.getScoreboard().equals(Bukkit.getServer().getScoreboardManager().getMainScoreboard())) player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();
            String teamName = getScoreboardTeamName("npc");
            boolean existsTeam = scoreboard.getTeam(teamName) != null;
            Team scoreboardTeam = existsTeam ? scoreboard.getTeam(teamName) : scoreboard.registerNewTeam(teamName);
            scoreboardTeam.setColor(getGlowingColor().asBukkitChatColor());
            scoreboardTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, isShowNameTag() ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
            scoreboardTeam.setOption(Team.Option.COLLISION_RULE, isCollidable() ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
            scoreboardTeam.addEntry(getGameProfile().getName());
            scoreboardTeam.setPrefix(getNameTag().getPrefix() != null ? getNameTag().getPrefix() : "");
            scoreboardTeam.setSuffix(getNameTag().getSuffix() != null ? getNameTag().getSuffix() : "");
        }

        protected void updateTabList(){
            entityPlayer.listName = getTabListName() != null ? IChatBaseComponent.a(getTabListName().replaceAll("\\{id\\}", getGameProfile().getName()) + (getNPCLib().isDebug() && player.isOp() ? " §7(" + getTabListVisibility().getName(EnumUtils.NameFormat.FIRST_UPPER_CASE_WITH_SPACES) + ")" : "")) : null;
            if(shownOnTabList) NMSPacketPlayOutPlayerInfo.refreshPlayerTabList(player, entityPlayer);
        }

        protected void addTabList(boolean permanent){
            if(shownOnTabList) return;
            boolean effectiveShownOnTabList = permanent || pluginManager.getTicksUntilTabListHide() > 0 || getServerVersion().isOlderThanOrEqual(ServerVersion.VERSION_1_19_2) || getClientVersion().isOlderThanOrEqual(ServerVersion.VERSION_1_19_2);
            NMSPacketPlayOutPlayerInfo.addPlayer(player, entityPlayer, effectiveShownOnTabList);
            if(!effectiveShownOnTabList) return;
            shownOnTabList = true;
            updateTabList();
            if(getTabListVisibility().equals(TabListVisibility.ALWAYS) || (getTabListVisibility().equals(TabListVisibility.NEAR) && !hiddenToClient) || (getTabListVisibility().equals(TabListVisibility.SAME_WORLD) && player.getWorld().getName().equals(getWorld().getName()))) return;
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), ()-> removeTabList(), Math.max(1, pluginManager.getTicksUntilTabListHide()));
        }

        protected void removeTabList(){
            if(!shownOnTabList) return;
            NMSPacketPlayOutPlayerInfo.removePlayer(player, entityPlayer);
            shownOnTabList = false;
        }

        @Override
        protected void updatePlayerRotation(){
            if(entityPlayer == null) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutEntityLook(NMSEntity.getEntityID(entityPlayer), (byte) ((super.yaw * 256 / 360)), (byte) ((super.pitch * 256 / 360)), false));
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) (super.yaw * 256 / 360)));
        }

        protected void updateSkin(){
            if(entityPlayer == null) return;
            GameProfile gameProfile = NMSEntityPlayer.getGameProfile(entityPlayer);
            gameProfile.getProperties().get("textures").clear();
            if(getSkin() == null) return;
            gameProfile.getProperties().put("textures", new Property("textures", super.attributes.skin.getTexture(), super.attributes.skin.getSignature()));
        }

        protected void updatePose(){
            Validate.notNull(entityPlayer, "Failed to update pose the NPC. The NPC has not been created yet.");
            if(getPose().equals(NPC.Pose.SLEEPING)) entityPlayer.e(new BlockPosition(super.x.intValue(), super.y.intValue(), super.z.intValue()));
            NMSEntity.setPose(entityPlayer, getPose().asMinecraftEntityPose());
        }

        protected void move(double x, double y, double z){
            Validate.isTrue(x < 8 && y < 8 && z < 8, "NPC cannot move 8 blocks or more at once, use teleport instead");
            NPC.Events.Move npcMoveEvent = new NPC.Events.Move(this, new Location(super.world, super.x + x, super.y + y, super.z + z));
            if(npcMoveEvent.isCancelled()) return;
            super.x += x;
            super.y += y;
            super.z += z;
            NMSEntity.move(entityPlayer, super.x, super.y, super.z);
            if(npcHologram != null) npcHologram.move(new Vector(x, y, z));
            movePacket(x, y, z);
        }

        protected void movePacket(double x, double y, double z) {
            Validate.isTrue(x < 8);
            Validate.isTrue(y < 8);
            Validate.isTrue(z < 8);
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutRelEntityMove(NMSEntity.getEntityID(entityPlayer), (short)(x * 4096), (short)(y * 4096), (short)(z * 4096), true));
        }

        protected void updateMetadata() {
            DataWatcher dataWatcher = NMSEntity.getDataWatcher(entityPlayer);
            if(dataWatcher == null) return;
            NMSEntity.setGlowingTag(entityPlayer, isGlowing());
            //entityPlayer.b(getPose().getEntityPose());
            Map<Integer, DataWatcher.Item<?>> map = NMSDataWatcher.getMapOfDataWatcherItems(dataWatcher);
            if(map == null) return;
            byte b = 0x00;
            boolean bit0; //0x01
            boolean bit1; //0x02
            boolean bit2; //0x04
            boolean bit3; //0x08
            boolean bit4; //0x10
            boolean bit5; //0x20
            boolean bit6; //0x40
            boolean bit7; //0x80
            //byte b = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40 | 0x80;
            //
            // Entity
            // https://wiki.vg/Entity_metadata#Entity
            //
            // INDEX 0: General metadata. (Byte, Default 0)
            DataWatcher.Item item = map.get(0); //byte initialBitMask = (Byte) item.b();
            b = 0x00;
            bit0 = isOnFire(); //0x01 -> is on fire
            bit1 = getPose().equals(Pose.CROUCHING); //0x02 -> is crouching
            bit2 = true; //0x04 -> unused
            bit3 = isGroundParticles(); //0x08 -> is sprinting
            bit4 = getPose().equals(Pose.SWIMMING); //0x10 -> is swimming
            bit5 = isInvisible(); //0x20 -> is invisible
            bit6 = isGlowing(); //0x40 -> is glowing
            bit7 = getPose().equals(Pose.GLIDING); //0x80 -> is gliding
            b = (byte) ((bit0 ? 0x01 : 0) | (bit1 ? 0x02 : 0) | (bit2 ? 0x04 : 0) | (bit3 ? 0x08 : 0) | (bit4 ? 0x10 : 0) | (bit5 ? 0x20 : 0) | (bit6 ? 0x40 : 0) | (bit7 ? 0x80 : 0));
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerByte().a(0), b);
            // Bitmask debug
            if(false){
                String s1 = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                System.out.println("Bit mask for " + getID().getFullID() + ": "+ s1);
                System.out.println("Fire: " + bit0);
                System.out.println("Crouching: " + bit1);
                System.out.println("Unused: " + bit2);
                System.out.println("Sprinting: " + bit3);
                System.out.println("Swimming: " + bit4);
                System.out.println("Invisible: " + bit5);
                System.out.println("Glowing: " + bit6);
                System.out.println("Gliding: " + bit7);
            }
            //
            // INDEX 3: Is custom name visible (Boolean, Default false)
            //NMSDataWatcher.set(dataWatcher, DataWatcherRegistry.j.a(3), false);
            //
            // INDEX 6: Pose (EntityPose, Default STANDING)
            //NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerEntityPose().a(6), getPose().getEntityPose());
            //
            // INDEX 7: Ticks frozen in powdered snow (VarInt, Default 0)
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerInteger().a(7), isShaking() ? 140 : 0);
            //
            // Living Entity
            // https://wiki.vg/Entity_metadata#Living_Entity
            //
            // INDEX 8: Hand states, used to trigger blocking/eating/drinking animation.
            item = map.get(8);
            b = (Byte) item.b();
            b = (byte) (getPose().equals(Pose.SPIN_ATTACK) ? (b | 0x04) : (b & 0x04));
            NMSDataWatcher.set(dataWatcher, DataWatcherRegistry.a.a(8), b);
            //
            // INDEX 10: Potion effect color (or 0 if there is no effect) (VarInt, default 0)
            boolean noColor = getPotionParticlesType().equals(PotionParticlesType.DISABLED);
            int rgb = ((getPotionParticlesColor().getRed() & 0x0ff)<<16)|((getPotionParticlesColor().getGreen() & 0x0ff)<<8)|(getPotionParticlesColor().getBlue() & 0x0ff);
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerInteger().a(10), noColor ? 0 : rgb);
            //
            // INDEX 11: Is potion effect ambient: reduces the number of particles generated by potions to 1/5 the normal amount (Boolean, default false)
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerBoolean().a(11), getPotionParticlesType().metadata);
            //
            // INDEX 12: Number of arrows in entity (VarInt, default 0)
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerInteger().a(12), getArrowsInBody());
            //
            // INDEX 13: Number of bee stingers in entity (VarInt, default 0)
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerInteger().a(13), getBeeStingersInBody());
            //
            // Player
            // https://wiki.vg/Entity_metadata#Player
            //
            // INDEX 17: The Displayed Skin Parts bit mask that is sent in Client Settings (Byte, default 0)
            b = 0x00;
            Skin.VisibleLayers visibleLayers = getSkinVisibleLayers();
            for(Skin.Layer layer : visibleLayers.getVisibleLayers()){ b = (byte) (b | layer.getMask()); }
            NMSDataWatcher.set(dataWatcher, NMSDataWatcher.getDataWatcherSerializerByte().a(17), b);
            //
            NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityMetadata.getPacket(entityPlayer, dataWatcher));
        }

        protected void updateEquipment(){
            List<Pair<EnumItemSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
            for(NPC.Slot slot : NPC.Slot.values()){
                if(!getSlots().containsKey(slot)) getSlots().put(slot, new ItemStack(Material.AIR));
                ItemStack item = getSlots().get(slot);
                net.minecraft.world.item.ItemStack minecraftItem = null;
                try{ minecraftItem = (net.minecraft.world.item.ItemStack) NMSCraftItemStack.getCraftItemStackAsNMSCopy().invoke(null, item); }  catch (Exception e){}
                Validate.notNull(minecraftItem, "Error at NMSCraftItemStack");
                equipment.add(new Pair(slot.asMinecraftEnumItemSlot(), minecraftItem));
            }
            PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(NMSEntity.getEntityID(entityPlayer), equipment);
            NMSCraftPlayer.sendPacket(player, packet);
        }

        protected void showToClient(){
            if(player == null || !player.isOnline()) return;
            if(!hiddenToClient) return;
            hiddenToClient = false;
            addTabList(!getTabListVisibility().equals(TabListVisibility.NEVER));
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutNamedEntitySpawn(entityPlayer));
            updatePlayerRotation();
            if(getText().size() > 0) simpleUpdateText();
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), () -> {
                if(isCreated()) simpleUpdate();
            }, 1);
        }

        protected void hideToClient(){
            if(hiddenToClient) return;
            if(shownOnTabList && (getTabListVisibility().equals(TabListVisibility.NEAR) || (getTabListVisibility().equals(TabListVisibility.SAME_WORLD) && !player.getWorld().getName().equals(getWorld().getName()))))
                removeTabList();
            if(getNPCLib().isUsingBukkitScoreboards()){
                Team team = player.getScoreboard().getTeam(getScoreboardTeamName("npc"));
                if(team != null) team.unregister();
            }
            else{
                Scoreboard scoreboard = NMSScoreboard.getScoreboard(player);
                ScoreboardTeam scoreboardTeam = NMSScoreboard.getTeam(scoreboard, getScoreboardTeamName("npc"));
                if(scoreboardTeam != null) NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam));
            }
            NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityDestroy.createPacket(NMSEntity.getEntityID(entityPlayer)));
            if(npcHologram != null) npcHologram.hide();
            pendingUpdates.clear();
            hiddenToClient = true;
        }

        private void hideText(){
            Validate.notNull(npcHologram, "Failed to update NPC text. The NPCHologram has not been created yet.");
            npcHologram.hide();
            hiddenText = true;
        }

        private void showText(){
            Validate.notNull(npcHologram, "Failed to update NPC text. The NPCHologram has not been created yet.");
            if(hiddenText) return;
            npcHologram.show();
            hiddenText = false;
        }

        protected void changeWorld(World world) {
            super.getPluginManager().getNPCLib().getNPCPlayerManager(player).changeWorld(this, super.world, world);
            super.world = world;
        }

    /*
                             Getters
    */

        public EntityPlayer getEntity(){ return this.entityPlayer; }

        public boolean isCreated(){ return entityPlayer != null; }

        public GameProfile getGameProfile() {
            Validate.notNull(entityPlayer, "Cannot get GameProfile because NPC is not created yet.");
            return NMSEntityPlayer.getGameProfile(entityPlayer);
        }

        protected String getShortUUID(){ return gameProfileID.toString().substring(0, 5); }

        private String getScoreboardTeamName(String id) {
            boolean limited16 = getClientVersion().isOlderThanOrEqual(ServerVersion.VERSION_1_17_1) || getServerVersion().isOlderThanOrEqual(ServerVersion.VERSION_1_17_1);
            String npcid = getID().getFullID();
            if(limited16) npcid = getShortUUID();
            String name = npcid + "/" + id;
            return limited16 && name.length() > 16 ? name.substring(0, 15) : name;
        }

        protected String getReplacedNameTag(){ return getReplacedNameTag(super.attributes.getNameTag().getName()); }

        protected String getReplacedNameTag(String name){
            if(name.contains("{id}")) name = name.replaceAll("\\{id\\}", getShortUUID());
            return name.length() > 16 ? name.substring(0, 15) : name;
        }

        private boolean canBeShownInView(){
            if(getClientVersion().isNewerThanOrEqual(ServerVersion.VERSION_1_19_3)) return true;
            return isInView();
        }

        public boolean isInView(){ return isInView(60.0D); }

        public boolean isInView(double fov){
            if(!getWorld().getName().equals(player.getWorld().getName())) return false;
            Vector dir = getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            return dir.dot(player.getEyeLocation().getDirection()) >= Math.cos(Math.toRadians(fov));
        }

        public boolean isInRange(){
            if(!getWorld().getName().equals(player.getWorld().getName())) return false;
            return getLocation().distance(player.getLocation()) < getHideDistance();
        }

        public Player getPlayer() { return player; }

        protected NPCLib.PlayerManager getPlayerManager() { return getNPCLib().getNPCPlayerManager(player); }

        protected ServerVersion getClientVersion() { return getPlayerManager().getClientVersion(); }

        protected ServerVersion getServerVersion() { return ServerVersion.getServerVersion(); }

        public boolean isShown(){ return canShow; }

        public boolean isShownOnClient() { return canShow && !hiddenToClient; }

        public boolean isShownOnClientTabList() { return shownOnTabList; }

        public boolean isHiddenText() { return hiddenText; }

        public boolean canBeCreated(){ return entityPlayer == null; }

        protected NPC.Hologram getHologram() { return npcHologram; }

        public boolean hasGlobal(){ return global != null; }

        public NPC.Global getGlobal(){ return global; }

    }

    public static class Global extends NPC{

        private static final Integer LOOK_TICKS = 2;

        protected final HashMap<Player, NPC.Personal> players;
        private final HashMap<UUID, NPC.Attributes> customAttributes;
        private Visibility visibility;
        private Predicate<Player> visibilityRequirement;
        private Entity nearestEntity, nearestPlayer;
        private Long lastNearestEntityUpdate, lastNearestPlayerUpdate;
        private boolean autoCreate, autoShow;
        private boolean ownPlayerSkin;
        private boolean resetCustomAttributes;
        private List<String> selectedPlayers;
        protected boolean persistent;
        protected NPC.Global.PersistentManager persistentManager;

        protected Global(@Nonnull NPCLib.PluginManager pluginManager, @Nonnull NPCLib.Registry.ID id, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull World world, double x, double y, double z, float yaw, float pitch) {
            super(pluginManager,id, world, x, y, z, yaw, pitch);
            Validate.notNull(visibility, "Cannot generate Global NPC instance, Visibility cannot be null.");
            this.players = new HashMap<>();
            this.customAttributes = new HashMap<>();
            this.visibility = visibility;
            this.visibilityRequirement = visibilityRequirement;
            this.autoCreate = true;
            this.autoShow = true;
            this.resetCustomAttributes = false;
            this.persistent = false;
            this.selectedPlayers = new ArrayList<>();
            np(null);
            ne(null);
            checkVisiblePlayers();
            getNPCLib().globalNPCs.set(id, this);
        }

        protected Global(@Nonnull NPCLib.PluginManager pluginManager, @Nonnull NPCLib.Registry.ID id, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
            this(pluginManager, id, visibility, visibilityRequirement, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public enum Visibility{
            EVERYONE, SELECTED_PLAYERS;
        }

        public void setVisibility(Visibility visibility) {
            if(this.visibility.equals(visibility)) return;
            this.visibility = visibility;
            checkVisiblePlayers();
        }

        public void setVisibilityRequirement(Predicate<Player> visibilityRequirement) {
            this.visibilityRequirement = visibilityRequirement;
            checkVisiblePlayers();
        }

        private void checkVisiblePlayers(){
            Set<Player> playerSet = new HashSet<>();
            playerSet.addAll(players.keySet());
            playerSet.stream().filter(x-> !meetsVisibilityRequirement(x)).forEach(x-> removePlayer(x));
            if(visibility.equals(Visibility.EVERYONE)) addPlayers((Collection<Player>) Bukkit.getOnlinePlayers());
            else if(visibility.equals(Visibility.SELECTED_PLAYERS)) Bukkit.getOnlinePlayers().stream().filter(x-> !players.containsKey(x) && selectedPlayers.contains(x.getName())).forEach(x-> addPlayer(x));
        }

        public Visibility getVisibility() { return visibility; }

        public boolean meetsVisibilityRequirement(@Nonnull Player player){
            Validate.notNull(player, "Cannot verify a null Player");
            if(visibilityRequirement == null) return true;
            return visibilityRequirement.test(player);
        }

        public void addPlayers(@Nonnull Collection<Player> players){
            addPlayers(players, false);
        }

        public void addPlayers(@Nonnull Collection<Player> players, boolean ignoreVisibilityRequirement){
            Validate.notNull(players, "Cannot add a null collection of Players");
            players.forEach(x-> addPlayer(x, ignoreVisibilityRequirement));
        }

        public void addPlayer(@Nonnull Player player){
            addPlayer(player, false);
        }

        public void addPlayer(@Nonnull Player player, boolean ignoreVisibilityRequirement){
            Validate.notNull(player, "Cannot add a null Player");
            if(players.containsKey(player)) return;
            if(!ignoreVisibilityRequirement && !meetsVisibilityRequirement(player)) return;
            NPC.Personal personal = pluginManager.generatePlayerPersonalNPC(player, NPCLib.Registry.ID.of(getPlugin(), "global_" + getID().getSimpleID()), getLocation());
            personal.global = this;
            personal.globalPendingUpdates = new Updatable.PendingUpdates(this);
            players.put(player, personal);
            if(!selectedPlayers.contains(player.getName())) selectedPlayers.add(player.getName());
            if(!customAttributes.containsKey(player.getUniqueId())) customAttributes.put(player.getUniqueId(), new Attributes());
            //
            synchronizeGlobalAttributes(player);
            if(autoCreate) personal.create();
            if(autoCreate && autoShow) personal.show();
        }

        public void removePlayers(@Nonnull Collection<Player> players){
            Validate.notNull(players, "Cannot remove a null collection of Players");
            players.forEach(x-> removePlayer(x));
        }

        public PersistentManager getPersistentManager() { return persistentManager; }

        public void removePlayer(@Nonnull Player player){
            Validate.notNull(player, "Cannot remove a null Player");
            if(!players.containsKey(player)) return;
            NPC.Personal personal = getPersonal(player);
            if(resetCustomAttributes) customAttributes.remove(player.getUniqueId());
            if(selectedPlayers.contains(player.getName())) selectedPlayers.remove(player.getName());
            players.remove(player);
            getNPCLib().removePersonalNPC(personal);
        }

        protected void forEachActivePlayer(BiConsumer<Player, NPC.Personal> action){
            for(Map.Entry<Player, Personal> entry : players.entrySet()){
                if(!isActive(entry.getKey())) continue;
                action.accept(entry.getKey(), entry.getValue());
            }
        }

        public boolean hasPlayer(@Nonnull Player player){
            Validate.notNull(player, "Cannot verify a null Player");
            return players.containsKey(player);
        }

        public List<String> getSelectedPlayers() { return selectedPlayers; }

        public void addSelectedPlayer(String playerName){
            if(!StringUtils.containsIgnoreCase(selectedPlayers, playerName)) selectedPlayers.add(playerName);
        }

        public void removeSelectedPlayer(String playerName){
            if(StringUtils.containsIgnoreCase(selectedPlayers, playerName)) selectedPlayers.remove(playerName);
        }

        public boolean hasSelectedPlayer(String playerName){
            return StringUtils.containsIgnoreCase(selectedPlayers, playerName);
        }

        public boolean hasPendingUpdates(Player player) {
            if(!hasPlayer(player)) return false;
            return getPersonal(player).globalPendingUpdates.hasPending();
        }

        public void update(Player player) {
            if(!hasPlayer(player)) return;
            getPersonal(player).globalPendingUpdates.update();
        }

        public Set<Player> getPlayers(){ return players.keySet(); }

        public Predicate<Player> getVisibilityRequirement(){ return visibilityRequirement; }

        public boolean hasVisibilityRequirement(){ return visibilityRequirement != null; }

        protected boolean isActive(Player player){
            if(!player.isOnline()) return false;
            if(!hasPlayer(player)) return false;
            NPC.Personal personal = getPersonal(player);
            if(!personal.isCreated()) return false;
            return true;
        }

        public void create(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            synchronizeGlobalAttributes(player);
            getPersonal(player).create();
        }

        public void show(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            getPersonal(player).show();
        }

        public void hide(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            getPersonal(player).hide();
        }

        public void simpleUpdate(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            synchronizeGlobalAttributes(player);
            getPersonal(player).simpleUpdate();
        }

        public void forceUpdate(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            synchronizeGlobalAttributes(player);
            getPersonal(player).forceUpdate();
        }

        public void simpleUpdateText(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            synchronizeGlobalAttributes(player);
            getPersonal(player).simpleUpdateText();
        }

        public void forceUpdateText(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            synchronizeGlobalAttributes(player);
            getPersonal(player).forceUpdateText();
        }

        protected Updatable needUpdate(Player player, Updatable.Type type) {
            return switch (type){
                case NONE -> noNeedUpdate(player);
                case SIMPLE_UPDATE -> needSimpleUpdate(player);
                case FORCE_UPDATE -> needForceUpdate(player);
                case SIMPLE_UPDATE_TEXT -> needSimpleUpdateText(player);
                case FORCE_UPDATE_TEXT -> needForceUpdateText(player);
                case SYNCHRONIZE_GLOBAL_ATTRIBUTES -> needSynchronizeGlobalAttributes(player);
            };
        }

        protected Updatable noNeedUpdate(Player player) { return new Updatable.PerPlayer(); }

        protected Updatable needSimpleUpdate(Player player) { return new Updatable.PerPlayer(this, Updatable.Type.SIMPLE_UPDATE, player); }

        protected Updatable needForceUpdate(Player player) { return new Updatable.PerPlayer(this, Updatable.Type.FORCE_UPDATE, player); }

        protected Updatable needSimpleUpdateText(Player player) { return new Updatable.PerPlayer(this, Updatable.Type.SIMPLE_UPDATE_TEXT, player); }

        protected Updatable needForceUpdateText(Player player) { return new Updatable.PerPlayer(this, Updatable.Type.FORCE_UPDATE_TEXT, player); }

        protected Updatable needSimpleUpdateOrForce(Player player, boolean forceUpdate) { return forceUpdate ? needForceUpdate(player) : needSimpleUpdate(player); }

        protected Updatable needSimpleUpdateTextOrForce(Player player, boolean forceUpdate) { return forceUpdate ? needForceUpdateText(player) : needSimpleUpdateText(player); }

        protected Updatable needSynchronizeGlobalAttributes(Player player) { return new Updatable.PerPlayer(this, Updatable.Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES, player); }

        public void destroy(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            getPersonal(player).destroy();
        }

        public boolean isAutoCreate() { return autoCreate; }

        public void setAutoCreate(boolean autoCreate) { this.autoCreate = autoCreate; }

        public boolean isAutoShow() { return autoShow; }

        public void setAutoShow(boolean autoShow) { this.autoShow = autoShow; }

        public boolean isPersistent() { return persistent; }

        public boolean canBePersistent() { return getPlugin().equals(PlayerNPCPlugin.getInstance()); }

        public void setPersistent(boolean persistent){
            Validate.isTrue(canBePersistent(), "This NPC cannot be persistent because is not created by PlayerNPC plugin.");
            if(persistent == this.persistent) return;
            this.persistent = persistent;
            if(persistent){
                persistentManager = PersistentManager.getPersistent(getPlugin(), getID().getSimpleID());
                PersistentManager.getPersistent(getPlugin(),getID().getSimpleID()).save();
            }
            else PersistentManager.getPersistent(getPlugin(), getID().getSimpleID()).remove();
        }

        public List<String> getCustomText(Player player) { return getCustomAttributes(player).getText(); }

        public boolean hasCustomText(Player player) { return getCustomAttributes(player).text != null; }

        public Updatable setCustomText(Player player, List<String> lines){
            NPC.Attributes customAttributes = getCustomAttributes(player);
            List<String> previous = customAttributes.text != null ? customAttributes.getText() : super.attributes.getText();
            customAttributes.setText(lines);
            return needSimpleUpdateTextOrForce(player, previous.size() != customAttributes.getText().size());
        }

        public Updatable resetCustomText(Player player){
            NPC.Attributes customAttributes = getCustomAttributes(player);
            List<String> previous = customAttributes.text != null ? customAttributes.getText() : super.attributes.getText();
            customAttributes.text = null;
            return needSimpleUpdateTextOrForce(player, previous.size() != customAttributes.getText().size());
        }

        public ItemStack getCustomTextItem(Player player) { return getCustomAttributes(player).getTextItem(); }

        public boolean hasCustomTextItem(Player player) { return getCustomAttributes(player).textItem != null; }

        public Updatable setCustomTextItem(Player player, ItemStack item) {
            getCustomAttributes(player).setTextItem(item);
            return needForceUpdateText(player);
        }

        public Updatable resetCustomTextItem(Player player) {
            getCustomAttributes(player).textItem = null;
            return needForceUpdateText(player);
        }

        public boolean isCustomTextItemGlowing(Player player){ return getCustomAttributes(player).isTextItemGlowing(); }

        public boolean hasCustomTextItemGlowing(Player player){ return getCustomAttributes(player).textItemGlowing != null; }

        public Updatable setCustomTextItemGlowing(Player player, boolean glowing) {
            return needUpdate(getCustomAttributes(player).setTextItemGlowing(glowing));
        }

        public Updatable resetCustomTextItemGlowing(Player player) {
            getCustomAttributes(player).textItemGlowing = null;
            return needSimpleUpdateText(player);
        }

        public Updatable setCustomTextItemGlowingColor(Player player, NPC.Color color) {
            getCustomAttributes(player).setTextItemGlowingColor(color);
            return needSimpleUpdateText(player);
        }

        public Updatable resetCustomTextItemGlowingColor(Player player) {
            getCustomAttributes(player).textItemGlowingColor = null;
            return needSimpleUpdateText(player);
        }

        public Updatable setCustomSkin(Player player, NPC.Skin skin){
            getCustomAttributes(player).setSkin(skin);
            return needForceUpdate(player);
        }

        public Updatable resetCustomSkin(Player player){
            getCustomAttributes(player).skin = null;
            return needForceUpdate(player);
        }

        public Updatable setCustomCollidable(Player player, boolean collidable){
            getCustomAttributes(player).setCollidable(collidable);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomCollidable(Player player){
            getCustomAttributes(player).collidable = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomHideDistance(Player player, double hideDistance){
            getCustomAttributes(player).setHideDistance(hideDistance);
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable resetCustomHideDistance(Player player){
            getCustomAttributes(player).hideDistance = null;
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable setCustomGlowing(Player player, boolean glowing){
            getCustomAttributes(player).setGlowing(glowing);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomGlowing(Player player){
            getCustomAttributes(player).glowing = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomGlowingColor(Player player, NPC.Color color){ return needUpdate(player, getCustomAttributes(player).setGlowingColor(color)); }

        public Updatable resetCustomGlowingColor(Player player){
            getCustomAttributes(player).glowingColor = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomGazeTrackingType(Player player, GazeTrackingType followLookType){
            getCustomAttributes(player).setGazeTrackingType(followLookType);
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable resetCustomGazeTrackingType(Player player){
            getCustomAttributes(player).gazeTrackingType = null;
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable setCustomNameTag(Player player, NameTag nameTag){ return needUpdate(player,getCustomAttributes(player).setNameTag(nameTag)); }

        public Updatable resetCustomNameTag(Player player){
            getCustomAttributes(player).nameTag = null;
            return needForceUpdate(player);
        }

        public Updatable setCustomTabListVisibility(Player player, TabListVisibility tabListVisibility){
            getCustomAttributes(player).setTabListVisibility(tabListVisibility);
            return needForceUpdate(player);
        }

        public Updatable resetCustomTabListVisibility(Player player){
            getCustomAttributes(player).tabListVisibility = null;
            return needForceUpdate(player);
        }

        public Updatable setCustomPose(Player player, NPC.Pose pose){ return needUpdate(player, getCustomAttributes(player).setPose(pose)); }

        public Updatable resetCustomPose(Player player){
            getCustomAttributes(player).pose = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomLineSpacing(Player player, double lineSpacing){ return needUpdate(player, getCustomAttributes(player).setTextLineSpacing(lineSpacing)); }

        public Updatable resetCustomLineSpacing(Player player){
            getCustomAttributes(player).textLineSpacing = null;
            return needForceUpdateText(player);
        }

        public Updatable setCustomTextAlignment(Player player, Vector alignment){
            getCustomAttributes(player).setTextAlignment(alignment.clone());
            return needForceUpdateText(player);
        }

        public Updatable resetCustomTextAlignment(Player player){
            getCustomAttributes(player).textAlignment = null;
            return needForceUpdateText(player);
        }

        public Updatable setCustomInteractCooldown(Player player, long millis){
            getCustomAttributes(player).setInteractCooldown(millis);
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable resetCustomInteractCooldown(Player player){
            getCustomAttributes(player).interactCooldown = null;
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable setCustomTextOpacity(Player player, NPC.Hologram.Opacity opacity){
            getCustomAttributes(player).setTextOpacity(opacity);
            return needForceUpdateText();
        }

        public Updatable resetCustomTextOpacity(Player player){
            getCustomAttributes(player).textOpacity = null;
            return needForceUpdateText();
        }

        public Updatable setCustomMoveSpeed(Player player, double moveSpeed){
            getCustomAttributes(player).setMoveSpeed(moveSpeed);
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable resetCustomMoveSpeed(Player player){
            getCustomAttributes(player).moveSpeed = null;
            return needSynchronizeGlobalAttributes(player);
        }

        public Updatable setCustomOnFire(Player player, boolean onFire){
            getCustomAttributes(player).setOnFire(onFire);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomOnFire(Player player){
            getCustomAttributes(player).onFire = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomPotionParticlesColor(Player player, java.awt.Color color){
            getCustomAttributes(player).setPotionParticlesColor(color);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomPotionParticlesColor(Player player){
            getCustomAttributes(player).potionParticlesColor = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomPotionParticlesType(Player player, PotionParticlesType type){
            getCustomAttributes(player).setPotionParticlesType(type);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomPotionParticlesType(Player player){
            getCustomAttributes(player).potionParticlesType = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomGroundParticles(Player player, boolean groundParticles) {
            getCustomAttributes(player).setGroundParticles(groundParticles);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomGroundParticles(Player player) {
            getCustomAttributes(player).groundParticles = null;
            return needSimpleUpdate(player);
        }

        public Updatable setCustomInvisible(Player player, boolean invisible) {
            getCustomAttributes(player).setInvisible(invisible);
            return needSimpleUpdate(player);
        }

        public Updatable resetCustomInvisible(Player player) {
            getCustomAttributes(player).invisible = null;
            return needSimpleUpdate(player);
        }

        public Updatable resetAllCustomAttributes(Player player) {
            customAttributes.put(player.getUniqueId(), new Attributes());
            return needForceUpdate(player);
        }

        public boolean isResetCustomAttributesWhenRemovePlayer() { return resetCustomAttributes; }

        public void setResetCustomAttributesWhenRemovePlayer(boolean resetCustomAttributes) { this.resetCustomAttributes = resetCustomAttributes; }

        private void synchronizeGlobalAttributes(Player player){
            NPC.Personal personal = getPersonal(player);
            NPC.Attributes A = getAttributes();
            NPC.Attributes cA = getCustomAttributes(player);
            personal.updateGlobalLocation();
            if(ownPlayerSkin){
                Skin.SignedTexture playerSignedTexture = NPC.Skin.Minecraft.getSkinGameProfile(player).orElse(Skin.Minecraft.getSteveSkin().getSignedTexture());
                Skin.SignedTexture npcSignedTexture = personal.getSkin().getTextureData().getSignedTexture();
                if(npcSignedTexture == null || !playerSignedTexture.equals(npcSignedTexture)) personal.setSkin(playerSignedTexture);
            }
            else personal.setSkin(cA.skin != null ? cA.skin : A.skin);
            personal.setSkinVisibleLayers(cA.skinVisibleLayers != null ? cA.skinVisibleLayers : A.skinVisibleLayers);

            personal.setTabListName(cA.tabListName != null ? cA.tabListName : A.tabListName);
            personal.setNameTag(cA.nameTag != null ? cA.nameTag : A.nameTag.clone());
            personal.setTabListVisibility(cA.tabListVisibility != null ? cA.tabListVisibility : A.tabListVisibility);
            personal.setShowNameTag(cA.showNameTag != null ? cA.showNameTag : A.showNameTag);

            personal.setText(cA.text != null ? cA.text : A.text);
            personal.setTextHideDistance(cA.textHideDistance != null ? cA.textHideDistance : A.textHideDistance);
            personal.setTextLineSpacing(cA.textLineSpacing != null ? cA.textLineSpacing : A.textLineSpacing);
            personal.setTextAlignment((cA.textAlignment != null ? cA.textAlignment : A.textAlignment).clone());
            personal.setTextOpacity(cA.textOpacity != null ? cA.textOpacity : A.textOpacity);
            personal.setTextLinesOpacity((HashMap<Integer, Hologram.Opacity>) (cA.textLinesOpacity != null ? cA.textLinesOpacity : A.textLinesOpacity).clone());
            personal.setTextItem((cA.textItem != null ? cA.textItem : A.textItem).clone());
            personal.setTextItemGlowing(cA.textItemGlowing != null ? cA.textItemGlowing : A.textItemGlowing);
            personal.setTextItemGlowingColor(cA.textItemGlowingColor != null ? cA.textItemGlowingColor : A.textItemGlowingColor);

            personal.setEquipment((HashMap<Slot, ItemStack>) (cA.equipment != null ? cA.equipment : A.equipment).clone());
            personal.setCollidable(cA.collidable != null ? cA.collidable : A.collidable);
            personal.setHideDistance(cA.hideDistance != null ? cA.hideDistance : A.hideDistance);
            personal.setGazeTrackingType(cA.gazeTrackingType != null ? cA.gazeTrackingType : A.gazeTrackingType);

            personal.setGlowing(cA.glowing != null ? cA.glowing : A.glowing);
            personal.setGlowingColor(cA.glowingColor != null ? cA.glowingColor : A.glowingColor);
            personal.setPose(cA.pose != null ? cA.pose : A.pose);
            personal.setOnFire(cA.onFire != null ? cA.onFire : A.onFire);
            personal.setGroundParticles(cA.groundParticles != null ? cA.groundParticles : A.groundParticles);
            personal.setPotionParticlesColor(cA.potionParticlesColor != null ? cA.potionParticlesColor : A.potionParticlesColor);
            personal.setPotionParticlesType(cA.potionParticlesType != null ? cA.potionParticlesType : A.potionParticlesType);
            personal.setArrowsInBody(cA.arrowsInBody != null ? cA.arrowsInBody : A.arrowsInBody);
            personal.setBeeStingersInBody(cA.beeStingersInBody != null ? cA.beeStingersInBody : A.beeStingersInBody);
            personal.setShaking(cA.shaking != null ? cA.shaking : A.shaking);
            personal.setInvisible(cA.invisible != null ? cA.invisible : A.invisible);

            personal.setInteractCooldown(cA.interactCooldown != null ? cA.interactCooldown : A.interactCooldown);

            personal.setMoveSpeed(cA.moveSpeed != null ? cA.moveSpeed : A.moveSpeed);

            personal.globalPendingUpdates.onExecute(Updatable.Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES);
        }

        public void createAllPlayers(){
            players.forEach((player, npc)->{
                if(!npc.isCreated()) create(player);
            });
        }

        public void show(){ forEachActivePlayer((player, npc) -> show(player)); }

        public void hide(){ forEachActivePlayer((player, npc) -> hide(player)); }

        @Override
        public void simpleUpdate() {
            forEachActivePlayer((player, npc) -> simpleUpdate(player));
            pendingUpdates.onExecute(Updatable.Type.SIMPLE_UPDATE);
        }

        @Override
        public void forceUpdate() {
            forEachActivePlayer((player, npc) -> forceUpdate(player));
            pendingUpdates.onExecute(Updatable.Type.FORCE_UPDATE);
        }

        @Override
        public void simpleUpdateText() {
            forEachActivePlayer((player, npc) -> simpleUpdateText(player));
            pendingUpdates.onExecute(Updatable.Type.SIMPLE_UPDATE_TEXT);
        }

        @Override
        public void forceUpdateText() {
            forEachActivePlayer((player, npc) -> forceUpdateText(player));
            pendingUpdates.onExecute(Updatable.Type.FORCE_UPDATE_TEXT);
        }

        @Override
        public void destroy() {
            Set<Player> playerSet = new HashSet<>();
            playerSet.addAll(players.keySet());
            playerSet.forEach((player) -> getNPCLib().removePersonalNPC(getPersonal(player)));
        }

        @Override
        public void teleport(World world, double x, double y, double z, float yaw, float pitch) {
            NPC.Events.Teleport npcTeleportEvent = new NPC.Events.Teleport(this, new Location(world, x, y, z, yaw, pitch));
            if(npcTeleportEvent.isCancelled()) return;
            super.world = world;
            super.x = x;
            super.y = y;
            super.z = z;
            super.yaw = yaw;
            super.pitch = pitch;
            forEachActivePlayer((player, npc)-> npc.teleport(world, x, y, z, yaw, pitch));
        }

        @Override
        public Updatable lookAt(float yaw, float pitch) {
            super.yaw = yaw;
            super.pitch = pitch;
            forEachActivePlayer((player, npc)-> npc.lookAt(yaw, pitch));
            return this.needSimpleUpdate();
        }

        @Override
        public void playAnimation(Animation animation) { forEachActivePlayer((player, npc) -> playAnimation(player, animation)); }

        @Override
        public <T> void playParticle(Particle particle, Vector regarding, Vector offset, int count, double speed, @Nullable T data) { forEachActivePlayer((player, npc) -> playParticle(particle, regarding, offset, count, speed, data)); }

        public <T> void playParticle(Player player, Particle particle, Vector regarding, Vector offset, int count, double speed, @Nullable T data){ getPersonal(player).playParticle(particle, regarding, offset, count, speed, data); }

        public void playParticle(Player player, Particle particle, Vector regarding, Vector offset, int count, double speed) { playParticle(player, particle, regarding, offset, count, speed, null); }

        public void playAnimation(Player player, Animation animation){ getPersonal(player).playAnimation(animation); }

        @Override
        public void hit() {
            playAnimation(Animation.TAKE_DAMAGE);
            forEachActivePlayer((player, npc) -> player.playSound(getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0F, 1.0F));
        }

        @Override
        protected void move(double moveX, double moveY, double moveZ) {
            Validate.isTrue(Math.abs(moveX) < 8 && Math.abs(moveY) < 8 && Math.abs(moveZ) < 8, "NPC cannot move 8 blocks or more at once, use teleport instead");
            NPC.Events.Move npcMoveEvent = new NPC.Events.Move(this, new Location(super.world, super.x + moveX, super.y + moveY, super.z + moveZ));
            if(npcMoveEvent.isCancelled()) return;
            super.x += moveX;
            super.y += moveY;
            super.z += moveZ;
            forEachActivePlayer((player, npc) -> npc.move(moveX, moveY, moveZ));
        }

        @Override
        protected void updatePlayerRotation() { forEachActivePlayer((player, npc) -> updatePlayerRotation(player)); }

        protected void updatePlayerRotation(Player player){ getPersonal(player).updatePlayerRotation(); }

        @Override
        protected void updateLocation() { forEachActivePlayer((player, npc) -> npc.updateLocation()); }

        @Override
        protected void updateMove() { forEachActivePlayer((player, npc) -> npc.updateMove()); }

        public Item dropItem(ItemStack itemStack){
            if(itemStack == null || itemStack.getType().equals(Material.AIR)) return null;
            return getWorld().dropItemNaturally(getLocation(), itemStack);
        }

        public Item dropItemInSlot(NPC.Slot slot){
            ItemStack itemStack = getEquipment(slot);
            if(itemStack == null || itemStack.getType().equals(Material.AIR)) return null;
            clearEquipment(slot);
            Item item = dropItem(itemStack);
            simpleUpdate();
            return item;
        }

        public Item dropItemInHand(){
            return dropItemInSlot(Slot.MAINHAND);
        }

        public Updatable<Global> setOwnPlayerSkin(boolean ownPlayerSkin){
            this.ownPlayerSkin = ownPlayerSkin;
            return needForceUpdate();
        }

        public Updatable<Global> setOwnPlayerSkin(){
            return setOwnPlayerSkin(true);
        }

        public boolean isOwnPlayerSkin() {
            return ownPlayerSkin;
        }

        @Deprecated
        public NPC.Personal getPersonal(Player player){
            Validate.isTrue(players.containsKey(player), "Player is not added to this Global NPC");
            return players.get(player);
        }

        public NPC.Attributes getCustomAttributes(Player player){
            Validate.isTrue(customAttributes.containsKey(player.getUniqueId()), "Player is not added to this Global NPC");
            return customAttributes.get(player.getUniqueId());
        }

        protected void np(Entity entity){
            this.nearestPlayer = entity;
            this.lastNearestPlayerUpdate = System.currentTimeMillis();
        }

        protected Entity np(){
            if(System.currentTimeMillis() - lastNearestPlayerUpdate > LOOK_TICKS * (1000 / 20)) nearestPlayer = null;
            return nearestPlayer;
        }

        protected Entity ne(){
            if(System.currentTimeMillis() - lastNearestEntityUpdate > LOOK_TICKS * (1000 / 20)) nearestEntity = null;
            return nearestEntity;
        }

        protected void ne(Entity entity){
            this.nearestEntity = entity;
            this.lastNearestEntityUpdate = System.currentTimeMillis();
        }

        public static class PersistentManager{

            private static HashMap<Plugin, HashMap<String, PersistentManager>> PERSISTENT_DATA;

            static{
                PERSISTENT_DATA = new HashMap<>();
            }

            protected static PersistentManager getPersistent(Plugin plugin, String id){
                checkExistPlugin(plugin);
                if(PERSISTENT_DATA.get(plugin).containsKey(id)) return PERSISTENT_DATA.get(plugin).get(id);
                else return new PersistentManager(plugin, id);
            }

            private static void setPersistentData(Plugin plugin, String id, PersistentManager globalPersistentData) {
                checkExistPlugin(plugin);
                PERSISTENT_DATA.get(plugin).put(id, globalPersistentData);
            }

            private static void checkExistPlugin(Plugin plugin){
                if(!PERSISTENT_DATA.containsKey(plugin)) PERSISTENT_DATA.put(plugin, new HashMap<>());
            }

            protected static void forEachGlobalPersistent(Consumer<NPC.Global> action){
                PERSISTENT_DATA.forEach((x, y) -> forEachGlobalPersistent(x, action));
            }

            protected static void forEachGlobalPersistent(Plugin plugin, Consumer<NPC.Global> action){
                if(!PERSISTENT_DATA.containsKey(plugin)) return;
                PERSISTENT_DATA.get(plugin).values().stream().filter(x-> x.global != null).forEach(x -> action.accept(x.global));
            }

            protected static void forEachPersistentManager(Consumer<NPC.Global.PersistentManager> action){
                PERSISTENT_DATA.forEach((x, y) -> forEachPersistentManager(x, action));
            }

            protected static void forEachPersistentManager(Plugin plugin, Consumer<NPC.Global.PersistentManager> action){
                if(!PERSISTENT_DATA.containsKey(plugin)) return;
                PERSISTENT_DATA.get(plugin).values().stream().forEach(x -> action.accept(x));
            }

            private Plugin plugin;
            private String id;
            private NPC.Global global;
            private File file;
            private FileConfiguration config;
            private LastUpdate lastUpdate;

            protected PersistentManager(Plugin plugin, String simpleID) {
                this.plugin = plugin;
                this.id = simpleID;
                this.file = new File(getFilePath());
                this.lastUpdate = new LastUpdate();
                setPersistentData(plugin, id, this);
            }

            public void load(){
                if(global != null) NPCLib.getInstance().removeGlobalNPC(global);
                if(!file.exists()) throw new IllegalArgumentException("Persistent NPC data file doesn't exists.");
                this.config = YamlConfiguration.loadConfiguration(file);
                Location location = null;
                try{ location = config.getLocation("location"); } catch (Exception e) {}
                if(location == null) throw new IllegalArgumentException("There was an error loading NPC location.");
                Visibility visibility = Visibility.EVERYONE;
                String visibilityPermission = null;
                if(config.contains("visibility.type")) visibility = Visibility.valueOf(config.getString("visibility.type"));
                if(config.contains("visibility.requirement")) visibilityPermission = config.getString("visibility.requirement");
                String finalVisibilityPermission = (visibilityPermission == null || visibilityPermission.equals("none")) ? null : visibilityPermission;
                global = NPCLib.getInstance().generateGlobalNPC(plugin, id, visibility, finalVisibilityPermission != null ? (player -> player.hasPermission(finalVisibilityPermission)) : null, location);
                global.persistent = true;
                global.persistentManager = this;
                //
                if(config.contains("skin.custom.enabled") && config.getBoolean("skin.custom.enabled") && config.contains("skin.custom.texture") && config.contains("skin.custom.signature")){
                    String texture = config.getString("skin.custom.texture");
                    String signature = config.getString("skin.custom.signature");
                    String skinName = null;
                    if(config.contains("skin.custom.name")) config.getString("skin.custom.name");
                    if(signature.length() < 684 || texture.length() == 0){
                        texture = Skin.Minecraft.STEVE.getTexture();
                        signature = Skin.Minecraft.STEVE.getSignature();
                        skinName = null;
                    }
                    global.setSkin(texture, signature);
                }
                else if(config.contains("skin.player")) global.setSkin(config.getString("skin.player"), skin -> global.forceUpdate());
                else if(config.contains("skin.mineskin")) global.setMineSkin(config.getString("skin.mineskin"), mineSkin -> global.forceUpdate());
                if(config.contains("hologram.text")){
                    List<String> lines = config.getStringList("hologram.text");
                    if(lines != null && lines.size() > 0){ global.setText(StringUtils.replaceAll(lines, "&", "§")); }
                }
                if(config.contains("hologram.textOpacity")) global.setTextOpacity(Hologram.Opacity.valueOf(config.getString("hologram.textOpacity")));
                if(config.getConfigurationSection("hologram.linesOpacity") != null){
                    for(String line : config.getConfigurationSection("hologram.linesOpacity").getKeys(false)) global.setTextLineOpacity(Integer.valueOf(line), Hologram.Opacity.valueOf(config.getString("hologram.linesOpacity." + line)));
                }
                if(config.contains("visibility.selectedPlayers") && global.getVisibility().equals(Visibility.SELECTED_PLAYERS)) global.selectedPlayers = config.getStringList("visibility.selectedPlayers");
                if(config.contains("hologram.alignment")) global.setTextAlignment(config.getVector("hologram.alignment"));
                if(config.contains("skin.ownPlayer")) global.setOwnPlayerSkin(config.getBoolean("skin.ownPlayer"));
                Arrays.stream(Skin.Layer.values()).filter(x-> config.contains("skin.visibleLayers." + x.name().toLowerCase())).forEach(x-> global.getSkinVisibleLayers().setVisibility(x, config.getBoolean("skin.visibleLayers." + x.name().toLowerCase())));
                if(config.contains("glow.color")) global.setGlowingColor(Color.fromName(config.getString("glow.color")).orElse(null));
                if(config.contains("glow.enabled")) global.setGlowing(config.getBoolean("glow.enabled"));
                if(config.contains("pose")) global.setPose(Pose.valueOf(config.getString("pose")));
                if(config.contains("collidable")) global.setCollidable(config.getBoolean("collidable"));
                ConfigurationSection nameTagSection = config.getConfigurationSection("nameTag");
                if(nameTagSection != null){
                    String prefix = null, name = null, suffix = null, tabListName = null;
                    if(nameTagSection.contains("prefix") && !nameTagSection.getString("prefix").equals("")) prefix = ChatColor.translateAlternateColorCodes('&', nameTagSection.getString("prefix"));
                    if(nameTagSection.contains("name") && !nameTagSection.getString("name").equals("")) name = ChatColor.translateAlternateColorCodes('&', nameTagSection.getString("name"));
                    if(nameTagSection.contains("suffix") && !nameTagSection.getString("suffix").equals("")) suffix = ChatColor.translateAlternateColorCodes('&', nameTagSection.getString("suffix"));
                    global.setNameTag(prefix, name, suffix);
                    if(nameTagSection.contains("showAbovePlayer")) global.setShowNameTag(nameTagSection.getBoolean("showAbovePlayer"));
                }
                if(config.contains("tabList.show")){
                    if(config.isBoolean("tabList.show")) global.setShowOnTabList(config.getBoolean("tabList.show"));
                    else global.setTabListVisibility(TabListVisibility.fromName(config.getString("tabList.show")));
                }
                if(config.contains("tabList.name")) global.setTabListName(config.getString("tabList.name").equals("") ? null : ChatColor.translateAlternateColorCodes('&', config.getString("tabList.name")));
                if(config.contains("onFire")) global.setOnFire(config.getBoolean("onFire"));
                if(config.contains("invisible")) global.setInvisible(config.getBoolean("invisible"));
                if(config.contains("body.stuck.arrows")) global.setArrowsInBody(config.getInt("body.stuck.arrows"));
                if(config.contains("body.stuck.bee_stingers")) global.setBeeStingersInBody(config.getInt("body.stuck.bee_stingers"));
                if(config.contains("body.shaking")) global.setShaking(config.getBoolean("body.shaking"));
                if(config.contains("move.speed")) global.setMoveSpeed(config.getDouble("move.speed"));
                if(config.contains("interact.cooldown")) global.setInteractCooldown(config.getLong("interact.cooldown"));
                if(config.contains("gazeTracking.type")) global.setGazeTrackingType(GazeTrackingType.valueOf(config.getString("gazeTracking.type")));
                if(config.contains("distance.hide")) global.setHideDistance(config.getDouble("distance.hide"));
                for(Slot slot : Slot.values()){
                    if(!config.contains("slots." + slot.name().toLowerCase())) continue;
                    ItemStack item = null;
                    try{ item = config.getItemStack("slots." + slot.name().toLowerCase()); } catch (Exception e) { config.set("slots." + slot.name().toLowerCase(), null); }
                    global.setItem(slot, item);
                }
                Arrays.stream(Slot.values()).filter(x-> config.contains("slots." + x.name().toLowerCase())).forEach(x-> global.setItem(x, config.getItemStack("slots." + x.name().toLowerCase())));
                if(config.getConfigurationSection("customData") != null){
                    for(String keys : config.getConfigurationSection("customData").getKeys(false)) global.setCustomData(NPCLib.Registry.ID.playerNPC(keys), config.getString("customData." + keys));
                }
                if(config.getConfigurationSection("interact.actions") != null){
                    for(String keys : config.getConfigurationSection("interact.actions").getKeys(false)){
                        try{
                            Interact.ClickAction clickAction = null;
                            Interact.Actions.Type actionType = null;
                            String stringType = config.getString("interact.actions." + keys + ".type");
                            try{ actionType = Interact.Actions.Type.valueOf( stringType); } catch (Exception e){}
                            if(actionType == null){ try{ actionType = Interact.Actions.LegacyType.valueOf(stringType).type; Bukkit.getConsoleSender().sendMessage("§8- §eLegacy click action " + stringType + " type was found. Fixing it..."); } catch (Exception e) {} }
                            if(actionType == null){ throw new IllegalArgumentException(stringType + " type of action is not valid."); }
                            Interact.ClickType clickType = Interact.ClickType.valueOf(config.getString("interact.actions." + keys + ".click"));
                            if(actionType.equals(Interact.Actions.Type.PLAYER_SEND_CHAT_MESSAGE)){
                                List<String> message = config.getStringList("interact.actions." + keys + ".messages");
                                String[] messages = new String[message.size()];
                                for(int i = 0; i < message.size(); i++) messages[i] = message.get(i).replaceAll("&", "§");
                                clickAction = global.addMessageClickAction(clickType, messages);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_SEND_ACTIONBAR_MESSAGE)){
                                String message = config.getString("interact.actions." + keys + ".message").replaceAll("&", "§");
                                clickAction = global.addActionBarMessageClickAction(clickType, message);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_CONNECT_BUNGEE_SERVER)){
                                String server = config.getString("interact.actions." + keys + ".server");
                                clickAction = global.addConnectBungeeServerClickAction(clickType, server);
                            }
                            else if(actionType.equals(Interact.Actions.Type.CONSOLE_PERFORM_COMMAND)){
                                String command = config.getString("interact.actions." + keys + ".command");
                                clickAction = global.addRunConsoleCommandClickAction(clickType, command);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_PERFORM_COMMAND)){
                                String command = config.getString("interact.actions." + keys + ".command");
                                clickAction = global.addRunPlayerCommandClickAction(clickType, command);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_SEND_TITLE_MESSAGE)){
                                String title = config.getString("interact.actions." + keys + ".title").replaceAll("&", "§");
                                String subtitle = config.getString("interact.actions." + keys + ".subtitle").replaceAll("&", "§");
                                Integer fadeIn = config.getInt("interact.actions." + keys + ".fadeIn");
                                Integer stay = config.getInt("interact.actions." + keys + ".stay");
                                Integer fadeOut = config.getInt("interact.actions." + keys + ".fadeOut");
                                clickAction = global.addTitleMessageClickAction(clickType, title, subtitle, fadeIn, stay, fadeOut);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_TELEPORT_TO_LOCATION)){
                                Location location1 = config.getLocation("interact.actions." + keys + ".location");
                                clickAction = global.addTeleportToLocationClickAction(clickType, location1);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_GIVE_ITEM)){
                                ItemStack itemStack = config.getItemStack("interact.actions." + keys + ".item");
                                clickAction = global.addGivePlayerItemClickAction(clickType, itemStack);
                            }
                            else if(actionType.equals(Interact.Actions.Type.NPC_SET_CUSTOM_DATA)){
                                String key = config.getString("interact.actions." + keys + ".key");
                                String value = config.getString("interact.actions." + keys + ".value");
                                Boolean checkIfSame = config.getBoolean("interact.actions." + keys + ".checkIfSame");
                                clickAction = global.addSetCustomDataClickAction(clickType, PlayerNPCPlugin.getInstance(), key, value);
                                ((Interact.Actions.SetCustomData) clickAction).setCheckIfSame(checkIfSame);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_OPEN_BOOK)){
                                ItemStack itemStack = config.getItemStack("interact.actions." + keys + ".book");
                                clickAction = global.addOpenBookClickAction(clickType, itemStack);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_OPEN_WORKBENCH)){
                                clickAction = global.addOpenWorkbenchClickAction(clickType);
                            }
                            else if(actionType.equals(Interact.Actions.Type.PLAYER_OPEN_ENCHANTING)){
                                clickAction = global.addOpenEnchantingClickAction(clickType);
                            }
                            else if(actionType.equals(Interact.Actions.Type.NPC_PLAY_ANIMATION)){
                                Animation animation = Animation.valueOf(config.getString("interact.actions." + keys + ".animation"));
                                clickAction = global.addPlayAnimationClickAction(clickType, animation);
                            }
                            else if(actionType.name().contains("_MONEY")){
                                Double balance = config.getDouble("interact.actions." + keys + ".balance");
                                if(actionType.equals(Interact.Actions.Type.PLAYER_WITHDRAW_MONEY)) clickAction = global.addPlayerWithdrawMoneyClickAction(clickType, balance);
                                if(actionType.equals(Interact.Actions.Type.PLAYER_GIVE_MONEY)) clickAction = global.addPlayerGiveMoneyClickAction(clickType, balance);
                            }
                            Long delayTicks = config.getLong("interact.actions." + keys + ".delayTicks");
                            Long cooldownMilliseconds = config.getLong("interact.actions." + keys + ".cooldownMilliseconds");
                            Boolean enabled = config.contains("interact.actions." + keys + ".enabled") ? config.getBoolean("interact.actions." + keys + ".enabled") : true;
                            clickAction.setEnabled(enabled);
                            clickAction.setDelayTicks(delayTicks);
                            clickAction.setCooldownMilliseconds(cooldownMilliseconds);
                            if(config.getConfigurationSection("interact.actions." + keys + ".condition") != null){
                                for(String keysS : config.getConfigurationSection("interact.actions." + keys + ".condition").getKeys(false)){
                                    clickAction.addCondition((Conditions.Condition) config.get("interact.actions." + keys + ".condition." + keysS));
                                }
                            }
                        }
                        catch (Exception e) { NPCLib.printError(e); }
                    }
                }
                //
                global.forceUpdate();
                this.lastUpdate.load();
                Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Persistent Global NPC §a" + global.getID().getFullID() + " §7has been loaded.");
            }

            public void save(){
                if(global == null) global = NPCLib.getInstance().grabGlobalNPC(plugin, id).orElse(null);
                if(global == null || !global.isPersistent()) return;
                try{
                    checkFileExists();
                    if(config == null) config  = YamlConfiguration.loadConfiguration(file);
                    //
                    if(config.contains("disableSaving") && config.getBoolean("disableSaving")) return;
                    NMSFileConfiguration.setHeader(config, Arrays.asList("Persistent Global NPC " + global.getID().getFullID()));
                    config.set("location", global.getLocation());
                    config.set("visibility.type", global.getVisibility().name());
                    NPCLib.Registry.ID visibilityRequirementKey = NPCLib.Registry.ID.playerNPC("visibilityrequirementpermission");
                    config.set("visibility.requirement", (global.getVisibilityRequirement() != null && global.getCustomDataKeysID().contains(visibilityRequirementKey)) ? global.grabCustomData(visibilityRequirementKey).get() : "none");
                    if(global.getVisibility().equals(Visibility.SELECTED_PLAYERS)) config.set("visibility.selectedPlayers", global.selectedPlayers);
                    else config.set("visibility.selectedPlayers", null);
                    config.set("skin.player", global.getSkin().getType().equals(Skin.Type.MINECRAFT) ? global.getSkin().getName() : null);
                    config.set("skin.mineskin", global.getSkin().getType().equals(Skin.Type.MINESKIN) ? ((Skin.MineSkin) global.getSkin()).getId() : null);
                    NMSFileConfiguration.setComments(config, "skin.mineskin", Arrays.asList(global.getSkin().getType().equals(Skin.Type.MINESKIN) ? global.getSkin().castMineSkin().getMineSkinURL() : null));
                    if(!config.contains("skin.custom")){
                        config.set("skin.custom.enabled", false);
                        config.set("skin.custom.texture", "");
                        config.set("skin.custom.signature", "");
                        config.set("skin.custom.name", "");
                    }
                    if(global.getSkin().getType().equals(Skin.Type.CUSTOM)){
                        NPC.Skin.Custom custom = global.getSkin().castCustomSkin();
                        config.set("skin.custom.enabled", true);
                        config.set("skin.custom.texture", custom.getTexture());
                        config.set("skin.custom.signature", custom.getSignature());
                        config.set("skin.custom.name", custom.getName());
                    }
                    else config.set("skin.custom.enabled", false);
                    NMSFileConfiguration.setComments(config, "skin.custom.enabled", Arrays.asList("If you want to use a custom texture, set enabled as true, if not, it will use the player name skin.", "To easily get texture and signature use '/npclib getskininfo (type) (name)' or https://mineskin.org/"));
                    config.set("skin.ownPlayer", global.isOwnPlayerSkin());
                    Arrays.stream(Skin.Layer.values()).forEach(x-> config.set("skin.visibleLayers." + x.name().toLowerCase(), global.getSkinVisibleLayers().isVisible(x)));
                    config.set("customData", null);
                    for(NPCLib.Registry.ID keys : global.getCustomDataKeysID()){
                        if(!keys.getPluginName().equals(PlayerNPCPlugin.getInstance().getName().toLowerCase())) continue;
                        config.set("customData." + keys.getSimpleID(), global.grabCustomData(keys).get());
                    }
                    List<String> lines = global.getText();
                    if(lines != null && lines.size() > 0){
                        List<String> coloredLines = new ArrayList<>();
                        lines.forEach(x-> coloredLines.add(x.replaceAll("§", "&")));
                        config.set("hologram.text", coloredLines);
                    } else config.set("hologram.text", lines);
                    config.set("hologram.lineSpacing", global.getTextLineSpacing());
                    config.set("hologram.textOpacity", global.getTextOpacity().name());
                    config.set("hologram.linesOpacity", null);
                    for(Integer line : global.getLinesOpacity().keySet()) config.set("hologram.linesOpacity." + line, global.getLineOpacity(line).name());
                    config.set("hologram.alignment", global.getTextAlignment());
                    config.set("gazeTracking.type", global.getGazeTrackingType().name());
                    config.set("pose", global.getPose().name());
                    config.set("collidable", global.isCollidable());
                    config.set("distance.hide", global.getHideDistance());
                    config.set("glow.enabled", global.isGlowing());
                    config.set("glow.color", global.getGlowingColor().name());
                    ConfigurationSection nameTagSection = config.getConfigurationSection("nameTag") != null ? config.getConfigurationSection("nameTag") : config.createSection("nameTag");
                    NameTag nameTag = global.getNameTag();
                    if(nameTag.getPrefix() != null) nameTagSection.set("prefix", global.getNameTag().getPrefix().replaceAll("§", "&"));
                    else nameTagSection.set("prefix", "");
                    nameTagSection.set("name", global.getNameTag().getName().replaceAll("§", "&"));
                    if(nameTag.getSuffix() != null) nameTagSection.set("suffix", global.getNameTag().getSuffix().replaceAll("§", "&"));
                    else nameTagSection.set("suffix", "");
                    nameTagSection.set("showAbovePlayer", global.isShowNameTag());
                    config.set("tabList.show", global.getTabListVisibility().name());
                    config.set("tabList.name", global.getTabListName() != null ? global.getTabListName().replaceAll("§", "&") : "");
                    config.set("move.speed", global.getMoveSpeed());
                    Arrays.stream(Slot.values()).forEach(x-> config.set("slots." + x.name().toLowerCase(), (global.getEquipment(x) != null && !global.getEquipment(x).getType().isAir()) ? global.getEquipment(x) : null));
                    config.set("onFire", global.isOnFire());
                    config.set("invisible", global.isInvisible());
                    config.set("body.stuck.arrows", global.getArrowsInBody());
                    config.set("body.stuck.bee_stingers", global.getBeeStingersInBody());
                    config.set("body.shaking", global.isShaking());
                    config.set("interact.cooldown", global.getInteractCooldown());
                    config.set("interact.actions", null);
                    int clickActionID = 0;
                    for(Interact.ClickAction clickAction : global.getClickActions()){
                        if(clickAction.getActionType().equals(Interact.Actions.Type.CUSTOM_ACTION)) continue;
                        clickActionID++;
                        config.set("interact.actions." + clickActionID + ".type", clickAction.actionType.name());
                        config.set("interact.actions." + clickActionID + ".click", clickAction.clickType.name());
                        config.set("interact.actions." + clickActionID + ".enabled", clickAction.isEnabled());
                        config.set("interact.actions." + clickActionID + ".delayTicks", clickAction.delayTicks);
                        config.set("interact.actions." + clickActionID + ".cooldownMilliseconds", clickAction.cooldownMilliseconds);
                        if(clickAction instanceof Interact.Actions.Player.SendChatMessage){
                            Interact.Actions.Player.SendChatMessage castAction = (Interact.Actions.Player.SendChatMessage) clickAction;
                            String[] messages = new String[castAction.getMessages().length];
                            for(int i = 0; i < castAction.getMessages().length; i++) messages[i] = castAction.getMessages()[i].replaceAll("§", "&");
                            config.set("interact.actions." + clickActionID + ".messages", messages);
                        }
                        else if(clickAction instanceof Interact.Actions.Player.SendActionBarMessage){
                            Interact.Actions.Player.SendActionBarMessage castAction = (Interact.Actions.Player.SendActionBarMessage) clickAction;
                            config.set("interact.actions." + clickActionID + ".message", castAction.getMessage().replaceAll("§", "&"));
                        }
                        else if(clickAction instanceof Interact.Actions.Player.ConnectBungeeServer){
                            Interact.Actions.Player.ConnectBungeeServer castAction = (Interact.Actions.Player.ConnectBungeeServer) clickAction;
                            config.set("interact.actions." + clickActionID + ".server", castAction.getServer());
                        }
                        else if(clickAction instanceof Interact.Actions.Console.PerformCommand){
                            Interact.Actions.Console.PerformCommand castAction = (Interact.Actions.Console.PerformCommand) clickAction;
                            config.set("interact.actions." + clickActionID + ".command", castAction.getCommand());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.PerformCommand){
                            Interact.Actions.Player.PerformCommand castAction = (Interact.Actions.Player.PerformCommand) clickAction;
                            config.set("interact.actions." + clickActionID + ".command", castAction.getCommand());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.SendTitleMessage){
                            Interact.Actions.Player.SendTitleMessage castAction = (Interact.Actions.Player.SendTitleMessage) clickAction;
                            config.set("interact.actions." + clickActionID + ".title", castAction.getTitle().replaceAll("§", "&"));
                            config.set("interact.actions." + clickActionID + ".subtitle", castAction.getSubtitle().replaceAll("§", "&"));
                            config.set("interact.actions." + clickActionID + ".fadeIn", castAction.getFadeIn());
                            config.set("interact.actions." + clickActionID + ".stay", castAction.getStay());
                            config.set("interact.actions." + clickActionID + ".fadeOut", castAction.getFadeOut());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.TeleportToLocation){
                            Interact.Actions.Player.TeleportToLocation castAction = (Interact.Actions.Player.TeleportToLocation) clickAction;
                            config.set("interact.actions." + clickActionID + ".location", castAction.getLocation());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.GiveItem){
                            Interact.Actions.Player.GiveItem castAction = (Interact.Actions.Player.GiveItem) clickAction;
                            config.set("interact.actions." + clickActionID + ".item", castAction.getItemStack());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.GiveKit){
                            Interact.Actions.Player.GiveKit castAction = (Interact.Actions.Player.GiveKit) clickAction;
                            config.set("interact.actions." + clickActionID + ".kit", castAction.getKit());
                        }
                        else if(clickAction instanceof Interact.Actions.SetCustomData){
                            Interact.Actions.SetCustomData castAction = (Interact.Actions.SetCustomData) clickAction;
                            config.set("interact.actions." + clickActionID + ".key", castAction.getSimpleKey());
                            config.set("interact.actions." + clickActionID + ".value", castAction.getValue());
                            config.set("interact.actions." + clickActionID + ".checkIfSame", castAction.isCheckIfSame());
                        }
                        else if(clickAction instanceof Interact.Actions.Player.OpenBook){
                            Interact.Actions.Player.OpenBook castAction = (Interact.Actions.Player.OpenBook) clickAction;
                            config.set("interact.actions." + clickActionID + ".book", castAction.getBook());
                        }
                        else if(clickAction instanceof Interact.Actions.PlayAnimation){
                            Interact.Actions.PlayAnimation castAction = (Interact.Actions.PlayAnimation) clickAction;
                            config.set("interact.actions." + clickActionID + ".animation", castAction.getAnimation().name());
                        }
                        else if(clickAction instanceof Interact.Actions.Money){
                            Interact.Actions.Money castAction = (Interact.Actions.Money) clickAction;
                            config.set("interact.actions." + clickActionID + ".balance", castAction.getBalance());
                        }
                        for(int i = 0; i < clickAction.getConditions().size(); i++){
                            config.set("interact.actions." + clickActionID + ".condition." + (i+1), clickAction.getConditions().get(i));
                        }
                    }
                    if(!config.contains("disableSaving")) config.set("disableSaving", false);
                    config.save(file);
                    this.lastUpdate.save();
                    PlayerNPCPlugin.sendConsoleMessage("§7Persistent Global NPC §a" + global.getID().getFullID() + " §7has been saved.");
                }
                catch (Exception e){
                    e.printStackTrace();
                    PlayerNPCPlugin.sendConsoleMessage("§7There was an error while saving Persistent Global NPC §c" + global.getID().getFullID());
                }
            }

            private void checkFileExists(){
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile();} catch (Exception e){};
            }

            protected void remove(){
                config = null;
                if(file.exists()) file.delete();
                File folder = new File(getFolderPath());
                if(folder.exists()) folder.delete();
                this.global = null;
            }

            public void setDisableSaving(boolean b){
                checkFileExists();
                if(config == null) config  = YamlConfiguration.loadConfiguration(file);
                config.set("disableSaving", b);
                try { config.save(file); } catch (Exception ignored) {}
            }

            public boolean isDisableSaving(){ return (boolean) get("disableSaving"); }

            protected String getFilePath(){ return getFolderPath() + "/data.yml"; }

            protected String getFolderPath(){ return "plugins/PlayerNPC/persistent/global/" + plugin.getName().toLowerCase() + "/" + id; }

            public NPC.Global getGlobal() { return global; }

            protected void setGlobal(Global global) { this.global = global; }

            public boolean isLoaded(){ return config != null; }

            protected Object get(String s){ return containsKey(s) ? config.get(s) : null; }

            protected boolean containsKey(String s){ return config != null && s != null ? config.contains(s) : false; }

            protected FileConfiguration getConfig(){ return this.config; }

            protected void set(String s, Object o){
                if(config == null) return;
                config.set(s, o);
            }

            public LastUpdate getLastUpdate() { return lastUpdate; }

            public class LastUpdate{

                private Type type; private String time;

                private LastUpdate() {}

                protected void load() { type = Type.LOAD; time(); }

                protected void save() { type = Type.SAVE; time(); }

                private void time() { time = TimerUtils.getCurrentDate(); }

                public Type getType() { return type; }

                public String getTime() { return time; }

                public enum Type{ SAVE, LOAD }

            }

        }
    }

    /*
                    Enums and Classes
     */

    public static abstract class Updatable<N extends NPC>{

        private final Type type;
        private final N npc;

        private Updatable(@Nullable N npc, @Nullable Type type) {
            this.npc = npc;
            this.type = type != null ? type : Type.NONE;
        }

        public abstract void update();
        public abstract boolean hasUpdateNeeded();
        @Nonnull public Type getUpdateType() { return type; }
        
        public void delayUpdate(int ticks) { Bukkit.getScheduler().scheduleSyncDelayedTask(npc.getPlugin(), () -> update(), ticks); }

        protected static class InGeneral<N extends NPC> extends Updatable<N>{

            private final Consumer<N> action;

            private InGeneral(@Nullable N npc, @Nullable Type type, @Nullable Consumer<N> action){
                super(npc, type);
                this.action = action;
                if(npc != null) npc.pendingUpdates.addPendingUpdate(this);
            }

            @Deprecated private InGeneral() { this(null, null, null); }

            private InGeneral(@Nonnull N npc, @Nonnull Type type){ this(npc, type, (Consumer<N>) type.generalAction); }

            private InGeneral(@Nonnull N npc, @Nonnull Consumer<N> action){ this(npc, null, action); }

            @Override
            public void update() {
                if(!hasUpdateNeeded() || super.npc == null) return;
                Bukkit.getScheduler().scheduleSyncDelayedTask(PlayerNPCPlugin.getInstance(), () -> action.accept(super.npc));
            }

            @Override public boolean hasUpdateNeeded() { return action != null; }

        }

        protected static class PerPlayer extends Updatable<NPC.Global>{

            private final Player player;
            private final BiConsumer<NPC.Global, Player> action;

            private PerPlayer(@Nullable NPC.Global global, @Nullable NPC.Updatable.Type type, @Nullable BiConsumer<NPC.Global, Player> action, @Nullable Player player) {
                super(global, type);
                this.player = player;
                this.action = action;
                if(global != null && global.hasPlayer(player)) global.getPersonal(player).globalPendingUpdates.addPendingUpdate(this);
            }

            private PerPlayer(@Nonnull NPC.Global global, @Nonnull NPC.Updatable.Type type, @Nonnull Player player) { this(global, type, type.playerAction, player); }

            private PerPlayer(@Nonnull NPC.Global global, @Nonnull BiConsumer<NPC.Global, Player> action, @Nonnull Player player) { this(global, null, action, player); }

            @Deprecated private PerPlayer() { this(null, null, null, null); }

            @Override
            public void update(){
                if(!hasUpdateNeeded() || super.npc == null || player == null) return;
                Bukkit.getScheduler().scheduleSyncDelayedTask(PlayerNPCPlugin.getInstance(), () -> this.action.accept(super.npc, player));
            }

            @Override public boolean hasUpdateNeeded() { return action != null; }
        }

        public enum Type{
            NONE(null, null),
            SIMPLE_UPDATE(n-> n.simpleUpdate(),(g, p) -> g.simpleUpdate(p)),
            FORCE_UPDATE(n-> n.forceUpdate(),(g,p) -> g.forceUpdate(p)),
            SIMPLE_UPDATE_TEXT(n-> n.simpleUpdateText(),(g, p) -> g.simpleUpdateText(p)),
            FORCE_UPDATE_TEXT(n-> n.forceUpdateText(),(g,p) -> g.forceUpdateText(p)),
            SYNCHRONIZE_GLOBAL_ATTRIBUTES(null, (g, p) -> g.synchronizeGlobalAttributes(p)),
            ;

            private Consumer<NPC> generalAction;
            private BiConsumer<NPC.Global, Player> playerAction;

            Type(Consumer<NPC> generalAction, BiConsumer<NPC.Global, Player> playerAction) {
                this.generalAction = generalAction;
                this.playerAction = playerAction;
            }

            public void updatePlayer(NPC.Global global, Player player) { this.playerAction.accept(global, player); }

            public void update(NPC npc) { this.generalAction.accept(npc); }

        }

        protected static class PendingUpdates<N extends NPC>{

            private final N npc;
            private final List<Updatable> pending;

            private PendingUpdates(N npc){
                this.npc = npc;
                this.pending = new ArrayList<>();
            }

            private void addPendingUpdate(Updatable updatable){
                Validate.isTrue(updatable.npc.equals(npc), "Cannot add this update because it's not the same NPC");
                Type type = updatable.getUpdateType();
                if(type == null){
                    pending.add(updatable);
                    return;
                }
                if(containsType(type)) return;
                switch(type){
                    case SYNCHRONIZE_GLOBAL_ATTRIBUTES -> {
                        if(containsType(Type.SIMPLE_UPDATE) || containsType(Type.FORCE_UPDATE) || containsType(Type.SIMPLE_UPDATE_TEXT) || containsType(Type.FORCE_UPDATE_TEXT)) return;
                    }
                    case SIMPLE_UPDATE, FORCE_UPDATE_TEXT -> {
                        if(containsType(Type.FORCE_UPDATE)) return;
                        checkAndRemove(Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES, Type.SIMPLE_UPDATE_TEXT);
                    }
                    case SIMPLE_UPDATE_TEXT -> {
                        if(containsType(Type.FORCE_UPDATE_TEXT) || containsType(Type.FORCE_UPDATE)) return;
                        checkAndRemove(Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES);
                    }
                    case FORCE_UPDATE -> checkAndRemove(Type.values());
                }
                pending.add(updatable);
            }

            private boolean containsType(Type type){ return getContainsType(type) != null; }
            @Nullable private Updatable getContainsType(Type type) { return pending.stream().filter(x-> x.getUpdateType() != null && x.getUpdateType().equals(type)).findFirst().orElse(null); }

            private void checkAndRemove(Type... types) { Arrays.stream(types).forEach(this::checkAndRemove); }
            private void checkAndRemove(Type type) { if(containsType(type)) pending.remove(getContainsType(type)); }

            protected boolean hasPending() { return !pending.isEmpty(); }

            private void clear(){ pending.clear(); }

            protected void update() {
                if(pending.isEmpty()) return;
                pending.forEach(x-> x.update());
                clear();
            }

            protected void onExecute(Type type){
                checkAndRemove(type);
                if(pending.isEmpty()) return;
                switch(type){
                    case SIMPLE_UPDATE, FORCE_UPDATE_TEXT -> checkAndRemove(Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES, Type.SIMPLE_UPDATE_TEXT);
                    case SIMPLE_UPDATE_TEXT -> checkAndRemove(Type.SYNCHRONIZE_GLOBAL_ATTRIBUTES);
                    case FORCE_UPDATE -> checkAndRemove(Type.values());
                }
            }
        }
    }

    public static class NameTag implements Cloneable{

        @Nullable private String prefix;
        @Nonnull private String name;
        @Nullable private String suffix;

        public NameTag(@Nullable String prefix, @Nullable String name, @Nullable String suffix){
            setPrefix(prefix);
            setName(name);
            setSuffix(suffix);
        }

        public NameTag(@Nullable String name){ this(null, name, null); }

        public Updatable.Type setPrefix(@Nullable String prefix) {
            if(this.prefix != null && this.prefix.equals(prefix)) return Updatable.Type.NONE;
            this.prefix = prefix;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public Updatable.Type setName(@Nullable String name) {
            if(this.name != null && this.name.equals(name)) return Updatable.Type.NONE;
            this.name = name != null ? name : "{id}";
            return Updatable.Type.FORCE_UPDATE;
        }

        public Updatable.Type setSuffix(@Nullable String suffix) {
            if(this.suffix != null && this.suffix.equals(suffix)) return Updatable.Type.NONE;
            this.suffix = suffix;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        @Nullable public String getPrefix() { return prefix; }

        @Nonnull public String getName() { return name; }

        @Nullable public String getSuffix() { return suffix; }

        @Override public String toString() { return (prefix != null ? prefix + "§r" : "") + name +  (suffix != null ? "§r" + suffix : ""); }

        @Override protected NameTag clone() { return new NameTag(prefix, name, suffix); }
    }

    /**
     * Set the follow look type to the NPC with {@link NPC#setGazeTrackingType(GazeTrackingType)}
     * @see NPC#setGazeTrackingType(GazeTrackingType)
     * @see NPC#getGazeTrackingType()
     * @since 2021.1
     */
    public enum GazeTrackingType implements EnumUtils.GetName{
        /** The NPC will not move the look direction automatically. */
        NONE,
        /** The NPC will move the look direction automatically to the player that see the NPC.
         * That means that each player will see the NPC looking himself. */
        PLAYER,
        /** The NPC will move the look direction automatically to the nearest player to the NPC location.
         * That means that a player can see his NPC looking to another player if it's nearer than he.*/
        NEAREST_PLAYER,
        /** The NPC will move the look direction automatically to the nearest entity to the NPC location. */
        NEAREST_ENTITY,
        ;

        @Nullable public static GazeTrackingType fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }

    }

    public enum TabListVisibility implements EnumUtils.GetName{
        ALWAYS,
        SAME_WORLD,
        NEAR,
        NEVER;

        @Nullable public static TabListVisibility fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }

    }

    /** Set the NPCPose of the NPC with {@link NPC#setPose(Pose)}
     * <p> After setting the NPCPose you will need to {@link NPC#simpleUpdate()}
     * @since 2021.2
     * @see NPC#getPose() 
     * @see NPC#setPose(Pose)
     * */
    public enum Pose implements EnumUtils.GetName {
        /** The NPC will be standing on the ground.
         * @see NPC#setPose(Pose)
         * @see NPC#resetPose()
         * @since 2021.2
         **/
        STANDING(EntityPose.a, "STANDING"),
        /**
         * The NPC will be gliding.
         * @since 2022.1
         * */
        GLIDING(EntityPose.b, "FALL_FLYING"),
        /** The NPC will be lying on the ground, looking up, with the arms next to the body.
         * @see NPC#setPose(Pose)
         * @see NPC#setSleeping(boolean)
         * @since 2021.2
         * */
        SLEEPING(EntityPose.c, "SLEEPING"),
        /** The NPC will be lying on the ground, looking down, with the arms separated from the body. 
         * @see NPC#setPose(Pose)
         * @see NPC#setSwimming(boolean)
         * @since 2021.2
         * */
        SWIMMING(EntityPose.d, "SWIMMING"),
        /**
         * Entity is riptiding with a trident.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         */
        SPIN_ATTACK(EntityPose.e, "SPIN_ATTACK"),
        /** The NPC will be standing on the ground, but crouching (sneaking).
         * @see NPC#setPose(Pose)
         * @see NPC#setCrouching(boolean)
         * @since 2021.2
         * */
        CROUCHING(EntityPose.f, ServerVersion.getServerVersion().isOlderThanOrEqual(ServerVersion.VERSION_1_18_2) ? "SNEAKING" : "CROAKING"),
        /**
         * Entity is long jumping.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         * */
        @Deprecated LONG_JUMPING(EntityPose.g, "LONG_JUMPING"),
        /**
         * Entity is dead.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         * */
        @Deprecated DYING(EntityPose.h, "DYING"),
        ;

        private EntityPose minecraftEntityPose;
        private org.bukkit.entity.Pose bukkitPose;

        Pose(EntityPose minecraftEntityPose, String bukkitPoseName){
            this.minecraftEntityPose = minecraftEntityPose;
            this.bukkitPose = EnumUtils.getEnumConstant(org.bukkit.entity.Pose.class, bukkitPoseName);
        }

        protected EntityPose asMinecraftEntityPose() { return minecraftEntityPose; }
        public org.bukkit.entity.Pose asBukkitPose() { return bukkitPose; }

        public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }

        public static Optional<Pose> fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst(); }
        public static Optional<Pose> of(org.bukkit.entity.Pose bukkitPose){ return Arrays.stream(values()).filter(x-> x.bukkitPose.equals(bukkitPose)).findFirst(); }
        protected static Optional<Pose> of(EntityPose minecraftEntityPose){ return Arrays.stream(values()).filter(x-> x.minecraftEntityPose.equals(minecraftEntityPose)).findFirst(); }

    }

    /**
     *
     * @since 2021.1
     */
    public enum Slot implements EnumUtils.GetName {

        MAINHAND("MAINHAND", null, EquipmentSlot.HAND),
        FEET("FEET", "BOOTS", EquipmentSlot.FEET),
        LEGS("LEGS", "LEGGINGS", EquipmentSlot.LEGS),
        CHEST("CHEST", "CHESTPLATE", EquipmentSlot.CHEST),
        HEAD("HEAD", "HELMET", EquipmentSlot.HEAD),
        OFFHAND("OFFHAND", null, EquipmentSlot.OFF_HAND),
        ;

        private final String slotName;
        private final String itemCategoryName;
        private final EnumItemSlot minecraftEnumItemSlot;
        private final EquipmentSlot bukkitEquipmentSlot;

        Slot(String slotName, String itemCategoryName, EquipmentSlot bukkitEquipmentSlot) {
            this.slotName = slotName;
            this.itemCategoryName = itemCategoryName;
            this.bukkitEquipmentSlot = bukkitEquipmentSlot;
            this.minecraftEnumItemSlot = EnumUtils.getEnumConstant(EnumItemSlot.class, this.slotName);
        }

        @Nonnull protected EnumItemSlot asMinecraftEnumItemSlot() { return EnumUtils.getEnumConstant(EnumItemSlot.class, this.slotName); }
        @Nonnull public EquipmentSlot asBukkitEquipmentSlot() { return bukkitEquipmentSlot; }

        public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }

        public static Optional<Slot> fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name) || x.slotName.equalsIgnoreCase(name)).findFirst(); }
        protected static Optional<Slot> of(EnumItemSlot minecraftEnumItemSlot){ return Arrays.stream(values()).filter(x-> x.minecraftEnumItemSlot.equals(minecraftEnumItemSlot)).findFirst(); }
        public static Optional<Slot> of(EquipmentSlot bukkitEquipmentSlot){ return Arrays.stream(values()).filter(x-> x.bukkitEquipmentSlot.equals(bukkitEquipmentSlot)).findFirst(); }
        @Nullable public static Slot of(Material material){
            if(material == null || material.isAir()) return null;
            if(material.equals(Material.SHIELD)) return OFFHAND;
            if(material.equals(Material.ELYTRA)) return CHEST;
            return Arrays.stream(values()).filter(x-> x.itemCategoryName != null).filter(x-> material.name().endsWith("_" + x.itemCategoryName)).findFirst().orElse(MAINHAND);
        }
    }

    public enum PotionParticlesType{

        DISABLED(false), NORMAL(false), AMBIENT(true);

        protected final Boolean metadata;

        PotionParticlesType(Boolean metadata) { this.metadata = metadata; }

        @Nullable public static PotionParticlesType fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }

    }

    /**
     * @since 2022.2
     */
    public enum Color implements EnumUtils.GetName{

        BLACK(ChatColor.BLACK,org.bukkit.Color.BLACK, DyeColor.BLACK),
        DARK_BLUE(ChatColor.DARK_BLUE,org.bukkit.Color.BLUE, DyeColor.BLUE),
        DARK_GREEN(ChatColor.DARK_GREEN,org.bukkit.Color.GREEN, DyeColor.GREEN),
        DARK_AQUA(ChatColor.DARK_AQUA,org.bukkit.Color.TEAL, DyeColor.CYAN),
        DARK_RED(ChatColor.DARK_RED,org.bukkit.Color.MAROON, DyeColor.RED),
        DARK_PURPLE(ChatColor.DARK_PURPLE,org.bukkit.Color.PURPLE, DyeColor.PURPLE),
        GOLD(ChatColor.GOLD,org.bukkit.Color.ORANGE, DyeColor.ORANGE),
        GRAY(ChatColor.GRAY,org.bukkit.Color.SILVER, DyeColor.LIGHT_GRAY),
        DARK_GRAY(ChatColor.DARK_GRAY,org.bukkit.Color.GRAY, DyeColor.GRAY),
        BLUE(ChatColor.BLUE,org.bukkit.Color.NAVY, DyeColor.BLUE),
        GREEN(ChatColor.GREEN,org.bukkit.Color.LIME, DyeColor.LIME),
        AQUA(ChatColor.AQUA,org.bukkit.Color.AQUA, DyeColor.LIGHT_BLUE),
        RED(ChatColor.RED,org.bukkit.Color.RED, DyeColor.RED),
        LIGHT_PURPLE(ChatColor.LIGHT_PURPLE,org.bukkit.Color.FUCHSIA, DyeColor.MAGENTA),
        YELLOW(ChatColor.YELLOW,org.bukkit.Color.YELLOW, DyeColor.YELLOW),
        WHITE(ChatColor.WHITE,org.bukkit.Color.WHITE, DyeColor.WHITE),
        ;

        private ChatColor bukkitChatColor;
        private net.md_5.bungee.api.ChatColor bungeeChatColor;
        private EnumChatFormat minecraftEnumChatFormat;
        private java.awt.Color javaColor;
        private org.bukkit.Color bukkitColor;
        private org.bukkit.DyeColor bukkitDyeColor;

        Color(ChatColor bukkitChatColor, org.bukkit.Color bukkitColor, org.bukkit.DyeColor bukkitDyeColor){
            this.bukkitChatColor = bukkitChatColor;
            this.bungeeChatColor = bukkitChatColor.asBungee();
            this.minecraftEnumChatFormat = EnumUtils.getEnumConstant(EnumChatFormat.class, this.name());
            this.javaColor = bungeeChatColor.getColor();
            this.bukkitColor = bukkitColor;
            this.bukkitDyeColor = bukkitDyeColor;
        }

        @Nonnull @Override public String getName(EnumUtils.NameFormat nameFormat) { return getName(nameFormat, false); }
        @Nonnull public String getName(EnumUtils.NameFormat nameFormat, boolean colored) { return (colored ? bukkitChatColor.toString() : "")  + nameFormat.format(this); }

        @Nonnull public ChatColor asBukkitChatColor(){ return bukkitChatColor; }
        @Nonnull public net.md_5.bungee.api.ChatColor asBungeeChatColor() { return bungeeChatColor; }
        @Nonnull public java.awt.Color asJavaColor() { return javaColor; }
        @Nonnull public org.bukkit.Color asBukkitColor() { return bukkitColor; }
        @Nonnull public org.bukkit.DyeColor asBukkitDyeColor() { return bukkitDyeColor; }
        @Nonnull protected EnumChatFormat asMinecraftEnumChatFormat(){ return minecraftEnumChatFormat; }
        @Nonnull public Material getBukkitMaterial(@Nonnull MaterialType type){ return Material.valueOf(bukkitDyeColor.name() + "_" + type.name()); }

        public static Optional<Color> fromName(@Nonnull String name) { return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull ChatColor color) { return Arrays.stream(values()).filter(x-> x.bukkitChatColor.equals(color)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull net.md_5.bungee.api.ChatColor color) { return Arrays.stream(values()).filter(x-> x.bungeeChatColor.equals(color)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull java.awt.Color color) { return Arrays.stream(values()).filter(x-> x.javaColor.equals(color)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull org.bukkit.Color color) { return Arrays.stream(values()).filter(x-> x.bukkitColor.equals(color)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull org.bukkit.DyeColor color) { return Arrays.stream(values()).filter(x-> x.bukkitDyeColor.equals(color)).findFirst(); }
        public static Optional<NPC.Color> of(@Nonnull Material material) {
            if(material == null || material.isAir()) return Optional.empty();
            Optional<MaterialType> materialType = Arrays.stream(MaterialType.values()).filter(x-> material.name().endsWith("_" + x.name())).findFirst();
            if(materialType.isEmpty()) return Optional.empty();
            return Arrays.stream(values()).filter(x-> x.getBukkitMaterial(materialType.get()).equals(material)).findFirst();
        }

        protected static Optional<NPC.Color> of(@Nonnull EnumChatFormat color) { return Arrays.stream(values()).filter(x-> x.minecraftEnumChatFormat.equals(color)).findFirst(); }

        public enum MaterialType { BANNER, BED, CANDLE, CARPET, CANDLE_CAKE, CONCRETE, CONCRETE_POWDER, DYE, GLAZED_TERRACOTTA, SHULKER_BOX, STAINED_GLASS, STAINED_GLASS_PANE, TERRACOTTA, WALL_BANNER, WOOL }
    }

    /**
     * @since 2022.2
     */
    public enum Animation implements EnumUtils.GetName{
        SWING_MAIN_ARM(0),
        TAKE_DAMAGE(1),
        @Deprecated LEAVE_BED(2),
        SWING_OFF_HAND(3),
        CRITICAL_EFFECT(4),
        MAGICAL_CRITICAL_EFFECT(5),
        ;

        private final int id;

        Animation(int id){ this.id = id; }

        public int getId(){ return id; }

        protected PacketPlayOutAnimation createPacket(net.minecraft.world.entity.Entity minecraftEntity){ return createPacket(minecraftEntity, this); }

        protected static PacketPlayOutAnimation createPacket(net.minecraft.world.entity.Entity minecraftEntity, Animation animation) { return new PacketPlayOutAnimation(minecraftEntity, animation.getId()); }

        public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }

        public static Optional<Animation> fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst(); }
        public static Optional<Animation> fromID(int id){ return Arrays.stream(values()).filter(x-> x.getId() == id).findFirst(); }

    }

    public static abstract class Skin {

        protected static List<String> SUGGESTED_SKIN_WEBSITES;

        static{
            SUGGESTED_SKIN_WEBSITES = Arrays.asList(
                    "https://mineskin.org/",
                    "https://www.minecraftskins.com/",
                    "https://namemc.com/skin/"
            );
        }

        protected final Skin.Type type;
        protected Textures textures;
        protected String lastUpdate;
        protected Avatar avatar;

        private Skin(@Nonnull Type type, @Nonnull String texture, @Nonnull String signature){
            Validate.notNull(type); this.type = type;
            this.textures = new Textures(texture, signature);
            this.avatar = null;
            resetLastUpdate();
        }

        public abstract String getName();

        protected abstract void save() throws IOException;

        protected abstract void delete();

        protected abstract File getAvatarFile();

        protected abstract File getTextureFile();

        protected abstract File getDataFile();

        protected abstract String getSkinFolderPath();

        protected boolean needsToUpdate(Plugin plugin){ return needsToUpdate(this.lastUpdate, plugin); }

        protected String resetLastUpdate(){ return this.lastUpdate = TimerUtils.getCurrentDate(); }

        public Integer getTimeFromLastUpdate(TimeUnit timeUnit){ return getTimeFromLastUpdate(getLastUpdate(), timeUnit); }

        protected static Integer getTimeFromLastUpdate(String lastUpdate, TimeUnit timeUnit) { return TimerUtils.getBetweenDatesString(lastUpdate, TimerUtils.getCurrentDate(), TimerUtils.DATE_FORMAT_LARGE, timeUnit); }

        protected static boolean needsToUpdate(String lastUpdate, Plugin plugin){
            NPCLib.SkinUpdateFrequency frequency = NPCLib.getInstance().getPluginManager(plugin).getSkinUpdateFrequency();
            return getTimeFromLastUpdate(lastUpdate, frequency.timeUnit()) > frequency.value();
        }

        public boolean isLoadedAvatar() { return this.avatar != null; }

        public CompletableFuture<Avatar> getAvatar() {
            return CompletableFuture.supplyAsync(() -> {
                try{
                    if(avatar != null) return avatar;
                    BufferedImage bufferedImage;
                    if(getSkinFolderPath() != null){
                        if(!getAvatarFile().exists()) downloadImages().join();
                        bufferedImage = ImageIO.read(getAvatarFile());
                    }
                    else bufferedImage = downloadTextureImage();
                    if(bufferedImage == null) return Avatar.UNKNOWN_AVATAR;
                    this.avatar = new Avatar(bufferedImage);
                    return this.avatar;
                }
                catch (Exception e) { NPCLib.printError(e); return Avatar.UNKNOWN_AVATAR; }
            }).completeOnTimeout(Avatar.UNKNOWN_AVATAR, 5, TimeUnit.SECONDS);
        }

        protected CompletableFuture<BufferedImage[]> downloadImages(){
            return CompletableFuture.supplyAsync(() -> {
                if(getSkinFolderPath() == null) return null;
                if(getTextureFile().exists() && getAvatarFile().exists()) return null;
                try{
                    BufferedImage bufferedImage = downloadTextureImage();
                    getTextureFile().mkdirs();
                    ImageIO.write(bufferedImage, "png", getTextureFile());
                    BufferedImage avatarResult = Avatar.cropHeadTexture(bufferedImage);
                    getAvatarFile().mkdirs();
                    ImageIO.write(avatarResult, "png", getAvatarFile());
                    return new BufferedImage[]{bufferedImage, avatarResult};
                }
                catch (Exception e){ NPCLib.printError(e); return null; }
            });
        }

        private BufferedImage downloadTextureImage() throws IOException {
            Validate.notNull(textures.getSkinUrl(), "Cannot download images because texture url is null");
            return ImageIO.read(new URL(textures.getSkinUrl()));
        }

        public void applyNPC(NPC npc){
            applyNPC(npc, false);
        }

        public void applyNPC(NPC npc, boolean forceUpdate){
            npc.setSkin(this);
            if(forceUpdate) npc.forceUpdate();
        }

        @Nullable
        public net.md_5.bungee.api.ChatColor getMostCommonColor() {
            if(avatar == null) return null;
            return avatar.getMostCommonColor();
        }

        public void applyNPCs(Collection<NPC> npcs){
            applyNPCs(npcs, false);
        }

        public void applyNPCs(Collection<NPC> npcs, boolean forceUpdate){
            npcs.forEach(x-> applyNPC(x, forceUpdate));
        }

        public void applyToPlayer(Player player) { applyToPlayer(player, null); }

        public void applyToPlayer(Player player, Consumer<Player> finishAction){
            EntityPlayer entityPlayer = NMSCraftPlayer.getEntityPlayer(player);
            GameProfile gameProfile = NMSEntityPlayer.getGameProfile(entityPlayer);
            Property textures = gameProfile.getProperties().get("textures").stream().filter(x-> x.getName().equals("textures")).findFirst().orElse(null);
            if(textures != null && textures.getValue().equals(getTexture())) return;
            Bukkit.getOnlinePlayers().forEach(x-> NMSPacketPlayOutPlayerInfo.removePlayer(x, entityPlayer));
            gameProfile.getProperties().get("textures").clear();
            gameProfile.getProperties().put("textures", generateTexturesProperty());
            //
            Location actual = player.getLocation();
            actual.setYaw(player.getLocation().getYaw());
            actual.setPitch(player.getLocation().getPitch());
            boolean isFlying = player.isFlying();
            int heldSlot = player.getInventory().getHeldItemSlot();
            //
            long seed = player.getWorld().getSeed();
            net.minecraft.world.level.World world = NMSEntity.getWorld(entityPlayer);
            Bukkit.getScheduler().scheduleSyncDelayedTask(PlayerNPCPlugin.getInstance(), () -> {
                NMSPacketPlayOutRespawn.respawn(player);
                if(ServerVersion.getServerVersion().isNewerThanOrEqual(ServerVersion.VERSION_1_19_3))
                    NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityMetadata.getPacket(NMSCraftPlayer.getEntityPlayer(player)));
                //
                player.teleport(actual);
                if(isFlying) player.setFlying(true);
                player.updateInventory();
                player.getInventory().setHeldItemSlot(heldSlot);
                //
                Bukkit.getOnlinePlayers().forEach(x-> NMSPacketPlayOutPlayerInfo.addPlayer(x, entityPlayer, true));
                if(finishAction != null) finishAction.accept(player);
                Bukkit.getOnlinePlayers().stream().filter(x-> !x.equals(player)).forEach(x-> {
                    if(x.canSee(player)){
                        x.hidePlayer(PlayerNPCPlugin.getInstance(), player);
                        x.showPlayer(PlayerNPCPlugin.getInstance(), player);
                    }
                });
            }, 2L);
        }

        public Property generateTexturesProperty() { return generateTexturesProperty(true); }

        public Property generateTexturesProperty(boolean signed) { return new Property("textures", getTexture(), signed ? getSignature() : null); }

        public String getTexture() { return textures.getEncodedTexture(); }

        public String getSignature() { return textures.getSignature(); }

        public String getLastUpdate() { return lastUpdate; }

        @Nullable public Cape getCape() { return textures.getCape(); }

        public boolean hasCape() { return textures.getCape() != null; }

        public Type getType(){ return type; }

        public Model getModel() { return textures.getModel(); }

        public abstract String getInformation();

        public boolean hasSameSignedTextureAs(@Nullable NPC.Skin skin) { return skin == null ? false : getSignedTexture().equals(skin.getSignedTexture()); }

        public boolean hasSameSignedTextureAs(@Nullable SignedTexture signedTexture) { return signedTexture == null ? false : getTextureData().getSignedTexture().equals(signedTexture); }

        public boolean hasSameTextureAs(@Nullable NPC.Skin skin){ return skin == null ? false : textures.getSkinId().equals(skin.textures.getSkinId()); }

        public boolean hasSameCapeAs(@Nullable NPC.Skin skin){ return skin == null ? false :  textures.getCapeId().equals(skin.textures.getCapeId()); }

        public boolean hasSameTextureAndCapeAs(@Nullable NPC.Skin skin) { return skin == null ? false : (hasSameTextureAs(skin) && hasSameCapeAs(skin)); }

        public boolean isSameTypeAs(@Nullable NPC.Skin skin) {  return skin == null ? false : type.equals(skin.getType()); }

        @Nullable public Minecraft castMinecraftSkin(){ return type.equals(Type.MINECRAFT) ? (Minecraft) this : null; }

        @Nullable public Custom castCustomSkin(){ return type.equals(Type.CUSTOM) ? (Custom) this : null; }

        @Nullable public MineSkin castMineSkin(){ return type.equals(Type.MINESKIN) ? (MineSkin) this : null; }

        public enum Type implements EnumUtils.GetName {
            MINECRAFT, CUSTOM, MINESKIN;

            @Nullable public static Type fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
        }

        public Textures getTextureData() { return textures; }

        public SignedTexture getSignedTexture() { return textures.getSignedTexture(); }

        private static String getSkinsFolderPath() { return "plugins/PlayerNPC/skins/"; }
        private static String getSkinsFolderPath(Type type){ return getSkinsFolderPath() + type.name().toLowerCase() + "/"; }

        public static List<String> getSuggestedSkinWebsites(){ return SUGGESTED_SKIN_WEBSITES; }

        public static class Avatar{

            private static Avatar UNKNOWN_AVATAR;

            static{
                net.md_5.bungee.api.ChatColor r = net.md_5.bungee.api.ChatColor.of(ColorUtils.getColorFromRGB(new int[]{217, 13, 13}));
                net.md_5.bungee.api.ChatColor b = net.md_5.bungee.api.ChatColor.of(ColorUtils.getColorFromRGB(new int[]{40, 40, 40}));
                net.md_5.bungee.api.ChatColor[][] pixels = new net.md_5.bungee.api.ChatColor[8][8];
                pixels[0] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, b, b, b, b, b };
                pixels[1] = new net.md_5.bungee.api.ChatColor[]{ b, b, r, r, r, r, b, b };
                pixels[2] = new net.md_5.bungee.api.ChatColor[]{ b, r, r, b, b, r, r, b };
                pixels[3] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, b, r, r, b, b };
                pixels[4] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, r, r, b, b, b };
                pixels[5] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, b, b, b, b, b };
                pixels[6] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, r, r, b, b, b };
                pixels[7] = new net.md_5.bungee.api.ChatColor[]{ b, b, b, b, b, b, b, b };
                UNKNOWN_AVATAR = new Avatar(pixels, r);
            }

            public static Avatar getUnknownAvatar() { return UNKNOWN_AVATAR; }

            public static BufferedImage cropHeadTexture(BufferedImage skinTexture) { return cropHeadTexture(skinTexture, true); }

            public static BufferedImage cropHeadTexture(BufferedImage skinTexture, boolean overlayHead){
                BufferedImage avatarResult = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                BufferedImage bufferedImage2 = skinTexture.getSubimage(8, 8, 8, 8); // Base Head
                avatarResult.getGraphics().drawImage(bufferedImage2, 0, 0, null);
                if(!overlayHead) return avatarResult;
                BufferedImage bufferedImage3 = skinTexture.getSubimage(40, 8, 8, 8); // Overlay Head
                avatarResult.getGraphics().drawImage(bufferedImage3, 0, 0, null);
                return avatarResult;
            }

            protected BufferedImage image;
            protected final net.md_5.bungee.api.ChatColor[][] pixels;
            protected final net.md_5.bungee.api.ChatColor mostCommonColor;

            protected Avatar(@Nonnull BufferedImage bufferedImage){
                Validate.notNull(bufferedImage, "Image cannot be null");
                if(bufferedImage.getHeight() == 64 && bufferedImage.getWidth() == 64) bufferedImage = cropHeadTexture(bufferedImage);
                Validate.isTrue(bufferedImage.getHeight() == 8 && bufferedImage.getWidth() == 8, "This skin avatar is not 8x8 pixels");
                this.image = bufferedImage;
                this.pixels = new net.md_5.bungee.api.ChatColor[8][8];
                Map m = new HashMap();
                height: for(int y = 0; y < 8; y++) {
                    width: for (int x = 0; x < 8; x++) {
                        int color = bufferedImage.getRGB(x, y);
                        int[] rgb = ColorUtils.getRGB(color);
                        pixels[y][x] = net.md_5.bungee.api.ChatColor.of(ColorUtils.getColorFromRGB(rgb));
                        if(ColorUtils.isGray(rgb)) continue width;
                        if(m.containsKey(color)) m.put(color, (int) m.get(color) + 1);
                        else m.put(color, 1);
                    }
                }
                java.awt.Color mostCommon = ColorUtils.getMostCommonColour(m);
                this.mostCommonColor = mostCommon != null ? net.md_5.bungee.api.ChatColor.of(mostCommon) : null;
            }

            protected Avatar(URL url) throws IOException { this(ImageIO.read(url)); }

            protected Avatar(File file) throws IOException { this(ImageIO.read(file)); }

            protected Avatar(net.md_5.bungee.api.ChatColor[][] pixels, net.md_5.bungee.api.ChatColor mostCommonColor){
                this.pixels = pixels;
                this.mostCommonColor = mostCommonColor;
                this.image = null;
            }

            @Nullable public BufferedImage getImage() { return image; }

            @Nonnull public net.md_5.bungee.api.ChatColor getPixel(int x, int y) { return pixels[y][x]; }

            @Nullable public net.md_5.bungee.api.ChatColor getMostCommonColor() { return mostCommonColor; }

        }

        public record SignedTexture(String texture, String signature){
            public boolean equals(SignedTexture other) { return this.texture.equals(other.texture) && this.signature.equals(other.signature); }
        }

        public static class Textures{

            protected SignedTexture signedTexture;
            protected Cape cape;
            protected Model model;

            protected long timestamp;
            protected String profileId;
            protected String profileName;
            protected boolean signatureRequired;

            protected String skinUrl;
            protected String skinMetadataModel;

            protected String capeUrl;

            protected Textures(@Nonnull String encodedTexture, @Nonnull String signature){
                Validate.notNull(encodedTexture, "Texture value cannot be null.");
                Validate.notNull(signature, "Signature cannot be null.");
                Validate.isTrue(signature.length() == 684, "This signature is not valid");
                this.signedTexture = new SignedTexture(encodedTexture, signature);
                byte[] decodedBytes = Base64.getDecoder().decode(encodedTexture);
                String decodedTexture = new String(decodedBytes);
                JsonObject textureJSON = new JsonParser().parse(decodedTexture).getAsJsonObject();
                if(textureJSON.has("timestamp")) timestamp = textureJSON.get("timestamp").getAsLong();
                if(textureJSON.has("profileId")) profileId = textureJSON.get("profileId").getAsString();
                if(textureJSON.has("profileName")) profileName = textureJSON.get("profileName").getAsString();
                if(textureJSON.has("signatureRequired")) signatureRequired = textureJSON.get("signatureRequired").getAsBoolean();
                if(textureJSON.has("textures")){
                    JsonObject textureElement = textureJSON.get("textures").getAsJsonObject();
                    if(textureElement.has("SKIN")){
                        JsonObject skinElement = textureElement.getAsJsonObject("SKIN");
                        if(skinElement.has("url")) skinUrl = skinElement.get("url").getAsString();
                        if(skinElement.has("metadata") && skinElement.get("metadata").getAsJsonObject().has("model")) skinMetadataModel = skinElement.get("metadata").getAsJsonObject().get("model").getAsString();
                        else skinMetadataModel = "classic";
                    }
                    if(textureElement.has("CAPE")){
                        JsonObject capeElement = textureElement.getAsJsonObject("CAPE");
                        if(capeElement.has("url")) capeUrl = capeElement.get("url").getAsString();
                    }
                }
                Validate.notNull(skinUrl, "This texture is not valid");
                this.model = Model.valueOf(skinMetadataModel.toUpperCase());
                if(capeUrl != null) this.cape = Arrays.stream(Cape.values()).filter(x-> x.getTextureID().equals(getCapeId())).findAny().orElse(null);
            }

            public String getEncodedTexture() { return signedTexture.texture(); }

            public String getSignature() { return signedTexture.signature(); }

            public SignedTexture getSignedTexture() { return signedTexture; }

            public long getTimestamp() { return timestamp; }

            public String getProfileId() { return profileId; }

            public String getProfileName() { return profileName; }

            public boolean isSignatureRequired() { return signatureRequired; }

            public String getSkinUrl() { return skinUrl; }

            public String getSkinId() { return skinUrl.replaceFirst("http://textures.minecraft.net/texture/", ""); }

            public String getSkinMetadataModel() { return skinMetadataModel; }

            public String getCapeUrl() { return capeUrl; }

            public String getCapeId() { return capeUrl.replaceFirst("http://textures.minecraft.net/texture/", ""); }

            public boolean hasCape() { return capeUrl != null; }

            public Cape getCape() { return cape; }

            public Model getModel() { return model; }

        }

        public abstract static class FetchResult<S extends Skin, E extends FetchResult.Error>{

            private Long started;
            private final Long finished = System.currentTimeMillis();

            private Optional<S> skin;
            private Optional<E> error;

            public Optional<S> grabSkin() { return skin; }
            public Optional<Long> grabResponseTime() { return started == null ? Optional.empty() : Optional.of(finished - started); }

            public Optional<E> grabError() { return error; }

            public boolean hasFound() { return skin != null; }
            public boolean hasError() { return !hasFound(); }

            protected interface Error{
                @Nonnull String getMessage();
                @Override String toString();
            }
        }

        public enum Model implements EnumUtils.GetName {

            CLASSIC("Classic",4,Variant.CLASSIC),
            SLIM("Slim",3, Variant.SLIM),
            ;

            private String name;
            private Variant variant;
            private int pixels;

            Model(String name, int pixels, Variant variant){
                this.pixels = pixels;
                this.name = name;
                this.variant = variant;
            }

            public String getName() { return name; }

            public int getPixels() {
                return pixels;
            }

            public Variant toMineSkinVariant() { return variant; }

            @Nullable public static Model fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }

        }

        public enum Cape {

            // https://laby.net/capes
            MIGRATOR("Migrator", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933", 0),
            VANILLA("Vanilla", "f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4", 0),
            MINECON_2011("MineCon 2011", "953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6", 1),
            MINECON_2012("MineCon 2012", "a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6", 1),
            MINECON_2013("MineCon 2013", "153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da", 1),
            MINECON_2015("MineCon 2015", "b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635", 1),
            MINECON_2016("MineCon 2016", "e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980", 1),
            REALMS_MAPMAKER("Realms Mapmaker", "17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56", 1),
            MOJANG("Mojang", "5786fe99be377dfb6858859f926c4dbc995751e91cee373468c5fbf4865e7151", 2),
            TRANSLATOR("Translator", "1bf91499701404e21bd46b0191d63239a4ef76ebde88d27e4d430ac211df681e", 2),
            MOJANG_STUDIOS("Mojang Studios", "9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7", 2),
            MOJIRA_MODERATOR("Mojira Moderator", "ae677f7d98ac70a533713518416df4452fe5700365c09cf45d0d156ea9396551", 2),
            COBALT("Cobalt", "ca35c56efe71ed290385f4ab5346a1826b546a54d519e6a3ff01efa01acce81", 2),
            MOJANG_CLASSIC("Mojang Classic", "8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761", 2),
            SCROLLS("Scrolls", "3efadf6510961830f9fcc077f19b4daf286d502b5f5aafbd807c7bbffcaca245", 2),
            TRANSLATOR_CHINESE("Translator (Chinese)", "2262fb1d24912209490586ecae98aca8500df3eff91f2a07da37ee524e7e3cb6", 2),
            TURTLE("Turtle", "5048ea61566353397247d2b7d946034de926b997d5e66c86483dfb1e031aee95", 2),
            TRANSLATOR_JAPANESE("Translator (Japanese)", "ca29f5dd9e94fb1748203b92e36b66fda80750c87ebc18d6eafdb0e28cc1d05f", 2),
            SPADE("Spade", "2e002d5e1758e79ba51d08d92a0f3a95119f2f435ae7704916507b6c565a7da8", 2),
            SNOWMAN("Snowman", "23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe", 2),
            MILLIONTH_CUSTOMER("Millionth Customer", "70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf", 2),
            DB("dB", "bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac", 2),
            PRISMARINE("Prismarine", "d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac", 2),
            @Deprecated VALENTINE("Valentine"),
            @Deprecated BIRTHDAY("Birthday"),
            ;

            private Integer rartiy;
            private String name;
            private String textureID;

            Cape(String name, String textureID, Integer rarity){
                this.rartiy = rarity;
                this.name = name;
                this.textureID = textureID;
            }

            @Deprecated
            Cape(String name){ this(name, null, null); }

            public String getName() { return "§" + (rartiy >= 2 ? "d" : (rartiy >= 1 ? "b" : "a")) + (rartiy >= 2 ? "✯ " : "") + name + (rartiy >= 1 ? " ✯" : ""); }

            public String getTextureID() { return textureID; }

            public String getTextureURL() { return "http://textures.minecraft.net/texture/" + textureID; }

        }

        public static class Minecraft extends Skin{

            protected static final Skin.Minecraft STEVE;
            protected static final Skin.Minecraft ALEX;
            //protected static final Skin.Minecraft ZURI;
            //protected static final Skin.Minecraft SUNNY;
            //protected static final Skin.Minecraft NOOR;
            //protected static final Skin.Minecraft MAKENA;
            //protected static final Skin.Minecraft KAI;
            //protected static final Skin.Minecraft EFE;
            //protected static final Skin.Minecraft ARI;

            protected static final HashMap<String, NPC.Skin.Minecraft> SKIN_CACHE;
            protected static final List<String> LOCAL_SKIN_NAMES;

            static{
                SKIN_CACHE = new HashMap<>();
                STEVE = new Skin.Minecraft(
                        "MHF_Steve",
                        "c06f89064c8a49119c29ea1dbd1aab82",
                        "ewogICJ0aW1lc3RhbXAiIDogMTY1NjUzMDcyOTgyNiwKICAicHJvZmlsZUlkIiA6ICJjMDZmODkwNjRjOGE0OTExOWMyOWVhMWRiZDFhYWI4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfU3RldmUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWE0YWY3MTg0NTVkNGFhYjUyOGU3YTYxZjg2ZmEyNWU2YTM2OWQxNzY4ZGNiMTNmN2RmMzE5YTcxM2ViODEwYiIKICAgIH0KICB9Cn0=",
                        "D5KDlE7KmMYeo+n0bY7kRjxdoZ8ondpgLC0tVcDW/wER9tRAWGlkaUyC4cUjkiYtMFANOxnPNz42iWg+gKAX/qE3lKoJpFw8LmgC587QpEDZTsIwzrIriDDiUc+RQ83VNzy9lkrzm+/llFhuPmONhWIeoVgXQYnJXFXOjTA3uiqHq6IJR4fZzD+0lSpr8jm0X1B+XAiAV7xbzMjg2woC3ur7+81Ub27MNGdAmI5eh50rqqjIHx+kRHJPPB3klbAdkTkcnF2rhDuP9jLtJbb17L+40yR8MH3G1AsRBg+N9MlGb4qF3fK9m2lDNxrGpVe+5fj4ffHnTJ680X9O8cnGxtHFyHm3I65iIhVgFY/DQQ6XSxLgPrmdyVOV98OATc7g2/fFpiI6aRzFrXvCLzXcBzmcayhv8BgG8yBlHdYmMZScjslLKjgWB9mgtOh5ZFFb3ZRkwPvdKUqCQHDPovo9K3LwyAtg9QwJ7u+HN03tpDWllXIjT3mKrtsfWMorNNQ5Bh1St0If4Dg00tpW/DUwNs+oua0PhN/DbFEe3aog2jVfzy3IAXqW2cqiZlnRSm55vMrr1CI45PgjP2LS1c9OYJJ3k+ov4IdvBpDTiG9PfsPWcwtqm8ujxy/TqIWfSajL/RP+TFDoN/F8j8HhHU8wwA9JXJekmvUExEOxPWwisLA=",
                        ObtainedFrom.MINECRAFT_ORIGINAL
                );
                SKIN_CACHE.put(STEVE.playerName.toLowerCase(), STEVE);
                //
                ALEX = new Skin.Minecraft(
                        "MHF_Alex",
                        "6ab4317889fd490597f60f67d9d76fd9",
                        "ewogICJ0aW1lc3RhbXAiIDogMTY1NjU3Nzg5MTQ2NywKICAicHJvZmlsZUlkIiA6ICI2YWI0MzE3ODg5ZmQ0OTA1OTdmNjBmNjdkOWQ3NmZkOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfQWxleCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84M2NlZTVjYTZhZmNkYjE3MTI4NWFhMDBlODA0OWMyOTdiMmRiZWJhMGVmYjhmZjk3MGE1Njc3YTFiNjQ0MDMyIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
                        "d2dYqFQpP2ZjeUROSm23WTdWhWnuaW68v8Biw4towx04vJMRls95W/gmIFGa2Tq171yXHlE8kpP2KFe3jAC7qukkXjDiXSRdCSOYZPA7N91Uw6amyt7x5IKZ90QK8BxE1mCjV7KJNGZ28u8klbf1QUOB4fE27gEfQYGyEcSrkPa4e/QzmOGYbnyiIt36np/qBtWHf87brRdVeKRNfO/ExCJkKbwpKfyGf06luCAfUW9wuHkFURux9naU+ilk2ZHUsPVBdkmfOXZrdxxdpDE19W5VkFryMbtVP5XNEBVC7SAsllHXrf8nskgk+m57bCPMP6RF8k+h+mXIJMuQd7yd7azOAnyLlOoufyY1hs1Po+EGDOSQUUHQKTi7AEYp2C71DpkqpGuPCbL/DkxchblYW5iuIek+BmO3wXbmBPv+0gWkiP/c1n605X0g+h4oO5yQqyI8Fki9F2Hb8T5QeHmC3+yzVVf7gOQ6MB7bBt+uX9wcl5yYBDHbmYGZtbNko7dq584FZKRRWeVhxdcDUXfdfzKmNR73BUIEqzeyOh2hUrk47VHK5d5FajKzgi9j5U8D0EJKjVMPZiulcF0J/ZQ4EOxUkOTNPuphiu43j1C7NXZ4RaPFrSrg7QMsObitqLUP5Pmq15Edg7vpvYME8Fe5Ia8sXLbNDHd3AWuXnfpeAUE=",
                        ObtainedFrom.MINECRAFT_ORIGINAL
                );
                SKIN_CACHE.put(ALEX.playerName.toLowerCase(), ALEX);
                //
                LOCAL_SKIN_NAMES = new ArrayList<>();
                Bukkit.getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), () ->{
                    File folder = new File(getSkinsFolderPath(Type.MINECRAFT));
                    if(!folder.exists() || !folder.isDirectory()) return;
                    for (File skin : folder.listFiles()) {
                        if (!skin.isDirectory()) continue;
                        LOCAL_SKIN_NAMES.add(skin.getName());
                    }
                });
            }

            protected final String playerName;
            protected final UUID playerUUID;
            protected final ObtainedFrom obtainedFrom;

            protected Minecraft(@Nonnull String playerName, @Nonnull UUID playerUUID, @Nonnull String texture, @Nonnull String signature, @Nullable ObtainedFrom obtainedFrom) {
                super(Type.MINECRAFT, texture, signature);
                Validate.notNull(playerName); Validate.notNull(playerUUID);
                if(obtainedFrom == null) obtainedFrom = ObtainedFrom.NONE;
                this.playerName = playerName;
                this.playerUUID = playerUUID;
                this.obtainedFrom = obtainedFrom;
                SKIN_CACHE.put(playerName.toLowerCase(), this);
            }

            protected Minecraft(@Nonnull String playerName, @Nonnull String playerUUID, @Nonnull String texture, @Nonnull String signature, @Nullable ObtainedFrom obtainedFrom) {
                this(playerName, UUID.fromString(StringUtils.formatUUID(playerUUID)), texture, signature, obtainedFrom);
            }

            protected Minecraft(@Nonnull String playerName, @Nonnull String playerUUID, @Nonnull SignedTexture signedTexture, @Nullable ObtainedFrom obtainedFrom) {
                this(playerName, playerUUID, signedTexture.texture(), signedTexture.signature(), obtainedFrom);
            }

            protected Minecraft(@Nonnull String playerName, @Nonnull UUID playerUUID, @Nonnull SignedTexture signedTexture, @Nullable ObtainedFrom obtainedFrom) {
                this(playerName, playerUUID, signedTexture.texture(), signedTexture.signature(),  obtainedFrom);
            }

            protected Minecraft(@Nonnull Player player) { this(player, getSkinGameProfile(player).orElse(Minecraft.getSteveSkin().getSignedTexture())); }

            protected Minecraft(@Nonnull Player player, @Nonnull SignedTexture signedTexture) {
                this(player.getName(), player.getUniqueId(), signedTexture,ObtainedFrom.GAME_PROFILE);
            }

            protected Minecraft(PlayerProfileData profileData){
                this(profileData.name, profileData.id, profileData.texture, profileData.signature, profileData.obtainedFrom);
            }

            protected Minecraft(YamlConfiguration config){
                this(config.getString("player.name"),
                        config.getString("player.id"),
                        config.getString("texture.value"),
                        config.getString("texture.signature"),
                        Minecraft.ObtainedFrom.valueOf(config.getString("obtainedFrom"))
                );
            }

            @Override public String getName() { return playerName; }

            public String getSimplifiedUUID() { return playerUUID.toString().replaceAll("-", ""); }

            public OfflinePlayer getPlayer() { return Bukkit.getOfflinePlayer(playerUUID); }

            public UUID getUUID() { return playerUUID; }

            public ObtainedFrom getObtainedFrom() { return obtainedFrom; }

            public boolean canBeDeleted() { return !isMinecraftOriginal(); }

            public boolean canBeUpdated() { return !isMinecraftOriginal(); }

            public boolean isMinecraftOriginal() { return obtainedFrom.equals(ObtainedFrom.MINECRAFT_ORIGINAL); }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin, String skinName){ return Custom.createCustomSkin(plugin, skinName, textures.getEncodedTexture(), textures.getSignature()); }

            @Deprecated public NPC.Skin.Custom convertToCustomSkin(Plugin plugin){ return Custom.createCustomSkin(plugin,textures.getEncodedTexture(), textures.getSignature()); }

            @Override protected File getAvatarFile(){ return getAvatarFile(this.playerName); }

            @Override protected File getTextureFile(){ return getTextureFile(this.playerName); }

            @Override protected File getDataFile(){ return getDataFile(this.playerName); }

            @Override protected String getSkinFolderPath(){ return getSkinFolderPath(this.playerName); }

            @Override
            public String getInformation() {
                Integer lastUpdateSeconds = getTimeFromLastUpdate(TimeUnit.SECONDS);
                String lastUpdateString = lastUpdateSeconds > 5 ? TimerUtils.getCRCounterSimple(lastUpdateSeconds, true) : "moments";
                return "§e§lInformation about this skin\n" +
                        "§8Minecraft skin\n" +
                        "\n" +
                        "§eName: " + (getName() != null ? "§7" + getName() : "§cUnknown") + "\n" +
                        "§eUUID: " + (getSimplifiedUUID() != null ? "§7" + getSimplifiedUUID() : "§cUnknown") +"\n" +
                        "\n" +
                        "§eModel: §a" + getModel().getName() + " §7(" + getModel().getPixels() + " pixel arms)"  +"\n" +
                        "§eCape: " + (getCape() != null ? "§d" + getCape().getName() : "§cNone") +"\n" +
                        "\n" +
                        "§eObtained from: §7" + getObtainedFrom().getTitle() + "\n" +
                        "§7§oLast updated " + lastUpdateString + " ago.";
            }

            private static File getAvatarFile(String playerName){ return new File(getSkinFolderPath(playerName) + "/avatar.png"); }

            private static File getTextureFile(String playerName){ return new File(getSkinFolderPath(playerName) + "/texture.png"); }

            private static File getDataFile(String playerName){ return new File(getSkinFolderPath(playerName) + "/data.yml"); }

            private static String getSkinFolderPath(String playerName){ return getSkinsFolderPath(Type.MINECRAFT) + playerName.toLowerCase(); }

            @Override
            public void delete(){
                if(!canBeDeleted()) throw new IllegalStateException("This skin cannot be deleted.");
                String playerNameLowerCase = playerName.toLowerCase();
                if(SKIN_CACHE.containsKey(playerNameLowerCase)) SKIN_CACHE.remove(playerNameLowerCase);
                if(LOCAL_SKIN_NAMES.contains(playerNameLowerCase)) LOCAL_SKIN_NAMES.remove(playerNameLowerCase);
                File folder = new File(getSkinFolderPath(playerNameLowerCase) + "/");
                if(!folder.exists()) return;
                try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
            }

            private static YamlConfiguration loadConfig(String playerName){
                File file = getDataFile(playerName);
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile();} catch (Exception e){};
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                return config;
            }

            @Override
            protected void save() throws IOException {
                File file = getDataFile();
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile(); }catch (Exception e){}
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("player.name", this.playerName);
                config.set("player.id", getSimplifiedUUID());
                config.set("texture.value", getTexture());
                config.set("texture.signature", getSignature());
                List<String> comments = new ArrayList<>();
                comments.add("Minecraft skin texture: " + textures.getSkinUrl());
                if(hasCape()) comments.add("Cape texture: " + textures.getCapeUrl());
                NMSFileConfiguration.setComments(config, "texture", comments);
                config.set("obtainedFrom", this.obtainedFrom.name());
                config.set("lastUpdate", resetLastUpdate());
                config.save(file);
                downloadImages();
            }

            // Using Player object as identifier
            @Deprecated public static void fetchSkinAsync(@Nonnull org.bukkit.entity.Player player, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(null, player, false, action); }
            @Deprecated public static void fetchSkinAsync(@Nonnull org.bukkit.entity.Player player, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(null, player, forceDownload, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull org.bukkit.entity.Player player, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(plugin, player.getName(), false, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull org.bukkit.entity.Player player, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(plugin, player.getName(), forceDownload, action); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull org.bukkit.entity.Player player){ return fetchSkinAsync(player, false); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull org.bukkit.entity.Player player, boolean forceDownload){ return fetchSkinAsync(null, player, forceDownload); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull org.bukkit.entity.Player player){ return fetchSkinAsync(plugin, player, false); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull org.bukkit.entity.Player player, boolean forceDownload){ return fetchSkinAsync(plugin, player.getName(), forceDownload); }

            // Using UUID object as identifier
            @Deprecated public static void fetchSkinAsync(@Nonnull UUID playerUUID, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(null, playerUUID, false, action); }
            @Deprecated public static void fetchSkinAsync(@Nonnull UUID playerUUID, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ fetchSkinAsync(null, playerUUID, forceDownload, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull UUID playerUUID, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(plugin, playerUUID, false, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull UUID playerUUID, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ fetchSkinAsync(plugin, playerUUID.toString(), forceDownload, action); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull UUID playerUUID){ return fetchSkinAsync(PlayerNPCPlugin.getInstance(), playerUUID, false); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull UUID playerUUID, boolean forceDownload){ return fetchSkinAsync(null, playerUUID, forceDownload); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull UUID playerUUID){ return fetchSkinAsync(plugin, playerUUID, false); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull UUID playerUUID, boolean forceDownload){ return fetchSkinAsync(plugin, playerUUID.toString(), forceDownload); }

            // Using String object as identifier
            @Deprecated public static void fetchSkinAsync(@Nonnull String playerName, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(null, playerName, false, action); }
            @Deprecated public static void fetchSkinAsync(@Nonnull String playerName, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ fetchSkinAsync(null, playerName, forceDownload, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull String playerName, Consumer<NPC.Skin.Minecraft> action){ Skin.Minecraft.fetchSkinAsync(plugin, playerName, false, action); }
            public static void fetchSkinAsync(Plugin plugin, @Nonnull String playerName, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){ fetchSkinAsync(plugin, playerName, forceDownload).thenAccept(result -> action.accept(result.grabSkin().orElse(null))); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull String playerName){ return fetchSkinAsync(null, playerName, false); }
            @Deprecated public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(@Nonnull String playerName, boolean forceDownload){ return fetchSkinAsync(null, playerName, forceDownload); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull String playerName){ return fetchSkinAsync(plugin, playerName, false); }
            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinAsync(Plugin plugin, @Nonnull String playerName, boolean forceDownload){
                Validate.notNull(playerName, "Player cannot be null");
                long started = System.currentTimeMillis();
                final Plugin finalPlugin = plugin != null ? plugin : PlayerNPCPlugin.getInstance();
                final String playerNameLowerCase = playerName.toLowerCase();
                final String possibleUUID = playerName.length() >= 32 ? playerName.replaceAll("-", "") : null;
                CompletableFuture<NPC.Skin.Minecraft.FetchResult> futureAsync = CompletableFuture.supplyAsync(() -> {
                    if(!forceDownload && possibleUUID == null){
                        // Load from cache
                        if(SKIN_CACHE.containsKey(playerNameLowerCase)) {
                            Skin.Minecraft skin = SKIN_CACHE.get(playerNameLowerCase);
                            if(!skin.needsToUpdate(finalPlugin)) return new FetchResult(skin, started);
                            else SKIN_CACHE.remove(playerNameLowerCase);
                        }
                        // Load from stored file
                        if(getDataFile(playerNameLowerCase).exists()){
                            YamlConfiguration config = loadConfig(playerNameLowerCase);
                            String lastUpdate = config.getString("lastUpdate");
                            if(!NPC.Skin.needsToUpdate(lastUpdate, finalPlugin)){
                                if(config.contains("variant")) config.set("model", config.getString("variant"));
                                Skin.Minecraft skin = new Skin.Minecraft(config);
                                skin.lastUpdate = lastUpdate;
                                LOCAL_SKIN_NAMES.remove(playerName.toLowerCase());
                                return new FetchResult(skin, started);
                            }
                        }
                    }
                    // Load from GameProfile
                    org.bukkit.entity.Player player = possibleUUID == null ? Bukkit.getServer().getPlayerExact(playerName) : null;
                    if(player != null && player.isOnline()){
                        SignedTexture signedTexture = getSkinGameProfile(player).orElse(null);
                        if(signedTexture != null){
                            Skin.Minecraft skin = new Skin.Minecraft(player);
                            try { skin.save(); } catch (IOException e) { NPCLib.printError(e); }
                            if(skin.getAvatarFile().exists()) skin.getAvatarFile().delete();
                            return new FetchResult(skin, started);
                        }
                    }
                    //Load from Mojang API
                    return fetchSkinMojangAsync(playerNameLowerCase).join();
                }).completeOnTimeout(new NPC.Skin.Minecraft.FetchResult(NPC.Skin.Minecraft.FetchResult.Error.TIME_OUT), 5, TimeUnit.SECONDS);
                return futureAsync;
            }

            public static CompletableFuture<NPC.Skin.Minecraft.FetchResult> fetchSkinMojangAsync(String playerName){
                long started = System.currentTimeMillis();
                final String playerNameLowerCase = playerName.toLowerCase();
                final String possibleUUID = playerName.length() >= 32 ? playerName.replaceAll("-", "") : null;
                CompletableFuture<NPC.Skin.Minecraft.FetchResult> futureAsync = CompletableFuture.supplyAsync(() -> {
                    try {
                        String uuid = possibleUUID;
                        if(uuid == null){ // Transform Player Name to Player UUID
                            try{ uuid = getUUID(playerNameLowerCase); }
                            catch (IOException e){ return new FetchResult(FetchResult.Error.NO_CONNECTION_TO_MOJANG); }
                            catch (IllegalStateException e){ return new FetchResult(FetchResult.Error.NO_PLAYER_WITH_THAT_NAME); }
                        }
                        PlayerProfileData data;
                        try{ data = getProfileMojangServer(uuid); }
                        catch (IOException e){ return new FetchResult(FetchResult.Error.NO_CONNECTION_TO_MOJANG); }
                        catch (IllegalStateException e){ return new FetchResult(FetchResult.Error.NO_PLAYER_WITH_THAT_UUID); }
                        Skin.Minecraft skin = new Skin.Minecraft(data);
                        if(skin.getAvatarFile().exists()) skin.getAvatarFile().delete();
                        skin.save();
                        return new FetchResult(skin, started);
                    }
                    catch (Exception e) {
                        NPCLib.printError(e);
                        return new FetchResult(FetchResult.Error.UNKNOWN);
                    }
                }).completeOnTimeout(new NPC.Skin.Minecraft.FetchResult(NPC.Skin.Minecraft.FetchResult.Error.TIME_OUT), 5, TimeUnit.SECONDS);
                return futureAsync;
            }

            public static class FetchResult extends NPC.Skin.FetchResult<Skin.Minecraft, FetchResult.Error>{

                protected FetchResult(@Nullable Minecraft skin, long started) {
                    super.started = started;
                    super.skin = Optional.ofNullable(skin);
                    if(super.grabSkin().isPresent()) super.error = Optional.empty();
                    else super.error = Optional.of(Error.UNKNOWN);
                }

                protected FetchResult(@Nullable Error error) {
                    super.skin = Optional.empty();
                    super.error = Optional.of(error != null ? error : Error.UNKNOWN);
                }

                public enum Error implements NPC.Skin.FetchResult.Error{
                    UNKNOWN("There was an error trying to fetch that skin."),
                    NO_PLAYER_WITH_THAT_NAME("No player with that name has been found."),
                    NO_PLAYER_WITH_THAT_UUID("No player with that UUID has been found."),
                    NO_CONNECTION_TO_MOJANG("Failed to establish connection to Mojang servers."),
                    TIME_OUT("Mojang server has exceeded the maximum response time."),
                    ;

                    private String message;

                    Error(@Nullable String message){ this.message = message; }

                    @Nonnull public String getMessage() { return this.message != null ? this.message : UNKNOWN.message; }
                    @Override public String toString() { return getMessage(); }

                }
            }

            public static List<String> getSuggestedSkinNames(){
                List<String> suggested = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(x-> suggested.add(x.getName().toLowerCase()));
                Skin.Minecraft.SKIN_CACHE.keySet().stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(SKIN_CACHE.get(x).getName().toLowerCase()));
                Skin.Minecraft.LOCAL_SKIN_NAMES.stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(x.toLowerCase()));
                if(!suggested.contains("sergiferry")) suggested.add("sergiferry");
                return suggested;
            }

            private record PlayerProfileData(String id, String name, String texture, String signature, ObtainedFrom obtainedFrom){}

            public boolean isModifiedByThirdParty() { return obtainedFrom.equals(ObtainedFrom.GAME_PROFILE) && !textures.getProfileId().equals(getSimplifiedUUID()); }

            public static boolean hasSkinGameProfile(Player player){
                EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
                GameProfile profile = NMSEntityPlayer.getGameProfile(p);
                if(profile.getProperties() == null || !profile.getProperties().containsKey("textures") || profile.getProperties().get("textures").isEmpty()) return false;
                return true;
            }

            public static Optional<SignedTexture> getSkinGameProfile(@Nullable Player player){
                if(player == null || !player.isOnline()) return Optional.empty();
                EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
                GameProfile profile = NMSEntityPlayer.getGameProfile(p);
                if(profile.getProperties() == null || !profile.getProperties().containsKey("textures") || profile.getProperties().get("textures").isEmpty()) return Optional.empty();
                Property property = profile.getProperties().get("textures").iterator().next();
                String texture = property.getValue();
                String signature = property.getSignature();
                return Optional.of(new SignedTexture(texture, signature));
            }

            private static PlayerProfileData getProfileMojangServer(String uuid) throws IOException, JsonParseException {
                URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                InputStreamReader reader2 = new InputStreamReader(url2.openStream());
                JsonObject profile = new JsonParser().parse(reader2).getAsJsonObject();
                JsonObject property = profile.get("properties").getAsJsonArray().get(0).getAsJsonObject();
                return new PlayerProfileData(profile.get("id").getAsString(), profile.get("name").getAsString(), property.get("value").getAsString(), property.get("signature").getAsString(), ObtainedFrom.MOJANG_API);
            }

            private static String getUUID(String name) throws IOException, JsonParseException {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                InputStreamReader reader = new InputStreamReader(url.openStream());
                JsonObject property = new JsonParser().parse(reader).getAsJsonObject();
                return property.get("id").getAsString();
            }

            public enum ObtainedFrom{
                MOJANG_API("§aMojang API"),
                GAME_PROFILE("§aGame Profile"),
                MINECRAFT_ORIGINAL("§aMinecraft Original"),
                NONE("§cUnknown"),
                ;

                private String title;

                ObtainedFrom(String title){
                    this.title = title;
                }

                public String getTitle() {
                    return title;
                }
            }

            public static Skin.Minecraft getSteveSkin(){ return STEVE; }

            public static Skin.Minecraft getAlexSkin(){ return ALEX; }
        }

        public static class Custom extends Skin implements NPCLib.Registry.Identified {

            protected static final NPCLib.Registry<NPC.Skin.Custom> SKIN_CACHE;

            static{
                SKIN_CACHE = new NPCLib.Registry<>(NPCLib.Registry.ID.playerNPC("customSkins"));
            }

            protected static void onUnregisterPlugin(Plugin plugin){
                Set<NPCLib.Registry.ID> remove = new HashSet<>();
                SKIN_CACHE.entrySet().stream().filter(x-> x.getValue().getPluginInfo().getName().equals(plugin.getDescription().getName())).forEach(x-> {
                    if(x.getValue().getName() != null){
                        File folder = new File(x.getValue().getSkinFolderPath() + "/");
                        if(folder.exists() && !x.getValue().getDataFile().exists()) try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
                    }
                    remove.add(NPCLib.Registry.ID.of(x.getKey()));
                });
                remove.forEach(x-> SKIN_CACHE.remove(x));
            }

            private final String skinName;
            private final Plugin plugin;
            private NPCLib.Registry.ID id;

            private Custom(@Nonnull Plugin plugin, @Nullable String skinName, @Nonnull String texture, @Nonnull String signature) {
                super(Type.CUSTOM, texture, signature);
                Validate.notNull(plugin, "Plugin cannot be null.");
                this.plugin = plugin;
                this.skinName = skinName;
                if(skinName == null) return;
                this.id = NPCLib.Registry.ID.of(plugin, skinName);
                Validate.isTrue(!SKIN_CACHE.contains(id), "Custom texture skin with this name already exists.");
                SKIN_CACHE.set(id, this);
            }

            private Custom(Plugin plugin, String texture, String signature) {
                this(plugin, null, texture, signature);
            }

            @Nullable @Override public String getName() { return skinName; }

            @Nullable @Override public NPCLib.Registry.ID getID() { return this.id; }

            public boolean hasID() { return this.id != null; }

            @Nonnull public PluginDescriptionFile getPluginInfo(){
                return plugin.getDescription();
            }

            public void changeTexture(Plugin plugin, String texture, String signature){
                Validate.isTrue(plugin.equals(this.plugin), "This plugin is not the owner of this custom skin.");
                super.textures = new Textures(texture, signature);
                if(getAvatarFile().exists()) getAvatarFile().delete();
                if(getTextureFile().exists()) getTextureFile().delete();
                try { save(); } catch (Exception e) { NPCLib.printError(e); }
            }

            public static CompletableFuture<NPC.Skin.Custom.FetchResult> fetchCustomSkinAsync(@Nonnull Plugin skinPlugin, @Nonnull String skinName){ return fetchCustomSkinAsync(NPCLib.Registry.ID.of(skinPlugin, skinName)); }

            public static CompletableFuture<NPC.Skin.Custom.FetchResult> fetchCustomSkinAsync(@Nonnull String fullID){ return fetchCustomSkinAsync(NPCLib.Registry.ID.of(fullID)); }

            public static CompletableFuture<NPC.Skin.Custom.FetchResult> fetchCustomSkinAsync(@Nonnull NPCLib.Registry.ID id){
                Validate.notNull(id, "ID cannot be null");
                long started = System.currentTimeMillis();
                CompletableFuture<NPC.Skin.Custom.FetchResult> futureSkin = CompletableFuture.supplyAsync(() -> {
                    if(!SKIN_CACHE.contains(id)) return new FetchResult(FetchResult.Error.NO_SKIN_WITH_THAT_NAME);
                    return new FetchResult(SKIN_CACHE.grab(id).get(), started);
                }).completeOnTimeout(new FetchResult(FetchResult.Error.TIME_OUT), 5, TimeUnit.SECONDS);
                return futureSkin;
            }

            public static class FetchResult extends NPC.Skin.FetchResult<Skin.Custom, FetchResult.Error>{

                protected FetchResult(@Nullable Custom skin, long started) {
                    super.started = started;
                    super.skin = Optional.ofNullable(skin);
                    if(super.grabSkin().isPresent()) super.error = Optional.empty();
                    else super.error = Optional.of(FetchResult.Error.UNKNOWN);
                }

                protected FetchResult(@Nullable FetchResult.Error error) {
                    super.skin = Optional.empty();
                    super.error = Optional.of(error != null ? error : FetchResult.Error.UNKNOWN);
                }

                public enum Error implements NPC.Skin.FetchResult.Error{
                    UNKNOWN("There was an error trying to fetch that skin."),
                    NO_SKIN_WITH_THAT_NAME("No skin with that name has been found."),
                    TIME_OUT("Server has exceeded the maximum response time."),
                    ;

                    private String message;

                    Error(@Nullable String message){ this.message = message; }

                    @Nonnull public String getMessage() { return this.message != null ? this.message : UNKNOWN.message; }
                    @Override public String toString() { return getMessage(); }

                }
            }

            @Deprecated public static boolean isLoadedSkin(String fullID) { return isLoadedSkin(NPCLib.Registry.ID.of(fullID)); }

            public static boolean isLoadedSkin(Plugin plugin, String skinName) { return isLoadedSkin(NPCLib.Registry.ID.of(plugin, skinName)); }

            public static boolean isLoadedSkin(NPCLib.Registry.ID id) { return SKIN_CACHE.contains(id); }

            @Deprecated public static Optional<Skin.Custom> getLoadedSkin(String skinCode) { return getLoadedSkin(NPCLib.Registry.ID.of(skinCode)); }

            public static Optional<Skin.Custom> getLoadedSkin(Plugin plugin, String skinName){ return getLoadedSkin(NPCLib.Registry.ID.of(plugin, skinName)); }

            public static Optional<Skin.Custom> getLoadedSkin(NPCLib.Registry.ID id){ return Optional.ofNullable(SKIN_CACHE.grab(id).orElse(null)); }

            public static Skin.Custom createCustomSkin(@Nonnull Plugin plugin, @Nonnull String skinName, @Nonnull String texture, @Nonnull String signature){
                Validate.isTrue(!SKIN_CACHE.contains(NPCLib.Registry.ID.of(plugin, skinName)), "Custom texture skin with this name already exists.");
                return new Skin.Custom(plugin, skinName, texture, signature);
            }

            public static Skin.Custom createCustomSkin(@Nonnull Plugin plugin, @Nonnull String texture, @Nonnull String signature){ return new Skin.Custom(plugin, null, texture, signature); }

            public static Skin.Custom createCustomSkin(@Nonnull Plugin plugin, @Nonnull String skinName, @Nonnull SignedTexture signedTexture) { return createCustomSkin(plugin, skinName, signedTexture.texture(), signedTexture.signature()); }

            public static Skin.Custom createCustomSkin(@Nonnull Plugin plugin, @Nonnull SignedTexture signedTexture) { return createCustomSkin(plugin, signedTexture.texture(), signedTexture.signature()); }

            public static List<String> getSuggestedSkinNames(){ return Skin.Custom.SKIN_CACHE.getKeys().stream().collect(Collectors.toList()); }

            @Override
            protected void save() throws IOException {
                if(!hasID()) return;
                File file = getDataFile();
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile(); }catch (Exception e){}
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("name", this.skinName);
                config.set("plugin", this.plugin.getName());
                config.set("texture.value", textures.getEncodedTexture());
                config.set("texture.signature", textures.getSignature());
                NMSFileConfiguration.setComments(config, "texture", Arrays.asList("Minecraft skin texture: " + textures.getSkinUrl()));
                config.set("lastUpdate", resetLastUpdate());
                config.save(file);
                downloadImages();
            }

            @Override
            public void delete() {
                if(!hasID()) return;
                if(SKIN_CACHE.contains(id)) SKIN_CACHE.remove(id);
                File folder = new File(getSkinFolderPath(plugin, skinName) + "/");
                if(!folder.exists()) return;
                try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
            }

            @Override
            public String getInformation() {
                Integer lastUpdateSeconds = getTimeFromLastUpdate(TimeUnit.SECONDS);
                String lastUpdateString = lastUpdateSeconds > 5 ? TimerUtils.getCRCounterSimple(lastUpdateSeconds, true) : "moments";
                return "§e§lInformation about this skin\n" +
                        "§8Custom skin\n" +
                        "\n" +
                        (hasID() ? "§eName: §7" + getName() + "\n" : "") +
                        "§ePlugin: §7" + getPluginInfo().getName() + "\n" +
                        (hasID() ? "§eID: §7" + getID().getFullID() : "§c§oThis Skin does not have identifier") + "\n" +
                        "\n" +
                        "§eModel: §a" + getModel().getName() + " §7(" + getModel().getPixels() + " pixel arms)"  +"\n" +
                        "§eCape: " + (getCape() != null ? getCape().getName() : "§cNone") +"\n" +
                        "\n" +
                        "§7§oLast updated " + lastUpdateString + " ago.";
            }

            @Override protected File getAvatarFile(){
                if(this.skinName == null) return null;
                return getAvatarFile(this.plugin, this.skinName);
            }

            @Override protected File getTextureFile(){
                if(this.skinName == null) return null;
                return getTextureFile(this.plugin, this.skinName);
            }

            @Override protected File getDataFile(){
                if(this.skinName == null) return null;
                return getDataFile(this.plugin, this.skinName);
            }

            @Override protected String getSkinFolderPath(){
                if(this.skinName == null) return null;
                return getSkinFolderPath(this.plugin, this.skinName);
            }

            private static File getAvatarFile(Plugin plugin, String skinName){ return new File(getSkinFolderPath(plugin, skinName) + "/avatar.png"); }

            private static File getTextureFile(Plugin plugin, String skinName){ return new File(getSkinFolderPath(plugin, skinName) + "/texture.png"); }

            private static File getDataFile(Plugin plugin, String skinName){ return new File(getSkinFolderPath(plugin, skinName) + "/data.yml"); }

            private static String getSkinFolderPath(Plugin plugin, String skinName){ return getSkinsFolderPath(Type.CUSTOM) + plugin.getName().toLowerCase() + "/" + skinName.toLowerCase(); }
        }

        public static class MineSkin extends Skin{

            private static final HashMap<Integer, MineSkin> SKIN_CACHE_BY_ID;
            private static final HashMap<String, MineSkin> SKIN_CACHE_BY_UUID;
            protected static final List<String> LOCAL_SKIN_NAMES;
            private static final MineskinClient MINESKIN_CLIENT;

            static{
                MINESKIN_CLIENT = new MineskinClient();
                SKIN_CACHE_BY_ID = new HashMap<>();
                SKIN_CACHE_BY_UUID = new HashMap<>();
                LOCAL_SKIN_NAMES = new ArrayList<>();
                Bukkit.getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), () ->{
                    File folder = new File(getSkinsFolderPath(Type.MINESKIN));
                    if(!folder.exists() || !folder.isDirectory()) return;
                    for (File skin : folder.listFiles()) {
                        if (!skin.isDirectory()) continue;
                        LOCAL_SKIN_NAMES.add(skin.getName());
                    }
                });
            }

            public static List<String> getSuggestedSkinNames(){
                List<String> suggested = new ArrayList<>();
                SKIN_CACHE_BY_ID.keySet().stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(SKIN_CACHE_BY_ID.get(x).getId().toString()));
                LOCAL_SKIN_NAMES.stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(x));
                return suggested;
            }

            private String uuid; //UUID without "-"
            private Integer id;
            private ObtainedFrom obtainedFrom;

            private MineSkin(String uuid, Integer id, String texture, String signature, @Nullable ObtainedFrom obtainedFrom) {
                super(Type.MINESKIN, texture, signature);
                Validate.notNull(uuid); Validate.notNull(id);
                if(obtainedFrom == null) obtainedFrom = ObtainedFrom.NONE;
                this.uuid = uuid;
                this.id = id;
                this.obtainedFrom = obtainedFrom;
                SKIN_CACHE_BY_ID.put(id, this);
                SKIN_CACHE_BY_UUID.put(uuid, this);
            }

            private MineSkin(UUID uuid, Integer id, String texture, String signature, ObtainedFrom obtainedFrom){ this(uuid.toString().replaceAll("-", ""), id, texture, signature, obtainedFrom); }

            private MineSkin(org.mineskin.data.Skin mineSkinData, ObtainedFrom obtainedFrom){ this( mineSkinData.data.uuid, mineSkinData.id, mineSkinData.data.texture.value, mineSkinData.data.texture.signature, obtainedFrom); }

            private MineSkin(YamlConfiguration config){ this( config.getString("mineskin.uuid"), config.getInt("mineskin.id"), config.getString("texture.value"), config.getString("texture.signature"), config.contains("obtainedFrom") ? ObtainedFrom.valueOf(config.getString("obtainedFrom")) : ObtainedFrom.NONE); }

            // Using URL object as identifier
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull URL url){ return generateSkinFromURLAsync(url.toString()); }
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull URL url, @Nullable Model model){ return generateSkinFromURLAsync(url.toString(), model); }
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull URL url, @Nullable Variant variant){ return generateSkinFromURLAsync(url.toString(), variant); }

            // Using String object as identifier
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull String url){ return generateSkinFromURLAsync(url, Variant.AUTO); }
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull String url, @Nullable Model model){ return generateSkinFromURLAsync(url, model != null ? model.toMineSkinVariant() : null); }
            public static CompletableFuture<FetchResult> generateSkinFromURLAsync(@Nonnull String url, @Nullable Variant variant){
                Validate.notNull(url, "URL cannot be null");
                if(url.startsWith("https://namemc.com/skin/")) url = url.replaceFirst("https://namemc.com/skin/", "https://s.namemc.com/i/") + ".png";
                final String finalUrl = url;
                CompletableFuture<FetchResult> futureSkin = CompletableFuture.supplyAsync(() -> {
                    org.mineskin.data.Skin skinData;
                    try { skinData = MINESKIN_CLIENT.generateUrl(finalUrl, SkinOptions.create("", variant != null ? variant : Variant.AUTO, Visibility.PUBLIC)).join(); }
                    catch (RuntimeException exception){ return new FetchResult(exception); }
                    catch (Exception e){ NPCLib.printError(e); return new FetchResult(FetchResult.Error.UNKNOWN); }
                    if(skinData == null) return new FetchResult(FetchResult.Error.UNKNOWN);
                    FetchResult fetchResult = fetchSkinAsync(PlayerNPCPlugin.getInstance(), "" + skinData.id, false).join();
                    if(fetchResult.hasFound()){
                        fetchResult.grabSkin().get().obtainedFrom = ObtainedFrom.GENERATED_FROM_AN_IMAGE_URL;
                        try { fetchResult.grabSkin().get().save(); } catch (IOException e) {}
                    }
                    return fetchResult;
                }).completeOnTimeout(new NPC.Skin.MineSkin.FetchResult(NPC.Skin.MineSkin.FetchResult.Error.TIME_OUT), 5, TimeUnit.SECONDS);;
                return futureSkin;
            }

            private static CompletableFuture<org.mineskin.data.Skin> getMineSkinData(long id){ return MINESKIN_CLIENT.getId(id); }
            private static CompletableFuture<org.mineskin.data.Skin> getMineSkinData(UUID uuid){ return MINESKIN_CLIENT.getUuid(uuid); }

            // Using MineSkin URL as identifier
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(URL mineSkinURL){ return fetchSkinAsync(mineSkinURL, false); }
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(URL mineSkinURL, boolean forceDownload){ return fetchSkinAsync(null, mineSkinURL, forceDownload); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, URL mineSkinURL){ return fetchSkinAsync(plugin, mineSkinURL, false); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, URL mineSkinURL, boolean forceDownload){ return fetchSkinAsync(plugin, mineSkinURL.toString(), forceDownload); }

            // Using UUID object as identifier
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(UUID uuid){ return fetchSkinAsync(uuid, false); }
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(UUID uuid, boolean forceDownload){ return fetchSkinAsync(null, uuid, forceDownload); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, UUID uuid){ return fetchSkinAsync(plugin, uuid, false); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, UUID uuid, boolean forceDownload){ return fetchSkinAsync(plugin, uuid.toString(), forceDownload); }

            // Using Long object as identifier
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(Long id){ return fetchSkinAsync(id, false); }
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(Long id, boolean forceDownload){ return fetchSkinAsync(null, id, forceDownload); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, Long id){ return fetchSkinAsync(plugin, id, false); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, Long id, boolean forceDownload){ return fetchSkinAsync(plugin, id.intValue(), forceDownload); }

            // Using Integer object as identifier
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(Integer id){ return fetchSkinAsync(id, false); }
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(Integer id, boolean forceDownload){ return fetchSkinAsync(null, id, forceDownload); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, Integer id){ return fetchSkinAsync(plugin, id, false); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, Integer id, boolean forceDownload){ return fetchSkinAsync(plugin, id.toString(), forceDownload); }

            // Using String object as identifier
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(String id){ return fetchSkinAsync(id, false); }
            @Deprecated public static CompletableFuture<FetchResult> fetchSkinAsync(String id, boolean forceDownload){ return fetchSkinAsync(null, id, forceDownload); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, String id){ return fetchSkinAsync(plugin, id, false); }
            public static CompletableFuture<FetchResult> fetchSkinAsync(Plugin plugin, String id, boolean forceDownload){
                Validate.notNull(plugin); Validate.notNull(id);
                long started = System.currentTimeMillis();
                CompletableFuture<FetchResult> futureSkin = CompletableFuture.supplyAsync(() -> {
                    try {
                        String actualID = id;
                        if(actualID.startsWith("https://minesk.in/")) actualID = actualID.replaceFirst("https://minesk\\.in/", "");
                        else if(actualID.startsWith("https://mineskin.org/")) actualID = actualID.replaceFirst("https://mineskin\\.org/", "");
                        String possibleUUID = actualID.length() >= 32 ? actualID.replaceAll("-", "") : null;
                        Integer possibleID = MathUtils.isInteger(actualID) ? Integer.valueOf(actualID) : null;
                        if(possibleID == null && possibleUUID == null) return new FetchResult(FetchResult.Error.INCORRECT_NAME);
                        //
                        if(!forceDownload){
                            if(possibleUUID != null){
                                if(SKIN_CACHE_BY_UUID.containsKey(possibleUUID)) {
                                    Skin.MineSkin skin = SKIN_CACHE_BY_UUID.get(possibleUUID);
                                    if(!skin.needsToUpdate(plugin)) return new FetchResult(skin, started);
                                    else SKIN_CACHE_BY_UUID.remove(possibleID);
                                }
                            }
                            if(possibleID != null){
                                if(SKIN_CACHE_BY_ID.containsKey(possibleID)) {
                                    Skin.MineSkin skin = SKIN_CACHE_BY_ID.get(possibleID);
                                    if(!skin.needsToUpdate(plugin)) return new FetchResult(skin, started);
                                    else SKIN_CACHE_BY_ID.remove(possibleID);
                                }
                                if(getDataFile(possibleID).exists()){
                                    YamlConfiguration config = loadConfig(possibleID);
                                    String lastUpdate = config.getString("lastUpdate");
                                    if(!NPC.Skin.needsToUpdate(lastUpdate, plugin)){
                                        Skin.MineSkin skin = new Skin.MineSkin( config);
                                        skin.lastUpdate = lastUpdate;
                                        if(LOCAL_SKIN_NAMES.contains(possibleID.toString())) LOCAL_SKIN_NAMES.remove(possibleID.toString());
                                        return new FetchResult(skin, started);
                                    }
                                }
                            }
                        }
                        org.mineskin.data.Skin mineSkinData = null;
                        if(possibleID != null) mineSkinData = getMineSkinData(possibleID.longValue()).join();
                        else if(possibleUUID != null) mineSkinData = getMineSkinData(UUID.fromString(StringUtils.formatUUID(possibleUUID))).join();
                        if(mineSkinData == null) return new FetchResult(FetchResult.Error.SKIN_NOT_FOUND);
                        MineSkin mineSkin = new MineSkin(mineSkinData, ObtainedFrom.MINESKIN_GALLERY);
                        mineSkin.save();
                        return new FetchResult(mineSkin, started);
                    } catch (RuntimeException exception){
                        return new FetchResult(exception);
                    } catch (Exception exception) {
                        NPCLib.printError(exception);
                        return new FetchResult(FetchResult.Error.UNKNOWN);
                    }
                }).completeOnTimeout(new NPC.Skin.MineSkin.FetchResult(NPC.Skin.MineSkin.FetchResult.Error.TIME_OUT), 5, TimeUnit.SECONDS);
                return futureSkin;
            }

            public static class FetchResult extends NPC.Skin.FetchResult<Skin.MineSkin, FetchResult.Error>{

                private Optional<RuntimeException> mineskinException;

                protected FetchResult(@Nullable MineSkin skin, long started) {
                    super.started = started;
                    super.skin = Optional.ofNullable(skin);
                    if(super.grabSkin().isPresent()) super.error = Optional.empty();
                    else super.error = Optional.of(FetchResult.Error.UNKNOWN);
                    this.mineskinException = Optional.empty();
                }

                protected FetchResult(@Nullable Error error) {
                    super.skin = Optional.empty();
                    super.error = Optional.of(error != null ? error : FetchResult.Error.UNKNOWN);
                    this.mineskinException = Optional.empty();
                }


                public FetchResult(@Nonnull RuntimeException exception){
                    super.skin = Optional.empty();
                    if(exception == null){
                        super.error = Optional.of(Error.UNKNOWN);
                        return;
                    }
                    this.mineskinException = Optional.of(exception);
                    super.error = Optional.of(Error.MINESKIN_EXCEPTION);
                }

                public String getErrorMessage(){
                    if(!super.hasError()) return null;
                    if(super.grabError().get().equals(Error.MINESKIN_EXCEPTION)) return "There was an error: §c" + getMineSkinErrorMessage();
                    return super.grabError().get().getMessage();
                }

                private Optional<RuntimeException> getMineSkinException() { return mineskinException; }
                @Nonnull private String getMineSkinErrorMessage() { return mineskinException.isPresent() ? mineskinException.get().getMessage().split(":", 3)[2].replaceFirst(" ", "") : "Unknown"; }

                public enum Error implements Skin.FetchResult.Error {
                    UNKNOWN("There was an error trying to fetch that skin."),
                    TIME_OUT("MineSkin server has exceeded the maximum response time."),
                    SKIN_NOT_FOUND("No skin with that identifier has been found."),
                    INCORRECT_NAME("Incorrect name. Use MineSkin ID, UUID, or Direct Link."),
                    NO_CONNECTION_TO_MINESKIN("Failed to establish connection to MineSkin servers."),
                    MINESKIN_EXCEPTION(),
                    ;

                    private String message;

                    Error(@Nullable String message){ this.message = message; }
                    Error() { this(null); }

                    @Nonnull public String getMessage() { return this.message != null ? this.message : UNKNOWN.message; }
                    @Override  public String toString() { return getMessage(); }
                }
            }

            private static YamlConfiguration loadConfig(Integer id){
                File file = getDataFile(id);
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile();} catch (Exception e){};
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                return config;
            }

            public String getUUID() { return uuid; }

            public Integer getId() { return id; }

            @Override public String getName() { return id.toString(); }

            public String getMineSkinURL(){ return "https://minesk.in/" + uuid; }

            @Nonnull public ObtainedFrom getObtainedFrom() { return obtainedFrom; }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin, String skinName){ return Custom.createCustomSkin(plugin, skinName, textures.getEncodedTexture(), textures.getSignature()); }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin){ return Custom.createCustomSkin(plugin,textures.getEncodedTexture(), textures.getSignature()); }

            @Override
            protected void save() throws IOException {
                File file = getDataFile();
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile(); }catch (Exception e){}
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                NMSFileConfiguration.setHeader(config, Arrays.asList(getMineSkinURL()));
                config.set("mineskin.id", this.id);
                config.set("mineskin.uuid", this.uuid);
                config.set("texture.value", getTexture());
                config.set("texture.signature", getSignature());
                List<String> comments = new ArrayList<>();
                comments.add("Minecraft skin texture: " + textures.getSkinUrl());
                if(hasCape()) comments.add("Cape texture: " + textures.getCapeUrl());
                NMSFileConfiguration.setComments(config, "texture", comments);
                config.set("obtainedFrom", this.obtainedFrom.name());
                config.set("lastUpdate", resetLastUpdate());
                config.save(file);
                downloadImages();
            }

            @Override
            public void delete() {
                if(SKIN_CACHE_BY_UUID.containsKey(uuid)) SKIN_CACHE_BY_UUID.remove(uuid);
                if(SKIN_CACHE_BY_ID.containsKey(id)) SKIN_CACHE_BY_ID.remove(id);
                if(LOCAL_SKIN_NAMES.contains(id.toString())) LOCAL_SKIN_NAMES.remove(id.toString());
                File folder = new File(getSkinFolderPath(id) + "/");
                if(!folder.exists()) return;
                try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
            }

            @Override protected File getAvatarFile(){ return getAvatarFile(this.id); }

            @Override protected File getTextureFile(){ return getTextureFile(this.id); }

            @Override protected File getDataFile(){ return getDataFile(this.id); }

            @Override protected String getSkinFolderPath(){ return getSkinFolderPath(this.id); }

            @Override
            public String getInformation() {
                Integer lastUpdateSeconds = getTimeFromLastUpdate(TimeUnit.SECONDS);
                String lastUpdateString = lastUpdateSeconds > 5 ? TimerUtils.getCRCounterSimple(lastUpdateSeconds, true) : "moments";
                return "§e§lInformation about this skin\n" +
                        "§8MineSkin skin\n" +
                        "\n" +
                        "§eMineSkin ID: §7" + getId() +"\n" +
                        "§eUUID: §7" + getUUID() + "\n" +
                        "\n" +
                        "§eModel: §a" + getModel().getName() + " §7(" + getModel().getPixels() + " pixel arms)"  +"\n" +
                        "§eCape: §c§oMineSkins don't have cape\n" +
                        "\n" +
                        "§eObtained from: " + getObtainedFrom().getTitle() + "\n" +
                        "§7§oLast updated " + lastUpdateString + " ago.";
            }

            private static File getAvatarFile(Integer id){ return new File(getSkinFolderPath(id) + "/avatar.png"); }

            private static File getTextureFile(Integer id){ return new File(getSkinFolderPath(id) + "/texture.png"); }

            private static File getDataFile(Integer id){ return new File(getSkinFolderPath(id) + "/data.yml"); }

            private static String getSkinFolderPath(Integer id){ return getSkinsFolderPath(Type.MINESKIN) + id.toString(); }

            public enum ObtainedFrom{
                MINESKIN_GALLERY("§aMineSkin Gallery"),
                GENERATED_FROM_AN_IMAGE_URL("§aGenerated from an Image URL"),
                NONE("§cUnknown"),
                ;

                private String title;

                ObtainedFrom(String title){
                    this.title = title;
                }

                public String getTitle() { return title; }
            }

        }

        public enum Layer implements EnumUtils.GetName {
            CAPE((byte) 0x01),
            JACKET((byte) 0x02),
            LEFT_SLEEVE((byte) 0x04),
            RIGHT_SLEEVE((byte) 0x08),
            LEFT_PANTS((byte) 0x10),
            RIGHT_PANTS((byte) 0x20),
            HAT((byte) 0x40),
            ;

            private byte mask;

            Layer(byte mask){ this.mask = mask; }

            protected byte getMask() { return mask; }

            @Nullable public static Layer fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findFirst().orElse(null); }

        }

        public static class VisibleLayers implements Cloneable{

            private HashMap<Layer, Boolean> visibleLayers;

            protected VisibleLayers(){
                this.visibleLayers = new HashMap<>();
                enableAll();
            }

            public void enableAll(){ Arrays.stream(Layer.values()).forEach(x-> visibleLayers.put(x, true)); }

            public void disableAll(){ Arrays.stream(Layer.values()).forEach(x-> visibleLayers.put(x, false)); }

            public List<Layer> getVisibleLayers(){ return Arrays.stream(Layer.values()).filter(x-> isVisible(x)).collect(Collectors.toList()); }

            public List<Layer> getInvisibleLayers(){ return Arrays.stream(Layer.values()).filter(x-> !isVisible(x)).collect(Collectors.toList()); }

            public Updatable.Type setVisibility(Layer layer, boolean visible){
                visibleLayers.put(layer, visible);
                return Updatable.Type.SIMPLE_UPDATE;
            }

            public boolean isVisible(Layer layer) { return visibleLayers.get(layer); }

            public boolean isCape() { return isVisible(Layer.CAPE); }

            public Updatable.Type setCape(boolean cape) { return setVisibility(Layer.CAPE, cape); }

            public boolean isJacket() { return isVisible(Layer.JACKET); }

            public Updatable.Type setJacket(boolean jacket) { return setVisibility(Layer.JACKET, jacket); }

            public boolean isLeftSleeve() { return visibleLayers.get(Layer.LEFT_SLEEVE); }

            public Updatable.Type setLeftSleeve(boolean leftSleeve) { return setVisibility(Layer.LEFT_SLEEVE, leftSleeve); }

            public boolean isRightSleeve() { return isVisible(Layer.RIGHT_SLEEVE); }

            public Updatable.Type setRightSleeve(boolean rightSleeve) { return setVisibility(Layer.RIGHT_SLEEVE, rightSleeve); }

            public boolean isLeftPants() { return isVisible(Layer.LEFT_PANTS); }

            public Updatable.Type setLeftPants(boolean leftPants) { return setVisibility(Layer.LEFT_PANTS, leftPants); }

            public boolean isRightPants() { return isVisible(Layer.RIGHT_PANTS); }

            public Updatable.Type setRightPants(boolean rightPants) { return setVisibility(Layer.RIGHT_PANTS, rightPants); }

            public boolean isHat() { return isVisible(Layer.HAT); }

            public Updatable.Type setHat(boolean hat) { return setVisibility(Layer.HAT, hat); }

            @Override
            public VisibleLayers clone(){
                VisibleLayers visibleLayers = new VisibleLayers();
                Arrays.stream(Layer.values()).forEach(x-> visibleLayers.setVisibility(x, isVisible(x)));
                return visibleLayers;
            }

        }

    }

    public static class Move {

        private Move() {}

        public enum Speed{
            SLOW(0.1),
            NORMAL(0.15),
            SPRINT(0.2),
            ;

            private double speed;

            Speed(double speed){ this.speed = speed; }

            public double doubleValue() { return speed; }
        }

        protected static class Path{

            private NPC npc;
            private Location start;
            private List<Location> locations;
            private int actual;
            private Type type;

            private Path(NPC npc, List<Location> locations, Type type) {
                this.npc = npc;
                this.locations = locations;
                this.type = type;
                this.actual = -1;
            }

            public Path start(){
                this.start = npc.getLocation();
                next();
                return this;
            }

            private void next(){
                actual++;
                if(actual >= locations.size()){
                    if(type.isBackToStart() && actual == locations.size()){
                        go(start);
                        return;
                    }
                    else finish();
                }
                go(locations.get(actual));
            }

            private void finish(){
                if(type.isRepetitive()){
                    actual = -1;
                    start();
                    return;
                }
                npc.getMoveBehaviour().cancel();
            }

            private void go(Location location){
                if(npc.moveTask == null) npc.goTo(location);
                else npc.moveTask.end = location;
            }

            public enum Type{
                NORMAL (false, false),
                REPETITIVE (true, false),
                BACK_TO_START (false, true),
                ;

                private boolean repetitive;
                private boolean backToStart;

                Type(boolean repetitive, boolean backToStart){
                    this.repetitive = repetitive;
                    this.backToStart = backToStart;
                }

                public boolean isRepetitive() { return repetitive; }

                public boolean isBackToStart() { return backToStart; }
            }

        }

        public static class Behaviour{

            private NPC npc;
            private NPC.Move.Behaviour.Type type;
            private Integer taskID;
            //
            private Double followMinDistance;
            private Double followMaxDistance;
            private Entity followEntity;
            private NPC followNPC;
            private NPC.Move.Path path;

            protected Behaviour(NPC npc){
                this.npc = npc;
                this.type = Type.NONE;
                this.taskID = null;
                //
                this.followMinDistance = 5.0;
                this.followMaxDistance = 50.0;
                this.followEntity = null;
                this.followNPC = null;
                this.path = null;
            }

            protected void tick(){
                if(type == null || type.equals(Type.NONE) || type.equals(Type.CUSTOM_PATH)) return;
                if(type.equals(Type.FOLLOW_ENTITY) || type.equals(Type.FOLLOW_PLAYER) || type.equals(Type.FOLLOW_NPC)){
                    if(type.equals(Type.FOLLOW_ENTITY) && followEntity == null) return;
                    if(type.equals(Type.FOLLOW_NPC) && followNPC == null) return;
                    Location target = null;
                    if(type.equals(Type.FOLLOW_ENTITY)) target = followEntity.getLocation();
                    if(type.equals(Type.FOLLOW_PLAYER) && npc instanceof NPC.Personal) target = ((NPC.Personal) npc).player.getLocation();
                    if(type.equals(Type.FOLLOW_NPC)) target = followNPC.getLocation();
                    if(target == null) return;
                    if(!target.getWorld().equals(npc.getWorld())){
                        npc.teleport(target);
                        return;
                    }
                    if(npc.getMoveTask() == null){
                        npc.goTo(target, true);
                        return;
                    }
                    if(target.distance(npc.getLocation()) <= followMinDistance){
                        npc.getMoveTask().pause();
                        return;
                    }
                    if(target.distance(npc.getLocation()) >= followMaxDistance){
                        npc.teleport(target);
                        return;
                    }
                    if(npc.getMoveTask().isPaused()) npc.getMoveTask().resume();
                    npc.getMoveTask().end = target;
                    return;
                }
                if(type.equals(Type.RANDOM_PATH)){
                    return;
                }
            }

            public Move.Path setPath(List<Location> locations, Move.Path.Type type){
                setType(Type.CUSTOM_PATH);
                this.path = new Path(npc, locations, type);
                return this.path;
            }

            public Move.Behaviour setFollowEntity(Entity entity){ return setFollowEntity(entity, followMinDistance, followMaxDistance); }

            public Move.Behaviour setFollowEntity(Entity entity, double followMinDistance){ return setFollowEntity(entity, followMinDistance, followMaxDistance); }

            public Move.Behaviour setFollowEntity(Entity entity, double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_ENTITY);
                this.followEntity = entity;
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
                return this;
            }

            public Move.Behaviour setFollowNPC(NPC npc){ return setFollowNPC(npc, followMinDistance, followMaxDistance); }

            public Move.Behaviour setFollowNPC(NPC npc, double followMinDistance){ return setFollowNPC(npc, followMinDistance, followMaxDistance); }

            public Move.Behaviour setFollowNPC(NPC npc, double followMinDistance, double followMaxDistance){
                if(npc.equals(this.npc)) return this;
                if(npc instanceof NPC.Personal && this.npc instanceof NPC.Global) return this;
                if(npc instanceof NPC.Personal && this.npc instanceof NPC.Personal && !((Personal) npc).getPlayer().equals(((Personal) this.npc).getPlayer())) return this;
                setType(Type.FOLLOW_NPC);
                this.followNPC = npc;
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
                return this;
            }

            public Move.Behaviour setFollowPlayer(){
                return setFollowPlayer(followMinDistance, followMaxDistance);
            }

            public Move.Behaviour setFollowPlayer(double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_PLAYER);
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
                return this;
            }

            public Move.Behaviour cancel(){ return setType(Type.NONE); }

            private Move.Behaviour startTimer(){
                if(taskID != null) return this;
                taskID = Bukkit.getScheduler().runTaskTimer(PlayerNPCPlugin.getInstance(), ()->{
                    tick();
                }, 20L, 20L).getTaskId();
                return this;
            }

            private Move.Behaviour finishTimer(){
                if(taskID == null) return this;
                Bukkit.getScheduler().cancelTask(taskID);
                return this;
            }

            private Move.Behaviour setType(Type type) {
                this.type = type;
                if(type == Type.NONE) finishTimer();
                else startTimer();
                return this;
            }

            public Type getType() { return type; }

            public NPC getNPC() { return npc; }

            public enum Type{
                NONE, FOLLOW_PLAYER, FOLLOW_ENTITY, FOLLOW_NPC, CUSTOM_PATH, @Deprecated RANDOM_PATH;

                public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }
            }
        }

        public static class Task{

            private final NPC npc;
            private Integer taskID;
            private Location start;
            private Location end;
            private boolean pause;
            private boolean lookToEnd;
            private GazeTrackingType lastGazeTrackingType;
            private NPC.Pose lastNPCPose;
            private boolean checkSwimming;
            private boolean checkSlabCrouching;
            private boolean checkSlowness;
            private boolean checkLadders;

            private Task(NPC npc, Location end, boolean lookToEnd){
                this.npc = npc;
                this.end = end;
                this.lookToEnd = lookToEnd;
                this.pause = false;
                setPerformanceOptions(PerformanceOptions.ALL);
            }

            protected Task start(){
                start = npc.getLocation();
                this.lastGazeTrackingType = npc.getGazeTrackingType();
                this.lastNPCPose = npc.getPose();
                if(lookToEnd) npc.setGazeTrackingType(NPC.GazeTrackingType.NONE);
                taskID = Bukkit.getScheduler().runTaskTimer(PlayerNPCPlugin.getInstance(), ()-> {
                    if(pause) return;
                    tick();
                },1 ,1).getTaskId();
                NPC.Events.StartMove npcStartMoveEvent = new NPC.Events.StartMove(npc, start, end, taskID);
                if(npcStartMoveEvent.isCancelled()){
                    cancel(NPC.Move.Task.CancelCause.CANCELLED);
                    npc.teleport(start);
                }
                return this;
            }

            private void tick(){
                if(npc instanceof NPC.Personal && !((Personal) npc).isCreated()){
                    cancel(CancelCause.ERROR);
                    return;
                }
                if(npc.getX().equals(end.getX()) && npc.getZ().equals(end.getZ())){
                    cancel(NPC.Move.Task.CancelCause.SUCCESS);
                    return;
                }
                double moveX, moveY, moveZ;
                moveX = calculateMovement(npc.getX(), end.getX());
                moveZ = calculateMovement(npc.getZ(), end.getZ());
                if(moveX != 0.00 && moveZ != 0.00){
                    if(Math.abs(moveX) > npc.getMoveSpeed() / 1.7) moveX = moveX / 1.7;
                    if(Math.abs(moveZ) > npc.getMoveSpeed() / 1.7) moveZ = moveZ / 1.7;
                }
                Location from = asLocation(npc.getX(), npc.getY() + 0.1, npc.getZ());
                Location to = asLocation(npc.getX() + moveX, npc.getY() + 0.1, npc.getZ() + moveZ);
                double locY = npc.getY();
                Block blockInLegFrom = from.getBlock();
                Block blockInFootFrom = blockInLegFrom.getRelative(BlockFace.DOWN);
                Block blockInLeg = to.getBlock();
                Block blockInFoot = blockInLeg.getRelative(BlockFace.DOWN);
                boolean uppingLadder = false;
                boolean falling = false;
                boolean jumpingBlock = false;
                if(blockInLeg.getType().isSolid() || isStair(blockInLegFrom)){
                    //JUMP
                    double jump = 1.0;
                    if(isStair(blockInLeg) || isSlab(blockInLeg)) jump = 0.5;
                    else if(checkLadders && isLadder(blockInLegFrom)) uppingLadder = true;
                    else jumpingBlock = true;
                    locY = blockInLeg.getY() + jump;
                }
                else{
                    if(blockInFoot.getType().isSolid()){
                        //ADJUST AIR BETWEEN FLOOR AND FOOT
                        if(isSlab(blockInFoot)) locY = blockInFoot.getY() + 0.5;
                        else locY = blockInLeg.getY();
                    }
                    else{
                        //FALL
                        if(!blockInFootFrom.getType().isSolid()){
                            locY = blockInFoot.getY() - 0.1;
                            falling = true;
                        }
                    }
                }
                if(checkSwimming){
                    if(blockInLeg.getType().equals(Material.WATER) && blockInFoot.getType().equals(Material.WATER)){
                        if(!npc.getPose().equals(Pose.SWIMMING)) npc.setPose(Pose.SWIMMING).update();
                    }
                    else if(npc.getPose().equals(Pose.SWIMMING)){
                        if(lastNPCPose.equals(Pose.CROUCHING)) npc.setPose(Pose.CROUCHING).update();
                        else npc.setPose(Pose.STANDING).update();
                    }
                }
                if(checkSlabCrouching){
                    if((!blockInLeg.getType().isSolid() && isSlab(blockInLeg.getRelative(BlockFace.UP), Slab.Type.TOP)) || (isSlab(blockInLeg) && isSlab(blockInLeg.getRelative(BlockFace.UP).getRelative(BlockFace.UP), Slab.Type.BOTTOM))){
                        if(!npc.getPose().equals(Pose.CROUCHING)) npc.setPose(Pose.CROUCHING).update();
                    }
                    else if(npc.getPose().equals(Pose.CROUCHING) && lastNPCPose != Pose.CROUCHING) npc.setPose(Pose.STANDING).update();
                }
                if(checkSlowness){
                    if(blockInLeg.getType().equals(Material.COBWEB) || blockInLeg.getRelative(BlockFace.UP).getType().equals(Material.COBWEB)){
                        moveX = moveX / 4;
                        moveZ = moveZ / 4;
                    }
                    if(blockInFoot.getType().equals(Material.SOUL_SAND)){
                        moveX = moveX / 2;
                        moveZ = moveZ / 2;
                    }
                }
                //
                if(npc.getPose().equals(Pose.SWIMMING)){
                    moveX = moveX * 3;
                    moveZ = moveZ * 3;
                    locY = blockInLeg.getY() + 0.5;
                }
                if(npc.getPose().equals(Pose.CROUCHING)){
                    moveX = moveX / 3;
                    moveZ = moveZ / 3;
                }
                moveY = locY - npc.getY();
                if(moveY > 1.0) moveY = 1.0;
                if(moveY < -1.0) moveY = -1.0;
                if(uppingLadder){
                    moveX = 0.00;
                    moveZ = 0.00;
                    if(moveY > 0.01) moveY = 0.05;
                    else if(moveY < -0.01) moveY = -0.05;
                }
                if(jumpingBlock){
                    moveX = moveX / 2;
                    moveZ = moveZ / 2;
                    if(moveY > 0.2) moveY = 0.2;
                }
                if(falling){
                    moveX = moveX / 4;
                    moveZ = moveZ / 4;
                    if(moveY < -0.4) moveY = -0.4;
                }
                if(false && npc instanceof NPC.Personal){
                    Personal personal = (Personal) npc;
                    NMSPlayer.sendMessage(personal.getPlayer(), "", "", "", "", "", "", "", "", "");
                    personal.getPlayer().sendMessage("Block in leg " + blockInLeg.getType() + " " + blockInLeg.getType().isSolid());
                    personal.getPlayer().sendMessage("Block in foot " + blockInFoot.getType() + " " + blockInFoot.getType().isSolid());
                    personal.getPlayer().sendMessage("moveY " + moveY);
                    if(uppingLadder) personal.getPlayer().sendMessage("§c§lUPPING LADDER");
                    if(jumpingBlock) personal.getPlayer().sendMessage("§c§lJUMPING BLOCK");
                    if(falling) personal.getPlayer().sendMessage("§c§lFALLING");
                }
                if(lookToEnd && npc.getLocation().getWorld().equals(end.getWorld())){
                    npc.lookAt(end);
                    npc.updatePlayerRotation();
                    if(npc.hasPendingUpdate(Updatable.Type.FORCE_UPDATE)) npc.update();
                }
                npc.move(moveX, moveY, moveZ);
            }

            private boolean isLadder(Block block){ return block.getType().name().equals("LADDER") || block.getType().name().equals("VINE"); }

            private boolean isStair(Block block){ return block.getType().name().contains("_STAIRS"); }

            private boolean isSlab(Block block){ return isSlab(block, Slab.Type.BOTTOM); }

            private boolean isSlab(Block block, Slab.Type type){
                return (block.getType().name().contains("_SLAB") && ((Slab) block.getBlockData()).getType().equals(type))
                        || block.getType().name().contains("_BED");
            }

            public boolean isCheckSwimming() { return checkSwimming; }

            public Task setCheckSwimming(boolean checkSwimming) {
                this.checkSwimming = checkSwimming;
                return this;
            }

            public boolean isCheckSlabCrouching() { return checkSlabCrouching; }

            public Task setCheckSlabCrouching(boolean checkSlabCrouching) {
                this.checkSlabCrouching = checkSlabCrouching;
                return this;
            }

            public boolean isCheckSlowness() { return checkSlowness; }

            public Task setCheckSlowness(boolean checkSlowness) {
                this.checkSlowness = checkSlowness;
                return this;
            }

            public boolean isCheckLadders() { return checkLadders; }

            public Task setCheckLadders(boolean checkLadders) {
                this.checkLadders = checkLadders;
                return this;
            }

            public Task setPerformanceOptions(PerformanceOptions performanceOptions){
                if(performanceOptions == null) performanceOptions = PerformanceOptions.ALL;
                switch (performanceOptions){
                    case ALL -> {
                        setCheckLadders(true);
                        setCheckSlowness(true);
                        setCheckSlabCrouching(true);
                        setCheckSwimming(true);
                        break;
                    }
                    case NONE -> {
                        setCheckLadders(false);
                        setCheckSlowness(false);
                        setCheckSlabCrouching(false);
                        setCheckSwimming(false);
                        break;
                    }
                }
                return this;
            }

            private double calculateMovement(double startPoint, double endPoint){
                double move = 0.00;
                if(startPoint == endPoint) return move;
                double maxMove = npc.getMoveSpeed();
                if(endPoint < startPoint){
                    move = -maxMove;
                    if(endPoint-startPoint < -move) move = endPoint-startPoint;
                }
                else if(endPoint > startPoint){
                    move = maxMove;
                    if(endPoint-startPoint > move) move = endPoint-startPoint;
                }
                if(move > maxMove) move = maxMove;
                else if(move < -maxMove) move = -maxMove;
                return move;
            }

            private Location asLocation(double x, double y, double z){ return new Location(npc.getWorld(), x, y, z); }

            public void pause(){
                this.pause = true;
                if(lookToEnd) npc.setGazeTrackingType(lastGazeTrackingType);
            }

            public void resume(){
                this.pause = false;
                if(lookToEnd) npc.setGazeTrackingType(NPC.GazeTrackingType.NONE);
            }

            public boolean isPaused() { return pause; }

            public void cancel(){ cancel(NPC.Move.Task.CancelCause.CANCELLED); }

            protected void cancel(NPC.Move.Task.CancelCause cancelCause){
                if(taskID == null) return;
                NPC.Events.FinishMove npcFinishMoveEvent = new NPC.Events.FinishMove(npc, start,end, taskID, cancelCause);
                if(lookToEnd && cancelCause.equals(CancelCause.SUCCESS)){
                    npc.setGazeTrackingType(lastGazeTrackingType);
                    npc.updateMove();
                }
                Bukkit.getScheduler().cancelTask(taskID);
                this.taskID = null;
                npc.updateLocation();
                npc.clearMoveTask();
                if(npc.getMoveBehaviour().getType().equals(Behaviour.Type.CUSTOM_PATH) && npc.getMoveBehaviour().path != null){
                    npc.getMoveBehaviour().path.next();
                }
            }

            public boolean hasStarted(){ return taskID != null && start != null; }

            public NPC getNPC() { return npc; }

            public enum PerformanceOptions {
                ALL,
                NONE,
            }

            public enum CancelCause{
                SUCCESS,
                CANCELLED,
                ERROR,
            }
        }
    }

    public static class Events {

        private Events(){}

        protected abstract static class Event extends org.bukkit.event.Event {

            private static final HandlerList HANDLERS_LIST = new HandlerList();
            private final NPC npc;

            protected Event(NPC npc){ this.npc = npc; }

            public NPC getNPC() { return npc; }

            @Override
            public HandlerList getHandlers() { return HANDLERS_LIST; }

            public static HandlerList getHandlerList(){ return HANDLERS_LIST; }

            protected abstract static class Player extends NPC.Events.Event{

                private final org.bukkit.entity.Player player;

                protected Player(org.bukkit.entity.Player player, NPC.Personal npc) {
                    super(npc);
                    this.player = player;
                }

                @Override
                public NPC.Personal getNPC(){ return (NPC.Personal) super.getNPC(); }

                public org.bukkit.entity.Player getPlayer() { return player; }
            }
        }

        public static class FinishMove extends NPC.Events.Event {

            private final Location start;
            private final Location end;
            private final int taskID;
            private final NPC.Move.Task.CancelCause cancelCause;

            protected FinishMove(NPC npc, Location start, Location end, int taskID, NPC.Move.Task.CancelCause cancelCause) {
                super(npc);
                this.start = start;
                this.end = end;
                this.taskID = taskID;
                this.cancelCause = cancelCause;
                Bukkit.getPluginManager().callEvent(this);
            }

            public Location getStart() { return start; }

            public Location getEnd() { return end; }

            public int getTaskID() { return taskID; }

            public NPC.Move.Task.CancelCause getCancelCause() { return cancelCause; }

        }

        public static class Hide extends NPC.Events.Event.Player implements Cancellable {

            private boolean isCancelled;

            protected Hide(org.bukkit.entity.Player player, NPC.Personal npc) {
                super(player, npc);
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }

        public static class Interact extends NPC.Events.Event.Player implements Cancellable{

            private final NPC.Interact.ClickType clickType;
            private boolean isCancelled;

            protected Interact(org.bukkit.entity.Player player, NPC.Personal npc, NPC.Interact.ClickType clickType) {
                super(player, npc);
                this.clickType = clickType;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public NPC.Interact.ClickType getClickType() { return clickType; }

            public boolean isRightClick(){ return clickType.equals(NPC.Interact.ClickType.RIGHT_CLICK); }

            public boolean isLeftClick(){ return clickType.equals(NPC.Interact.ClickType.LEFT_CLICK); }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }

        public static class Move extends Event implements Cancellable{

            private final Location to;
            private boolean isCancelled;

            protected Move(NPC npc, Location to) {
                super(npc);
                this.to = to;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public Location getFrom(){ return getNPC().getLocation(); }

            public Location getTo() { return to; }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }

        public static class Show extends Event.Player implements Cancellable {

            private boolean isCancelled;

            protected Show(org.bukkit.entity.Player player, NPC.Personal npc) {
                super(player, npc);
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }

        public static class Teleport extends Event implements Cancellable {

            private final Location to;
            private boolean isCancelled;

            protected Teleport(NPC npc, Location to) {
                super(npc);
                this.to = to;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public Location getFrom(){ return getNPC().getLocation(); }

            public Location getTo() { return to; }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }

        public static class StartMove extends Event implements Cancellable {

            private final Location start;
            private final Location end;
            private final int taskID;
            private boolean isCancelled;

            protected StartMove(NPC npc, Location start, Location end, int taskID) {
                super(npc);
                this.start = start;
                this.end = end;
                this.taskID = taskID;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public Location getStart() { return start; }

            public Location getEnd() { return end; }

            public int getTaskID() { return taskID; }

            @Override
            public boolean isCancelled() { return isCancelled; }

            @Override
            public void setCancelled(boolean arg) { isCancelled = arg; }

        }
    }

    public static class Interact {

        private Interact(){}

        private static void test(ClickAction clickAction, Player player){ clickAction.execute(player); }

        public abstract static class ClickAction{

            protected final NPC npc;
            protected final NPC.Interact.Actions.Type actionType;
            protected final HashMap<Player, Long> lastTimeExecuted;
            protected boolean enabled;
            protected NPC.Interact.ClickType clickType;
            protected BiConsumer<NPC, Player> action;
            protected Long delayTicks;
            protected Long cooldownMilliseconds;
            protected BukkitTask executingTask;
            protected List<Conditions.Condition> conditions;

            protected ClickAction(NPC npc, NPC.Interact.Actions.Type actionType, NPC.Interact.ClickType clickType) {
                this.npc = npc;
                this.actionType = actionType;
                if(clickType == null) clickType = ClickType.EITHER;
                this.clickType = clickType;
                this.delayTicks = 0L;
                this.enabled = true;
                this.cooldownMilliseconds = npc.getInteractCooldown();
                this.lastTimeExecuted = new HashMap<>();
                this.conditions = new ArrayList<>();
            }

            public String getReplacedString(Player player, String s) { return NPC.Placeholders.replace(npc, player, s); }

            public NPC getNPC() { return npc; }

            public NPC.Interact.Actions.Type getActionType() { return actionType; }

            public NPC.Interact.ClickType getClickType() {
                if(clickType == null) return ClickType.EITHER;
                return clickType;
            }

            protected void execute(Player player){
                if(!enabled) return;
                if(executingTask != null) return;
                if(delayTicks >= 1) executingTask = Bukkit.getScheduler().runTaskLater(npc.getPlugin(), () -> executeAction(player), delayTicks);
                else executeAction(player);
            }

            private void executeAction(Player player){
                if(lastTimeExecuted.containsKey(player) && System.currentTimeMillis() - lastTimeExecuted.get(player) < cooldownMilliseconds) return;
                for(Conditions.Condition condition : conditions){
                    if(!condition.test(npc, player)) return;
                }
                action.accept(npc, player);
                lastTimeExecuted.put(player, System.currentTimeMillis());
                clearExecutingTask();
            }

            private void clearExecutingTask(){
                if(executingTask != null && !executingTask.isCancelled()) executingTask.cancel();
                executingTask = null;
            }

            public Long getDelayTicks() { return delayTicks; }

            public void setDelayTicks(Long delayTicks) {
                if(delayTicks < 0) delayTicks = 0L;
                this.delayTicks = delayTicks;
            }

            public Long getCooldownMilliseconds() { return cooldownMilliseconds; }

            public void setCooldownMilliseconds(Long cooldownMilliseconds) {
                if(cooldownMilliseconds < npc.getInteractCooldown()) cooldownMilliseconds = npc.getInteractCooldown();
                this.cooldownMilliseconds = cooldownMilliseconds;
            }

            public boolean isEnabled() { return enabled; }

            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public void setClickType(@Nonnull ClickType clickType) {
                Validate.notNull(clickType);
                this.clickType = clickType;
            }

            public List<Conditions.Condition> getConditions() { return conditions; }

            public void addCondition(Conditions.Condition condition){ this.conditions.add(condition); }
        }

        public static class Actions{

            private Actions() {}

            public enum Type {
                CONSOLE_PERFORM_COMMAND,
                CUSTOM_ACTION,
                NPC_PLAY_ANIMATION,
                NPC_SET_CUSTOM_DATA,
                @Deprecated PLAYER_CLEAR_POTION_EFFECT,
                PLAYER_CONNECT_BUNGEE_SERVER,
                PLAYER_GIVE_ITEM,
                @Deprecated PLAYER_GIVE_KIT,
                PLAYER_GIVE_MONEY,
                @Deprecated PLAYER_GIVE_POTION_EFFECT,
                PLAYER_OPEN_BOOK,
                @Deprecated PLAYER_OPEN_ENCHANTING, //Not working
                PLAYER_OPEN_WORKBENCH,
                PLAYER_PERFORM_COMMAND,
                PLAYER_PLAY_SOUND,
                PLAYER_SEND_ACTIONBAR_MESSAGE,
                PLAYER_SEND_CHAT_MESSAGE,
                PLAYER_SEND_TITLE_MESSAGE,
                PLAYER_TELEPORT_TO_LOCATION,
                PLAYER_WITHDRAW_MONEY,
                ;

                public String getName(){ return StringUtils.getFirstCharUpperCase(name().replaceAll("_", " "), true).replaceAll("Npc", "NPC"); }

                public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }

                public boolean requiresVaultEconomy(){ return Arrays.asList(PLAYER_WITHDRAW_MONEY, PLAYER_GIVE_MONEY).contains(this); }

                public String getRequiredDependency(){
                    String requiredDependency = null;
                    if(this.requiresVaultEconomy() && (!IntegrationsManager.isUsingVault() || !IntegrationsManager.getVault().isUsingEconomy())) requiredDependency = "Vault Economy";
                    return requiredDependency;
                }
            }

            @Deprecated
            protected enum LegacyType {
                CONNECT_BUNGEE_SERVER(Type.PLAYER_CONNECT_BUNGEE_SERVER),
                CUSTOM_ACTION(Type.CUSTOM_ACTION),
                GIVE_PLAYER_ITEM(Type.PLAYER_GIVE_ITEM),
                RUN_CONSOLE_COMMAND(Type.CONSOLE_PERFORM_COMMAND),
                RUN_PLAYER_COMMAND(Type.PLAYER_PERFORM_COMMAND),
                SEND_ACTIONBAR_MESSAGE(Type.PLAYER_SEND_ACTIONBAR_MESSAGE),
                SEND_CHAT_MESSAGE(Type.PLAYER_SEND_CHAT_MESSAGE),
                SEND_TITLE_MESSAGE(Type.PLAYER_SEND_TITLE_MESSAGE),
                SET_CUSTOM_DATA(Type.NPC_SET_CUSTOM_DATA),
                TELEPORT_PLAYER_TO_LOCATION(Type.PLAYER_TELEPORT_TO_LOCATION),
                OPEN_BOOK(Type.PLAYER_OPEN_BOOK),
                ;

                protected final Type type;

                LegacyType(Type type){
                    this.type = type;
                }
            }

            public static class CustomAction extends ClickAction{

                protected CustomAction(NPC npc, NPC.Interact.ClickType clickType, BiConsumer<NPC, org.bukkit.entity.Player> customAction) {
                    super(npc, NPC.Interact.Actions.Type.CUSTOM_ACTION, clickType);
                    super.action = customAction;
                }

            }

            public static class SetCustomData extends ClickAction{

                private final Plugin pluginKey;
                private final String simpleKey;
                private final String value;
                private boolean checkIfSame;

                protected SetCustomData(NPC npc, NPC.Interact.ClickType clickType, Plugin pluginKey, String simpleKey, String value) {
                    super(npc, Type.NPC_SET_CUSTOM_DATA, clickType);
                    this.pluginKey = pluginKey;
                    this.simpleKey = simpleKey;
                    this.value = value;
                    this.checkIfSame = false;
                    super.action = (npc1, player) -> {
                        String replacedKey = getReplacedString(player, getSimpleKey());
                        String replacedValue = getReplacedString(player, getValue());
                        NPCLib.Registry.ID id = NPCLib.Registry.ID.of(getPluginKey(), replacedKey);
                        if(checkIfSame && npc1.hasCustomData(id) && npc1.grabCustomData(id).get().equals(replacedValue)) return;
                        npc1.setCustomData(id, replacedValue);
                    };
                }

                public boolean isCheckIfSame() {
                    return checkIfSame;
                }

                public void setCheckIfSame(boolean checkIfSame) {
                    this.checkIfSame = checkIfSame;
                }

                public Plugin getPluginKey() { return pluginKey; }

                public String getSimpleKey() {
                    return simpleKey;
                }

                public String getValue() {
                    return value;
                }
            }

            public static class PlayAnimation extends ClickAction{

                private final Animation animation;

                protected PlayAnimation(NPC npc, NPC.Interact.ClickType clickType, Animation animation) {
                    super(npc, Type.NPC_PLAY_ANIMATION, clickType);
                    this.animation = animation;
                    super.action = (npc1, player) -> npc1.playAnimation(getAnimation());
                }

                public Animation getAnimation() {
                    return animation;
                }

            }

            public static class Player{

                private Player(){}

                public static class PlaySound extends ClickAction{

                    private Sound sound;
                    private float volume;
                    private float pitch;

                    protected PlaySound(NPC npc, ClickType clickType, Sound sound) {
                        super(npc, Type.PLAYER_PLAY_SOUND, clickType);
                        this.sound = sound;
                        super.action = (npc1, player) -> player.playSound(npc1.getLocation(), getSound(), getVolume(), getPitch());
                    }

                    public Sound getSound() {
                        return sound;
                    }

                    public float getVolume() {
                        return volume;
                    }

                    public float getPitch() {
                        return pitch;
                    }

                    public void setVolume(Float soundVolume) {
                        if(soundVolume <= 0.1) soundVolume = 0.1F;
                        if(soundVolume >= 1.0) soundVolume = 1.0F;
                        this.volume = (float) (Math.round(soundVolume * 10.0) / 10.0);
                    }

                    public void setPitch(Float soundPitch) {
                        if(soundPitch <= 0.5) soundPitch = 0.5F;
                        if(soundPitch >= 2.0) soundPitch = 2.0F;
                        this.pitch = (float) (Math.round(soundPitch * 10.0) / 10.0);
                    }
                }

                public static class SendChatMessage extends ClickAction{

                    private final String[] messages;

                    protected SendChatMessage(NPC npc, NPC.Interact.ClickType clickType, String... message) {
                        super(npc, Type.PLAYER_SEND_CHAT_MESSAGE, clickType);
                        this.messages = message;
                        super.action = (npc1, player) -> Arrays.stream(getMessages()).toList().forEach(x-> player.sendMessage(getReplacedString(player,x)));
                    }

                    public String[] getMessages() {
                        return messages;
                    }

                }

                public static class PerformCommand extends NPC.Interact.Actions.Command{

                    protected PerformCommand(NPC npc, NPC.Interact.ClickType clickType, String command) {
                        super(npc, NPC.Interact.Actions.Type.PLAYER_PERFORM_COMMAND, clickType, command);
                        super.action = (npc1, player) -> Bukkit.getServer().dispatchCommand(player, getReplacedString(player, super.getCommand()));
                    }

                }

                public static class SendTitleMessage extends ClickAction{

                    private final String title;
                    private final String subtitle;
                    private Integer fadeIn;
                    private Integer stay;
                    private Integer fadeOut;

                    protected SendTitleMessage(NPC npc, NPC.Interact.ClickType clickType, String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
                        super(npc, Type.PLAYER_SEND_TITLE_MESSAGE, clickType);
                        this.title = title;
                        this.subtitle = subtitle;
                        this.fadeIn = fadeIn;
                        this.stay = stay;
                        this.fadeOut = fadeOut;
                        super.action = (npc1, player) -> player.sendTitle("§f" + getReplacedString(player,getTitle()), getReplacedString(player,getSubtitle()), getFadeIn(), getStay(), getFadeOut());
                    }

                    public String getTitle() {
                        return title;
                    }

                    public String getSubtitle() {
                        return subtitle;
                    }

                    public Integer getFadeIn() {
                        return fadeIn;
                    }

                    public Integer getStay() {
                        return stay;
                    }

                    public Integer getFadeOut() {
                        return fadeOut;
                    }

                    public void setFadeIn(Integer fadeIn) {
                        this.fadeIn = fadeIn;
                    }

                    public void setStay(Integer stay) {
                        this.stay = stay;
                    }

                    public void setFadeOut(Integer fadeOut) {
                        this.fadeOut = fadeOut;
                    }

                }

                public static class SendActionBarMessage extends ClickAction{

                    private final String message;

                    protected SendActionBarMessage(NPC npc, NPC.Interact.ClickType clickType, String message) {
                        super(npc, Type.PLAYER_SEND_ACTIONBAR_MESSAGE, clickType);
                        this.message = message;
                        super.action = (npc1, player) -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(getReplacedString(player, getMessage())));
                    }

                    public String getMessage() {
                        return message;
                    }
                }

                public static class ConnectBungeeServer extends ClickAction{

                    private final String server;

                    protected ConnectBungeeServer(NPC npc, NPC.Interact.ClickType clickType, String server) {
                        super(npc, Type.PLAYER_CONNECT_BUNGEE_SERVER, clickType);
                        this.server = server;
                        super.action = (npc1, player) -> {
                            if(!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(npc1.getPlugin(), "BungeeCord")) Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(npc1.getPlugin(), "BungeeCord");
                            ByteArrayDataOutput out = ByteStreams.newDataOutput();
                            out.writeUTF("Connect");
                            out.writeUTF(getReplacedString(player, getServer()));
                            player.sendPluginMessage(npc1.getPlugin(), "BungeeCord", out.toByteArray());
                        };
                    }

                    public String getServer() {
                        return server;
                    }
                }

                public static class TeleportToLocation extends ClickAction{

                    private final Location location;

                    protected TeleportToLocation(NPC npc, NPC.Interact.ClickType clickType, Location location) {
                        super(npc, Type.PLAYER_TELEPORT_TO_LOCATION, clickType);
                        this.location = location;
                        super.action = (npc1, player) -> player.teleport(getLocation());
                    }

                    public Location getLocation() {
                        return location;
                    }
                }

                public static class GiveItem extends ClickAction{

                    private final ItemStack itemStack;
                    private GiveType giveType;

                    protected GiveItem(NPC npc, NPC.Interact.ClickType clickType, ItemStack itemStack) {
                        super(npc, Type.PLAYER_GIVE_ITEM, clickType);
                        this.itemStack = itemStack;
                        setGiveType(GiveType.ADD_ITEM);
                    }

                    public void setGiveType(GiveType giveType) {
                        if(giveType.equals(GiveType.ADD_ITEM)) super.action = (npc1, player) -> player.getInventory().addItem(getItemStack());
                        if(giveType.equals(GiveType.SET_ITEM_MAIN_HAND)) super.action = (npc1, player) -> player.getInventory().setItemInMainHand(getItemStack());
                        if(giveType.equals(GiveType.SET_ITEM_OFF_HAND)) super.action = (npc1, player) -> player.getInventory().setItemInOffHand(getItemStack());
                        if(giveType.equals(GiveType.SET_EQUIPMENT)) super.action = (npc1, player) -> {
                            if(getItemStack().getType().name().contains("_HELMET")) player.getInventory().setHelmet(getItemStack());
                            if(getItemStack().getType().name().contains("_CHESTPLATE")) player.getInventory().setChestplate(getItemStack());
                            if(getItemStack().getType().name().contains("_LEGGINGS")) player.getInventory().setLeggings(getItemStack());
                            if(getItemStack().getType().name().contains("_BOOTS")) player.getInventory().setBoots(getItemStack());
                        };
                        this.giveType = giveType;
                    }

                    public ItemStack getItemStack() {
                        return itemStack;
                    }

                    public GiveType getGiveType() {
                        return giveType;
                    }

                    public enum GiveType{
                        ADD_ITEM, SET_ITEM_MAIN_HAND, SET_ITEM_OFF_HAND, SET_EQUIPMENT;
                    }
                }

                public static class GiveKit extends ClickAction{

                    private final NPC.Inventory.Kit kit;

                    protected GiveKit(NPC npc, NPC.Interact.ClickType clickType, NPC.Inventory.Kit kit) {
                        super(npc, Type.PLAYER_GIVE_KIT, clickType);
                        this.kit = kit;
                        super.action = (npc1, player) -> getKit().giveKit(player);
                    }

                    public Inventory.Kit getKit() {
                        return kit;
                    }
                }

                public static class OpenWorkbench extends ClickAction{

                    protected OpenWorkbench(NPC npc, ClickType clickType) {
                        super(npc, Type.PLAYER_OPEN_WORKBENCH, clickType);
                        super.action = (npc1, player) -> player.openWorkbench(player.getLocation(), true);
                    }

                }

                @Deprecated
                public static class OpenEnchanting extends ClickAction{

                    protected OpenEnchanting(NPC npc, ClickType clickType) {
                        super(npc, Type.PLAYER_OPEN_ENCHANTING, clickType);
                        super.action = (npc1, player) -> player.openEnchanting(player.getLocation(), true);
                    }

                }

                public static class OpenBook extends ClickAction{

                    protected ItemStack book;

                    protected OpenBook(NPC npc, ClickType clickType, ItemStack book) {
                        super(npc, Type.PLAYER_OPEN_BOOK, clickType);
                        this.book = book;
                        super.action = (npc1, player) -> player.openBook(getBook());
                    }

                    public ItemStack getBook() {
                        return book;
                    }
                }


                public static class GiveMoney extends Money{

                    public GiveMoney(NPC npc, ClickType clickType, Double balance) {
                        super(npc, Type.PLAYER_GIVE_MONEY, clickType, balance);
                        this.balance = balance;
                        super.action = (npc1, player) -> {
                            if(!IntegrationsManager.isUsingVault() || !IntegrationsManager.getVault().isUsingEconomy()) return;
                            IntegrationsManager.getVault().getEconomyManager().addBalance(player, getBalance());
                        };
                    }

                }

                public static class WithdrawMoney extends Money {

                    public WithdrawMoney(NPC npc, ClickType clickType, Double balance) {
                        super(npc, Type.PLAYER_WITHDRAW_MONEY, clickType, balance);
                        super.action = (npc1, player) -> {
                            if(!IntegrationsManager.isUsingVault() || !IntegrationsManager.getVault().isUsingEconomy()) return;
                            IntegrationsManager.getVault().getEconomyManager().withdrawBalance(player, Math.min(IntegrationsManager.getVault().getEconomyManager().getBalance(player), getBalance()));
                        };
                    }

                }

            }

            public static class Console{

                private Console() {}

                public static class PerformCommand extends NPC.Interact.Actions.Command{

                    protected PerformCommand(NPC npc, NPC.Interact.ClickType clickType, String command) {
                        super(npc, Type.CONSOLE_PERFORM_COMMAND, clickType, command);
                        super.action = (npc1, player) -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), getReplacedString(player, super.getCommand()));
                    }

                }
            }

            public static abstract class Command extends ClickAction {

                private final String command;

                protected Command(NPC npc, NPC.Interact.Actions.Type actionType, NPC.Interact.ClickType clickType, String command) {
                    super(npc, actionType, clickType);
                    this.command = command;
                }

                public String getCommand() {
                    return command;
                }
            }

            public static abstract class Money extends ClickAction {

                protected Double balance;

                protected Money(NPC npc, NPC.Interact.Actions.Type actionType, NPC.Interact.ClickType clickType, Double balance) {
                    super(npc, actionType, clickType);
                    this.balance = balance;
                }

                public Double getBalance() {
                    return balance;
                }
            }

        }

        public enum ClickType{
            RIGHT_CLICK, LEFT_CLICK, @Deprecated EITHER;

            public boolean isRightClick(){ return this.equals(RIGHT_CLICK); }

            public boolean isLeftClick(){ return this.equals(LEFT_CLICK); }

            public ClickType getInvert(){
                if(this.equals(RIGHT_CLICK)) return LEFT_CLICK;
                if(this.equals(LEFT_CLICK)) return RIGHT_CLICK;
                return null;
            }

        }
    }

    public static class Inventory{

        private Inventory(){}

        public static class Kit implements ConfigurationSerializable {

            private HashMap<Integer, ItemStack> items;

            public Kit() {
                this.items = new HashMap<>();
            }

            public Kit(@Nonnull PlayerInventory playerInventory){
                this();
                for(int i = 0; i < 36; i++){
                    ItemStack itemStack = playerInventory.getItem(i);
                    if(itemStack == null || itemStack.getType().equals(Material.AIR)) continue;
                    items.put(i, playerInventory.getItem(i));
                }
                items.put(36, playerInventory.getItemInOffHand());
                items.put(37, playerInventory.getHelmet());
                items.put(38, playerInventory.getChestplate());
                items.put(39, playerInventory.getLeggings());
                items.put(40, playerInventory.getBoots());
            }

            public Kit(@Nonnull Player player){
                this(player.getInventory());
            }

            public void setItem(Integer slot, ItemStack itemStack){
                if(itemStack == null || itemStack.getType().equals(Material.AIR)) return;
                items.put(slot, itemStack);
            }

            public void giveKit(Player player){
                for(int i = 0; i < 36; i++){
                    ItemStack itemStack = items.get(i);
                    if(itemStack == null || itemStack.getType().equals(Material.AIR)) continue;
                    player.getInventory().setItem(i, itemStack);
                }
            }

            public static void giveKit(Kit kit, Player player){ kit.giveKit(player); }

            @Override
            public Map<String, Object> serialize() {
                return null;
            }

        }
    }

    public static class Hologram {

        private final NPC.Personal npc;
        private final Player player;
        private Location location;
        private HashMap<Integer, List<EntityArmorStand>> lines;
        private EntityItem item;
        private boolean shownToClient;

        protected Hologram(NPC.Personal npc, Player player) {
            this.npc = npc;
            this.player = player;
            this.shownToClient = false;
            create();
        }

        private void create(){
            this.lines = new HashMap<>();
            this.location = new Location(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ()).add(npc.getTextAlignment());
            WorldServer world = null;
            try{ world = (WorldServer) NMSCraftWorld.getCraftWorldGetHandle().invoke(NMSCraftWorld.getCraftWorldClass().cast(location.getWorld()));}catch (Exception e){}
            if(world == null) return;
            for (int i = 1; i <= getText().size(); i++) {
                createLine(world);
                setLine(i, getText().get(i-1));
            }
            if(npc.getTextItem() != null && !npc.getTextItem().getType().isAir()){
                item = new EntityItem(world, location.getX(), location.getY() + (npc.getTextLineSpacing() * ((getText().size() + 1))), location.getZ(), NMSCraftItemStack.asNMSCopy(npc.getTextItem()));
                NMSEntity.setNoGravity(item, true);
            }
        }

        protected void createLine(WorldServer world) {
            int line = 1;
            while(lines.containsKey(line)) line++;
            NPC.Hologram.Opacity textOpacity = getLinesOpacity().getOrDefault(line, npc.getTextOpacity());
            List<EntityArmorStand> armorStands = new ArrayList<>();
            for(int i = 1; i <= textOpacity.getTimes(); i++){
                EntityArmorStand armor = new EntityArmorStand(world, location.getX(), location.getY() + (npc.getTextLineSpacing() * ((getText().size() - line))), location.getZ());
                NMSEntity.setCustomNameVisible(armor, true);
                NMSEntity.setNoGravity(armor, true);
                NMSEntity.setCustomName(armor, "§f");
                NMSEntityArmorStand.setInvisible(armor, true);
                NMSEntityArmorStand.setMarker(armor, true);
                armorStands.add(armor);
            }
            lines.put(line, armorStands);
        }


        protected void setLine(int line, String text) {
            if(!lines.containsKey(line)) return;
            String replacedText = NPC.Placeholders.replace(npc, player, text);
            replacedText = ColorUtils.formatHexColor(replacedText);
            for(EntityArmorStand as : lines.get(line)){
                NMSEntity.setNoGravity(as, true);
                NMSEntityArmorStand.setInvisible(as, true);
                NMSEntity.setCustomName(as, replacedText);
                NMSEntity.setCustomNameVisible(as, text != null && text != "");
            }
        }

        protected String getLine(int line) {
            if(!lines.containsKey(line)) return "";
            return NMSEntity.getCustomName(lines.get(line).get(0)).getString();
        }

        protected boolean hasLine(int line){
            return lines.containsKey(line);
        }

        protected void show(){
            if(shownToClient) return;
            if(npc.isHiddenText()) return;
            if(!npc.isInRange()) return;
            if(!npc.isShownOnClient()) return;
            for(Integer line : lines.keySet()){
                for(EntityArmorStand armor : lines.get(line)){
                    NMSPacketPlayOutSpawnEntity.sendPacketEntityLiving(player, armor);
                    NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityMetadata.getPacket(armor, NMSEntity.getDataWatcher(armor)));
                }
            }
            if(item != null){
                if(npc.getTextItem() != null && !npc.getTextItem().getType().isAir()){
                    NMSEntityItem.setItem(item, npc.getTextItem());
                    NMSEntity.setGlowingTag(item, npc.isTextItemGlowing());
                    updateScoreboard();
                    NMSPacketPlayOutSpawnEntity.sendPacketEntity(player, item);
                    NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityMetadata.getPacket(item, NMSEntity.getDataWatcher(item)));
                }
                else{ NMSPacketPlayOutEntityDestroy.destroyEntity(getPlayer(), item); }
            }
            shownToClient = true;
        }

        protected void hide(){
            if(!shownToClient) return;
            for (Integer in : lines.keySet()) { try{ NMSPacketPlayOutEntityDestroy.destroyEntities(player, lines.get(in)); } catch (Exception e) { NPCLib.printError(e); } }
            if(item != null) NMSPacketPlayOutEntityDestroy.destroyEntity(player, item);
            shownToClient = false;
        }

        protected void move(Vector vector){
            this.location.add(vector);
            PlayerConnection playerConnection = NMSCraftPlayer.getPlayerConnection(getPlayer());
            List<net.minecraft.world.entity.Entity> entities = new ArrayList<>();
            lines.values().forEach(x-> entities.addAll(x));
            if(item != null){ entities.add(item); }
            for(net.minecraft.world.entity.Entity entity : entities){
                double fx = NMSEntity.getX(entity) + vector.getX();
                double fy = NMSEntity.getY(entity) + vector.getY();
                double fz = NMSEntity.getZ(entity) + vector.getZ();
                NMSEntity.move(entity, fx, fy, fz);
                NMSCraftPlayer.sendPacket(playerConnection, new PacketPlayOutEntityTeleport(entity));
            }
        }

        protected void simpleUpdate(){
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
            item = null;
        }

        protected void updateScoreboard(){
            if(npc.getNPCLib().isUsingBukkitScoreboards()){
                updateBukkitScoreboard();
                return;
            }
            Scoreboard scoreboard = NMSCraftScoreboard.getScoreboard(player);
            String entityUUID = NMSEntity.getEntityUUID(item).toString();
            String teamName = npc.getScoreboardTeamName("tI");
            boolean existsTeam = NMSScoreboard.getTeam(scoreboard, teamName) != null;
            ScoreboardTeam scoreboardTeam = existsTeam ? NMSScoreboard.getTeam(scoreboard, teamName) : new ScoreboardTeam(scoreboard, teamName);
            NMSScoreboard.setTeamColor(scoreboardTeam, npc.getTextItemGlowingColor().asMinecraftEnumChatFormat());
            NMSScoreboard.setPlayerTeam(scoreboard, entityUUID, scoreboardTeam);
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, !existsTeam));
        }

        protected void updateBukkitScoreboard(){
            org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();
            if(scoreboard.equals(Bukkit.getScoreboardManager().getMainScoreboard())) return;
            String entityUUID = NMSEntity.getEntityUUID(item).toString();
            String teamName = npc.getScoreboardTeamName("tI");
            boolean existsTeam = scoreboard.getTeam(teamName) != null;
            Team scoreboardTeam = existsTeam ? scoreboard.getTeam(teamName) : scoreboard.registerNewTeam(teamName);
            scoreboardTeam.setColor(npc.getTextItemGlowingColor().asBukkitChatColor());
            scoreboardTeam.addEntry(entityUUID);
        }

        protected boolean isCreatedLine(Integer line){ return lines.containsKey(line); }

        protected boolean isShownToClient(){ return shownToClient; }

        protected Player getPlayer(){ return this.player; }

        protected List<String> getText(){ return npc.getText(); }

        protected HashMap<Integer, NPC.Hologram.Opacity> getLinesOpacity() { return npc.getLinesOpacity(); }

        protected NPC getNPC() { return npc; }

        public enum Opacity implements EnumUtils.GetName{

            LOWEST(1),
            LOW(2),
            MEDIUM(3),
            HARD(4),
            HARDER(6),
            FULL(10)
            ;

            private int times;

            Opacity(int times){ this.times = times; }

            protected int getTimes() { return times; }

            public static Opacity fromName(String name){ return Arrays.stream(values()).filter(x-> x.name().equalsIgnoreCase(name)).findAny().orElse(null); }

        }

    }

    public static class Attributes {

        protected static final Double VARIABLE_MIN_LINE_SPACING = 0.27;
        protected static final Double VARIABLE_MAX_LINE_SPACING = 1.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_XZ = 2.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_Y = 5.00;

        // Skin
        protected NPC.Skin skin;
        protected Skin.VisibleLayers skinVisibleLayers;

        // TabList
        protected String tabListName;
        protected NameTag nameTag;
        protected TabListVisibility tabListVisibility;
        protected Boolean showNameTag;

        // Hologram
        protected List<String> text;
        protected Double textHideDistance;
        protected Double textLineSpacing;
        protected Vector textAlignment;
        protected NPC.Hologram.Opacity textOpacity;
        protected HashMap<Integer, NPC.Hologram.Opacity> textLinesOpacity;
        protected ItemStack textItem;
        protected Boolean textItemGlowing;
        protected NPC.Color textItemGlowingColor;

        // Body
        protected HashMap<NPC.Slot, ItemStack> equipment;
        protected Boolean collidable;
        protected Double hideDistance;
        protected NPC.GazeTrackingType gazeTrackingType;

        //Metadata
        protected Boolean glowing;
        protected NPC.Color glowingColor;
        protected NPC.Pose pose;
        protected Boolean onFire;
        protected Boolean groundParticles;
        protected java.awt.Color potionParticlesColor;
        protected PotionParticlesType potionParticlesType;
        protected Integer arrowsInBody;
        protected Integer beeStingersInBody;
        protected Boolean shaking;
        protected Boolean invisible;

        //Interact
        protected Long interactCooldown;

        //Movement
        protected Double moveSpeed;

        public Attributes(
                Skin skin,
                Skin.VisibleLayers skinVisibleLayers,

                String tabListName,
                NameTag nameTag,
                TabListVisibility tabListVisibility,
                Boolean showNameTag,

                List<String> text,
                Double textHideDistance,
                Double textLineSpacing,
                Vector textAlignment,
                Hologram.Opacity textOpacity,
                HashMap<Integer, Hologram.Opacity> textLinesOpacity,
                ItemStack textItem,
                Boolean textItemGlowing,
                NPC.Color textItemGlowingColor,

                HashMap<Slot, ItemStack> equipment,
                Boolean collidable,
                Double hideDistance,
                GazeTrackingType gazeTrackingType,

                Boolean glowing,
                Color glowingColor,
                Pose pose,
                Boolean onFire,
                Boolean groundParticles,
                java.awt.Color potionParticlesColor,
                PotionParticlesType potionParticlesType,
                Integer arrowsInBody,
                Integer beeStingersInBody,
                Boolean shaking,
                Boolean invisible,

                Long interactCooldown,

                Double moveSpeed
        ) {
            this.skin = skin;
            this.skinVisibleLayers = skinVisibleLayers;

            this.tabListName = tabListName;
            this.nameTag = nameTag;
            this.tabListVisibility = tabListVisibility;
            this.showNameTag = showNameTag;

            this.text = text;
            this.textHideDistance = textHideDistance;
            this.textLineSpacing = textLineSpacing;
            this.textAlignment = textAlignment;
            this.textOpacity = textOpacity;
            this.textLinesOpacity = textLinesOpacity;
            this.textItem = textItem;
            this.textItemGlowing = textItemGlowing;
            this.textItemGlowingColor = textItemGlowingColor;

            this.equipment = equipment;
            this.collidable = collidable;
            this.hideDistance = hideDistance;
            this.gazeTrackingType = gazeTrackingType;

            this.glowing = glowing;
            this.glowingColor = glowingColor;
            this.pose = pose;
            this.onFire = onFire;
            this.groundParticles = groundParticles;
            this.potionParticlesColor = potionParticlesColor;
            this.potionParticlesType = potionParticlesType;
            this.arrowsInBody = arrowsInBody;
            this.beeStingersInBody = beeStingersInBody;
            this.shaking = shaking;
            this.invisible = invisible;

            this.interactCooldown = interactCooldown;

            this.moveSpeed = moveSpeed;
            Arrays.stream(NPC.Slot.values()).filter(x-> !equipment.containsKey(x)).forEach(x-> equipment.put(x, new ItemStack(Material.AIR)));
        }

        private static final Attributes DEFAULT = new Attributes(
                NPC.Skin.Minecraft.getSteveSkin(),
                new Skin.VisibleLayers(),

                "§8[NPC] {id}",
                new NameTag("§8[NPC] ", "{id}", null),
                TabListVisibility.NEVER,
                false,

                new ArrayList<>(),
                50.0,
                0.27,
                new Vector(0, 1.75, 0),
                NPC.Hologram.Opacity.LOWEST,
                new HashMap<>(),
                new ItemStack(Material.AIR),
                false,
                Color.WHITE,

                new HashMap<>(),
                false,
                50.0,
                NPC.GazeTrackingType.NONE,

                false,
                NPC.Color.WHITE,
                NPC.Pose.STANDING,
                false,
                false,
                java.awt.Color.WHITE,
                PotionParticlesType.DISABLED,
                0,
                0,
                false,
                false,

                200L,

                Move.Speed.NORMAL.doubleValue()
        );

        protected Attributes(){}

        protected static Attributes copyOf(Attributes attributes){
            return new Attributes(
                    attributes.getSkin(),
                    attributes.getSkinVisibleLayers().clone(),

                    attributes.getTabListName(),
                    attributes.getNameTag().clone(),
                    attributes.getTabListVisibility(),
                    attributes.isShowNameTag(),

                    attributes.getText(),
                    attributes.getTextHideDistance(),
                    attributes.getTextLineSpacing(),
                    attributes.getTextAlignment().clone(),
                    attributes.getTextOpacity(),
                    (HashMap<Integer, Hologram.Opacity>) attributes.getTextLinesOpacity().clone(),
                    attributes.getTextItem().clone(),
                    attributes.isTextItemGlowing(),
                    attributes.getTextItemGlowingColor(),

                    (HashMap<NPC.Slot, ItemStack>) attributes.getEquipment().clone(),
                    attributes.isCollidable(),
                    attributes.getHideDistance(),
                    attributes.getGazeTrackingType(),

                    attributes.isGlowing(),
                    attributes.getGlowingColor(),
                    attributes.getPose(),
                    attributes.isOnFire(),
                    attributes.isGroundParticles(),
                    attributes.getPotionParticlesColor(),
                    attributes.getPotionParticlesType(),
                    attributes.getArrowsInBody(),
                    attributes.getBeeStingersInBody(),
                    attributes.isShaking(),
                    attributes.isInvisible(),

                    attributes.getInteractCooldown(),

                    attributes.getMoveSpeed()
            );
        }

        public void applyNPC(@Nonnull NPC.Personal npc, boolean forceUpdate){
            applyNPC(npc);
            if(forceUpdate) npc.forceUpdate();
        }

        public void applyNPC(@Nonnull NPC.Personal npc){
            Validate.notNull(npc, "Cannot apply NPC.Attributes to a null NPC.");
            //
            npc.setSkin(this.skin);
            npc.setSkinVisibleLayers(this.skinVisibleLayers);

            npc.setTabListName(this.tabListName);
            npc.setNameTag(this.nameTag.clone());
            npc.setTabListVisibility(this.tabListVisibility);
            npc.setShowNameTag(this.showNameTag);

            npc.setText(this.text);
            npc.setTextHideDistance(this.textHideDistance);
            npc.setTextLineSpacing(this.textLineSpacing);
            npc.setTextAlignment(this.textAlignment.clone());
            npc.setTextOpacity(this.textOpacity);
            npc.setTextLinesOpacity((HashMap<Integer, Hologram.Opacity>) this.textLinesOpacity.clone());
            npc.setTextItem(this.textItem.clone());
            npc.setTextItemGlowing(this.textItemGlowing);
            npc.setTextItemGlowingColor(this.textItemGlowingColor);

            npc.setEquipment((HashMap<NPC.Slot, ItemStack>) this.equipment.clone());
            npc.setCollidable(this.collidable);
            npc.setHideDistance(this.hideDistance);
            npc.setGazeTrackingType(this.gazeTrackingType);

            npc.setGlowing(this.glowing);
            npc.setGlowingColor(this.glowingColor);
            npc.setPose(this.pose);
            npc.setOnFire(this.onFire);
            npc.setGroundParticles(this.groundParticles);
            npc.setPotionParticlesColor(this.potionParticlesColor);
            npc.setPotionParticlesType(this.potionParticlesType);
            npc.setArrowsInBody(this.arrowsInBody);
            npc.setBeeStingersInBody(this.beeStingersInBody);
            npc.setShaking(this.shaking);
            npc.setInvisible(this.invisible);

            npc.setInteractCooldown(this.interactCooldown);

            npc.setMoveSpeed(this.moveSpeed);
        }

        public void applyNPC(@Nonnull Collection<NPC.Personal> npc){
            applyNPC(npc, false);
        }

        public void applyNPC(@Nonnull Collection<NPC.Personal> npc, boolean forceUpdate){
            Validate.notNull(npc, "Cannot apply NPC.Attributes to a null NPC.");
            npc.forEach(x-> applyNPC(x, forceUpdate));
        }

        public static Attributes getDefault(){ return DEFAULT; }

        public static Attributes getNPCAttributes(@Nonnull NPC npc){
            Validate.notNull(npc, "Cannot get NPC.Attributes from a null NPC");
            return npc.getAttributes();
        }

        public NPC.Skin getSkin() { return skin; }

        public static NPC.Skin getDefaultSkin(){ return DEFAULT.getSkin(); }

        public static void setDefaultSkin(@Nullable NPC.Skin npcSkin){
            DEFAULT.setSkin(npcSkin);
        }

        protected Updatable.Type setSkin(@Nullable NPC.Skin skin) {
            NPC.Skin previous = this.skin;
            this.skin = skin != null ? skin : NPC.Skin.Minecraft.getSteveSkin();
            return previous != null && previous.hasSameSignedTextureAs(skin) ? Updatable.Type.NONE : Updatable.Type.FORCE_UPDATE;
        }

        public Skin.VisibleLayers getSkinVisibleLayers() { return skinVisibleLayers; }

        public Skin.VisibleLayers getDefaultSkinVisibleLayers() { return DEFAULT.getSkinVisibleLayers(); }

        protected Updatable.Type setSkinVisibleLayers(@Nullable Skin.VisibleLayers skinVisibleLayers) {
            this.skinVisibleLayers = skinVisibleLayers != null ? skinVisibleLayers : new Skin.VisibleLayers();
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultSkinVisibleLayers(@Nullable Skin.VisibleLayers skinVisibleLayers){ DEFAULT.setSkinVisibleLayers(skinVisibleLayers); }

        public List<String> getText() { return text; }

        public static List<String> getDefaultText(){ return DEFAULT.getText(); }

        protected Updatable.Type setText(@Nullable List<String> text) {
            boolean forceUpdate = (this.text == null && text != null && !text.isEmpty()) || (this.text != null && text == null && !this.text.isEmpty()) || (this.text != null && text != null && this.text.size() != text.size());
            this.text = text != null ? text : new ArrayList<>();
            return forceUpdate ? Updatable.Type.FORCE_UPDATE_TEXT : Updatable.Type.SIMPLE_UPDATE_TEXT;
        }

        public static void setDefaultText(@Nullable List<String> text){
            DEFAULT.setText(text);
        }

        public ItemStack getTextItem() { return textItem; }

        public static ItemStack getDefaultTextItem() { return DEFAULT.getTextItem(); }

        protected Updatable.Type setTextItem(@Nullable ItemStack textItem) {
            this.textItem = textItem != null ? textItem : new ItemStack(Material.AIR);
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public static void setDefaultTextItem(@Nullable ItemStack itemStack) { DEFAULT.setTextItem(itemStack); }

        public Boolean isTextItemGlowing() { return textItemGlowing; }

        public static Boolean isDefaultTextItemGlowing() { return DEFAULT.isTextItemGlowing(); }

        protected Updatable.Type setTextItemGlowing(boolean textItemGlowing) {
            this.textItemGlowing = textItemGlowing;
            return Updatable.Type.SIMPLE_UPDATE_TEXT;
        }

        public static void setDefaultTextItemGlowing(boolean textItemGlowing) { DEFAULT.setTextItemGlowing(textItemGlowing); }

        public Color getTextItemGlowingColor() { return textItemGlowingColor; }

        public static Color getDefaultTextItemGlowingColor() { return DEFAULT.getTextItemGlowingColor(); }

        protected Updatable.Type setTextItemGlowingColor(@Nullable Color textItemGlowingColor) {
            this.textItemGlowingColor = textItemGlowingColor != null ? textItemGlowingColor : Color.WHITE;
            return Updatable.Type.SIMPLE_UPDATE_TEXT;
        }

        public static void setDefaultTextItemGlowingColor(@Nullable Color textItemGlowingColor) { DEFAULT.setTextItemGlowingColor(textItemGlowingColor); }

        protected HashMap<NPC.Slot, ItemStack> getEquipment() { return equipment; }

        public ItemStack getHelmet(){ return getItem(NPC.Slot.HEAD); }

        public static ItemStack getDefaultHelmet(){ return DEFAULT.getHelmet(); }

        protected void setHelmet(@Nullable ItemStack itemStack){ setItem(NPC.Slot.HEAD, itemStack); }

        public static void setDefaultHelmet(@Nullable ItemStack itemStack){ DEFAULT.setHelmet(itemStack); }

        public ItemStack getChestplate(){ return getItem(NPC.Slot.CHEST); }

        public static ItemStack getDefaultChestplate(){ return DEFAULT.getChestplate(); }

        protected void setChestplate(@Nullable ItemStack itemStack){ setItem(NPC.Slot.CHEST, itemStack); }

        public static void setDefaultChestplate(@Nullable ItemStack itemStack){ DEFAULT.setChestplate(itemStack); }

        public ItemStack getLeggings(){ return getItem(NPC.Slot.LEGS); }

        public static ItemStack getDefaultLeggings(){ return DEFAULT.getLeggings(); }

        protected void setLeggings(@Nullable ItemStack itemStack){ setItem(NPC.Slot.LEGS, itemStack); }

        public static void setDefaultLeggings(@Nullable ItemStack itemStack){ DEFAULT.setLeggings(itemStack); }

        public ItemStack getBoots(){ return getItem(NPC.Slot.FEET); }

        public static ItemStack getDefaultBoots(){ return DEFAULT.getBoots(); }

        protected void setBoots(@Nullable ItemStack itemStack){ setItem(NPC.Slot.FEET, itemStack); }

        public static void setDefaultBoots(@Nullable ItemStack itemStack){ DEFAULT.setBoots(itemStack); }

        protected Updatable.Type setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
            Validate.notNull(slot, "Failed to set item, NPCSlot cannot be null");
            equipment.put(slot, itemStack != null ? itemStack : new ItemStack(Material.AIR));
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack) { DEFAULT.setItem(slot, itemStack); }

        public ItemStack getItem(@Nonnull NPC.Slot slot){
            Validate.notNull(slot, "Failed to get item, NPCSlot cannot be null");
            return equipment.get(slot);
        }

        public static ItemStack getDefaultItem(@Nonnull NPC.Slot slot){ return DEFAULT.getItem(slot); }

        protected static HashMap<NPC.Slot, ItemStack> getDefaultSlots(){ return DEFAULT.getEquipment(); }

        protected void setEquipment(@Nonnull HashMap<NPC.Slot, ItemStack> equipment) { this.equipment = equipment; }

        protected static void setDefaultSlots(HashMap<NPC.Slot, ItemStack> slots){ DEFAULT.setEquipment(slots); }

        public boolean isCollidable() { return collidable; }

        public static boolean isDefaultCollidable(){ return DEFAULT.isCollidable(); }

        protected Updatable.Type setCollidable(boolean collidable) {
            if(this.collidable != null && this.collidable.equals(collidable)) return Updatable.Type.NONE;
            this.collidable = collidable;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultCollidable(boolean collidable){ DEFAULT.setCollidable(collidable); }

        public boolean isGroundParticles() { return this.groundParticles; }

        public static boolean isDefaultGroundParticles(){ return DEFAULT.isGroundParticles(); }

        protected Updatable.Type setGroundParticles(boolean groundParticles) {
            if(this.groundParticles != null && this.groundParticles.equals(groundParticles)) return Updatable.Type.NONE;
            this.groundParticles = groundParticles;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultGroundParticles(boolean groundParticles){ DEFAULT.setGroundParticles(groundParticles); }

        public Double getHideDistance() { return hideDistance; }

        public static Double getDefaultHideDistance(){ return DEFAULT.getHideDistance(); }

        /**
         * When the player is far enough, the NPC will temporally hide, in order
         * to be more efficient. And when the player approach, the NPC will be unhidden.
         *
         * @param hideDistance the distance in blocks
         * @see NPC.Attributes#getHideDistance()
         * @see NPC.Attributes#setDefaultHideDistance(double)
         * @see NPC.Attributes#getDefaultHideDistance()
         */
        protected void setHideDistance(double hideDistance) {
            Validate.isTrue(hideDistance > 0.00, "The hide distance cannot be negative or 0");
            this.hideDistance = hideDistance;
        }

        /**
         * When the player is far enough, the NPC will temporally hide, in order
         * to be more efficient. And when the player approach, the NPC will be unhidden.
         *
         * @param hideDistance the distance in blocks
         * @see NPC.Attributes#getHideDistance()
         * @see NPC.Attributes#setHideDistance(double)
         * @see NPC.Attributes#getDefaultHideDistance()
         */
        public static void setDefaultHideDistance(double hideDistance){ DEFAULT.setHideDistance(hideDistance); }

        public Double getTextHideDistance() { return textHideDistance; }

        public static Double getDefaultTextHideDistance(){ return DEFAULT.getTextHideDistance(); }

        protected void setTextHideDistance(double textHideDistance) {
            Validate.isTrue(textHideDistance > 0.00, "The text hide distance cannot be negative or 0");
            this.textHideDistance = textHideDistance;
        }

        public static void setDefaultTextHideDistance(double textHideDistance){ DEFAULT.setTextHideDistance(textHideDistance); }

        public boolean isGlowing() { return glowing; }

        public static boolean isDefaultGlowing(){ return DEFAULT.isGlowing(); }

        protected Updatable.Type setGlowing(boolean glowing) {
            this.glowing = glowing;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultGlowing(boolean glowing){ DEFAULT.setGlowing(glowing); }

        public NPC.Color getGlowingColor(){ return this.glowingColor; }

        public static NPC.Color getDefaultGlowingColor(){ return DEFAULT.getGlowingColor(); }

        protected Updatable.Type setGlowingColor(@Nullable NPC.Color color){
            if(color == null) color = Color.WHITE;
            this.glowingColor = color;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultGlowingColor(@Nullable NPC.Color color){ DEFAULT.setGlowingColor(color); }

        public Boolean isInvisible() { return invisible; }

        protected Updatable.Type setInvisible(boolean invisible) {
            this.invisible = invisible;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static Boolean isDefaultInvisible() { return DEFAULT.isInvisible(); }

        public static void setDefaultInvisible(boolean invisible) { DEFAULT.setInvisible(invisible); }

        public NPC.GazeTrackingType getGazeTrackingType() { return gazeTrackingType; }

        public static NPC.GazeTrackingType getDefaultGazeTrackingType(){ return DEFAULT.getGazeTrackingType(); }

        protected void setGazeTrackingType(@Nullable NPC.GazeTrackingType gazeTrackingType) {
            if(gazeTrackingType == null) gazeTrackingType = NPC.GazeTrackingType.NONE;
            this.gazeTrackingType = gazeTrackingType;
        }

        public static void setDefaultGazeTrackingType(@Nullable NPC.GazeTrackingType followLookType){ DEFAULT.setGazeTrackingType(followLookType); }

        public NameTag getNameTag() { return nameTag; }

        public static NameTag getDefaultNameTag(){ return DEFAULT.getNameTag(); }

        protected Updatable.Type setNameTag(@Nullable NameTag nameTag){
            this.nameTag = nameTag != null ? nameTag : DEFAULT.getNameTag().clone();
            return Updatable.Type.FORCE_UPDATE;
        }

        public static void setDefaultNameTag(@Nullable String prefix, @Nullable String suffix){ DEFAULT.setNameTag(new NameTag(prefix, "{id}", suffix)); }

        public String getTabListName() { return tabListName; }

        protected Updatable.Type setTabListName(@Nullable String tabListName) {
            this.tabListName = tabListName;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public Boolean isShowNameTag() { return showNameTag; }

        public Updatable.Type setShowNameTag(boolean showNameTag) {
            this.showNameTag = showNameTag;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public TabListVisibility getTabListVisibility() { return tabListVisibility; }

        public static TabListVisibility getDefaultTabListVisibility(){ return DEFAULT.getTabListVisibility(); }

        protected Updatable.Type setTabListVisibility(@Nullable TabListVisibility tabListVisibility) {
            if(tabListVisibility == null) tabListVisibility = DEFAULT.getTabListVisibility();
            this.tabListVisibility = tabListVisibility;
            return Updatable.Type.FORCE_UPDATE;
        }

        public static void setDefaultTabListVisibility(TabListVisibility tabListVisibility){ DEFAULT.setTabListVisibility(tabListVisibility); }

        public Long getInteractCooldown() { return interactCooldown; }

        public static Long getDefaultInteractCooldown(){ return DEFAULT.getInteractCooldown(); }

        protected void setInteractCooldown(long milliseconds) {
            Validate.isTrue(milliseconds >= 0, "Error setting interact cooldown, cannot be negative.");
            this.interactCooldown = milliseconds;
        }

        public static void setDefaultInteractCooldown(long interactCooldown){ DEFAULT.setInteractCooldown(interactCooldown); }

        public Double getTextLineSpacing() { return textLineSpacing; }

        public static Double getDefaultTextLineSpacing(){ return DEFAULT.getTextLineSpacing(); }

        protected Updatable.Type setTextLineSpacing(double textLineSpacing) {
            if(textLineSpacing < NPC.Attributes.VARIABLE_MIN_LINE_SPACING) textLineSpacing = NPC.Attributes.VARIABLE_MIN_LINE_SPACING;
            else if(textLineSpacing > NPC.Attributes.VARIABLE_MAX_LINE_SPACING) textLineSpacing = NPC.Attributes.VARIABLE_MAX_LINE_SPACING;
            this.textLineSpacing = textLineSpacing;
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public static void setDefaultTextLineSpacing(double lineSpacing){ DEFAULT.setTextLineSpacing(lineSpacing); }

        public Vector getTextAlignment() { return textAlignment; }

        public static Vector getDefaultTextAlignment(){ return DEFAULT.getTextAlignment(); }

        protected Updatable.Type setTextAlignment(@Nonnull Vector vector) {
            if(vector == null) vector = DEFAULT.getTextAlignment();
            if(vector.getX() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            else if(vector.getX() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            if(vector.getY() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
            else if(vector.getY() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
            if(vector.getZ() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            else if(vector.getZ() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            this.textAlignment = vector;
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public static void setDefaultTextAlignment(Vector textAlignment){ DEFAULT.setTextAlignment(textAlignment); }

        public NPC.Pose getPose() { return pose; }

        public static NPC.Pose getDefaultPose(){ return DEFAULT.getPose(); }

        protected Updatable.Type setPose(@Nullable NPC.Pose pose) {
            NPC.Pose previous = this.pose;
            this.pose = pose != null ? pose : Pose.STANDING;
            if(previous.equals(this.pose)) return Updatable.Type.NONE;
            return (previous.equals(Pose.SPIN_ATTACK) || pose.equals(Pose.STANDING)) ? Updatable.Type.FORCE_UPDATE
                    : Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultPose(@Nullable NPC.Pose npcPose){ DEFAULT.setPose(npcPose); }

        public static NPC.Hologram.Opacity getDefaultTextOpacity(){ return DEFAULT.getTextOpacity(); }

        public NPC.Hologram.Opacity getTextOpacity() { return textOpacity; }

        public static void setDefaultTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity){ DEFAULT.setTextOpacity(textOpacity); }

        protected Updatable.Type setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity) {
            if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
            this.textOpacity = textOpacity;
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public boolean isOnFire() { return onFire; }

        public static boolean isDefaultOnFire(){ return DEFAULT.isOnFire(); }

        protected Updatable.Type setOnFire(boolean onFire) {
            this.onFire = onFire;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultOnFire(boolean onFire){ DEFAULT.setOnFire(onFire); }

        public double getMoveSpeed() { return moveSpeed; }

        public static Double getDefaultMoveSpeed(){ return DEFAULT.getMoveSpeed(); }

        protected void setMoveSpeed(double moveSpeed) {
            if(moveSpeed <= 0.00) moveSpeed = 0.1;
            this.moveSpeed = moveSpeed;
        }

        protected Updatable.Type setTextLineOpacity(int line, Hologram.Opacity opacity){
            if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
            this.textLinesOpacity.put(line, opacity);
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public Hologram.Opacity getTextLineOpacity(int line){ return textLinesOpacity.getOrDefault(line, Hologram.Opacity.LOWEST); }

        protected HashMap<Integer, Hologram.Opacity> getTextLinesOpacity() { return textLinesOpacity; }

        protected void setTextLinesOpacity(HashMap<Integer, Hologram.Opacity> textLinesOpacity) { this.textLinesOpacity = textLinesOpacity; }

        public void resetTextLineOpacity(int line){ if(textLinesOpacity.containsKey(line)) textLinesOpacity.remove(line); }

        public Updatable.Type resetTextLinesOpacity(){
            textLinesOpacity = new HashMap<>();
            return Updatable.Type.FORCE_UPDATE_TEXT;
        }

        public java.awt.Color getPotionParticlesColor() { return potionParticlesColor; }

        public static java.awt.Color getDefaultPotionParticlesColor() { return DEFAULT.getPotionParticlesColor(); }

        public Updatable.Type setPotionParticlesColor(@Nullable java.awt.Color potionParticlesColor) {
            this.potionParticlesColor = potionParticlesColor != null ? potionParticlesColor : java.awt.Color.WHITE;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultPotionParticlesColor(@Nullable java.awt.Color potionParticlesColor) { DEFAULT.setPotionParticlesColor(potionParticlesColor); }

        public PotionParticlesType getPotionParticlesType() { return potionParticlesType; }

        public static PotionParticlesType getDefaultPotionParticlesType() { return DEFAULT.getPotionParticlesType(); }

        public Updatable.Type setPotionParticlesType(@Nullable PotionParticlesType potionParticlesType) {
            this.potionParticlesType = potionParticlesType != null ? potionParticlesType : PotionParticlesType.DISABLED;
            return Updatable.Type.SIMPLE_UPDATE;
        }

        public static void setDefaultPotionParticlesType(@Nullable PotionParticlesType potionParticlesType) { DEFAULT.setPotionParticlesType(potionParticlesType); }

        public Integer getArrowsInBody() { return arrowsInBody; }

        public static Integer getDefaultArrowsInBody() { return DEFAULT.getArrowsInBody(); }

        protected Updatable.Type setArrowsInBody(int arrowsInBody) {
            int previous = this.arrowsInBody;
            this.arrowsInBody = arrowsInBody < 0 ? 0 : arrowsInBody;
            return (previous > 0 && arrowsInBody == 0) ? Updatable.Type.FORCE_UPDATE : Updatable.Type.SIMPLE_UPDATE;
        }

        public void setDefaultArrowsInBody(int arrowsInBody) { DEFAULT.setArrowsInBody(arrowsInBody); }

        public Integer getBeeStingersInBody() { return beeStingersInBody; }

        public static Integer getDefaultBeeStingersInBody() { return DEFAULT.getBeeStingersInBody(); }

        public Updatable.Type setBeeStingersInBody(int beeStingersInBody) {
            int previous = this.beeStingersInBody;
            this.beeStingersInBody = beeStingersInBody < 0 ? 0 : beeStingersInBody;
            return (previous > 0 && arrowsInBody == 0) ? Updatable.Type.FORCE_UPDATE : Updatable.Type.SIMPLE_UPDATE;
        }

        public void setDefaultBeeStingersInBody(int beeStingersInBody) { DEFAULT.setBeeStingersInBody(beeStingersInBody); }

        public Boolean isShaking() { return shaking; }

        public static Boolean isDefaultShaking() { return DEFAULT.isShaking(); }

        protected Updatable.Type setShaking(boolean shaking) {
            if(this.shaking != null && this.shaking.equals(shaking)) return Updatable.Type.NONE;
            this.shaking = shaking;
            return shaking ? Updatable.Type.SIMPLE_UPDATE : Updatable.Type.FORCE_UPDATE;
        }

        public static void setDefaultShaking(boolean shaking) { DEFAULT.setShaking(shaking); }
    }

    public static class Conditions {

        public enum Type {
            @Deprecated COMPARATOR_NUMBER,
            COMPARATOR_SENTENCE,
            PLAYER_HAS_BALANCE,
            PLAYER_HAS_PERMISSION,
            ;

            public boolean isDeprecated(){ return EnumUtils.isDeprecated(this); }

            public boolean requiresVaultEconomy(){
                return Arrays.asList(PLAYER_HAS_BALANCE).contains(this);
            }

            public String getRequiredDependency(){
                String requiredDependency = null;
                if(this.requiresVaultEconomy() && (!IntegrationsManager.isUsingVault() || !IntegrationsManager.getVault().isUsingEconomy())) requiredDependency = "Vault Economy";
                return requiredDependency;
            }
        }

        public static abstract class Condition implements ConfigurationSerializable{

            protected final Type type;
            protected BiFunction<NPC, org.bukkit.entity.Player, Boolean> condition;
            protected Boolean logicNegative;
            protected Boolean enabled;
            protected ErrorResponse errorResponse;

            protected Condition(Type type) {
                this.type = type;
                this.logicNegative = false;
                this.errorResponse = new ErrorResponse();
                this.condition = null;
                this.enabled = true;
            }

            public boolean test(NPC npc, org.bukkit.entity.Player player) {
                boolean result = testSilent(npc, player);
                if(!result && errorResponse != null) errorResponse.play(npc, player);
                return result;
            }

            public boolean testSilent(NPC npc, org.bukkit.entity.Player player) {
                if(!enabled) return true;
                boolean result = condition.apply(npc, player);
                if(logicNegative) result = !result;
                return result;
            }

            public ErrorResponse getErrorResponse() {
                return errorResponse;
            }

            protected void setErrorResponse(ErrorResponse errorResponse) {
                this.errorResponse = errorResponse;
            }

            public Boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                if(enabled == null) enabled = true;
                this.enabled = enabled;
            }

            public Type getType() {
                return type;
            }

            public Boolean isLogicNegative() {
                return logicNegative;
            }

            public void setLogicNegative(@Nonnull Boolean logicNegative) {
                Validate.notNull(logicNegative);
                this.logicNegative = logicNegative;
            }

            protected Map<String, Object> generalSerialization(){
                Map<String, Object> data = new HashMap<>();
                data.put("enabled", isEnabled());
                data.put("logicNegative", isLogicNegative());
                if(errorResponse.getErrorMessage() == null) data.put("errorResponse.message", "");
                else data.put("errorResponse.message", getErrorResponse().getErrorMessage().replaceAll("§", "&"));
                data.put("errorResponse.messageType", getErrorResponse().getChatMessageType().name());
                data.put("errorResponse.redAnimation", getErrorResponse().getPlayRedAnimation());
                data.put("errorResponse.sound", getErrorResponse().getSound() != null ? getErrorResponse().getSound().name() : "none");
                data.put("errorResponse.soundVolume", getErrorResponse().getSoundVolume().doubleValue());
                data.put("errorResponse.soundPitch", getErrorResponse().getSoundPitch().doubleValue());
                return data;
            }

            protected static void generalDeserialization(Map<String, Object> map, Condition condition){
                if(map.containsKey("enabled")) condition.setEnabled((Boolean) map.get("enabled"));
                if(map.containsKey("logicNegative")) condition.setLogicNegative((Boolean) map.get("logicNegative"));
                ErrorResponse errorResponse = condition.getErrorResponse();
                if(map.containsKey("errorResponse.message")){
                    String as = ((String) map.get("errorResponse.message"));
                    if(as.equals("")) errorResponse.setMessage(null);
                    else errorResponse.setMessage(as.replaceAll("&", "§"));
                }
                if(map.containsKey("errorResponse.messageType")) errorResponse.setChatMessageType(ChatMessageType.valueOf((String) map.get("errorResponse.messageType")));
                if(map.containsKey("errorResponse.redAnimation")) errorResponse.setPlayRedAnimation((Boolean) map.get("errorResponse.redAnimation"));
                if(map.containsKey("errorResponse.sound")){
                    if(map.get("errorResponse.sound").equals("none")) errorResponse.sound = null;
                    else errorResponse.sound = Sound.valueOf((String) map.get("errorResponse.sound"));
                }
                if(map.containsKey("errorResponse.soundVolume")) errorResponse.soundVolume = ((Double) map.get("errorResponse.soundVolume")).floatValue();
                if(map.containsKey("errorResponse.soundPitch")) errorResponse.soundPitch = ((Double) map.get("errorResponse.soundPitch")).floatValue();
            }
        }

        public static class Comparator{

            public static class Sentence extends Condition{

                private String first;
                private String second;
                private Logic logic;

                public Sentence() {
                    super(Type.COMPARATOR_SENTENCE);
                    this.logic = Logic.EQUALS;
                    this.first = "";
                    this.second = "";
                    super.condition = (npc, player) -> {
                        if(getFirst() == null || getSecond() == null || getLogic() == null) return true;
                        String firstSentence = NPC.Placeholders.replace(npc, player, getFirst());
                        String secondSentence = NPC.Placeholders.replace(npc, player, getSecond());
                        return getLogic().comparatorFunction.apply(firstSentence, secondSentence);
                    };
                }

                @Nonnull
                public String getFirst() {
                    return first;
                }

                @Nonnull
                public String getSecond() {
                    return second;
                }

                @Nonnull
                public Logic getLogic() {
                    return logic;
                }

                public enum Logic{
                    EQUALS((f, s) -> f.equals(s)),
                    EQUALS_IGNORE_CASE((f, s) -> f.equalsIgnoreCase(s)),
                    CONTAINS((f, s) -> f.contains(s)),
                    CONTAINS_IGNORE_CASE((f, s) -> f.toLowerCase().contains(s.toLowerCase())),
                    STARTS_WITH((f, s) -> f.startsWith(s)),
                    STARTS_WITH_IGNORE_CASE((f, s) -> f.toLowerCase().startsWith(s.toLowerCase())),
                    ENDS_WITH((f, s) -> f.endsWith(s)),
                    ENDS_WITH_IGNORE_CASE((f, s) -> f.toLowerCase().endsWith(s.toLowerCase())),
                    ;

                    protected BiFunction<String, String, Boolean> comparatorFunction;

                    Logic(BiFunction<String, String, Boolean> comparatorFunction){ this.comparatorFunction = comparatorFunction; }
                }

                public void setFirst(@Nonnull String first) {
                    Validate.notNull(first);
                    this.first = first;
                }

                public void setSecond(@Nonnull String second) {
                    Validate.notNull(second);
                    this.second = second;
                }

                public void setLogic(@Nonnull Logic logic) {
                    Validate.notNull(logic);
                    this.logic = logic;
                }

                @Override
                public Map<String, Object> serialize() {
                    Map<String, Object> data = new HashMap<>();
                    data.put("first", getFirst());
                    data.put("second", getSecond());
                    data.put("logic", getLogic().name());
                    data.putAll(generalSerialization());
                    return data;
                }

                public static Conditions.Comparator.Sentence deserialize(Map<String, Object> map){
                    Conditions.Comparator.Sentence condition = new Conditions.Comparator.Sentence();
                    if(map.containsKey("first")) condition.first = (String) map.get("first");
                    if(map.containsKey("second")) condition.second = (String) map.get("second");
                    if(map.containsKey("logic")) condition.logic = Logic.valueOf((String) map.get("logic"));
                    generalDeserialization(map, condition);
                    return condition;
                }

            }

        }

        public static class Player{

            private Player(){}

            public static class Permission extends Condition{

                private String permission;

                public Permission(String permission) {
                    super(Type.PLAYER_HAS_PERMISSION);
                    super.condition = (npc, player) -> player.hasPermission(NPC.Placeholders.replace(npc, player, getPermission())) || player.isOp();
                    this.permission = permission;
                }

                public String getPermission() {
                    return permission;
                }

                public void setPermission(@Nonnull String permission) {
                    Validate.notNull(permission);
                    this.permission = permission;
                }

                @Override
                public Map<String, Object> serialize() {
                    Map<String, Object> data = new HashMap<>();
                    data.put("permission", getPermission());
                    data.putAll(generalSerialization());
                    return data;
                }

                public static Conditions.Player.Permission deserialize(Map<String, Object> map){
                    Permission permission = new Permission((String) map.get("permission"));
                    generalDeserialization(map, permission);
                    return permission;
                }

            }

            public static class Balance extends Condition{

                private Double balance;

                public Balance(Double balance){
                    super(Type.PLAYER_HAS_BALANCE);
                    super.condition = (npc, player) -> {
                        if(!IntegrationsManager.isUsingVault() || !IntegrationsManager.getVault().isUsingEconomy()) return true;
                        return IntegrationsManager.getVault().getEconomyManager().hasBalance(player, balance);
                    };
                    this.balance = balance;
                }

                public void setBalance(@Nonnull Double balance) {
                    Validate.notNull(balance);
                    this.balance = balance;
                }

                public Double getBalance() {
                    return balance;
                }

                @Override
                public Map<String, Object> serialize() {
                    Map<String, Object> data = new HashMap<>();
                    data.put("balance", getBalance());
                    data.putAll(generalSerialization());
                    return data;
                }

                public static Conditions.Player.Balance deserialize(Map<String, Object> map){
                    Balance balance = new Balance((Double) map.get("balance"));
                    generalDeserialization(map, balance);
                    return balance;
                }

            }

        }

        public static class ErrorResponse{

            private ChatMessageType chatMessageType;
            private String errorMessage;
            private Boolean playRedAnimation;
            private Sound sound;
            private Float soundVolume;
            private Float soundPitch;

            public ErrorResponse() {
                chatMessageType = ChatMessageType.CHAT;
                errorMessage = null;
                playRedAnimation = false;
                sound = null;
                soundVolume = 1.000000000F;
                soundPitch = 1.0000000000F;
            }

            protected void play(NPC npc, org.bukkit.entity.Player player){
                if(errorMessage != null) player.spigot().sendMessage(chatMessageType, new TextComponent(NPC.Placeholders.replace(npc, player, errorMessage)));
                if(playRedAnimation){
                    if(npc instanceof Global) ((Global) npc).playAnimation(player, Animation.TAKE_DAMAGE);
                    else npc.playAnimation(Animation.TAKE_DAMAGE);
                }
                if(sound != null) player.playSound(npc.getLocation(), sound, soundVolume, soundPitch);
            }

            public void setMessage(String errorMessage){
                this.errorMessage = errorMessage;
            }

            public void setChatMessageType(ChatMessageType chatMessageType) {
                this.chatMessageType = chatMessageType;
            }

            public void setMessage(ChatMessageType chatMessageType, String errorMessage){
                this.chatMessageType = chatMessageType;
                this.errorMessage = errorMessage;
            }

            public void setSound(Sound sound){
                this.sound = sound;
            }

            public void setSound(Sound sound, Float volume, Float pitch){
                this.sound = sound;
                setSoundVolume(volume);
                setSoundPitch(pitch);
            }

            public void setSoundVolume(Float soundVolume) {
                if(soundVolume <= 0.1) soundVolume = 0.1F;
                if(soundVolume >= 1.0) soundVolume = 1.0F;
                this.soundVolume = (float) (Math.round(soundVolume * 10.0) / 10.0);
            }

            public void setSoundPitch(Float soundPitch) {
                if(soundPitch <= 0.5) soundPitch = 0.5F;
                if(soundPitch >= 2.0) soundPitch = 2.0F;
                this.soundPitch = (float) (Math.round(soundPitch * 10.0) / 10.0);
            }

            public void setPlayRedAnimation(Boolean playRedAnimation) {
                this.playRedAnimation = playRedAnimation;
            }

            public ChatMessageType getChatMessageType() {
                return chatMessageType;
            }

            public String getErrorMessage() {
                return errorMessage;
            }

            public Boolean getPlayRedAnimation() {
                return playRedAnimation;
            }

            public Sound getSound() {
                return sound;
            }

            public Float getSoundVolume() {
                return soundVolume;
            }

            public Float getSoundPitch() {
                return soundPitch;
            }

        }

    }

    public static class Placeholders {

        private static HashMap<String, BiFunction<NPC, Player, String>> placeholders;

        static{
            placeholders = new HashMap<>();
            addPlaceholder("playerName", (npc, player) -> player.getName());
            addPlaceholder("playerDisplayName", (npc, player) -> player.getDisplayName());
            addPlaceholder("playerUUID", (npc, player) -> player.getUniqueId().toString());
            addPlaceholder("playerVersion", (npc, player) -> ((Personal) asPersonalIfPossible(npc, player)).getClientVersion().getMinecraftVersion());
            addPlaceholder("playerWorld", (npc, player) -> player.getWorld().getName());
            addPlaceholder("npcPersonalID", (npc, player) -> npc.getID().getFullID());
            addPlaceholder("npcPersonalSimpleID", (npc, player) -> npc.getID().getSimpleID());
            addPlaceholder("npcWorld", (npc, player) -> npc.getWorld().getName());
            addPlaceholder("npcTabListName", (npc, player) -> npc.getTabListName());
            addPlaceholder("npcPluginName", (npc, player) -> npc.getPlugin().getDescription().getName());
            addPlaceholder("npcPluginVersion", (npc, player) -> npc.getPlugin().getDescription().getVersion());
            addPlaceholder("serverOnlinePlayers", (npc, player) -> "" + npc.getPlugin().getServer().getOnlinePlayers().size());
            addPlaceholder("serverMaxPlayers", (npc, player) -> "" + npc.getPlugin().getServer().getMaxPlayers());
            addPlaceholder("serverVersion", (npc, player) -> ((Personal) asPersonalIfPossible(npc, player)).getServerVersion().getMinecraftVersion());
            addPlaceholder("npcID", (npc, player) -> asGlobalIfPossible(npc).getFullID());
            addPlaceholder("npcSimpleID", (npc, player) -> asGlobalIfPossible(npc).getSimpleID());
        }
        
        private static NPC asGlobalIfPossible(NPC npc){
            if(npc instanceof Personal && ((Personal) npc).hasGlobal()) return ((Personal) npc).getGlobal();
            else return npc;
        }

        private static NPC asPersonalIfPossible(NPC npc, Player player){
            if(npc instanceof Global && ((Global) npc).hasPlayer(player)) return ((Global) npc).getPersonal(player);
            else return npc;
        }

        private Placeholders(){}

        public static String format(String s) { return "{" + s + "}"; }

        public static Set<String> getAllPlaceholders() {
            return getAllPlaceholders(null);
        }

        public static Set<String> getAllPlaceholders(NPC npc) {
            Set<String> list = new HashSet<>();
            list.addAll(placeholders.keySet());
            if(npc == null) return list;
            NPC customDataNPC = npc;
            if(npc instanceof Personal){
                Personal personal = (Personal) npc;
                if(personal.hasGlobal()) customDataNPC = personal.getGlobal();
            }
            if(!customDataNPC.getCustomDataKeys().isEmpty()) customDataNPC.getCustomDataKeys().forEach(x-> list.add("customData:" + x));
            return list;
        }

        public static void addPlaceholder(@Nonnull String placeholder, @Nonnull BiFunction<NPC, Player, String> replacement){
            Validate.notNull(placeholder, "Placeholder cannot be null.");
            Validate.notNull(replacement, "Replacement cannot be null.");
            Validate.isTrue(!placeholders.containsKey(placeholder), "Placeholder \"" + placeholder + "\" settled previously");
            placeholders.put(placeholder, replacement);
        }

        public static void addPlaceholderIfNotExists(@Nonnull String placeholder, @Nonnull BiFunction<NPC, Player, String> replacement){
            Validate.notNull(placeholder, "Placeholder cannot be null.");
            Validate.notNull(replacement, "Replacement cannot be null.");
            if(placeholders.containsKey(placeholder)) return;
            placeholders.put(placeholder, replacement);
        }

        public static boolean existsPlaceholder(@Nonnull String placeholder){
            Validate.notNull(placeholder, "Placeholder cannot be null.");
            return placeholders.containsKey(placeholder);
        }

        public static String replace(@Nonnull NPC npc, @Nonnull Player player, @Nonnull String string){
            Validate.notNull(npc, "NPC cannot be null.");
            Validate.notNull(player, "Player cannot be null.");
            if(string == null) return "";
            for(String placeholder : placeholders.keySet()){
                if(!string.contains("{" + placeholder + "}")) continue;
                string = r(string, placeholder, placeholders.get(placeholder).apply(npc, player));
            }
            NPC customDataNPC = npc;
            if(npc instanceof Personal){
                Personal personal = (Personal) npc;
                if(personal.hasGlobal()) customDataNPC = personal.getGlobal();
                else customDataNPC = personal;
            }
            for(NPCLib.Registry.ID key : customDataNPC.getCustomDataKeysID()){
                if(!string.contains("{customData:" + key + "}") && key.isPlayerNPC()){
                    if(string.contains("{customData:" + key.getSimpleID() + "}")) string = r(string, "customData:" + key.getSimpleID(), customDataNPC.grabCustomData(key).orElse("N/A"));
                    continue;
                }
                string = r(string, "customData:" + key, customDataNPC.grabCustomData(key).orElse("N/A"));
            }
            if(IntegrationsManager.isUsingPlaceholderAPI()) string = IntegrationsManager.getPlaceholderAPI().replace(player, string);
            return string;
        }

        private static String r(String string, String placeHolder, String value){ return string.replaceAll("\\{" + placeHolder +"\\}", value); }

    }

}
