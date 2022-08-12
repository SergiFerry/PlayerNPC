package dev.sergiferry.playernpc.api;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftItemStack;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftScoreboard;
import dev.sergiferry.playernpc.nms.minecraft.*;
import dev.sergiferry.playernpc.nms.spigot.NMSFileConfiguration;
import dev.sergiferry.playernpc.nms.spigot.NMSFileUtils;
import dev.sergiferry.playernpc.nms.spigot.NMSPlayer;
import dev.sergiferry.playernpc.utils.ColorUtils;
import dev.sergiferry.playernpc.utils.MathUtils;
import dev.sergiferry.playernpc.utils.StringUtils;
import dev.sergiferry.playernpc.utils.TimerUtils;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
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
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.logging.log4j.util.TriConsumer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.mineskin.MineskinClient;
import org.mineskin.SkinOptions;
import org.mineskin.data.MineskinException;
import org.mineskin.data.Skin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
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
public abstract class NPC {

    protected final NPCLib.PluginManager pluginManager;
    private final String code;
    private final HashMap<String, String> customData;
    private World world;
    private Double x, y, z;
    private Float yaw, pitch;
    private List<NPC.Interact.ClickAction> clickActions;
    private NPC.Move.Task moveTask;
    private NPC.Move.Behaviour moveBehaviour;

    // NPC.Attributes
    private NPC.Attributes attributes;

    protected NPC(@Nonnull NPCLib npcLib, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull World world, double x, double y, double z, float yaw, float pitch){
        Validate.notNull(npcLib, "Cannot generate NPC instance, NPCLib cannot be null.");
        Validate.notNull(plugin, "Cannot generate NPC instance, Plugin cannot be null.");
        Validate.notNull(code, "Cannot generate NPC instance, code cannot be null.");
        Validate.notNull(world, "Cannot generate NPC instance, World cannot be null.");
        this.pluginManager = npcLib.getPluginManager(plugin);
        this.code = code;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.clickActions = new ArrayList<>();
        this.moveTask = null;
        this.moveBehaviour = new NPC.Move.Behaviour(this);
        this.customData = new HashMap<>();

        //NPC Attributes
        this.attributes = new NPC.Attributes();
    }

    protected abstract void update();

    protected abstract void forceUpdate();

    protected abstract void updateText();

    protected abstract void forceUpdateText();

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

    public void teleport(double x, double y, double z){
        teleport(world, x, y, z);
    }

    public void teleport(World world, double x, double y, double z){
        teleport(world, x, y, z, yaw, pitch);
    }

    public void teleport(double x, double y, double z, float yaw, float pitch){ teleport(this.world, x, y, z, yaw, pitch); }

    public void setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
        Validate.notNull(slot, "Failed to set item, NPC.Slot cannot be null");
        if(itemStack == null) itemStack = new ItemStack(Material.AIR);
        attributes.slots.put(slot, itemStack);
    }

    public void setHelmet(@Nullable ItemStack itemStack){
        setItem(NPC.Slot.HELMET, itemStack);
    }

    public void setChestPlate(@Nullable ItemStack itemStack){
        setItem(Slot.CHESTPLATE, itemStack);
    }

    public void setLeggings(@Nullable ItemStack itemStack){
        setItem(Slot.LEGGINGS, itemStack);
    }

    public void setBoots(@Nullable ItemStack itemStack){
        setItem(Slot.BOOTS, itemStack);
    }

    public void setItemInRightHand(@Nullable ItemStack itemStack){
        setItem(NPC.Slot.MAINHAND, itemStack);
    }

    public void setItemInLeftHand(@Nullable ItemStack itemStack){
        setItem(NPC.Slot.OFFHAND, itemStack);
    }

    public void clearEquipment(@Nonnull NPC.Slot slot){
        setItem(slot, null);
    }

    public void clearEquipment(){
        Arrays.stream(NPC.Slot.values()).forEach(x-> clearEquipment(x));
    }

    public void lookAt(@Nonnull Entity entity){
        Validate.notNull(entity, "Failed to set look direction. The entity cannot be null");
        lookAt(entity.getLocation());
    }

    public void lookAt(@Nonnull Location location){
        Validate.notNull(location, "Failed to set look direction. The location cannot be null.");
        Validate.isTrue(location.getWorld().getName().equals(getWorld().getName()), "The location must be in the same world as NPC");
        Location npcLocation = new Location(world, x, y, z, yaw, pitch);
        Vector dirBetweenLocations = location.toVector().subtract(npcLocation.toVector());
        npcLocation.setDirection(dirBetweenLocations);
        lookAt(npcLocation.getYaw(), npcLocation.getPitch());
    }

    public abstract void lookAt(float yaw, float pitch);

    public void setCollidable(boolean collidable) {
        attributes.setCollidable(collidable);
    }

    public void setSkin(@Nonnull String texture, @Nonnull String signature){
        setSkin(null, texture, signature);
    }

    public void setSkin(@Nullable String skinName, @Nonnull String texture, @Nonnull String signature){
        if(skinName == null) setSkin(NPC.Skin.Custom.createCustomSkin(getPlugin(), texture, signature));
        else setSkin(NPC.Skin.Custom.createCustomSkin(getPlugin(), skinName, texture, signature));
    }

    public void setSkin(@Nullable String playerName, Consumer<NPC.Skin.Minecraft> finishAction){
        if(playerName == null){
            setSkin(Skin.Minecraft.STEVE);
            return;
        }
        NPC.Skin.Minecraft.fetchSkinAsync(getPlugin(), playerName, (skin) -> {
            setSkin(skin);
            if(finishAction != null) getPlugin().getServer().getScheduler().runTask(getPlugin(), ()-> finishAction.accept(skin));
        });
    }

    public void setMineSkin(Skin.MineSkin skin){
        setSkin(skin);
    }

    public void setMineSkin(@Nonnull String id, boolean forceDownload, Consumer<Skin.MineSkin> finishAction){
        Validate.notNull(id);
        NPC.Skin.MineSkin.fetchSkinAsync(getPlugin(), id, false, (skin) -> {
            setMineSkin(skin);
            if(finishAction != null) getPlugin().getServer().getScheduler().runTask(getPlugin(), ()-> finishAction.accept(skin));
        });
    }

    public void setMineSkin(@Nonnull String id, Consumer<Skin.MineSkin> finishAction){
        setMineSkin(id, false, finishAction);
    }

    public void setSkin(@Nullable String playerName){
        setSkin(playerName, (Consumer<Skin.Minecraft>) null);
    }

    public void setSkin(@Nullable Player playerSkin){
        if(playerSkin == null){
            setSkin(Skin.Minecraft.STEVE);
            return;
        }
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        setSkin(playerSkin.getName(), (Consumer<Skin.Minecraft>) null);
    }

    public void setSkin(@Nullable Player playerSkin, Consumer<Skin.Minecraft> finishAction){
        if(playerSkin == null){
            setSkin(Skin.Minecraft.STEVE);
            return;
        }
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        setSkin(playerSkin.getName(), finishAction);
    }

    public void setSkin(@Nullable NPC.Skin npcSkin){
        attributes.setSkin(npcSkin);
    }

    protected void setSkinParts(NPC.Skin.Parts skinParts){
        attributes.setSkinParts(skinParts);
    }

    public void clearSkin(){
        setSkin((NPC.Skin) null);
    }

    public void setSkinVisiblePart(NPC.Skin.Part part, boolean visible){
        attributes.skinParts.setVisible(part, visible);
    }

    public void setPose(NPC.Pose pose){
        attributes.setPose(pose);
    }

    public void setCrouching(boolean b){
        if(b) setPose(NPC.Pose.CROUCHING);
        else if(getPose().equals(NPC.Pose.CROUCHING)) resetPose();
    }

    public void setSwimming(boolean b){
        if(b) setPose(Pose.SWIMMING);
        else if(getPose().equals(Pose.SWIMMING)) resetPose();
    }

    public void setSleeping(boolean b){
        if(b) setPose(Pose.SLEEPING);
        else if(getPose().equals(Pose.SLEEPING)) resetPose();
    }

    public void resetPose(){
        setPose(NPC.Pose.STANDING);
    }

    public void clearText(){
        setText(new ArrayList<>());
    }

    public void setText(@Nonnull List<String> text){
        attributes.setText(text);
    }

    public void setText(@Nonnull String... text){
        setText(Arrays.asList(text));
    }

    public void setText(@Nonnull String text){
        setText(Arrays.asList(text));
    }

    public void resetLinesOpacity(){ attributes.resetLinesOpacity(); }

    public void resetLineOpacity(int line){ attributes.resetLineOpacity(line); }

    public void setLineOpacity(int line, @Nullable NPC.Hologram.Opacity textOpacity){
        attributes.setLineOpacity(line, textOpacity);
    }

    protected void setLinesOpacity(HashMap<Integer, NPC.Hologram.Opacity> linesOpacity){
        attributes.setLinesOpacity(linesOpacity);
    }

    public void setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity){
        attributes.setTextOpacity(textOpacity);
    }

    public void resetTextOpacity(){
        setTextOpacity(NPC.Hologram.Opacity.LOWEST);
    }

    public void setGlowingColor(@Nullable ChatColor color){
        setGlowingColor(NPC.Color.getColor(color));
    }

    public void setGlowingColor(@Nullable Color color){
        attributes.setGlowingColor(color);
    }

    public void setGlowing(boolean glowing, @Nullable ChatColor color){
        setGlowing(glowing, NPC.Color.getColor(color));
    }

    public void setGlowing(boolean glowing, @Nullable Color color){
        setGlowing(glowing);
        setGlowingColor(color);
    }

    public void setGlowing(boolean glowing){
        attributes.setGlowing(glowing);
    }

    public Move.Behaviour follow(NPC npc){
        Validate.isTrue(!npc.equals(this), "NPC cannot follow himself.");
        return moveBehaviour.setFollowNPC(npc);
    }

    public Move.Behaviour follow(NPC npc, double min, double max){
        Validate.isTrue(!npc.equals(this), "NPC cannot follow himself.");

        return moveBehaviour.setFollowNPC(npc, min, max);
    }

    public Move.Behaviour follow(Entity entity, double min, double max){
        return moveBehaviour.setFollowEntity(entity, min, max);
    }

    public Move.Behaviour follow(Entity entity, double min){
        return moveBehaviour.setFollowEntity(entity, min);
    }

    public Move.Behaviour follow(Entity entity){
        return moveBehaviour.setFollowEntity(entity);
    }

    public void cancelMoveBehaviour(){
        moveBehaviour.cancel();
    }

    public NPC.Move.Path setPath(Move.Path.Type type, List<Location> locations){
        return getMoveBehaviour().setPath(locations, type).start();
    }

    public NPC.Move.Path setPath(Move.Path.Type type, Location... locations){
        return setPath(type, Arrays.stream(locations).toList());
    }

    public NPC.Move.Path setRepetitivePath(List<Location> locations){
        return setPath(Move.Path.Type.REPETITIVE, locations);
    }

    public NPC.Move.Path setRepetitivePath(Location... locations){
        return setRepetitivePath(Arrays.stream(locations).toList());
    }

    public void setGazeTrackingType(@Nullable GazeTrackingType followLookType) {
        attributes.setGazeTrackingType(followLookType);
    }

    public void setHideDistance(double hideDistance) {
        attributes.setHideDistance(hideDistance);
    }

    public void setLineSpacing(double lineSpacing){
        attributes.setLineSpacing(lineSpacing);
    }

    public void resetLineSpacing(){
        setLineSpacing(NPC.Attributes.getDefault().getLineSpacing());
    }

    public void setTextAlignment(@Nonnull Vector vector){
        attributes.setTextAlignment(vector);
    }

    public void resetTextAlignment(){
        setTextAlignment(null);
    }

    public void setInteractCooldown(long milliseconds){
        attributes.setInteractCooldown(milliseconds);
    }

    public void resetInteractCooldown(){
        setInteractCooldown(NPC.Attributes.getDefaultInteractCooldown());
    }

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

    public Interact.Actions.SetCustomData addSetCustomDataClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String key, @Nullable String value) { return (Interact.Actions.SetCustomData) addClickAction(new Interact.Actions.SetCustomData(this, clickType, key, value)); }

    public Interact.Actions.SetCustomData addSetCustomDataClickAction(@Nonnull String key, @Nullable String value) { return addSetCustomDataClickAction(Interact.ClickType.EITHER, key, value); }

    public Interact.Actions.Player.GiveItem addGivePlayerItemClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull ItemStack itemStack) { return (Interact.Actions.Player.GiveItem) addClickAction(new Interact.Actions.Player.GiveItem(this, clickType, itemStack)); }

    public Interact.Actions.Player.GiveItem addGivePlayerItemClickAction(@Nonnull ItemStack itemStack) { return addGivePlayerItemClickAction(Interact.ClickType.EITHER, itemStack); }

    public Interact.Actions.Player.OpenWorkbench addOpenWorkbenchClickAction(@Nullable NPC.Interact.ClickType clickType) { return (Interact.Actions.Player.OpenWorkbench) addClickAction(new Interact.Actions.Player.OpenWorkbench(this, clickType)); }

    public Interact.Actions.Player.OpenWorkbench addOpenWorkbenchClickAction() { return addOpenWorkbenchClickAction(Interact.ClickType.EITHER); }

    @Deprecated
    public Interact.Actions.Player.OpenEnchanting addOpenEnchantingClickAction(@Nullable NPC.Interact.ClickType clickType) { return (Interact.Actions.Player.OpenEnchanting) addClickAction(new Interact.Actions.Player.OpenEnchanting(this, clickType)); }

    @Deprecated
    public Interact.Actions.Player.OpenEnchanting addOpenEnchantingClickAction() { return addOpenEnchantingClickAction(Interact.ClickType.EITHER); }

    public Interact.Actions.Player.OpenBook addOpenBookClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull ItemStack book) { return (Interact.Actions.Player.OpenBook) addClickAction(new Interact.Actions.Player.OpenBook(this, clickType, book)); }

    public Interact.Actions.Player.OpenBook addOpenBookClickAction(@Nonnull ItemStack book) { return addOpenBookClickAction(Interact.ClickType.EITHER, book); }

    public Interact.Actions.PlayAnimation addPlayAnimationClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Animation animation) { return (Interact.Actions.PlayAnimation) addClickAction(new Interact.Actions.PlayAnimation(this, clickType, animation)); }

    public Interact.Actions.PlayAnimation addPlayAnimationClickAction(@Nonnull Animation animation) { return addPlayAnimationClickAction(Interact.ClickType.EITHER, animation); }

    public Interact.Actions.Player.PlaySound addPlayerPlaySoundClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Sound sound) { return (Interact.Actions.Player.PlaySound) addClickAction(new Interact.Actions.Player.PlaySound(this, clickType, sound)); }

    public Interact.Actions.Player.PlaySound addPlayerPlaySoundClickAction(@Nonnull Sound sound) { return addPlayerPlaySoundClickAction(Interact.ClickType.EITHER, sound); }

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

    public void removeClickAction(NPC.Interact.ClickAction clickAction){
        if(this.clickActions.contains(clickAction)) clickActions.remove(clickAction);
    }

    public void resetClickActions(){
        this.clickActions = new ArrayList<>();
    }

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

    public void setShowOnTabList(boolean show){
        attributes.setShowOnTabList(show);
    }

    public void setMoveSpeed(@Nullable Move.Speed moveSpeed){
        attributes.setMoveSpeed(moveSpeed);
    }

    public void setMoveSpeed(double moveSpeed){
        attributes.setMoveSpeed(moveSpeed);
    }

    public abstract void playAnimation(NPC.Animation animation);

    public abstract void hit();

    public void setOnFire(boolean onFire) {
        attributes.setOnFire(onFire);
    }

    public void setFireTicks(@Nonnull Integer ticks){
        setOnFire(true);
        update();
        Bukkit.getScheduler().runTaskLater(pluginManager.getPlugin(), ()->{
            if(isOnFire()){
                setOnFire(false);
                update();
            }
        }, ticks.longValue());
    }

    public void setCustomTabListName(@Nullable String name, boolean show){
        setCustomTabListName(name);
        setShowOnTabList(show);
    }

    public abstract void setCustomTabListName(@Nullable String name);

    public void resetCustomTabListName(){
        setCustomTabListName(null);
    }

    public void setCustomData(String key, String value){
        if(customData.containsKey(key.toLowerCase()) && value == null){
            customData.remove(key.toLowerCase());
            return;
        }
        customData.put(key.toLowerCase(), value);
    }

    public String getCustomData(String key){
        if(!customData.containsKey(key.toLowerCase())) return null;
        return customData.get(key.toLowerCase());
    }

    public Set<String> getCustomDataKeys(){
        return customData.keySet();
    }

    public boolean hasCustomData(String key){
        return customData.containsKey(key.toLowerCase());
    }

    /*
                Protected and private access methods
    */

    protected abstract void move(double moveX, double moveY, double moveZ);

    protected abstract void updatePlayerRotation();

    protected void setClickActions(@Nonnull List<NPC.Interact.ClickAction> clickActions){
        this.clickActions = clickActions;
    }

    protected void setSlots(HashMap<NPC.Slot, ItemStack> slots){
        attributes.setSlots(slots);
    }

    protected abstract void updateLocation();

    protected abstract void updateMove();


    protected Interact.ClickAction addClickAction(@Nonnull NPC.Interact.ClickAction clickAction){
        this.clickActions.add(clickAction);
        return clickAction;
    }

    protected void clearMoveTask(){
        this.moveTask = null;
    }

    /*
                             Getters
    */

    public Location getLocation(){
        return new Location(getWorld(), getX(), getY(), getZ(), getYaw(), getPitch());
    }

    protected HashMap<NPC.Slot, ItemStack> getEquipment(){
        return attributes.slots;
    }

    public ItemStack getEquipment(NPC.Slot npcSlot){
        return attributes.slots.get(npcSlot);
    }

    public double getMoveSpeed() {
        return attributes.moveSpeed;
    }

    public Move.Task getMoveTask() {
        return moveTask;
    }

    protected Move.Behaviour getMoveBehaviour(){ return moveBehaviour; }

    public Move.Behaviour.Type getMoveBehaviourType(){
        return moveBehaviour.getType();
    }

    public World getWorld() {
        return world;
    }

    public Double getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    public Double getZ() {
        return z;
    }

    public Float getYaw() {
        return yaw;
    }

    public Float getPitch() {
        return pitch;
    }

    public NPCLib getNPCLib() {
        return pluginManager.getNPCLib();
    }

    public String getCode() {
        return code;
    }

    public String getSimpleCode() { return "" + this.code.replaceFirst("" + getPlugin().getName().toLowerCase() + "\\.", ""); }

    public List<String> getText() {
        return attributes.text;
    }

    public NPC.Skin getSkin() {
        return attributes.skin;
    }

    public boolean isCollidable() {
        return attributes.collidable;
    }

    public Double getHideDistance() {
        return attributes.hideDistance;
    }

    public Double getLineSpacing(){
        return attributes.lineSpacing;
    }

    public Vector getTextAlignment() {
        return attributes.textAlignment;
    }

    public Long getInteractCooldown() {
        return attributes.interactCooldown;
    }

    public NPC.Color getGlowingColor() {
        return attributes.glowingColor;
    }

    protected HashMap<NPC.Slot, ItemStack> getSlots() {
        return attributes.slots;
    }

    public boolean isShowOnTabList() { return attributes.showOnTabList; }

    public String getCustomTabListName() { return attributes.customTabListName; }

    public boolean isGlowing() { return attributes.glowing; }

    public GazeTrackingType getGazeTrackingType() { return attributes.gazeTrackingType; }

    public NPC.Pose getPose() { return attributes.pose; }

    public NPC.Skin.Parts getSkinParts() { return attributes.skinParts; }

    public NPC.Hologram.Opacity getLineOpacity(int line){ return attributes.getLineOpacity(line); }

    public NPC.Hologram.Opacity getTextOpacity() { return attributes.textOpacity; }

    public boolean isOnFire() { return attributes.onFire; }

    public NPC.Attributes getAttributes() { return attributes; }

    public Plugin getPlugin() { return pluginManager.getPlugin(); }

    public NPCLib.PluginManager getPluginManager() {
        return pluginManager;
    }

    public List<NPC.Interact.ClickAction> getClickActions() { return clickActions.stream().collect(Collectors.toList()); }

    public Integer getClickActionsSize() { return clickActions.size(); }

    public List<NPC.Interact.ClickAction> getClickActions(@Nonnull NPC.Interact.ClickType clickType){ return this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType)).collect(Collectors.toList()); }

    protected HashMap<Integer, NPC.Hologram.Opacity> getLinesOpacity() { return attributes.linesOpacity; }

    public static class Personal extends NPC{

        private final Player player;
        private final UUID gameProfileID;
        private EntityPlayer entityPlayer;
        private NPC.Hologram npcHologram;
        private boolean canSee;
        private boolean hiddenText;
        private boolean hiddenToPlayer;
        private boolean shownOnTabList;
        private NPC.Global global;

        protected Personal(@Nonnull NPCLib npcLib, @Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull World world, double x, double y, double z, float yaw, float pitch){
            super(npcLib, plugin, code, world, x, y, z, yaw, pitch);
            Validate.notNull(player, "Cannot generate NPC instance, Player cannot be null.");
            this.player = player;
            this.gameProfileID = UUID.randomUUID();
            this.canSee = false;
            this.npcHologram = null;
            this.shownOnTabList = false;
            this.hiddenToPlayer = true;
            this.hiddenText = false;
            this.global = null;
            npcLib.getNPCPlayerManager(player).set(code, this);
        }

        protected Personal(@Nonnull NPCLib npcLib, @Nonnull Player player, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Location location){
            this(npcLib, player, plugin, code, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public void create(){
            Validate.notNull(super.attributes.skin, "Failed to create the NPC. The NPC.Skin has not been configured.");
            Validate.isTrue(entityPlayer == null, "Failed to create the NPC. This NPC has already been created before.");
            MinecraftServer server = NMSCraftServer.getMinecraftServer();
            WorldServer worldServer = NMSCraftWorld.getWorldServer(super.world);
            GameProfile gameProfile = new GameProfile(gameProfileID, getReplacedCustomName());
            entityPlayer = NMSEntityPlayer.newEntityPlayer(server, worldServer, gameProfile);
            if(entityPlayer == null){
                gameProfile = new GameProfile(gameProfileID, ChatColor.stripColor(getReplacedCustomName()));
                entityPlayer = NMSEntityPlayer.newEntityPlayer(server, worldServer, gameProfile);
            }
            Validate.notNull(entityPlayer, "Error at NMSEntityPlayer");
            NMSEntity.setLocation(entityPlayer, super.x, super.y, super.z, super.yaw, super.pitch);
            this.npcHologram = new NPC.Hologram(this, player);
            updateSkin();
            updatePose();
            updateScoreboard(player);
        }


        public void update(){
            Validate.notNull(entityPlayer, "Failed to update the NPC. The NPC has not been created yet.");
            if(!canSee) return;
            if(!hiddenToPlayer && !isInRange()){
                hideToPlayer();
                return;
            }
            if(hiddenToPlayer && isInRange() && isInView()){
                showToPlayer();
                return;
            }
            updatePose();
            updateLook();
            updateSkin();
            updatePlayerRotation();
            updateEquipment();
            updateMetadata();
        }

        public void forceUpdate(){
            Validate.notNull(entityPlayer, "Failed to force update the NPC. The NPC has not been created yet.");
            reCreate();
            update();
            forceUpdateText();
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
            boolean show = canSee;
            if(npcHologram != null) npcHologram.hide();
            reCreate();
            if(npcHologram != null) forceUpdateText();
            if(show) show();
            else if(npcHologram != null) hideText();
        }

        public void updateText(){
            if(npcHologram == null) return;
            int i = 1;
            for(String s : super.attributes.text){
                npcHologram.setLine(i, s);
                i++;
            }
            npcHologram.update();
        }

        public void forceUpdateText(){
            if(npcHologram == null) return;
            npcHologram.forceUpdate();
        }

        public void destroy(){
            cancelMove();
            if(entityPlayer != null){
                if(canSee) hide();
                entityPlayer = null;
            }
            if(npcHologram != null) npcHologram.removeHologram();
        }

        public void show(){
            Validate.notNull(entityPlayer, "Failed to show NPC. The NPC has not been created yet.");
            if(canSee && !hiddenToPlayer) return;
            NPC.Events.Show npcShowEvent = new NPC.Events.Show(getPlayer(), this);
            if(npcShowEvent.isCancelled()) return;
            canSee = true;
            if(!isInRange() || !isInView()){
                hiddenToPlayer = true;
                return;
            }
            //showToPlayer();
            double hideDistance = super.attributes.hideDistance;
            super.attributes.hideDistance = 0.0;
            Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), ()-> {
                super.attributes.hideDistance = hideDistance;
                showToPlayer();
            },10);
        }

        public void hide(){
            Validate.notNull(entityPlayer, "Failed to hide the NPC. The NPC has not been created yet.");
            if(!canSee) return;
            NPC.Events.Hide npcHideEvent = new NPC.Events.Hide(getPlayer(), this);
            if(npcHideEvent.isCancelled()) return;
            hideToPlayer();
            canSee = false;
            return;
        }

        public void setHideText(boolean hide){
            boolean a = hiddenText;
            this.hiddenText = hide;
            if(a == hide) return;
            if(npcHologram == null) return;
            if(hide) hideText();
            else showText();
        }

        @Override
        public void setCustomTabListName(@Nullable String name){
            if(name == null) name = Attributes.getDefaultTabListName();
            final String finalName = getReplacedCustomName(name);
            Validate.isTrue(finalName.length() <= 16, "Error setting custom tab list name. Name must be 16 or less characters.");
            if(!name.contains("{id}")) Validate.isTrue(getNPCLib().getNPCPlayerManager(player).getNPCs().stream().filter(x-> x.getReplacedCustomName().equals(finalName)).findAny().orElse(null) == null, "Error setting custom tab list name. There's another NPC with that name already.");
            super.attributes.setCustomTabListName(name);
        }

        @Override
        protected void updateLocation() {
            updateLocation(player);
        }

        public Move.Behaviour followPlayer(){
            return super.moveBehaviour.setFollowPlayer();
        }

        public Move.Behaviour followPlayer(double min, double max){
            return super.moveBehaviour.setFollowPlayer(min, max);
        }

        public void playAnimation(NPC.Animation animation){
            if(animation.isDeprecated()) return;
            PacketPlayOutAnimation packet = new PacketPlayOutAnimation(entityPlayer, animation.getId());
            NMSCraftPlayer.sendPacket(player, packet);
        }

        @Override
        public void hit() {
            player.playSound(getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
            playAnimation(Animation.TAKE_DAMAGE);
        }

        public void lookAt(float yaw, float pitch){
            Validate.notNull(entityPlayer, "Failed to set look direction. The NPC has not been created yet.");
            float a = Math.abs(yaw - super.yaw);
            if(a > 45){
                // DO STUFF
            }
            super.yaw = yaw; //yRot
            super.pitch = pitch; //xRot
            NMSEntity.setYRot(entityPlayer, yaw);
            NMSEntity.setXRot(entityPlayer, pitch);
        }

    /*
                Protected and private access methods
    */

        protected void reCreate(){
            Validate.notNull(entityPlayer, "Failed to re-create the NPC. The NPC has not been created yet.");
            boolean show = canSee;
            hide();
            entityPlayer = null;
            create();
            if(show) show();
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

        protected void updateGlobalLocation(NPC.Global global){
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
            if(!canSee) return;
            if(!hiddenToPlayer && !isInRange()){
                hideToPlayer();
                return;
            }
            if(hiddenToPlayer && isInRange() && isInView()){
                showToPlayer();
                return;
            }
            updateLook();
            updatePlayerRotation();
        }

        private void updateLook(){
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

        protected void updateLocation(Player player){
            if(entityPlayer == null) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityTeleport(entityPlayer));
        }

        protected void updateScoreboard(Player player){
            GameProfile gameProfile = NMSEntityPlayer.getGameProfile(entityPlayer);
            Scoreboard scoreboard = NMSCraftScoreboard.getScoreboard(player);
            ScoreboardTeam scoreboardTeam = NMSScoreboard.getTeam(scoreboard, getShortUUID()) == null ? new ScoreboardTeam(scoreboard, getShortUUID()) : NMSScoreboard.getTeam(scoreboard, getShortUUID());
            NMSScoreboard.setNameTagVisibility(scoreboardTeam, NMSScoreboard.nameTagVisibility_NEVER);
            NMSScoreboard.setTeamColor(scoreboardTeam, getGlowingColor().getEnumChatFormat());
            ScoreboardTeamBase.EnumTeamPush var1 = NMSScoreboard.teamPush_NEVER;
            if(isCollidable()) var1 = NMSScoreboard.teamPush_ALWAYS;
            NMSScoreboard.setTeamPush(scoreboardTeam, var1);
            NMSScoreboard.setPlayerTeam(scoreboard, gameProfile.getName(), scoreboardTeam);
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, true));
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, false));
        }

        @Override
        protected void updatePlayerRotation(){
            if(entityPlayer == null) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutEntityLook(NMSEntity.getEntityID(entityPlayer), (byte) ((super.yaw * 256 / 360)), (byte) ((super.pitch * 256 / 360)), false));
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) (super.yaw * 256 / 360)));
        }

        protected void updateSkin(){
            GameProfile gameProfile = NMSEntityPlayer.getGameProfile(entityPlayer);
            gameProfile.getProperties().get("textures").clear();
            gameProfile.getProperties().put("textures", new Property("textures", super.attributes.skin.getTexture(), super.attributes.skin.getSignature()));
        }

        protected void updatePose(){
            if(getPose().equals(NPC.Pose.SLEEPING)) entityPlayer.e(new BlockPosition(super.x, super.y, super.z));
            NMSEntity.setPose(entityPlayer, getPose().getEntityPose());
        }

        protected void move(double x, double y, double z){
            Validate.isTrue(x < 8 && y < 8 && z < 8, "NPC cannot move 8 blocks or more at once, use teleport instead");
            NPC.Events.Move npcMoveEvent = new NPC.Events.Move(this, new Location(super.world, super.x + x, super.y + y, super.z + z));
            if(npcMoveEvent.isCancelled()) return;
            super.x += x;
            super.y += y;
            super.z += z;
            entityPlayer.g(super.x, super.y, super.z);
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
            NMSEntity.setGlowingTag(entityPlayer, isGlowing());
            Map<Integer, DataWatcher.Item<?>> map = null;
            try{ map = (Map<Integer, DataWatcher.Item<?>>) FieldUtils.readDeclaredField(dataWatcher, "f", true); } catch (IllegalAccessException e){}
            if(map == null) return;
            //http://wiki.vg/Entities#Entity
            //https://wiki.vg/Entity_metadata#Entity_Metadata_Format

            //Entity
            DataWatcher.Item item = map.get(0);
            byte initialBitMask = (Byte) item.b();
            byte b = initialBitMask;
            byte bitMaskIndex = (byte) 0x40;
            if(isGlowing()) b = (byte) (b | bitMaskIndex);
            else b = (byte) (b & ~(1 << bitMaskIndex));
            bitMaskIndex = (byte) 0x01;
            if(isOnFire()) b = (byte) (b | bitMaskIndex);
            else b = (byte) (b & ~(1 << bitMaskIndex));
            NMSDataWatcher.set(dataWatcher, DataWatcherRegistry.a.a(0), b);
            //
            //Player
            b = 0x00;
            //byte b = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40;
            NPC.Skin.Parts parts = getSkinParts();
            if(parts.isVisible(Skin.Part.CAPE)) b = (byte) (b | 0x01);
            if(parts.isVisible(Skin.Part.JACKET)) b = (byte) (b | 0x02);
            if(parts.isVisible(Skin.Part.LEFT_SLEEVE)) b = (byte) (b | 0x04);
            if(parts.isVisible(Skin.Part.RIGHT_SLEEVE)) b = (byte) (b | 0x08);
            if(parts.isVisible(Skin.Part.LEFT_PANTS)) b = (byte) (b | 0x10);
            if(parts.isVisible(Skin.Part.RIGHT_PANTS)) b = (byte) (b | 0x20);
            if(parts.isVisible(Skin.Part.HAT)) b = (byte) (b | 0x40);
            NMSDataWatcher.set(dataWatcher, DataWatcherRegistry.a.a(17), b);
            //
            PacketPlayOutEntityMetadata metadataPacket = new PacketPlayOutEntityMetadata(NMSEntity.getEntityID(entityPlayer), dataWatcher, true);
            NMSCraftPlayer.sendPacket(player, metadataPacket);
        }

        protected void updateEquipment(){
            List<Pair<EnumItemSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
            for(NPC.Slot slot : NPC.Slot.values()){
                EnumItemSlot nmsSlot = slot.getNmsEnum(EnumItemSlot.class);
                if(!getSlots().containsKey(slot)) getSlots().put(slot, new ItemStack(Material.AIR));
                ItemStack item = getSlots().get(slot);
                net.minecraft.world.item.ItemStack craftItem = null;
                try{ craftItem = (net.minecraft.world.item.ItemStack) NMSCraftItemStack.getCraftItemStackAsNMSCopy().invoke(null, item); }
                catch (Exception e){}
                Validate.notNull(craftItem, "Error at NMSCraftItemStack");
                equipment.add(new Pair(nmsSlot, craftItem));
            }
            PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(NMSEntity.getEntityID(entityPlayer), equipment);
            NMSCraftPlayer.sendPacket(player, packet);
        }

        private void createPacket(){
            try{
                NMSCraftPlayer.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.a, entityPlayer)); //EnumPlayerInfoAction.ADD_PLAYER
                NMSCraftPlayer.sendPacket(player, new PacketPlayOutNamedEntitySpawn(entityPlayer));
            }
            catch (Exception e){ return; }
            shownOnTabList = true;
            updatePlayerRotation();
            if(isShowOnTabList()) return;
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), ()-> {
                if(!isCreated()) return;
                NMSCraftPlayer.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer)); //EnumPlayerInfoAction.REMOVE_PLAYER
                shownOnTabList = false;
            }, pluginManager.getTicksUntilTabListHide());
        }

        private void showToPlayer(){
            if(!hiddenToPlayer) return;
            createPacket();
            hiddenToPlayer = false;
            if(getText().size() > 0) updateText();
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), () -> {
                if(!isCreated()) return;
                update();
            }, 1);
        }

        private void hideToPlayer(){
            if(hiddenToPlayer) return;
            if(shownOnTabList){
                NMSCraftPlayer.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer)); //EnumPlayerInfoAction.REMOVE_PLAYER
                shownOnTabList = false;
            }
            NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityDestroy.createPacket(NMSEntity.getEntityID(entityPlayer)));
            if(npcHologram != null) npcHologram.hide();
            hiddenToPlayer = true;
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

        protected void changeWorld(World world){
            super.getPluginManager().getNPCLib().getNPCPlayerManager(player).changeWorld(this, super.world, world);
            super.world = world;
        }

        protected String getReplacedCustomName(){ return getReplacedCustomName(super.attributes.getCustomTabListName()); }

        protected String getReplacedCustomName(String name){
            String id = getShortUUID();
            String replaced = name.replaceAll("\\{id\\}", id);
            if(replaced.length() > 16) replaced = replaced.substring(0, 15);
            return replaced;
        }

    /*
                             Getters
    */

        public EntityPlayer getEntity(){ return this.entityPlayer; }

        public String getShortUUID(){ return gameProfileID.toString().split("-")[1]; }

        public boolean isInView(){
            return isInView(60.0D);
        }

        public boolean isInView(double fov){
            Vector dir = getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            return dir.dot(player.getEyeLocation().getDirection()) >= Math.cos(Math.toRadians(fov));
        }

        public boolean isInRange(){
            if(!getWorld().getName().equals(player.getWorld().getName())) return false;
            return getLocation().distance(player.getLocation()) < getHideDistance();
        }

        public Player getPlayer() {
            return player;
        }

        public boolean isShown(){
            return canSee;
        }

        public boolean isShownOnClient() { return canSee && !hiddenToPlayer; }

        public boolean canSee() {
            return canSee;
        }

        public boolean isHiddenText() {
            return hiddenText;
        }

        public boolean isCreated(){
            return entityPlayer != null;
        }

        public boolean canBeCreated(){
            return getSkin() != null && entityPlayer == null;
        }

        protected NPC.Hologram getHologram() {
            return npcHologram;
        }

        public boolean hasGlobal(){ return global != null; }

        public NPC.Global getGlobal(){
            return global;
        }

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

        protected Global(@Nonnull NPCLib npcLib, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull World world, double x, double y, double z, float yaw, float pitch) {
            super(npcLib, plugin, code, world, x, y, z, yaw, pitch);
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
        }

        protected Global(@Nonnull NPCLib npcLib, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
            this(npcLib, plugin, code, visibility, visibilityRequirement, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
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

        public Visibility getVisibility() {
            return visibility;
        }

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
            NPC.Personal personal = getNPCLib().generatePlayerPersonalNPC(player, getPlugin(), getNPCLib().getFormattedName(getPlugin(), "global_" + getSimpleCode()), getLocation());
            personal.global = this;
            players.put(player, personal);
            if(!selectedPlayers.contains(player.getName())) selectedPlayers.add(player.getName());
            if(!customAttributes.containsKey(player.getUniqueId())) customAttributes.put(player.getUniqueId(), new Attributes(null));
            //
            updateAttributes(player);
            if(autoCreate) personal.create();
            if(autoCreate && autoShow) personal.show();
        }

        public void removePlayers(@Nonnull Collection<Player> players){
            Validate.notNull(players, "Cannot remove a null collection of Players");
            players.forEach(x-> removePlayer(x));
        }

        public PersistentManager getPersistentManager() {
            return persistentManager;
        }

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
            players.keySet().stream().filter(x-> isActive(x)).forEach(x-> action.accept(x, getPersonal(x)));
        }

        public boolean hasPlayer(@Nonnull Player player){
            Validate.notNull(player, "Cannot verify a null Player");
            return players.containsKey(player);
        }

        public List<String> getSelectedPlayers() {
            return selectedPlayers;
        }

        public void addSelectedPlayer(String playerName){
            if(!StringUtils.containsIgnoreCase(selectedPlayers, playerName)) selectedPlayers.add(playerName);
        }

        public void removeSelectedPlayer(String playerName){
            if(StringUtils.containsIgnoreCase(selectedPlayers, playerName)) selectedPlayers.remove(playerName);
        }

        public boolean hasSelectedPlayer(String playerName){
            return StringUtils.containsIgnoreCase(selectedPlayers, playerName);
        }

        public Set<Player> getPlayers(){
            return players.keySet();
        }

        public Predicate<Player> getVisibilityRequirement(){
            return visibilityRequirement;
        }

        public boolean hasVisibilityRequirement(){
            return visibilityRequirement != null;
        }

        protected boolean isActive(Player player){
            if(!player.isOnline()) return false;
            if(!hasPlayer(player)) return false;
            NPC.Personal personal = getPersonal(player);
            if(!personal.isCreated()) return false;
            return true;
        }

        public void create(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            updateAttributes(player);
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

        public void update(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            updateAttributes(player);
            getPersonal(player).update();
        }

        public void forceUpdate(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            updateAttributes(player);
            getPersonal(player).forceUpdate();
        }

        public void updateText(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            updateAttributes(player);
            getPersonal(player).updateText();
        }

        public void forceUpdateText(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            updateAttributes(player);
            getPersonal(player).forceUpdateText();
        }

        public void destroy(@Nonnull Player player){
            Validate.notNull(player, "Player cannot be null");
            getPersonal(player).destroy();
        }

        public boolean isAutoCreate() {
            return autoCreate;
        }

        public void setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
        }

        public boolean isAutoShow() {
            return autoShow;
        }

        public void setAutoShow(boolean autoShow) {
            this.autoShow = autoShow;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public boolean canBePersistent() { return getPlugin().equals(PlayerNPCPlugin.getInstance()); }

        public void setPersistent(boolean persistent){
            Validate.isTrue(canBePersistent(), "This NPC cannot be persistent because is not created by PlayerNPC plugin.");
            if(persistent == this.persistent) return;
            this.persistent = persistent;
            if(persistent){
                persistentManager = PersistentManager.getPersistent(getPlugin(),getSimpleCode());
                PersistentManager.getPersistent(getPlugin(),getSimpleCode()).save();
            }
            else PersistentManager.getPersistent(getPlugin(), getSimpleCode()).remove();
        }

        public void setCustomText(Player player, List<String> lines){
            getCustomAttributes(player).setText(lines);
        }

        public void resetCustomText(Player player){
            getCustomAttributes(player).text = null;
        }

        public void setCustomSkin(Player player, NPC.Skin skin){ getCustomAttributes(player).setSkin(skin); }

        public void resetCustomSkin(Player player){
            getCustomAttributes(player).skin = null;
        }

        public void setCustomCollidable(Player player, boolean collidable){ getCustomAttributes(player).setCollidable(collidable); }

        public void resetCustomCollidable(Player player){
            getCustomAttributes(player).collidable = null;
        }

        public void setCustomHideDistance(Player player, double hideDistance){ getCustomAttributes(player).setHideDistance(hideDistance); }

        public void resetCustomHideDistance(Player player){
            getCustomAttributes(player).hideDistance = null;
        }

        public void setCustomGlowing(Player player, boolean glowing){ getCustomAttributes(player).setGlowing(glowing); }

        public void resetCustomGlowing(Player player){
            getCustomAttributes(player).glowing = null;
        }

        public void setCustomGlowingColor(Player player, NPC.Color color){ getCustomAttributes(player).setGlowingColor(color); }

        public void resetCustomGlowingColor(Player player){
            getCustomAttributes(player).glowingColor = null;
        }

        public void setCustomGazeTrackingType(Player player, GazeTrackingType followLookType){ getCustomAttributes(player).setGazeTrackingType(followLookType); }

        public void resetCustomGazeTrackingType(Player player){
            getCustomAttributes(player).gazeTrackingType = null;
        }

        public void setCustomTabListName(Player player, String customTabListName){ getCustomAttributes(player).setCustomTabListName(customTabListName); }

        public void resetCustomTabListName(Player player){
            getCustomAttributes(player).customTabListName = null;
        }

        public void setCustomShowOnTabList(Player player, boolean showOnTabList){ getCustomAttributes(player).setShowOnTabList(showOnTabList); }

        public void resetCustomShowOnTabList(Player player){
            getCustomAttributes(player).showOnTabList = null;
        }

        public void setCustomPose(Player player, NPC.Pose pose){ getCustomAttributes(player).setPose(pose); }

        public void resetCustomPose(Player player){
            getCustomAttributes(player).pose = null;
        }

        public void setCustomLineSpacing(Player player, double lineSpacing){ getCustomAttributes(player).setLineSpacing(lineSpacing); }

        public void resetCustomLineSpacing(Player player){
            getCustomAttributes(player).lineSpacing = null;
        }

        public void setCustomTextAlignment(Player player, Vector alignment){ getCustomAttributes(player).setTextAlignment(alignment.clone()); }

        public void resetCustomTextAlignment(Player player){
            getCustomAttributes(player).textAlignment = null;
        }

        public void setCustomInteractCooldown(Player player, long millis){ getCustomAttributes(player).setInteractCooldown(millis); }

        public void resetCustomInteractCooldown(Player player){
            getCustomAttributes(player).interactCooldown = null;
        }

        public void setCustomTextOpacity(Player player, NPC.Hologram.Opacity opacity){ getCustomAttributes(player).setTextOpacity(opacity); }

        public void resetCustomTextOpacity(Player player){
            getCustomAttributes(player).textOpacity = null;
        }

        public void setCustomMoveSpeed(Player player, double moveSpeed){ getCustomAttributes(player).setMoveSpeed(moveSpeed); }

        public void resetCustomMoveSpeed(Player player){
            getCustomAttributes(player).moveSpeed = null;
        }

        public void setCustomOnFire(Player player, boolean onFire){ getCustomAttributes(player).setOnFire(onFire); }

        public void resetCustomOnFire(Player player){
            getCustomAttributes(player).onFire = null;
        }

        public void resetAllCustomAttributes(Player player) { customAttributes.put(player.getUniqueId(), new Attributes(null)); }

        public boolean isResetCustomAttributesWhenRemovePlayer() {
            return resetCustomAttributes;
        }

        public void setResetCustomAttributesWhenRemovePlayer(boolean resetCustomAttributes) {
            this.resetCustomAttributes = resetCustomAttributes;
        }

        private void updateAttributes(Player player){
            NPC.Personal personal = getPersonal(player);
            NPC.Attributes A = getAttributes();
            NPC.Attributes cA = getCustomAttributes(player);
            personal.updateGlobalLocation(this);
            if(ownPlayerSkin && (personal.getSkin().getType().equals(Skin.Type.CUSTOM) || !((Skin.Minecraft) personal.getSkin()).getPlayerName().equals(player.getName()))) personal.setSkin(player, skin -> personal.forceUpdate());
            else personal.setSkin(cA.skin != null ? cA.skin : A.skin);
            personal.setSkinParts(cA.skinParts != null ? cA.skinParts : A.skinParts);
            personal.setCollidable(cA.collidable != null ? cA.collidable : A.collidable);
            personal.setText(cA.text != null ? cA.text : A.text);
            personal.setHideDistance(cA.hideDistance != null ? cA.hideDistance : A.hideDistance);
            personal.setGlowing(cA.glowing != null ? cA.glowing : A.glowing);
            personal.setGlowingColor(cA.glowingColor != null ? cA.glowingColor : A.glowingColor);
            personal.setGazeTrackingType(cA.gazeTrackingType != null ? cA.gazeTrackingType : A.gazeTrackingType);
            personal.setSlots((HashMap<Slot, ItemStack>) (cA.slots != null ? cA.slots : A.slots).clone());
            personal.setCustomTabListName(cA.customTabListName != null ? cA.customTabListName : A.customTabListName);
            personal.setShowOnTabList(cA.showOnTabList != null ? cA.showOnTabList : A.showOnTabList);
            personal.setPose(cA.pose != null ? cA.pose : A.pose);
            personal.setLineSpacing(cA.lineSpacing != null ? cA.lineSpacing : A.lineSpacing);
            personal.setTextAlignment((cA.textAlignment != null ? cA.textAlignment : A.textAlignment).clone());
            personal.setInteractCooldown(cA.interactCooldown != null ? cA.interactCooldown : A.interactCooldown);
            personal.setTextOpacity(cA.textOpacity != null ? cA.textOpacity : A.textOpacity);
            personal.setMoveSpeed(cA.moveSpeed != null ? cA.moveSpeed : A.moveSpeed);
            personal.setOnFire(cA.onFire != null ? cA.onFire : A.onFire);
            personal.setLinesOpacity((HashMap<Integer, Hologram.Opacity>) (cA.linesOpacity != null ? cA.linesOpacity : A.linesOpacity).clone());
        }

        public void createAllPlayers(){
            players.forEach((player, npc)->{
                if(!npc.isCreated()) create(player);
            });
        }

        public void show(){
            forEachActivePlayer((player, npc) -> show(player));
        }

        public void hide(){
            forEachActivePlayer((player, npc) -> hide(player));
        }

        @Override
        public void update() {
            forEachActivePlayer((player, npc) -> update(player));
        }

        @Override
        public void forceUpdate() {
            forEachActivePlayer((player, npc) -> forceUpdate(player));
        }

        @Override
        public void updateText() {
            forEachActivePlayer((player, npc) -> updateText(player));
        }

        @Override
        public void forceUpdateText() {
            forEachActivePlayer((player, npc) -> forceUpdateText(player));
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
        public void lookAt(float yaw, float pitch) {
            super.yaw = yaw;
            super.pitch = pitch;
            forEachActivePlayer((player, npc)-> npc.lookAt(yaw, pitch));
        }

        @Override
        public void playAnimation(Animation animation) {
            forEachActivePlayer((player, npc) -> { playAnimation(player, animation); });
        }

        public void playAnimation(Player player, Animation animation){
            getPersonal(player).playAnimation(animation);
        }

        @Override
        public void hit() {
            playAnimation(Animation.TAKE_DAMAGE);
            forEachActivePlayer((player, npc) -> player.playSound(getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0F, 1.0F));
        }

        @Override
        public void setCustomTabListName(@Nullable String name) {
            forEachActivePlayer((player, npc) -> npc.setCustomTabListName(name));
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
        protected void updatePlayerRotation() {
            forEachActivePlayer((player, npc) -> updatePlayerRotation(player));
        }

        protected void updatePlayerRotation(Player player){
            getPersonal(player).updatePlayerRotation();
        }

        @Override
        protected void updateLocation() {
            forEachActivePlayer((player, npc) -> npc.updateLocation());
        }

        @Override
        protected void updateMove() {
            forEachActivePlayer((player, npc) -> npc.updateMove());
        }

        public Item dropItem(ItemStack itemStack){
            if(itemStack == null || itemStack.getType().equals(Material.AIR)) return null;
            return getWorld().dropItemNaturally(getLocation(), itemStack);
        }

        public Item dropItemInSlot(NPC.Slot slot){
            ItemStack itemStack = getEquipment(slot);
            if(itemStack == null || itemStack.getType().equals(Material.AIR)) return null;
            clearEquipment(slot);
            Item item = dropItem(itemStack);
            update();
            return item;
        }

        public Item dropItemInHand(){
            return dropItemInSlot(Slot.MAINHAND);
        }

        public void setOwnPlayerSkin(boolean ownPlayerSkin){
            this.ownPlayerSkin = ownPlayerSkin;
        }

        public boolean isOwnPlayerSkin() {
            return ownPlayerSkin;
        }

        public void setOwnPlayerSkin(){
            setOwnPlayerSkin(true);
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

            public static PersistentManager getPersistent(Plugin plugin, String id){
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
                PERSISTENT_DATA.get(plugin).values().stream().filter(x-> x.global != null).forEach(x -> action.accept(x.global));
            }

            protected static void forEachPersistentManager(Consumer<NPC.Global.PersistentManager> action){
                PERSISTENT_DATA.forEach((x, y) -> forEachPersistentManager(x, action));
            }

            protected static void forEachPersistentManager(Plugin plugin, Consumer<NPC.Global.PersistentManager> action){
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
                checkFileExists();
                this.config = YamlConfiguration.loadConfiguration(file);
                if(global != null) NPCLib.getInstance().removeGlobalNPC(global);
                Location location = config.getLocation("location");
                Visibility visibility = Visibility.EVERYONE;
                String visibilityPermission = null;
                if(config.contains("visibility.type")) visibility = Visibility.valueOf(config.getString("visibility.type"));
                if(config.contains("visibility.requirement")) visibilityPermission = config.getString("visibility.requirement");
                String finalVisibilityPermission = visibilityPermission;
                global = NPCLib.getInstance().generateGlobalNPC(plugin, id, visibility, visibilityPermission != null ? (player -> player.hasPermission(finalVisibilityPermission)) : null, location);
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
                    if(lines != null && lines.size() > 0){
                        List<String> coloredLines = new ArrayList<>();
                        lines.forEach(x-> coloredLines.add(x.replaceAll("&", "§")));
                        global.setText(coloredLines);
                    }
                }
                if(config.contains("hologram.textOpacity")) global.setTextOpacity(Hologram.Opacity.valueOf(config.getString("hologram.textOpacity")));
                if(config.getConfigurationSection("hologram.linesOpacity") != null){
                    for(String line : config.getConfigurationSection("hologram.linesOpacity").getKeys(false)) global.setLineOpacity(Integer.valueOf(line), Hologram.Opacity.valueOf(config.getString("hologram.linesOpacity." + line)));
                }
                if(config.contains("visibility.selectedPlayers") && global.getVisibility().equals(Visibility.SELECTED_PLAYERS)) global.selectedPlayers = config.getStringList("visibility.selectedPlayers");
                if(config.contains("hologram.alignment")) global.setTextAlignment(config.getVector("hologram.alignment"));
                if(config.contains("skin.ownPlayer")) global.setOwnPlayerSkin(config.getBoolean("skin.ownPlayer"));
                Arrays.stream(Skin.Part.values()).filter(x-> config.contains("skin.parts." + x.name().toLowerCase())).forEach(x-> global.getSkinParts().setVisible(x, config.getBoolean("skin.parts." + x.name().toLowerCase())));
                if(config.contains("glow.color")) global.setGlowingColor(Color.valueOf(config.getString("glow.color")));
                if(config.contains("glow.enabled")) global.setGlowing(config.getBoolean("glow.enabled"));
                if(config.contains("pose")) global.setPose(Pose.valueOf(config.getString("pose")));
                if(config.contains("collidable")) global.setCollidable(config.getBoolean("collidable"));
                if(config.contains("tabList.show")) global.setShowOnTabList(config.getBoolean("tabList.show"));
                if(config.contains("tabList.name")) global.setCustomTabListName(config.getString("tabList.name").replaceAll("&", "§"));
                if(config.contains("onFire")) global.setOnFire(config.getBoolean("onFire"));
                if(config.contains("move.speed")) global.setMoveSpeed(config.getDouble("move.speed"));
                if(config.contains("interact.cooldown")) global.setInteractCooldown(config.getLong("interact.cooldown"));
                if(config.contains("gazeTracking.type")) global.setGazeTrackingType(GazeTrackingType.valueOf(config.getString("gazeTracking.type")));
                if(config.contains("distance.hide")) global.setHideDistance(config.getDouble("distance.hide"));
                for(Slot slot : Slot.values()){
                    if(!config.contains("slots." + slot.name().toLowerCase())) continue;
                    ItemStack item = null;
                    try{ item = config.getItemStack("slots." + slot.name().toLowerCase()); } catch (Exception e) { config.set("slots." + slot.name().toLowerCase(), new ItemStack(Material.AIR)); }
                    global.setItem(slot, item);
                }
                Arrays.stream(Slot.values()).filter(x-> config.contains("slots." + x.name().toLowerCase())).forEach(x-> global.setItem(x, config.getItemStack("slots." + x.name().toLowerCase())));
                if(config.getConfigurationSection("customData") != null){
                    for(String keys : config.getConfigurationSection("customData").getKeys(false)) global.setCustomData(keys, config.getString("customData." + keys));
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
                                clickAction = global.addSetCustomDataClickAction(clickType, key, value);
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
                Bukkit.getConsoleSender().sendMessage(PlayerNPCPlugin.getInstance().getPrefix() + "§7Persistent Global NPC §a" + global.getCode() + " §7has been loaded.");
            }

            public void save(){
                if(global == null) global = NPCLib.getInstance().getGlobalNPC(plugin, id);
                if(global == null || !global.isPersistent()) return;
                try{
                    checkFileExists();
                    if(config == null) config  = YamlConfiguration.loadConfiguration(file);
                    //
                    if(config.contains("disableSaving") && config.getBoolean("disableSaving")) return;
                    NMSFileConfiguration.setHeader(config, Arrays.asList("Persistent Global NPC " + global.getCode()));
                    config.set("location", global.getLocation());
                    config.set("visibility.type", global.getVisibility().name());
                    config.set("visibility.requirement", global.getVisibilityRequirement() != null && global.getCustomDataKeys().contains("visibilityrequirementpermission") ? global.getCustomData("visibilityrequirementpermission") : null);
                    if(global.getVisibility().equals(Visibility.SELECTED_PLAYERS)) config.set("visibility.selectedPlayers", global.selectedPlayers);
                    else config.set("visibility.selectedPlayers", null);
                    config.set("skin.player", global.getSkin().getType().equals(Skin.Type.MINECRAFT) ? ((Skin.Minecraft) global.getSkin()).getPlayerName() : null);
                    config.set("skin.mineskin", global.getSkin().getType().equals(Skin.Type.MINESKIN) ? ((Skin.MineSkin) global.getSkin()).getMineSkinURL() : null);
                    if(!config.contains("skin.custom")){
                        config.set("skin.custom.enabled", false);
                        config.set("skin.custom.texture", "");
                        config.set("skin.custom.signature", "");
                        config.set("skin.custom.name", "");
                    }
                    if(global.getSkin().getType().equals(Skin.Type.CUSTOM)){
                        config.set("skin.custom.enabled", true);
                        config.set("skin.custom.texture", global.getSkin().getTexture());
                        config.set("skin.custom.signature", global.getSkin().getSignature());
                        config.set("skin.custom.name", ((NPC.Skin.Custom) global.getSkin()).getSkinName());
                    }
                    else config.set("skin.custom.enabled", false);
                    NMSFileConfiguration.setComments(config, "skin.custom.enabled", Arrays.asList("If you want to use a custom texture, set enabled as true, if not, it will use the player name skin.", "To easily get texture and signature use '/npclib getskininfo (type) (name)' or https://mineskin.org/"));
                    config.set("skin.ownPlayer", global.isOwnPlayerSkin());
                    Arrays.stream(Skin.Part.values()).forEach(x-> config.set("skin.parts." + x.name().toLowerCase(), global.getSkinParts().isVisible(x)));
                    config.set("customData", null);
                    for(String keys : global.getCustomDataKeys()) config.set("customData." + keys, global.getCustomData(keys));
                    List<String> lines = global.getText();
                    if(lines != null && lines.size() > 0){
                        List<String> coloredLines = new ArrayList<>();
                        lines.forEach(x-> coloredLines.add(x.replaceAll("§", "&")));
                        config.set("hologram.text", coloredLines);
                    } else config.set("hologram.text", lines);
                    config.set("hologram.lineSpacing", global.getLineSpacing());
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
                    config.set("tabList.show", global.isShowOnTabList());
                    config.set("tabList.name", global.getCustomTabListName().replaceAll("§", "&"));
                    config.set("move.speed", global.getMoveSpeed());
                    Arrays.stream(Slot.values()).forEach(x-> config.set("slots." + x.name().toLowerCase(), global.getSlots().get(x)));
                    config.set("onFire", global.isOnFire());
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
                            config.set("interact.actions." + clickActionID + ".key", castAction.getKey());
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
                        for(int i = 0; i < clickAction.getConditions().size(); i++){
                            config.set("interact.actions." + clickActionID + ".condition." + (i+1), clickAction.getConditions().get(i));
                        }
                    }
                    if(!config.contains("disableSaving")) config.set("disableSaving", false);
                    config.save(file);
                    this.lastUpdate.save();
                    PlayerNPCPlugin.sendConsoleMessage("§7Persistent Global NPC §a" + global.getCode() + " §7has been saved.");
                }
                catch (Exception e){
                    e.printStackTrace();
                    PlayerNPCPlugin.sendConsoleMessage("§7There was an error while saving Persistent Global NPC §c" + global.getCode() );
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

            public boolean isDisableSaving(){
                return (boolean) get("disableSaving");
            }

            protected String getFilePath(){
                return getFolderPath() + "/data.yml";
            }

            protected String getFolderPath(){
                return "plugins/PlayerNPC/persistent/global/" + plugin.getName().toLowerCase() + "/" + id;
            }

            public NPC.Global getGlobal() {
                return global;
            }

            protected void setGlobal(Global global) {
                this.global = global;
            }

            public boolean isLoaded(){
                return config != null;
            }

            protected Object get(String s){
                if(config != null && config.contains(s)){
                    return config.get(s);
                }
                return null;
            }

            protected boolean containsKey(String s){
                if(config == null) return false;
                return config.contains(s);
            }

            protected FileConfiguration getConfig(){
                return this.config;
            }

            protected void set(String s, Object o){
                if(config == null) return;
                config.set(s, o);
            }

            public LastUpdate getLastUpdate() {
                return lastUpdate;
            }

            public class LastUpdate{

                private Type type;
                private String time;

                private LastUpdate() {}

                protected void load() {
                    type = Type.LOAD;
                    time();
                }

                protected void save() {
                    type = Type.SAVE;
                    time();
                }

                private void time() { time = TimerUtils.getCurrentDate(); }

                public Type getType() {
                    return type;
                }

                public String getTime() {
                    return time;
                }

                public enum Type{
                    SAVE, LOAD;
                }

            }

        }
    }

    /*
                    Enums and Classes
     */

    /**
     * Set the follow look type to the NPC with {@link NPC#setGazeTrackingType(GazeTrackingType)}
     * @see NPC#setGazeTrackingType(GazeTrackingType)
     * @see NPC#getGazeTrackingType()
     * @since 2021.1
     */
    public enum GazeTrackingType {
        /** The NPC will not move the look direction automatically. */
        NONE,
        /** The NPC will move the look direction automatically to the player that see the NPC.
         * That means that each player will see the NPC looking himself. */
        PLAYER,
        /** The NPC will move the look direction automatically to the nearest player to the NPC location.
         * That means that a player can see his NPC looking to another player if it's nearer than him.*/
        NEAREST_PLAYER,
        /** The NPC will move the look direction automatically to the nearest entity to the NPC location. */
        NEAREST_ENTITY,
    }

    /** Set the NPCPose of the NPC with {@link NPC#setPose(Pose)}
     * <p> After setting the NPCPose you will need to {@link NPC#update()}
     * @since 2021.2
     * @see NPC#getPose() 
     * @see NPC#setPose(Pose)
     * */
    public enum Pose{
        /** The NPC will be standing on the ground.
         * @see NPC#setPose(Pose)
         * @see NPC#resetPose()
         * @since 2021.2
         **/
        STANDING(EntityPose.a),
        /**
         * The NPC will be gliding.
         * @since 2022.1
         * */
        GLIDING(EntityPose.b),
        /** The NPC will be lying on the ground, looking up, with the arms next to the body.
         * @see NPC#setPose(Pose)
         * @see NPC#setSleeping(boolean)
         * @since 2021.2
         * */
        SLEEPING(EntityPose.c),
        /** The NPC will be lying on the ground, looking down, with the arms separated from the body. 
         * @see NPC#setPose(Pose)
         * @see NPC#setSwimming(boolean)
         * @since 2021.2
         * */
        SWIMMING(EntityPose.d),
        /**
         * Entity is riptiding with a trident.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         */
        @Deprecated
        SPIN_ATTACK(EntityPose.e),
        /** The NPC will be standing on the ground, but crouching (sneaking).
         * @see NPC#setPose(Pose)
         * @see NPC#setCrouching(boolean)
         * @since 2021.2
         * */
        CROUCHING(EntityPose.f),
        /**
         * Entity is long jumping.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         * */
        @Deprecated
        LONG_JUMPING(EntityPose.g),
        /**
         * Entity is dead.
         * <p><strong>This NPCPose does not work</strong></p>
         * @since 2022.1
         * */
        @Deprecated
        DYING(EntityPose.h),
        ;

        private EntityPose entityPose;

        Pose(EntityPose entityPose){
            this.entityPose = entityPose;
        }

        protected EntityPose getEntityPose() {
            return entityPose;
        }

        public boolean isDeprecated(){
            try { return NPC.Pose.class.getField(this.name()).isAnnotationPresent(Deprecated.class); }
            catch (NoSuchFieldException | SecurityException e) { return false; }
        }
    }

    /**
     *
     * @since 2021.1
     */
    public enum Slot {

        HELMET(4, "HEAD"),
        CHESTPLATE(3, "CHEST"),
        LEGGINGS(2, "LEGS"),
        BOOTS(1, "FEET"),
        MAINHAND(0, "MAINHAND"),
        OFFHAND(5, "OFFHAND"),
        ;

        private final int slot;
        private final String nmsName;

        Slot(int slot, String nmsName) {
            this.slot = slot;
            this.nmsName = nmsName;
        }

        public int getSlot() {
            return this.slot;
        }

        protected String getNmsName() {
            return this.nmsName;
        }

        protected  <E extends Enum<E>> E getNmsEnum(Class<E> nmsEnumClass) {
            return Enum.valueOf(nmsEnumClass, this.nmsName);
        }

        public boolean isDeprecated(){
            try { return NPC.Slot.class.getField(this.name()).isAnnotationPresent(Deprecated.class); }
            catch (NoSuchFieldException | SecurityException e) { return false; }
        }
    }

    /**
     * @since 2022.2
     */
    public enum Color{

        BLACK(EnumChatFormat.a),
        DARK_BLUE(EnumChatFormat.b),
        DARK_GREEN(EnumChatFormat.c),
        DARK_AQUA(EnumChatFormat.d),
        DARK_RED(EnumChatFormat.e),
        DARK_PURPLE(EnumChatFormat.f),
        GOLD(EnumChatFormat.g),
        GRAY(EnumChatFormat.h),
        DARK_GRAY(EnumChatFormat.i),
        BLUE(EnumChatFormat.j),
        GREEN(EnumChatFormat.k),
        AQUA(EnumChatFormat.l),
        RED(EnumChatFormat.m),
        LIGHT_PURPLE(EnumChatFormat.n),
        YELLOW(EnumChatFormat.o),
        WHITE(EnumChatFormat.p),
        ;

        private EnumChatFormat enumChatFormat;

        Color(EnumChatFormat enumChatFormat){
            this.enumChatFormat = enumChatFormat;
        }

        public ChatColor getChatColor(){
            return ChatColor.valueOf(this.name());
        }

        protected EnumChatFormat getEnumChatFormat(){
            return enumChatFormat;
        }

        public static NPC.Color getColor(ChatColor chatColor){ try{ return NPC.Color.valueOf(chatColor.name()); } catch (IllegalArgumentException e){ return Color.WHITE; }}
    }

    /**
     * @since 2022.2
     */
    public enum Animation{
        SWING_MAIN_ARM(0),
        TAKE_DAMAGE(1),
        @Deprecated
        LEAVE_BED(2),
        SWING_OFF_HAND(3),
        CRITICAL_EFFECT(4),
        MAGICAL_CRITICAL_EFFECT(5),
        ;

        private final int id;

        Animation(int id){
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public boolean isDeprecated(){
            try { return NPC.Animation.class.getField(this.name()).isAnnotationPresent(Deprecated.class); }
            catch (NoSuchFieldException | SecurityException e) { return false; }
        }

        public static Animation getAnimation(int id){
            return Arrays.stream(Animation.values()).filter(x-> x.getId() == id).findAny().orElse(null);
        }

    }

    public static abstract class Skin {

        protected static net.md_5.bungee.api.ChatColor[][] UNKNOWN_AVATAR;
        protected static net.md_5.bungee.api.ChatColor MOST_COMMON_COLOR_UNKNOWN_AVATAR;

        static{
            try{
                String path = "plugins/PlayerNPC/resources/avatar";
                File avatarFolder = new File(path);
                avatarFolder.mkdirs();
                File unknownAvatarFile = new File(path + "/unknown.png");
                if(!unknownAvatarFile.exists()){
                    BufferedImage image = ImageIO.read(NPC.class.getResource("/resources/avatar/unknown.png"));
                    unknownAvatarFile.createNewFile();
                    ImageIO.write(image, "png", unknownAvatarFile);
                }
                AvatarLoadData avatarData = loadAvatarPixels(unknownAvatarFile);
                if(avatarData.success){
                    UNKNOWN_AVATAR = avatarData.avatarData;
                    MOST_COMMON_COLOR_UNKNOWN_AVATAR = avatarData.mostCommonColor;
                }
            }
            catch (Exception e) { NPCLib.printError(e); }
        }

        protected Type type;
        protected Texture texture;
        protected String lastUpdate;
        protected net.md_5.bungee.api.ChatColor[][] avatar;
        protected net.md_5.bungee.api.ChatColor mostCommonColor;

        protected Skin(@Nonnull String texture, @Nonnull String signature){
            String textureID = getTextureID(texture);
            Validate.notNull(textureID, "This texture is not valid");
            Validate.isTrue(signature.length() == 684, "This signature is not valid");
            this.texture = new Texture(texture, signature, textureID);
            this.avatar = null;
            this.mostCommonColor = null;
            resetLastUpdate();
        }

        protected Skin(@Nonnull String[] data){
            this(data[0], data[1]);
        }

        protected record Texture(String value, String signature, String textureID){}

        protected abstract void save() throws IOException;

        protected abstract void delete();

        protected abstract File getAvatarFile();

        protected abstract File getTextureFile();

        protected abstract File getDataFile();

        protected abstract String getSkinFolderPath();

        protected boolean needsToUpdate(Plugin plugin){
            return needsToUpdate(this.lastUpdate, plugin);
        }

        protected static boolean needsToUpdate(String lastUpdate, Plugin plugin){
            NPCLib.SkinUpdateFrequency frequency = NPCLib.getInstance().getPluginManager(plugin).getSkinUpdateFrequency();
            return TimerUtils.getBetweenDatesString(lastUpdate, TimerUtils.getCurrentDate(), TimerUtils.DATE_FORMAT_LARGE, frequency.timeUnit()) > frequency.value();
        }

        protected void loadAvatar() {
            if(!getAvatarFile().exists()) downloadImages();
            try {
                AvatarLoadData avatarLoadData = Skin.loadAvatarPixels(getAvatarFile());
                if (avatarLoadData.success) {
                    this.avatar = avatarLoadData.avatarData;
                    this.mostCommonColor = avatarLoadData.mostCommonColor;
                }
            } catch (Exception e) { NPCLib.printError(e); }
        }

        protected void downloadImages(){
            if(getTextureFile().exists() && getAvatarFile().exists()) return;
            try{
                URL url = new URL(getTextureURL());
                BufferedImage bufferedImage = ImageIO.read(url);
                getTextureFile().mkdirs();
                ImageIO.write(bufferedImage, "png", getTextureFile());
                //
                BufferedImage bufferedImage2 = bufferedImage.getSubimage(8, 8, 8, 8);
                BufferedImage bufferedImage3 = bufferedImage.getSubimage(40, 8, 8, 8);

                BufferedImage avatarResult = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);

                Graphics g = avatarResult.getGraphics();
                g.drawImage(bufferedImage2, 0, 0, null);
                g.drawImage(bufferedImage3, 0, 0, null);

                getAvatarFile().mkdirs();
                ImageIO.write(avatarResult, "png", getAvatarFile());
            }
            catch (Exception e){ NPCLib.printError(e); }
        }

        public net.md_5.bungee.api.ChatColor[][] getAvatar() {
            if(avatar == null) loadAvatar();
            return avatar;
        }

        public void applyNPC(NPC npc){
            applyNPC(npc, false);
        }

        public void applyNPC(NPC npc, boolean forceUpdate){
            npc.setSkin(this);
            if(forceUpdate) npc.forceUpdate();
        }

        public net.md_5.bungee.api.ChatColor getMostCommonColor() {
            return mostCommonColor;
        }

        public void applyNPCs(Collection<NPC> npcs){
            applyNPCs(npcs, false);
        }

        public void applyNPCs(Collection<NPC> npcs, boolean forceUpdate){
            npcs.forEach(x-> applyNPC(x, forceUpdate));
        }

        public String getTexture() {
            return texture.value();
        }

        public String getSignature() {
            return texture.signature();
        }

        public String getLastUpdate() {
            return lastUpdate;
        }

        public String getTextureID() {
            return texture.textureID();
        }

        public Type getType(){
            return type;
        }

        protected String getTextureURL(){
            return "http://textures.minecraft.net/texture/" + getTextureID();
        }

        protected void resetLastUpdate(){
            this.lastUpdate = TimerUtils.getCurrentDate();
        }

        public enum Type{
            MINECRAFT, CUSTOM, MINESKIN;
        }

        public String[] getTextureData() { return new String[]{texture.value(), texture.signature()}; }

        private static String getSkinsFolderPath(Type type){
            return "plugins/PlayerNPC/skins/" + type.name().toLowerCase() + "/";
        }

        public static net.md_5.bungee.api.ChatColor[][] getUnknownAvatar() {
            return UNKNOWN_AVATAR;
        }

        public static net.md_5.bungee.api.ChatColor getMostCommonColorUnknownAvatar() {
            return MOST_COMMON_COLOR_UNKNOWN_AVATAR;
        }

        protected static AvatarLoadData loadAvatarPixels(URL url) throws IOException {
            return loadAvatarPixels(ImageIO.read(url));
        }

        protected static AvatarLoadData loadAvatarPixels(File file) throws IOException {
            return loadAvatarPixels(ImageIO.read(file));
        }

        protected static AvatarLoadData loadAvatarPixels(BufferedImage bufferedImage){
            net.md_5.bungee.api.ChatColor[][] avatarData = new net.md_5.bungee.api.ChatColor[8][8];
            Map m = new HashMap();
            boolean loaded = false;
            for(int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int color = bufferedImage.getRGB(x, y);
                    int[] rgb = ColorUtils.getRGB(color);
                    if(rgb[0] > 0 && rgb[1] > 0 && rgb[2] > 0) loaded = true;
                    avatarData[x][y] = net.md_5.bungee.api.ChatColor.of(ColorUtils.getColorFromRGB(rgb));
                    if (!ColorUtils.isGray(rgb)) {
                        Integer counter = (Integer) m.get(color);
                        if (counter == null)
                            counter = 0;
                        counter++;
                        m.put(color, counter);
                    }
                }
            }
            java.awt.Color mostCommon = ColorUtils.getMostCommonColour(m);
            net.md_5.bungee.api.ChatColor mostCommonColor = null;
            if(mostCommon != null) mostCommonColor = net.md_5.bungee.api.ChatColor.of(mostCommon);
            return new AvatarLoadData(loaded, mostCommonColor, avatarData);
        }

        protected static record AvatarLoadData (boolean success, net.md_5.bungee.api.ChatColor mostCommonColor, net.md_5.bungee.api.ChatColor[][] avatarData){}

        @Nullable
        protected static String getTextureID(String texture){
            byte[] decodedBytes = Base64.getDecoder().decode(texture);
            String decodedTexture = new String(decodedBytes);
            JsonObject textureJSON = new JsonParser().parse(decodedTexture).getAsJsonObject();
            JsonObject textureElement = textureJSON.get("textures").getAsJsonObject();
            String textureURL = textureElement.get("SKIN").getAsJsonObject().get("url").getAsString();
            if(textureURL != null){
                String textureID = textureURL.replaceFirst("http://textures.minecraft.net/texture/", "");
                return textureID;
            }
            return null;
        }

        public static class Minecraft extends Skin{

            protected static final Skin.Minecraft STEVE;
            protected static final Skin.Minecraft ALEX;

            protected static final HashMap<String, NPC.Skin.Minecraft> SKIN_CACHE;
            protected static final List<String> LOCAL_SKIN_NAMES;

            static{
                SKIN_CACHE = new HashMap<>();
                STEVE = new Skin.Minecraft(
                        "ewogICJ0aW1lc3RhbXAiIDogMTY1NjUzMDcyOTgyNiwKICAicHJvZmlsZUlkIiA6ICJjMDZmODkwNjRjOGE0OTExOWMyOWVhMWRiZDFhYWI4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfU3RldmUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWE0YWY3MTg0NTVkNGFhYjUyOGU3YTYxZjg2ZmEyNWU2YTM2OWQxNzY4ZGNiMTNmN2RmMzE5YTcxM2ViODEwYiIKICAgIH0KICB9Cn0=",
                        "D5KDlE7KmMYeo+n0bY7kRjxdoZ8ondpgLC0tVcDW/wER9tRAWGlkaUyC4cUjkiYtMFANOxnPNz42iWg+gKAX/qE3lKoJpFw8LmgC587QpEDZTsIwzrIriDDiUc+RQ83VNzy9lkrzm+/llFhuPmONhWIeoVgXQYnJXFXOjTA3uiqHq6IJR4fZzD+0lSpr8jm0X1B+XAiAV7xbzMjg2woC3ur7+81Ub27MNGdAmI5eh50rqqjIHx+kRHJPPB3klbAdkTkcnF2rhDuP9jLtJbb17L+40yR8MH3G1AsRBg+N9MlGb4qF3fK9m2lDNxrGpVe+5fj4ffHnTJ680X9O8cnGxtHFyHm3I65iIhVgFY/DQQ6XSxLgPrmdyVOV98OATc7g2/fFpiI6aRzFrXvCLzXcBzmcayhv8BgG8yBlHdYmMZScjslLKjgWB9mgtOh5ZFFb3ZRkwPvdKUqCQHDPovo9K3LwyAtg9QwJ7u+HN03tpDWllXIjT3mKrtsfWMorNNQ5Bh1St0If4Dg00tpW/DUwNs+oua0PhN/DbFEe3aog2jVfzy3IAXqW2cqiZlnRSm55vMrr1CI45PgjP2LS1c9OYJJ3k+ov4IdvBpDTiG9PfsPWcwtqm8ujxy/TqIWfSajL/RP+TFDoN/F8j8HhHU8wwA9JXJekmvUExEOxPWwisLA=");
                STEVE.playerName = "MHF_Steve";
                STEVE.playerUUID = "c06f89064c8a49119c29ea1dbd1aab82";
                STEVE.obtainedFrom = ObtainedFrom.MINECRAFT_ORIGINAL;
                SKIN_CACHE.put(STEVE.playerName.toLowerCase(), STEVE);
                //
                ALEX = new Skin.Minecraft(
                        "ewogICJ0aW1lc3RhbXAiIDogMTY1NjU3Nzg5MTQ2NywKICAicHJvZmlsZUlkIiA6ICI2YWI0MzE3ODg5ZmQ0OTA1OTdmNjBmNjdkOWQ3NmZkOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNSEZfQWxleCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84M2NlZTVjYTZhZmNkYjE3MTI4NWFhMDBlODA0OWMyOTdiMmRiZWJhMGVmYjhmZjk3MGE1Njc3YTFiNjQ0MDMyIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
                        "d2dYqFQpP2ZjeUROSm23WTdWhWnuaW68v8Biw4towx04vJMRls95W/gmIFGa2Tq171yXHlE8kpP2KFe3jAC7qukkXjDiXSRdCSOYZPA7N91Uw6amyt7x5IKZ90QK8BxE1mCjV7KJNGZ28u8klbf1QUOB4fE27gEfQYGyEcSrkPa4e/QzmOGYbnyiIt36np/qBtWHf87brRdVeKRNfO/ExCJkKbwpKfyGf06luCAfUW9wuHkFURux9naU+ilk2ZHUsPVBdkmfOXZrdxxdpDE19W5VkFryMbtVP5XNEBVC7SAsllHXrf8nskgk+m57bCPMP6RF8k+h+mXIJMuQd7yd7azOAnyLlOoufyY1hs1Po+EGDOSQUUHQKTi7AEYp2C71DpkqpGuPCbL/DkxchblYW5iuIek+BmO3wXbmBPv+0gWkiP/c1n605X0g+h4oO5yQqyI8Fki9F2Hb8T5QeHmC3+yzVVf7gOQ6MB7bBt+uX9wcl5yYBDHbmYGZtbNko7dq584FZKRRWeVhxdcDUXfdfzKmNR73BUIEqzeyOh2hUrk47VHK5d5FajKzgi9j5U8D0EJKjVMPZiulcF0J/ZQ4EOxUkOTNPuphiu43j1C7NXZ4RaPFrSrg7QMsObitqLUP5Pmq15Edg7vpvYME8Fe5Ia8sXLbNDHd3AWuXnfpeAUE=");
                ALEX.playerName = "MHF_Alex";
                ALEX.playerUUID = "6ab4317889fd490597f60f67d9d76fd9";
                ALEX.obtainedFrom = ObtainedFrom.MINECRAFT_ORIGINAL;
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

            protected String playerName;
            protected String playerUUID;
            protected ObtainedFrom obtainedFrom;

            protected Minecraft(String texture, String signature) {
                super(texture, signature);
                this.type = Type.MINECRAFT;
                this.playerName = null;
                this.playerUUID = null;
                this.obtainedFrom = ObtainedFrom.NONE;
            }

            protected Minecraft(String[] data) {
                this(data[0], data[1]);
            }

            public String getPlayerName() {
                return playerName;
            }

            public String getPlayerUUID() {
                return playerUUID;
            }

            public ObtainedFrom getObtainedFrom() {
                return obtainedFrom;
            }

            public boolean canBeDeleted() {
                return !isMinecraftOriginal();
            }

            public boolean canBeUpdated() { return !isMinecraftOriginal(); }

            public boolean isMinecraftOriginal() {
                return obtainedFrom.equals(ObtainedFrom.MINECRAFT_ORIGINAL);
            }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin, String skinName){
                return Custom.createCustomSkin(plugin, skinName, texture.value, texture.signature);
            }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin){
                return Custom.createCustomSkin(plugin,texture.value, texture.signature);
            }

            @Override
            protected File getAvatarFile(){
                return getAvatarFile(this.playerName);
            }

            @Override
            protected File getTextureFile(){
                return getTextureFile(this.playerName);
            }

            @Override
            protected File getDataFile(){
                return getDataFile(this.playerName);
            }

            @Override
            protected String getSkinFolderPath(){
                return getSkinFolderPath(this.playerName);
            }

            private static File getAvatarFile(String playerName){
                return new File(getSkinFolderPath(playerName) + "/avatar.png");
            }

            private static File getTextureFile(String playerName){
                return new File(getSkinFolderPath(playerName) + "/texture.png");
            }

            private static File getDataFile(String playerName){
                return new File(getSkinFolderPath(playerName) + "/data.yml");
            }

            private static String getSkinFolderPath(String playerName){
                return getSkinsFolderPath(Type.MINECRAFT) + playerName.toLowerCase();
            }

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
                config.set("player.id", this.playerUUID);
                config.set("texture.value", getTexture());
                config.set("texture.signature", getSignature());
                config.set("texture.id", getTextureID());
                NMSFileConfiguration.setComments(config, "texture.id", Arrays.asList("Minecraft texture: " + getTextureURL()));
                config.set("obtainedFrom", this.obtainedFrom.name());
                super.resetLastUpdate();
                config.set("lastUpdate", this.lastUpdate);
                config.save(file);
                Bukkit.getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), () -> downloadImages());
            }

            public static void fetchSkinAsync(Plugin plugin, org.bukkit.entity.Player player, Consumer<NPC.Skin.Minecraft> action){
                Skin.Minecraft.fetchSkinAsync(plugin, player.getName(), action);
            }

            public static void fetchSkinAsync(Plugin plugin, String playerName, Consumer<NPC.Skin.Minecraft> action){
                Skin.Minecraft.fetchSkinAsync(plugin, playerName, false, action);
            }

            public static void fetchSkinAsync(Plugin plugin, String playerName, boolean forceDownload, Consumer<NPC.Skin.Minecraft> action){
                final String playerNameLowerCase = playerName.toLowerCase();
                final String possibleUUID = playerName.length() >= 32 ? playerName.replaceAll("-", "") : null;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, ()->{
                    if(!forceDownload && possibleUUID == null){
                        if(SKIN_CACHE.containsKey(playerNameLowerCase)) {
                            Skin.Minecraft skin = SKIN_CACHE.get(playerNameLowerCase);
                            if(!skin.needsToUpdate(plugin)){
                                action.accept(skin);
                                return;
                            }
                            else SKIN_CACHE.remove(playerNameLowerCase);
                        }
                        if(getDataFile(playerNameLowerCase).exists()){
                            YamlConfiguration config = loadConfig(playerNameLowerCase);
                            String lastUpdate = config.getString("lastUpdate");
                            if(!NPC.Skin.needsToUpdate(lastUpdate, plugin)){
                                Skin.Minecraft skin = new Skin.Minecraft(config.getString("texture.value"), config.getString("texture.signature"));
                                skin.playerName = config.getString("player.name");
                                skin.playerUUID = config.getString("player.id");
                                skin.obtainedFrom = Minecraft.ObtainedFrom.valueOf(config.getString("obtainedFrom"));
                                skin.lastUpdate = lastUpdate;
                                SKIN_CACHE.put(playerNameLowerCase, skin);
                                LOCAL_SKIN_NAMES.remove(playerName.toLowerCase());
                                action.accept(skin);
                                return;
                            }
                        }
                    }
                    org.bukkit.entity.Player player = possibleUUID == null ? Bukkit.getServer().getPlayerExact(playerName) : null;
                    if(Bukkit.getServer().getOnlineMode() && player != null){
                        Skin.Minecraft skin = new Skin.Minecraft(getSkinGameProfile(player));
                        skin.playerName = player.getName();
                        skin.playerUUID = player.getUniqueId().toString().replaceAll("-", "");
                        skin.obtainedFrom = ObtainedFrom.GAME_PROFILE;
                        try { skin.save(); } catch (IOException e) { NPCLib.printError(e); }
                        if(skin.getAvatarFile().exists()) skin.getAvatarFile().delete();
                        SKIN_CACHE.put(playerNameLowerCase, skin);
                        action.accept(skin);
                        return;
                    }
                    else{
                        try {
                            String uuid = possibleUUID == null ? getUUID(playerName) : possibleUUID;
                            HashMap<String, String> data = getProfileMojangServer(uuid);
                            Skin.Minecraft skin = new Skin.Minecraft(data.get("texture.value"), data.get("texture.signature"));
                            skin.playerName = data.get("name");
                            skin.playerUUID = data.get("id");
                            skin.obtainedFrom = Minecraft.ObtainedFrom.MOJANG_API;
                            skin.save();
                            skin.loadAvatar();
                            SKIN_CACHE.put(playerNameLowerCase, skin);
                            action.accept(skin);
                        }
                        catch (Exception e) {
                            NPCLib.printError(e);
                            action.accept(null);
                        }
                    }
                });
            }

            public static List<String> getSuggestedSkinNames(){
                List<String> suggested = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(x-> suggested.add(x.getName().toLowerCase()));
                Skin.Minecraft.SKIN_CACHE.keySet().stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(SKIN_CACHE.get(x).getPlayerName().toLowerCase()));
                Skin.Minecraft.LOCAL_SKIN_NAMES.stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(x.toLowerCase()));
                if(!suggested.contains("sergiferry")) suggested.add("sergiferry");
                return suggested;
            }

            private static String[] getSkinGameProfile(org.bukkit.entity.Player player){
                try{
                    EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
                    GameProfile profile = NMSEntityPlayer.getGameProfile(p);
                    Property property = profile.getProperties().get("textures").iterator().next();
                    String texture = property.getValue();
                    String signature = property.getSignature();
                    return new String[]{texture, signature};
                }
                catch (Exception e){
                    NPCLib.printError(e);
                    return NPC.Skin.Minecraft.getSteveSkin().getTextureData();
                }
            }

            private static HashMap<String, String> getProfileMojangServer(String uuid) throws IOException {
                HashMap<String, String> data = new HashMap<>();
                URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                InputStreamReader reader2 = new InputStreamReader(url2.openStream());
                JsonObject profile = new JsonParser().parse(reader2).getAsJsonObject();
                JsonObject property = profile.get("properties").getAsJsonArray().get(0).getAsJsonObject();
                data.put("id", profile.get("id").getAsString());
                data.put("name", profile.get("name").getAsString());
                data.put("texture.value", property.get("value").getAsString());
                data.put("texture.signature", property.get("signature").getAsString());
                return data;
            }

            private static String getUUID(String name) throws IOException {
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

        public static class Custom extends Skin{

            protected static final HashMap<String, NPC.Skin.Custom> SKIN_CACHE;

            static{
                SKIN_CACHE = new HashMap<>();
            }

            private final String skinName;
            private final Plugin plugin;

            private Custom(@Nonnull Plugin plugin, String skinName, String texture, String signature) {
                super(texture, signature);
                Validate.notNull(plugin, "Plugin cannot be null.");
                super.type = Type.CUSTOM;
                this.plugin = plugin;
                this.skinName = skinName;
            }

            private Custom(Plugin plugin, String texture, String signature) {
                this(plugin, null, texture, signature);
            }

            public String getSkinName() {
                return skinName;
            }

            public String getFullSkinCode() {
                if(skinName == null) return null;
                return NPCLib.getFormattedName(plugin, skinName);
            }

            public PluginDescriptionFile getPluginInfo(){
                return plugin.getDescription();
            }

            public void changeTexture(Plugin plugin, String texture, String signature){
                Validate.isTrue(plugin.equals(this.plugin), "This plugin is not the owner of this custom skin.");
                String textureID = getTextureID(texture);
                Validate.notNull(textureID, "This texture is not correct.");
                this.texture = new Texture(texture, signature, textureID);
                super.resetLastUpdate();
                if(getAvatarFile().exists()) getAvatarFile().delete();
                if(getTextureFile().exists()) getTextureFile().delete();
                try { save(); } catch (Exception e) { NPCLib.printError(e); }
            }

            @Nullable
            public static void fetchCustomSkinAsync(@Nonnull Plugin skinPlugin, @Nonnull String skinName, @Nonnull Consumer<NPC.Skin.Custom> action){
                Validate.notNull(new Object[] {skinPlugin, skinName});
                String formattedName = NPCLib.getInstance().getFormattedName(skinPlugin, skinName);
                fetchCustomSkinAsync(null, formattedName, action);
            }

            @Nullable
            @Deprecated
            public static void fetchCustomSkinAsync(@Nonnull String complexSkinName, @Nonnull Consumer<NPC.Skin.Custom> action){
                Validate.notNull(new Object[] {complexSkinName, action});
                Bukkit.getServer().getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), ()->{
                    if(!SKIN_CACHE.containsKey(complexSkinName)) action.accept(null);
                    action.accept(SKIN_CACHE.get(complexSkinName));
                });
            }

            public static Skin.Custom getLoadedSkin(Plugin plugin, String skinName){
                return SKIN_CACHE.get(NPCLib.getFormattedName(plugin, skinName));
            }

            public static Skin.Custom createCustomSkin(Plugin plugin, String skinName, String texture, String value){
                Validate.isTrue(!SKIN_CACHE.containsKey(NPCLib.getInstance().getFormattedName(plugin, skinName)), "Custom texture skin with this name already exists.");
                Skin.Custom custom = new Skin.Custom(plugin, skinName, texture, value);
                SKIN_CACHE.put(NPCLib.getInstance().getFormattedName(plugin, skinName), custom);
                return custom;
            }

            public static Skin.Custom createCustomSkin(Plugin plugin, String texture, String value){
                return new Skin.Custom(plugin, null, texture, value);
            }

            public static List<String> getSuggestedSkinNames(){
                List<String> suggested = new ArrayList<>();
                Skin.Custom.SKIN_CACHE.keySet().stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(x));
                return suggested;
            }

            @Override
            protected void save() throws IOException {
                if(this.skinName == null) return;
                File file = getDataFile();
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile(); }catch (Exception e){}
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("name", this.skinName);
                config.set("plugin", this.plugin.getName());
                config.set("texture.value", this.texture.value());
                config.set("texture.signature", this.texture.signature());
                config.set("texture.id", this.texture.textureID());
                NMSFileConfiguration.setComments(config, "texture.id", Arrays.asList("Minecraft texture: " + getTextureURL()));
                super.resetLastUpdate();
                config.set("lastUpdate", this.lastUpdate);
                config.save(file);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> downloadImages());
            }

            @Override
            public void delete() {
                if(skinName == null) return;
                String formattedName = NPCLib.getInstance().getFormattedName(plugin, skinName);
                if(SKIN_CACHE.containsKey(formattedName)) SKIN_CACHE.remove(formattedName);
                File folder = new File(getSkinFolderPath(plugin, skinName) + "/");
                if(!folder.exists()) return;
                try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
            }

            @Override
            protected void loadAvatar() {
                if(this.skinName == null) return;
                super.loadAvatar();
            }

            @Override
            protected File getAvatarFile(){
                return getAvatarFile(this.plugin, this.skinName);
            }

            @Override
            protected File getTextureFile(){
                return getTextureFile(this.plugin, this.skinName);
            }

            @Override
            protected File getDataFile(){
                return getDataFile(this.plugin, this.skinName);
            }

            @Override
            protected String getSkinFolderPath(){
                return getSkinFolderPath(this.plugin, this.skinName);
            }

            private static File getAvatarFile(Plugin plugin, String skinName){
                return new File(getSkinFolderPath(plugin, skinName) + "/avatar.png");
            }

            private static File getTextureFile(Plugin plugin, String skinName){
                return new File(getSkinFolderPath(plugin, skinName) + "/texture.png");
            }

            private static File getDataFile(Plugin plugin, String skinName){
                return new File(getSkinFolderPath(plugin, skinName) + "/data.yml");
            }

            private static String getSkinFolderPath(Plugin plugin, String skinName){
                return getSkinsFolderPath(Type.CUSTOM) + plugin.getName().toLowerCase() + "/" + skinName.toLowerCase();
            }
        }

        public static class MineSkin extends Skin{

            private static final HashMap<Integer, MineSkin> SKIN_CACHE;
            protected static final List<String> LOCAL_SKIN_NAMES;
            private static final MineskinClient MINESKIN_CLIENT;

            static{
                MINESKIN_CLIENT = new MineskinClient();
                SKIN_CACHE = new HashMap<>();
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
                SKIN_CACHE.keySet().stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(SKIN_CACHE.get(x).getId().toString()));
                LOCAL_SKIN_NAMES.stream().filter(x -> !suggested.contains(x)).forEach(x-> suggested.add(x));
                return suggested;
            }

            private String uuid; //UUID without "-"
            private Integer id;
            private Variant variant;

            private MineSkin(String uuid, Integer id, String texture, String signature, Variant variant) {
                super(texture, signature);
                super.type = Type.MINESKIN;
                this.uuid = uuid;
                this.id = id;
                this.variant = variant;
            }

            public static void generateSkinFromURLAsync(String url, BiConsumer<MineSkin, Exception> action){
                Bukkit.getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), () -> {
                    try {
                        org.mineskin.data.Skin skin = MINESKIN_CLIENT.generateUrl(url, SkinOptions.none()).join();
                        if(skin != null) fetchSkinAsync(PlayerNPCPlugin.getInstance(), "" + skin.id, false, mineSkin -> action.accept(mineSkin, null));
                    } catch (Exception e){
                        NPCLib.printError(e);
                        action.accept(null, e);
                    }
                });
            }

            @Nullable
            public static void fetchSkinAsync(Plugin plugin, String id, boolean forceDownload, Consumer<MineSkin> action) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, ()->{
                    try {
                        String possibleUUID = id.length() >= 32 ? id.replaceAll("-", "") : null;
                        Integer possibleID = MathUtils.isInteger(id) ? Integer.valueOf(id) : null;
                        if(id.startsWith("https://minesk.in/")) possibleUUID = id.replaceFirst("https://minesk\\.in/", "");
                        String apiLink = "https://api.mineskin.org/get/";
                        if(possibleUUID != null) apiLink = apiLink + "uuid/" + possibleUUID;
                        else if(possibleID != null) apiLink = apiLink + "id/" + possibleID;
                        if(possibleID == null && possibleUUID == null){
                            action.accept(null);
                            return;
                        }
                        //
                        if(!forceDownload && possibleID != null){
                            if(SKIN_CACHE.containsKey(possibleID)) {
                                Skin.MineSkin skin = SKIN_CACHE.get(possibleID);
                                if(!skin.needsToUpdate(plugin)){
                                    action.accept(skin);
                                    return;
                                }
                                else SKIN_CACHE.remove(possibleID);
                            }
                            if(getDataFile(possibleID).exists()){
                                YamlConfiguration config = loadConfig(possibleID);
                                String lastUpdate = config.getString("lastUpdate");
                                if(!NPC.Skin.needsToUpdate(lastUpdate, plugin)){
                                    Skin.MineSkin skin = new Skin.MineSkin( config.getString("mineskin.uuid"), config.getInt("mineskin.id"), config.getString("texture.value"), config.getString("texture.signature"), Variant.valueOf(config.getString("variant")));
                                    skin.lastUpdate = lastUpdate;
                                    skin.loadAvatar();
                                    SKIN_CACHE.put(possibleID, skin);
                                    if(LOCAL_SKIN_NAMES.contains(possibleID.toString())) LOCAL_SKIN_NAMES.remove(possibleID.toString());
                                    action.accept(skin);
                                    return;
                                }
                            }
                        }
                        //
                        StringBuilder builder = new StringBuilder();
                        HttpURLConnection httpURLConnection = (HttpURLConnection) (new URL(apiLink)).openConnection();
                        httpURLConnection.setRequestMethod("GET");
                        httpURLConnection.setDoOutput(true);
                        httpURLConnection.setDoInput(true);
                        httpURLConnection.connect();
                        Scanner scanner = new Scanner(httpURLConnection.getInputStream());
                        while (scanner.hasNextLine())
                            builder.append(scanner.nextLine());
                        scanner.close();
                        httpURLConnection.disconnect();
                        //
                        JsonObject jsonObject = (JsonObject)(new JsonParser()).parse(builder.toString());
                        JsonObject data = jsonObject.get("data").getAsJsonObject();
                        JsonObject textures = data.get("texture").getAsJsonObject();
                        String value = textures.get("value").getAsString();
                        String signature = textures.get("signature").getAsString();
                        String mineskinUUID = jsonObject.get("uuid").getAsString();
                        Integer mineskinID = jsonObject.get("id").getAsInt();
                        Variant variant = Variant.valueOf(jsonObject.get("variant").getAsString().toUpperCase());
                        MineSkin mineSkin = new MineSkin(mineskinUUID, mineskinID, value, signature, variant);
                        mineSkin.save();
                        mineSkin.loadAvatar();
                        SKIN_CACHE.put(mineskinID, mineSkin);
                        action.accept(mineSkin);
                    } catch (Exception exception) {
                        NPCLib.printError(exception);
                        action.accept(null);
                    }
                });
            }

            private static YamlConfiguration loadConfig(Integer id){
                File file = getDataFile(id);
                boolean exist = file.exists();
                if(!exist) try{ file.createNewFile();} catch (Exception e){};
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                return config;
            }

            public String getUUID() {
                return uuid;
            }

            public Integer getId() {
                return id;
            }

            public String getMineSkinURL(){
                return "https://minesk.in/" + uuid;
            }

            public Variant getVariant() {
                return variant;
            }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin, String skinName){
                return Custom.createCustomSkin(plugin, skinName, texture.value(), texture.signature());
            }

            public NPC.Skin.Custom convertToCustomSkin(Plugin plugin){
                return Custom.createCustomSkin(plugin,texture.value(), texture.signature());
            }

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
                config.set("texture.id", getTextureID());
                NMSFileConfiguration.setComments(config, "texture.id", Arrays.asList("Minecraft texture: " + getTextureURL()));
                config.set("variant", this.variant.name());
                super.resetLastUpdate();
                config.set("lastUpdate", this.lastUpdate);
                config.save(file);
                Bukkit.getScheduler().runTaskAsynchronously(PlayerNPCPlugin.getInstance(), () -> downloadImages());
            }

            @Override
            public void delete() {
                if(SKIN_CACHE.containsKey(id)) SKIN_CACHE.remove(id);
                if(LOCAL_SKIN_NAMES.contains(id.toString())) LOCAL_SKIN_NAMES.remove(id.toString());
                File folder = new File(getSkinFolderPath(id) + "/");
                if(!folder.exists()) return;
                try { NMSFileUtils.deleteDirectory(folder); } catch (Exception e) { NPCLib.printError(e); }
            }

            @Override
            protected File getAvatarFile(){
                return getAvatarFile(this.id);
            }

            @Override
            protected File getTextureFile(){
                return getTextureFile(this.id);
            }

            @Override
            protected File getDataFile(){
                return getDataFile(this.id);
            }

            @Override
            protected String getSkinFolderPath(){
                return getSkinFolderPath(this.id);
            }

            private static File getAvatarFile(Integer id){
                return new File(getSkinFolderPath(id) + "/avatar.png");
            }

            private static File getTextureFile(Integer id){
                return new File(getSkinFolderPath(id) + "/texture.png");
            }

            private static File getDataFile(Integer id){
                return new File(getSkinFolderPath(id) + "/data.yml");
            }

            private static String getSkinFolderPath(Integer id){
                return getSkinsFolderPath(Type.MINESKIN) + id.toString();
            }

            public enum Variant{
                CLASSIC(4), SLIM(3);

                private int pixels;

                Variant(int pixels){
                    this.pixels = pixels;
                }

                public int getPixels() {
                    return pixels;
                }
            }
        }

        /**
         * @since 2022.3
         */
        public enum Part{
            CAPE,
            JACKET,
            LEFT_SLEEVE,
            RIGHT_SLEEVE,
            LEFT_PANTS,
            RIGHT_PANTS,
            HAT,
        }

        /**
         * @since 2022.2
         */
        public static class Parts implements Cloneable{

            private HashMap<Part, Boolean> parts;

            protected Parts(){
                this.parts = new HashMap<>();
                enableAll();
            }

            public void enableAll(){ Arrays.stream(Part.values()).forEach(x-> parts.put(x, true)); }

            public void disableAll(){ Arrays.stream(Part.values()).forEach(x-> parts.put(x, false)); }

            public List<Part> getVisibleParts(){
                return Arrays.stream(Part.values()).filter(x-> isVisible(x)).collect(Collectors.toList());
            }

            public List<Part> getInvisibleParts(){
                return Arrays.stream(Part.values()).filter(x-> !isVisible(x)).collect(Collectors.toList());
            }

            public void setVisible(Part part, boolean visible){ parts.put(part, visible); }

            public boolean isVisible(Part part) { return parts.get(part); }

            public boolean isCape() {
                return isVisible(Part.CAPE);
            }

            public void setCape(boolean cape) {
                setVisible(Part.CAPE, cape);
            }

            public boolean isJacket() {
                return isVisible(Part.JACKET);
            }

            public void setJacket(boolean jacket) {
                setVisible(Part.JACKET, jacket);
            }

            public boolean isLeftSleeve() {
                return parts.get(Part.LEFT_SLEEVE);
            }

            public void setLeftSleeve(boolean leftSleeve) {
                setVisible(Part.LEFT_SLEEVE, leftSleeve);
            }

            public boolean isRightSleeve() {
                return isVisible(Part.RIGHT_SLEEVE);
            }

            public void setRightSleeve(boolean rightSleeve) {
                setVisible(Part.RIGHT_SLEEVE, rightSleeve);
            }

            public boolean isLeftPants() {
                return isVisible(Part.LEFT_PANTS);
            }

            public void setLeftPants(boolean leftPants) {
                setVisible(Part.LEFT_PANTS, leftPants);
            }

            public boolean isRightPants() {
                return isVisible(Part.RIGHT_PANTS);
            }

            public void setRightPants(boolean rightPants) {
                setVisible(Part.RIGHT_PANTS, rightPants);
            }

            public boolean isHat() {
                return isVisible(Part.HAT);
            }

            public void setHat(boolean hat) {
                setVisible(Part.HAT, hat);
            }

            @Override
            public NPC.Skin.Parts clone(){
                NPC.Skin.Parts parts = new NPC.Skin.Parts();
                Arrays.stream(Part.values()).forEach(x-> parts.setVisible(x, isVisible(x)));
                return parts;
            }

        }

    }

    /**
     * @since 2022.2
     */
    public static class Move {

        private Move() {
        }

        /**
         * @since 2022.2
         */
        public enum Speed{
            SLOW(0.1),
            NORMAL(0.15),
            SPRINT(0.2),
            ;

            private double speed;

            Speed(double speed){
                this.speed = speed;
            }

            public double doubleValue() {
                return speed;
            }
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

                public boolean isRepetitive() {
                    return repetitive;
                }

                public boolean isBackToStart() {
                    return backToStart;
                }
            }

        }

        /**
         * @since 2022.2
         */
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

            public Move.Behaviour setFollowEntity(Entity entity){
                return setFollowEntity(entity, followMinDistance, followMaxDistance);
            }

            public Move.Behaviour setFollowEntity(Entity entity, double followMinDistance){
                return setFollowEntity(entity, followMinDistance, followMaxDistance);
            }

            public Move.Behaviour setFollowEntity(Entity entity, double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_ENTITY);
                this.followEntity = entity;
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
                return this;
            }

            public Move.Behaviour setFollowNPC(NPC npc){
                return setFollowNPC(npc, followMinDistance, followMaxDistance);
            }

            public Move.Behaviour setFollowNPC(NPC npc, double followMinDistance){
                return setFollowNPC(npc, followMinDistance, followMaxDistance);
            }

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

            public Move.Behaviour cancel(){
                return setType(Type.NONE);
            }

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

            public Type getType() {
                return type;
            }

            public NPC getNPC() {
                return npc;
            }

            public enum Type{
                NONE, FOLLOW_PLAYER, FOLLOW_ENTITY, FOLLOW_NPC, CUSTOM_PATH, @Deprecated RANDOM_PATH;

                public boolean isDeprecated(){
                    try { return NPC.Move.Behaviour.Type.class.getField(this.name()).isAnnotationPresent(Deprecated.class); }
                    catch (NoSuchFieldException | SecurityException e) { return false; }
                }
            }
        }

        /**
         * @since 2022.2
         */
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
                moveX = a(npc.getX(), end.getX());
                moveZ = a(npc.getZ(), end.getZ());
                if(moveX != 0.00 && moveZ != 0.00){
                    if(Math.abs(moveX) > npc.getMoveSpeed() / 1.7) moveX = moveX / 1.7;
                    if(Math.abs(moveZ) > npc.getMoveSpeed() / 1.7) moveZ = moveZ / 1.7;
                }
                Location from = c(npc.getX(), npc.getY() + 0.1, npc.getZ());
                Location b = c(npc.getX() + moveX, npc.getY() + 0.1, npc.getZ() + moveZ);
                double locY = npc.getY();
                Block blockInLegFrom = from.getBlock();
                Block blockInFootFrom = blockInLegFrom.getRelative(BlockFace.DOWN);
                Block blockInLeg = b.getBlock();
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
                        if(!npc.getPose().equals(Pose.SWIMMING)){
                            npc.setSwimming(true);
                            npc.update();
                        }
                    }
                    else if(npc.getPose().equals(Pose.SWIMMING)){
                        if(lastNPCPose.equals(Pose.CROUCHING)) npc.setPose(Pose.CROUCHING);
                        else npc.setPose(Pose.STANDING);
                        npc.update();
                    }
                }
                if(checkSlabCrouching){
                    if((!blockInLeg.getType().isSolid() && isSlab(blockInLeg.getRelative(BlockFace.UP), Slab.Type.TOP)) || (isSlab(blockInLeg) && isSlab(blockInLeg.getRelative(BlockFace.UP).getRelative(BlockFace.UP), Slab.Type.BOTTOM))){
                        if(!npc.getPose().equals(Pose.CROUCHING)){
                            npc.setPose(Pose.CROUCHING);
                            npc.update();
                        }
                    }
                    else{
                        if(npc.getPose().equals(Pose.CROUCHING) && lastNPCPose != Pose.CROUCHING){
                            npc.setPose(Pose.STANDING);
                            npc.update();
                        }
                    }
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
                }
                npc.move(moveX, moveY, moveZ);
            }

            private boolean isLadder(Block block){ return block.getType().name().equals("LADDER") || block.getType().name().equals("VINE"); }

            private boolean isStair(Block block){ return block.getType().name().contains("_STAIRS"); }

            private boolean isSlab(Block block){
                return isSlab(block, Slab.Type.BOTTOM);
            }

            private boolean isSlab(Block block, Slab.Type type){
                return (block.getType().name().contains("_SLAB") && ((Slab) block.getBlockData()).getType().equals(type)) || block.getType().name().contains("_BED");
            }

            public boolean isCheckSwimming() {
                return checkSwimming;
            }

            public Task setCheckSwimming(boolean checkSwimming) {
                this.checkSwimming = checkSwimming;
                return this;
            }

            public boolean isCheckSlabCrouching() {
                return checkSlabCrouching;
            }

            public Task setCheckSlabCrouching(boolean checkSlabCrouching) {
                this.checkSlabCrouching = checkSlabCrouching;
                return this;
            }

            public boolean isCheckSlowness() {
                return checkSlowness;
            }

            public Task setCheckSlowness(boolean checkSlowness) {
                this.checkSlowness = checkSlowness;
                return this;
            }

            public boolean isCheckLadders() {
                return checkLadders;
            }

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

            private double a(double a, double b){
                double c = 0.00;
                double d = npc.getMoveSpeed();
                if(b < a){
                    c = -d;
                    if(b-a < -c) c = b-a;
                }
                else if(b > a){
                    c = d;
                    if(b-a > c) c = b-a;
                }
                if(c > d) c = d;
                if(c < -d) c = -d;
                return c;
            }

            private Location c(double x, double y, double z){
                return new Location(npc.getWorld(), x, y, z);
            }

            public void pause(){
                this.pause = true;
                if(lookToEnd) npc.setGazeTrackingType(lastGazeTrackingType);
            }

            public void resume(){
                this.pause = false;
                if(lookToEnd) npc.setGazeTrackingType(NPC.GazeTrackingType.NONE);
            }

            public boolean isPaused() {
                return pause;
            }

            public void cancel(){
                cancel(NPC.Move.Task.CancelCause.CANCELLED);
            }

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

            public boolean hasStarted(){
                return taskID != null && start != null;
            }

            public NPC getNPC() {
                return npc;
            }

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

            protected Event(NPC npc){
                this.npc = npc;
            }

            public NPC getNPC() {
                return npc;
            }

            @Override
            public HandlerList getHandlers() {
                return HANDLERS_LIST;
            }

            public static HandlerList getHandlerList(){
                return HANDLERS_LIST;
            }

            protected abstract static class Player extends NPC.Events.Event{

                private final org.bukkit.entity.Player player;

                protected Player(org.bukkit.entity.Player player, NPC.Personal npc) {
                    super(npc);
                    this.player = player;
                }

                @Override
                public NPC.Personal getNPC(){ return (NPC.Personal) super.getNPC(); }

                public org.bukkit.entity.Player getPlayer() {
                    return player;
                }
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

            public Location getStart() {
                return start;
            }

            public Location getEnd() {
                return end;
            }

            public int getTaskID() {
                return taskID;
            }

            public NPC.Move.Task.CancelCause getCancelCause() {
                return cancelCause;
            }

        }

        public static class Hide extends NPC.Events.Event.Player implements Cancellable {

            private boolean isCancelled;

            protected Hide(org.bukkit.entity.Player player, NPC.Personal npc) {
                super(player, npc);
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

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

            public NPC.Interact.ClickType getClickType() {
                return clickType;
            }

            public boolean isRightClick(){ return clickType.equals(NPC.Interact.ClickType.RIGHT_CLICK); }

            public boolean isLeftClick(){ return clickType.equals(NPC.Interact.ClickType.LEFT_CLICK); }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

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

            public Location getFrom(){
                return getNPC().getLocation();
            }

            public Location getTo() {
                return to;
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

        }

        public static class Show extends Event.Player implements Cancellable {

            private boolean isCancelled;

            protected Show(org.bukkit.entity.Player player, NPC.Personal npc) {
                super(player, npc);
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

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

            public Location getFrom(){
                return getNPC().getLocation();
            }

            public Location getTo() {
                return to;
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

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

            public Location getStart() {
                return start;
            }

            public Location getEnd() {
                return end;
            }

            public int getTaskID() {
                return taskID;
            }

            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public void setCancelled(boolean arg) {
                isCancelled = arg;
            }

        }
    }

    public static class Interact {

        private Interact(){}

        private static void test(ClickAction clickAction, Player player){
            clickAction.execute(player);
        }

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

            public String getReplacedString(Player player, String s) {
                return NPC.Placeholders.replace(npc, player, s);
            }

            public NPC getNPC() {
                return npc;
            }

            public NPC.Interact.Actions.Type getActionType() {
                return actionType;
            }

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

            public Long getDelayTicks() {
                return delayTicks;
            }

            public void setDelayTicks(Long delayTicks) {
                if(delayTicks < 0) delayTicks = 0L;
                this.delayTicks = delayTicks;
            }

            public Long getCooldownMilliseconds() {
                return cooldownMilliseconds;
            }

            public void setCooldownMilliseconds(Long cooldownMilliseconds) {
                if(cooldownMilliseconds < npc.getInteractCooldown()) cooldownMilliseconds = npc.getInteractCooldown();
                this.cooldownMilliseconds = cooldownMilliseconds;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public void setClickType(@Nonnull ClickType clickType) {
                Validate.notNull(clickType);
                this.clickType = clickType;
            }

            public List<Conditions.Condition> getConditions() {
                return conditions;
            }

            public void addCondition(Conditions.Condition condition){
                this.conditions.add(condition);
            }
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
                @Deprecated PLAYER_GIVE_POTION_EFFECT,
                PLAYER_OPEN_BOOK,
                @Deprecated PLAYER_OPEN_ENCHANTING, //Not working
                PLAYER_OPEN_WORKBENCH,
                PLAYER_PERFORM_COMMAND,
                PLAYER_PLAY_SOUND, //WIP
                PLAYER_SEND_ACTIONBAR_MESSAGE,
                PLAYER_SEND_CHAT_MESSAGE,
                PLAYER_SEND_TITLE_MESSAGE,
                PLAYER_TELEPORT_TO_LOCATION,
                ;

                public boolean isDeprecated() {
                    try {
                        return NPC.Interact.Actions.Type.class.getField(this.name()).isAnnotationPresent(Deprecated.class);
                    } catch (NoSuchFieldException | SecurityException e) {
                        return false;
                    }
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

                private final String key;
                private final String value;
                private boolean checkIfSame;

                protected SetCustomData(NPC npc, NPC.Interact.ClickType clickType, String key, String value) {
                    super(npc, Type.NPC_SET_CUSTOM_DATA, clickType);
                    this.key = key;
                    this.value = value;
                    this.checkIfSame = false;
                    super.action = (npc1, player) -> {
                        String replacedKey = getReplacedString(player, getKey());
                        String replacedValue = getReplacedString(player, getValue());
                        if(checkIfSame && npc1.hasCustomData(replacedKey) && npc1.getCustomData(replacedKey).equals(replacedValue)) return;
                        npc1.setCustomData(getReplacedString(player, getKey()), getReplacedString(player, getValue()));
                    };
                }

                public boolean isCheckIfSame() {
                    return checkIfSame;
                }

                public void setCheckIfSame(boolean checkIfSame) {
                    this.checkIfSame = checkIfSame;
                }

                public String getKey() {
                    return key;
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

            public static void giveKit(Kit kit, Player player){
                kit.giveKit(player);
            }

            @Override
            public Map<String, Object> serialize() {
                return null;
            }

        }
    }

    public static class Hologram {
        private final NPC npc;
        private final Player player;
        private Location location;
        private HashMap<Integer, List<EntityArmorStand>> lines;
        private boolean canSee;

        protected Hologram(NPC npc, Player player) {
            this.npc = npc;
            this.player = player;
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
            NPC.Hologram.Opacity textOpacity = getLinesOpacity().getOrDefault(line, npc.getTextOpacity());
            WorldServer world = null;
            try{ world = (WorldServer) NMSCraftWorld.getCraftWorldGetHandle().invoke(NMSCraftWorld.getCraftWorldClass().cast(location.getWorld()), new Object[0]);}catch (Exception e){}
            Validate.notNull(world, "Error at NMSCraftWorld");
            List<EntityArmorStand> armorStands = new ArrayList<>();
            for(int i = 1; i <= textOpacity.getTimes(); i++){
                EntityArmorStand armor = new EntityArmorStand(world, location.getX(), location.getY() + (npc.getLineSpacing() * ((getText().size() - line))), location.getZ());
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
            if(canSee) return;
            if(npc instanceof NPC.Personal){
                NPC.Personal personal = (NPC.Personal) npc;
                if(personal.isHiddenText()) return;
                if(!personal.isInRange()) return;
                if(!personal.isShownOnClient()) return;
            }
            for(Integer line : lines.keySet()){
                for(EntityArmorStand armor : lines.get(line)){
                    NMSPacketPlayOutSpawnEntity.sendPacket(getPlayer(), armor);
                    NMSCraftPlayer.sendPacket(getPlayer(), new PacketPlayOutEntityMetadata(NMSEntity.getEntityID(armor), NMSEntity.getDataWatcher(armor), true));
                }
            }
            canSee = true;
        }

        protected void hide(){
            if(!canSee) return;
            for (Integer in : lines.keySet()) {
                for(EntityArmorStand armor : lines.get(in)){
                    NMSCraftPlayer.sendPacket(getPlayer(), NMSPacketPlayOutEntityDestroy.createPacket(NMSEntity.getEntityID(armor)));
                }
            }
            canSee = false;
        }

        protected void move(Vector vector){
            this.location.add(vector);
            PlayerConnection playerConnection = NMSCraftPlayer.getPlayerConnection(getPlayer());
            for (Integer in : lines.keySet()) {
                for(EntityArmorStand armor : lines.get(in)){
                    Location location = armor.getBukkitEntity().getLocation();
                    double fx = location.getX() + vector.getX();
                    double fy = location.getY() + vector.getY();
                    double fz = location.getZ() + vector.getZ();
                    armor.g(fx, fy, fz);
                    NMSCraftPlayer.sendPacket(playerConnection, new PacketPlayOutEntityTeleport(armor));
                }
            }
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
            return this.player;
        }

        protected List<String> getText(){
            return npc.getText();
        }

        protected HashMap<Integer, NPC.Hologram.Opacity> getLinesOpacity() { return npc.getLinesOpacity(); }

        protected NPC getNpc() {
            return npc;
        }

        /**
         * @since 2022.1
         */
        public enum Opacity {

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

            public static Opacity getOpacity(String name){ return Arrays.stream(Opacity.values()).filter(x-> x.name().equalsIgnoreCase(name)).findAny().orElse(null); }
        }
    }

    /**
     * @since 2022.1
     */
    public static class Attributes {

        private static final Attributes DEFAULT = new Attributes(
                NPC.Skin.Minecraft.getSteveSkin(),
                new Skin.Parts(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                50.0,
                false,
                NPC.Color.WHITE,
                NPC.GazeTrackingType.NONE,
                "§8[NPC] {id}",
                false,
                200L,
                0.27,
                new Vector(0, 1.75, 0),
                NPC.Pose.STANDING,
                NPC.Hologram.Opacity.LOWEST,
                false,
                Move.Speed.NORMAL.doubleValue(),
                new HashMap<>()
        );

        protected static final Double VARIABLE_MIN_LINE_SPACING = 0.27;
        protected static final Double VARIABLE_MAX_LINE_SPACING = 1.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_XZ = 2.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_Y = 5.00;

        protected NPC.Skin skin;
        protected NPC.Skin.Parts skinParts;
        protected List<String> text;
        protected HashMap<NPC.Slot, ItemStack> slots;
        protected Boolean collidable;
        protected Double hideDistance;
        protected Boolean glowing;
        protected NPC.Color glowingColor;
        protected NPC.GazeTrackingType gazeTrackingType;
        protected String customTabListName;
        protected Boolean showOnTabList;
        protected Long interactCooldown;
        protected Double lineSpacing;
        protected Vector textAlignment;
        protected NPC.Pose pose;
        protected NPC.Hologram.Opacity textOpacity;
        protected Boolean onFire;
        protected Double moveSpeed;
        protected HashMap<Integer, NPC.Hologram.Opacity> linesOpacity;

        private Attributes(NPC.Skin skin,
                           NPC.Skin.Parts parts,
                           List<String> text,
                           HashMap<NPC.Slot, ItemStack> slots,
                           boolean collidable,
                           Double hideDistance,
                           boolean glowing,
                           NPC.Color glowingColor,
                           NPC.GazeTrackingType gazeTrackingType,
                           String customTabListName,
                           boolean showOnTabList,
                           Long interactCooldown,
                           Double lineSpacing,
                           Vector textAlignment,
                           NPC.Pose npcPose,
                           NPC.Hologram.Opacity textOpacity,
                           boolean onFire,
                           Double moveSpeed,
                           HashMap<Integer, NPC.Hologram.Opacity> linesOpacity
        ) {
            this.skin = skin;
            this.skinParts = parts;
            this.text = text;
            this.slots = slots;
            this.collidable = collidable;
            this.hideDistance = hideDistance;
            this.glowing = glowing;
            this.glowingColor = glowingColor;
            this.gazeTrackingType = gazeTrackingType;
            this.customTabListName = customTabListName;
            this.showOnTabList = showOnTabList;
            this.interactCooldown = interactCooldown;
            this.lineSpacing = lineSpacing;
            this.textAlignment = textAlignment;
            this.pose = npcPose;
            this.textOpacity = textOpacity;
            this.onFire = onFire;
            this.moveSpeed = moveSpeed;
            this.linesOpacity = linesOpacity;
            Arrays.stream(NPC.Slot.values()).filter(x-> !slots.containsKey(x)).forEach(x-> slots.put(x, new ItemStack(Material.AIR)));
        }

        protected Attributes(){
            this.collidable = DEFAULT.isCollidable();
            this.text = DEFAULT.getText();
            this.hideDistance = DEFAULT.getHideDistance();
            this.glowing = DEFAULT.isGlowing();
            this.skin = DEFAULT.getSkin();
            this.skinParts = DEFAULT.getSkinParts().clone();
            this.glowingColor = DEFAULT.getGlowingColor();
            this.gazeTrackingType = DEFAULT.getGazeTrackingType();
            this.slots = (HashMap<NPC.Slot, ItemStack>) DEFAULT.getSlots().clone();
            this.customTabListName = DEFAULT.getCustomTabListName();
            this.showOnTabList = DEFAULT.isShowOnTabList();
            this.pose = DEFAULT.getPose();
            this.lineSpacing = DEFAULT.getLineSpacing();
            this.textAlignment = DEFAULT.getTextAlignment().clone();
            this.interactCooldown = DEFAULT.getInteractCooldown();
            this.textOpacity = DEFAULT.getTextOpacity();
            this.onFire = DEFAULT.isOnFire();
            this.moveSpeed = DEFAULT.getMoveSpeed();
            this.linesOpacity = (HashMap<Integer, Hologram.Opacity>) DEFAULT.getLinesOpacity().clone();
        }

        protected Attributes(NPC npc){
            if(npc == null) return;
            this.collidable = npc.getAttributes().isCollidable();
            this.text = npc.getAttributes().getText();
            this.hideDistance = npc.getAttributes().getHideDistance();
            this.glowing = npc.getAttributes().isGlowing();
            this.skin = npc.getAttributes().getSkin();
            this.skinParts = npc.getAttributes().getSkinParts();
            this.glowingColor = npc.getAttributes().getGlowingColor();
            this.gazeTrackingType = npc.getAttributes().getGazeTrackingType();
            this.slots = (HashMap<NPC.Slot, ItemStack>) npc.getAttributes().getSlots().clone();
            this.customTabListName = npc.getAttributes().getCustomTabListName();
            this.showOnTabList = npc.getAttributes().isShowOnTabList();
            this.pose = npc.getAttributes().getPose();
            this.lineSpacing = npc.getAttributes().getLineSpacing();
            this.textAlignment = npc.getAttributes().getTextAlignment().clone();
            this.interactCooldown = npc.getAttributes().getInteractCooldown();
            this.textOpacity = npc.getAttributes().getTextOpacity();
            this.onFire = npc.getAttributes().isOnFire();
            this.moveSpeed = npc.getAttributes().getMoveSpeed();
            this.linesOpacity = (HashMap<Integer, Hologram.Opacity>) npc.getAttributes().getLinesOpacity().clone();
        }

        public void applyNPC(@Nonnull NPC.Personal npc, boolean forceUpdate){
            applyNPC(npc);
            if(forceUpdate) npc.forceUpdate();
        }

        public void applyNPC(@Nonnull NPC.Personal npc){
            Validate.notNull(npc, "Cannot apply NPC.Attributes to a null NPC.");
            npc.setSkin(this.skin);
            npc.setSkinParts(this.skinParts);
            npc.setCollidable(this.collidable);
            npc.setText(this.text);
            npc.setHideDistance(this.hideDistance);
            npc.setGlowing(this.glowing);
            npc.setGlowingColor(this.glowingColor);
            npc.setGazeTrackingType(this.gazeTrackingType);
            npc.setSlots((HashMap<NPC.Slot, ItemStack>) this.slots.clone());
            npc.setCustomTabListName(this.customTabListName);
            npc.setShowOnTabList(this.showOnTabList);
            npc.setPose(this.pose);
            npc.setLineSpacing(this.lineSpacing);
            npc.setTextAlignment(this.textAlignment.clone());
            npc.setInteractCooldown(this.interactCooldown);
            npc.setTextOpacity(this.textOpacity);
            npc.setMoveSpeed(this.moveSpeed);
            npc.setOnFire(this.onFire);
            npc.setLinesOpacity((HashMap<Integer, Hologram.Opacity>) this.linesOpacity.clone());
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

        public NPC.Skin getSkin() {
            return skin;
        }

        public Skin.Parts getSkinParts() {
            return skinParts;
        }

        public static NPC.Skin getDefaultSkin(){
            return DEFAULT.getSkin();
        }

        protected void setSkin(@Nullable NPC.Skin skin) {
            if(skin == null) skin = NPC.Skin.Minecraft.getSteveSkin();
            this.skin = skin;
        }

        protected void setSkinParts(@Nullable NPC.Skin.Parts skinParts) {
            if(skinParts == null) skinParts = new Skin.Parts();
            this.skinParts = skinParts;
        }

        public static void setDefaultSkinParts(@Nullable NPC.Skin.Parts skinParts){
            DEFAULT.setSkinParts(skinParts);
        }

        public static void setDefaultSkin(@Nullable NPC.Skin npcSkin){
            DEFAULT.setSkin(npcSkin);
        }

        public List<String> getText() {
            return text;
        }

        public static List<String> getDefaultText(){
            return DEFAULT.getText();
        }

        protected void setText(@Nullable List<String> text) {
            if(text == null) text = new ArrayList<>();
            this.text = text;
        }

        public static void setDefaultText(@Nullable List<String> text){
            DEFAULT.setText(text);
        }

        protected HashMap<NPC.Slot, ItemStack> getSlots() {
            return slots;
        }

        public ItemStack getHelmet(){
            return getItem(NPC.Slot.HELMET);
        }

        public static ItemStack getDefaultHelmet(){
            return DEFAULT.getHelmet();
        }

        protected void setHelmet(@Nullable ItemStack itemStack){
            setItem(NPC.Slot.HELMET, itemStack);
        }

        public static void setDefaultHelmet(@Nullable ItemStack itemStack){
            DEFAULT.setHelmet(itemStack);
        }

        public ItemStack getChestPlate(){
            return getItem(NPC.Slot.CHESTPLATE);
        }

        public static ItemStack getDefaultChestPlate(){
            return DEFAULT.getChestPlate();
        }

        protected void setChestPlate(@Nullable ItemStack itemStack){
            setItem(NPC.Slot.CHESTPLATE, itemStack);
        }

        public static void setDefaultChestPlate(@Nullable ItemStack itemStack){
            DEFAULT.setChestPlate(itemStack);
        }

        public ItemStack getLeggings(){
            return getItem(NPC.Slot.LEGGINGS);
        }

        public static ItemStack getDefaultLeggings(){
            return DEFAULT.getLeggings();
        }

        protected void setLeggings(@Nullable ItemStack itemStack){
            setItem(NPC.Slot.LEGGINGS, itemStack);
        }

        public static void setDefaultLeggings(@Nullable ItemStack itemStack){
            DEFAULT.setLeggings(itemStack);
        }

        public ItemStack getBoots(){
            return getItem(NPC.Slot.BOOTS);
        }

        public static ItemStack getDefaultBoots(){
            return DEFAULT.getBoots();
        }

        protected void setBoots(@Nullable ItemStack itemStack){
            setItem(NPC.Slot.BOOTS, itemStack);
        }

        public static void setDefaultBoots(@Nullable ItemStack itemStack){
            DEFAULT.setBoots(itemStack);
        }

        protected void setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
            Validate.notNull(slot, "Failed to set item, NPCSlot cannot be null");
            if(itemStack == null) itemStack = new ItemStack(Material.AIR);
            slots.put(slot, itemStack);
        }

        public static void setDefaultItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack) { DEFAULT.setItem(slot, itemStack); }

        public ItemStack getItem(@Nonnull NPC.Slot slot){
            Validate.notNull(slot, "Failed to get item, NPCSlot cannot be null");
            return slots.get(slot);
        }

        public static ItemStack getDefaultItem(@Nonnull NPC.Slot slot){
            return DEFAULT.getItem(slot);
        }

        protected static HashMap<NPC.Slot, ItemStack> getDefaultSlots(){
            return DEFAULT.getSlots();
        }

        protected void setSlots(@Nonnull HashMap<NPC.Slot, ItemStack> slots) {
            this.slots = slots;
        }

        protected static void setDefaultSlots(HashMap<NPC.Slot, ItemStack> slots){
            DEFAULT.setSlots(slots);
        }

        public boolean isCollidable() {
            return collidable;
        }

        public static boolean isDefaultCollidable(){
            return DEFAULT.isCollidable();
        }

        protected void setCollidable(boolean collidable) {
            this.collidable = collidable;
        }

        public static void setDefaultCollidable(boolean collidable){
            DEFAULT.setCollidable(collidable);
        }

        public Double getHideDistance() {
            return hideDistance;
        }

        public static Double getDefaultHideDistance(){
            return DEFAULT.getHideDistance();
        }

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
        public static void setDefaultHideDistance(double hideDistance){
            DEFAULT.setHideDistance(hideDistance);
        }

        public boolean isGlowing() {
            return glowing;
        }

        public static boolean isDefaultGlowing(){
            return DEFAULT.isGlowing();
        }

        protected void setGlowing(boolean glowing) {
            this.glowing = glowing;
        }

        public static void setDefaultGlowing(boolean glowing){
            DEFAULT.setGlowing(glowing);
        }

        public NPC.Color getGlowingColor(){
            return this.glowingColor;
        }

        public static NPC.Color getDefaultGlowingColor(){
            return DEFAULT.getGlowingColor();
        }

        protected void setGlowingColor(@Nullable ChatColor color) {
            if(color == null) color = ChatColor.WHITE;
            Validate.isTrue(color.isColor(), "Error setting glow color. It's not a color.");
            setGlowingColor(NPC.Color.getColor(color));
        }

        protected void setGlowingColor(@Nullable NPC.Color color){
            if(color == null) color = Color.WHITE;
            this.glowingColor = color;
        }

        public static void setDefaultGlowingColor(@Nullable ChatColor color){
            DEFAULT.setGlowingColor(color);
        }

        public NPC.GazeTrackingType getGazeTrackingType() {
            return gazeTrackingType;
        }

        public static NPC.GazeTrackingType getDefaultGazeTrackingType(){
            return DEFAULT.getGazeTrackingType();
        }

        protected void setGazeTrackingType(@Nullable NPC.GazeTrackingType gazeTrackingType) {
            if(gazeTrackingType == null) gazeTrackingType = NPC.GazeTrackingType.NONE;
            this.gazeTrackingType = gazeTrackingType;
        }

        public static void setDefaultGazeTrackingType(@Nullable NPC.GazeTrackingType followLookType){
            DEFAULT.setGazeTrackingType(followLookType);
        }

        public String getCustomTabListName() {
            return customTabListName;
        }

        public static String getDefaultTabListName(){
            return DEFAULT.getCustomTabListName();
        }

        protected void setCustomTabListName(@Nonnull String customTabListName){
            if(customTabListName == null) customTabListName = DEFAULT.getCustomTabListName();
            //Validate.isTrue(customTabListName.length() <= 16, "Error setting custom tab list name. Name must be 16 or less characters.");
            this.customTabListName = customTabListName;
        }

        public static void setDefaultCustomTabListName(@Nonnull String customTabListName){
            Validate.isTrue(customTabListName.contains("{id}"), "Custom tab list name attribute must have {id} placeholder.");
            DEFAULT.setCustomTabListName(customTabListName);
        }

        public boolean isShowOnTabList() {
            return showOnTabList;
        }

        public boolean isDefaultShowOnTabList(){
            return DEFAULT.isShowOnTabList();
        }

        protected void setShowOnTabList(boolean showOnTabList) {
            this.showOnTabList = showOnTabList;
        }

        public static void setDefaultShowOnTabList(boolean showOnTabList){
            DEFAULT.setShowOnTabList(showOnTabList);
        }

        public Long getInteractCooldown() {
            return interactCooldown;
        }

        public static Long getDefaultInteractCooldown(){
            return DEFAULT.getInteractCooldown();
        }

        protected void setInteractCooldown(long milliseconds) {
            Validate.isTrue(milliseconds >= 0, "Error setting interact cooldown, cannot be negative.");
            this.interactCooldown = milliseconds;
        }

        public static void setDefaultInteractCooldown(long interactCooldown){
            DEFAULT.setInteractCooldown(interactCooldown);
        }

        public Double getLineSpacing() {
            return lineSpacing;
        }

        public static Double getDefaultLineSpacing(){
            return DEFAULT.getLineSpacing();
        }

        protected void setLineSpacing(double lineSpacing) {
            if(lineSpacing < NPC.Attributes.VARIABLE_MIN_LINE_SPACING) lineSpacing = NPC.Attributes.VARIABLE_MIN_LINE_SPACING;
            else if(lineSpacing > NPC.Attributes.VARIABLE_MAX_LINE_SPACING) lineSpacing = NPC.Attributes.VARIABLE_MAX_LINE_SPACING;
            this.lineSpacing = lineSpacing;
        }

        public static void setDefaultLineSpacing(double lineSpacing){
            DEFAULT.setLineSpacing(lineSpacing);
        }

        public Vector getTextAlignment() {
            return textAlignment;
        }

        public static Vector getDefaultTextAlignment(){
            return DEFAULT.getTextAlignment();
        }

        protected void setTextAlignment(@Nonnull Vector vector) {
            if(vector == null) vector = DEFAULT.getTextAlignment();
            if(vector.getX() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            else if(vector.getX() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            if(vector.getY() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
            else if(vector.getY() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
            if(vector.getZ() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            else if(vector.getZ() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(-dev.sergiferry.playernpc.api.NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
            this.textAlignment = vector;
        }

        public static void setDefaultTextAlignment(Vector textAlignment){
            DEFAULT.setTextAlignment(textAlignment);
        }

        public NPC.Pose getPose() {
            return pose;
        }

        public static NPC.Pose getDefaultPose(){
            return DEFAULT.getPose();
        }

        protected void setPose(@Nullable NPC.Pose pose) {
            if(pose == null) pose = NPC.Pose.STANDING;
            this.pose = pose;
        }

        public static void setDefaultPose(@Nullable NPC.Pose npcPose){
            DEFAULT.setPose(npcPose);
        }

        public static NPC.Hologram.Opacity getDefaultTextOpacity(){
            return DEFAULT.getTextOpacity();
        }

        public NPC.Hologram.Opacity getTextOpacity() {
            return textOpacity;
        }

        public static void setDefaultTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity){
            DEFAULT.setTextOpacity(textOpacity);
        }

        protected void setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity) {
            if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
            this.textOpacity = textOpacity;
        }

        public boolean isOnFire() {
            return onFire;
        }

        public static boolean isDefaultOnFire(){
            return DEFAULT.isOnFire();
        }

        protected void setOnFire(boolean onFire) {
            this.onFire = onFire;
        }

        public static void setDefaultOnFire(boolean onFire){
            DEFAULT.setOnFire(onFire);
        }

        public double getMoveSpeed() {
            return moveSpeed;
        }

        public static Double getDefaultMoveSpeed(){
            return DEFAULT.getMoveSpeed();
        }

        protected void setMoveSpeed(double moveSpeed) {
            if(moveSpeed <= 0.00) moveSpeed = 0.1;
            this.moveSpeed = moveSpeed;
        }

        protected void setMoveSpeed(@Nullable Move.Speed moveSpeed){
            if(moveSpeed == null) moveSpeed = Move.Speed.NORMAL;
            setMoveSpeed(moveSpeed.doubleValue());
        }

        public void setLineOpacity(int line, Hologram.Opacity opacity){
            if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
            this.linesOpacity.put(line, opacity);
        }

        public Hologram.Opacity getLineOpacity(int line){
            return linesOpacity.getOrDefault(line, Hologram.Opacity.LOWEST);
        }

        protected HashMap<Integer, Hologram.Opacity> getLinesOpacity() {
            return linesOpacity;
        }

        protected void setLinesOpacity(HashMap<Integer, Hologram.Opacity> linesOpacity) {
            this.linesOpacity = linesOpacity;
        }

        public void resetLineOpacity(int line){
            if(linesOpacity.containsKey(line)) linesOpacity.remove(line);
        }

        public void resetLinesOpacity(){ linesOpacity = new HashMap<>(); }
    }

    public static class Conditions {

        public enum Type{
            PLAYER_HAS_PERMISSION,
            COMPARATOR_SENTENCE,
            @Deprecated COMPARATOR_NUMBER,
            ;

            public boolean isDeprecated(){
                try { return NPC.Conditions.Type.class.getField(this.name()).isAnnotationPresent(Deprecated.class); }
                catch (NoSuchFieldException | SecurityException e) { return false; }
            }
        }

        public static abstract class Condition implements ConfigurationSerializable{

            protected final Type type;
            protected BiFunction<NPC, org.bukkit.entity.Player, Boolean> condition;
            protected Boolean enabled;
            protected ErrorResponse errorResponse;

            protected Condition(Type type) {
                this.type = type;
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
                return condition.apply(npc, player);
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

            protected Map<String, Object> generalSerialization(){
                Map<String, Object> data = new HashMap<>();
                data.put("enabled", isEnabled());
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
                private Boolean logicNegative;

                public Sentence() {
                    super(Type.COMPARATOR_SENTENCE);
                    this.logic = Logic.EQUALS;
                    this.first = "";
                    this.second = "";
                    this.logicNegative = false;
                    super.condition = (npc, player) -> {
                        if(getFirst() == null || getSecond() == null || getLogic() == null) return true;
                        String firstSentence = NPC.Placeholders.replace(npc, player, getFirst());
                        String secondSentence = NPC.Placeholders.replace(npc, player, getSecond());
                        Boolean logicResult = true;
                        if(getLogic().equals(Logic.EQUALS)) logicResult = firstSentence.equals(secondSentence);
                        if(getLogic().equals(Logic.EQUALS_IGNORE_CASE)) logicResult = firstSentence.equalsIgnoreCase(secondSentence);
                        if(getLogic().equals(Logic.CONTAINS)) logicResult = firstSentence.contains(secondSentence);
                        if(getLogic().equals(Logic.CONTAINS_IGNORE_CASE)) logicResult = firstSentence.toLowerCase().contains(secondSentence.toLowerCase());
                        if(getLogic().equals(Logic.STARTS_WITH)) logicResult = firstSentence.startsWith(secondSentence);
                        if(getLogic().equals(Logic.ENDS_WITH)) logicResult = firstSentence.endsWith(secondSentence);
                        if(isLogicNegative()) logicResult = !logicResult;
                        return logicResult;
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

                public Boolean isLogicNegative() {
                    return logicNegative;
                }

                public enum Logic{
                    EQUALS, EQUALS_IGNORE_CASE, CONTAINS, CONTAINS_IGNORE_CASE, STARTS_WITH, ENDS_WITH;
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

                public void setLogicNegative(@Nonnull Boolean logicNegative) {
                    Validate.notNull(logicNegative);
                    this.logicNegative = logicNegative;
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
                    if(map.containsKey("logicNegative")) condition.logicNegative = (Boolean) map.get("logicNegative");
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
            addPlaceholder("playerWorld", (npc, player) -> player.getWorld().getName());
            addPlaceholder("npcCode", (npc, player) -> npc.getCode());
            addPlaceholder("npcSimpleCode", (npc, player) -> npc.getSimpleCode());
            addPlaceholder("npcWorld", (npc, player) -> npc.getWorld().getName());
            addPlaceholder("npcTabListName", (npc, player) -> npc.getCustomTabListName());
            addPlaceholder("npcPluginName", (npc, player) -> npc.getPlugin().getDescription().getName());
            addPlaceholder("npcPluginVersion", (npc, player) -> npc.getPlugin().getDescription().getVersion());
            addPlaceholder("serverOnlinePlayers", (npc, player) -> "" + npc.getPlugin().getServer().getOnlinePlayers().size());
            addPlaceholder("serverMaxPlayers", (npc, player) -> "" + npc.getPlugin().getServer().getMaxPlayers());
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
            for(String key : customDataNPC.getCustomDataKeys()){
                if(!string.contains("{customData:" + key + "}")) continue;
                string = r(string, "customData:" + key, customDataNPC.getCustomData(key));
            }
            return string;
        }

        private static String r(String string, String placeHolder, String value){
            return string.replaceAll("\\{" + placeHolder +"\\}", value);
        }

    }

}
