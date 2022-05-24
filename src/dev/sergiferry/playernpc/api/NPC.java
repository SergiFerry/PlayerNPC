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
import dev.sergiferry.playernpc.nms.minecraft.NMSEntityPlayer;
import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.chat.ChatMessage;
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
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.function.*;
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

    private final NPCLib npcLib;
    private final Plugin plugin;
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
        this.npcLib = npcLib;
        this.plugin = plugin;
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
        setSkin(new NPC.Skin(texture, signature));
    }

    public void setSkin(@Nonnull String texture, @Nonnull String signature, @Nullable String playerName){
        setSkin(new NPC.Skin(texture, signature, playerName));
    }

    public void setSkin(@Nullable String playerName, Consumer finishAction){
        if(playerName == null){
            setSkin(Skin.STEVE);
            return;
        }
        NPC.Skin.fetchSkinAsync(playerName, (skin) -> {
            setSkin(skin);
            if(finishAction != null) plugin.getServer().getScheduler().runTask(plugin, ()-> finishAction.accept(skin));
        });
    }

    public void setSkin(@Nullable String playerName){
        setSkin(playerName, (Consumer<Skin>) null);
    }

    public void setSkin(@Nullable Player playerSkin){
        if(playerSkin == null){
            setSkin(Skin.STEVE);
            return;
        }
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        setSkin(playerSkin.getName(), (Consumer<Skin>) null);
    }

    public void setSkin(@Nullable Player playerSkin, Consumer<Skin> finishAction){
        if(playerSkin == null){
            setSkin(Skin.STEVE);
            return;
        }
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        setSkin(playerSkin.getName(), finishAction);
    }

    public void setSkin(@Nullable NPC.Skin npcSkin){
        attributes.setSkin(npcSkin);
    }

    public void clearSkin(){
        setSkin((NPC.Skin) null);
    }

    public void setSkinVisiblePart(NPC.Skin.Part part, boolean visible){
        attributes.skin.parts.setVisible(part, visible);
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

    public void setFollowLookType(@Nullable FollowLookType followLookType) {
        attributes.setFollowLookType(followLookType);
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

    public void addCustomClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull BiConsumer<NPC, Player> customAction){ addClickAction(new NPC.Interact.Actions.Custom(this, clickType,customAction)); }

    public void addCustomClickAction(@Nonnull BiConsumer<NPC, Player> customAction){ addCustomClickAction(null, customAction); }

    public void addMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String... message){ addClickAction(new NPC.Interact.Actions.Message(this, clickType, message)); }

    public void addMessageClickAction(@Nonnull String... message){ addMessageClickAction(null, message); }

    public void addRunPlayerCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){ addClickAction(new NPC.Interact.Actions.PlayerCommand(this, clickType, command)); }

    public void addRunPlayerCommandClickAction(@Nonnull String command){ addRunPlayerCommandClickAction(null, command); }

    public void addRunConsoleCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){ addClickAction(new NPC.Interact.Actions.ConsoleCommand(this, clickType, command)); }

    public void addRunConsoleCommandClickAction(@Nonnull String command){ addRunConsoleCommandClickAction(null, command); }

    public void addConnectBungeeServerClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String server){ addClickAction(new NPC.Interact.Actions.BungeeServer(this, clickType, server)); }

    public void addConnectBungeeServerClickAction(@Nonnull String server){ addConnectBungeeServerClickAction(null, server); }

    public void addActionBarMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String message){ addClickAction(new NPC.Interact.Actions.ActionBar(this, clickType, message)); }

    public void addActionBarMessageClickAction(@Nonnull String message){ addActionBarMessageClickAction(null, message); }

    public void addTitleMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){ addClickAction(new NPC.Interact.Actions.Title(this, clickType, title, subtitle, fadeIn, stay, fadeOut)); }

    public void addTitleMessageClickAction(@Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){ addTitleMessageClickAction(null, title, subtitle, fadeIn, stay, fadeOut); }

    public void addTeleportToLocationClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull Location location){ addClickAction(new NPC.Interact.Actions.TeleportToLocation(this, clickType, location)); }

    public void addTeleportToLocationClickAction(@Nonnull Location location){ addTeleportToLocationClickAction(null, location); }

    public void resetClickActions(@Nonnull NPC.Interact.ClickType clickType){
        List<NPC.Interact.ClickAction> remove = this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType)).collect(Collectors.toList());
        clickActions.removeAll(remove);
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
        Bukkit.getScheduler().runTaskLater(npcLib.getPlugin(), ()->{
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
        customData.put(key, value);
    }

    public String getCustomData(String key){
        if(!customData.containsKey(key)) return null;
        return customData.get(key);
    }

    public boolean hasCustomData(String key){
        return customData.containsKey(key);
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


    protected void addClickAction(@Nonnull NPC.Interact.ClickAction clickAction){
        this.clickActions.add(clickAction);
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
        return npcLib;
    }

    public String getCode() {
        return code;
    }

    public String getSimpleCode() { return "" + this.code.replaceFirst("" + plugin.getName().toLowerCase() + "\\.", ""); }

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

    public FollowLookType getFollowLookType() { return attributes.followLookType; }

    public NPC.Pose getPose() { return attributes.pose; }

    public NPC.Hologram.Opacity getLineOpacity(int line){ return attributes.getLineOpacity(line); }

    public NPC.Hologram.Opacity getTextOpacity() { return attributes.textOpacity; }

    public boolean isOnFire() { return attributes.onFire; }

    public NPC.Attributes getAttributes() { return attributes; }

    public Plugin getPlugin() { return plugin; }

    protected List<NPC.Interact.ClickAction> getClickActions() { return clickActions; }

    protected List<NPC.Interact.ClickAction> getClickActions(@Nonnull NPC.Interact.ClickType clickType){ return this.clickActions.stream().filter(x-> clickType == null || x.getClickType() == null || x.getClickType().equals(clickType)).collect(Collectors.toList()); }

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
            entityPlayer = new EntityPlayer(server, worldServer, gameProfile);
            entityPlayer.a(super.x, super.y, super.z, super.yaw, super.pitch);//setLocation
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
            entityPlayer.o(yaw); //setYRot
            entityPlayer.p(pitch); //setXRot
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
            }
            getClickActions(clickType).forEach(x-> x.execute(player));
        }

        protected void updateGlobalLocation(NPC.Global global){
            super.x = global.getX();
            super.y = global.getY();
            super.z = global.getZ();
            super.yaw = global.getYaw();
            super.pitch = global.getPitch();
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
            if(getFollowLookType().equals(FollowLookType.PLAYER)) lookAt(player);
            else if(getFollowLookType().equals(FollowLookType.NEAREST_PLAYER) || getFollowLookType().equals(FollowLookType.NEAREST_ENTITY)){
                final boolean var3 = getFollowLookType().equals(FollowLookType.NEAREST_PLAYER);
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
            Scoreboard scoreboard = null;
            try{ scoreboard = (Scoreboard) NMSCraftScoreboard.getCraftScoreBoardGetHandle().invoke(NMSCraftScoreboard.getCraftScoreBoardClass().cast(player.getScoreboard())); }catch (Exception e){}
            Validate.notNull(scoreboard, "Error at NMSCraftScoreboard");
            ScoreboardTeam scoreboardTeam = scoreboard.f(getShortUUID()) == null ? new ScoreboardTeam(scoreboard, getShortUUID()) : scoreboard.f(getShortUUID());
            scoreboardTeam.a(ScoreboardTeamBase.EnumNameTagVisibility.b); //EnumNameTagVisibility.NEVER
            scoreboardTeam.a(getGlowingColor().getEnumChatFormat()); //setColor
            ScoreboardTeamBase.EnumTeamPush var1 = ScoreboardTeamBase.EnumTeamPush.b; //EnumTeamPush.NEVER
            if(isCollidable()) var1 = ScoreboardTeamBase.EnumTeamPush.a; //EnumTeamPush.ALWAYS
            scoreboardTeam.a(var1); //setTeamPush
            scoreboard.a(gameProfile.getName(), scoreboardTeam); //setPlayerTeam
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, true));
            NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, false));
        }

        @Override
        protected void updatePlayerRotation(){
            if(entityPlayer == null) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutEntityLook(NMSEntityPlayer.getEntityID(entityPlayer), (byte) ((super.yaw * 256 / 360)), (byte) ((super.pitch * 256 / 360)), false));
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) (super.yaw * 256 / 360)));
        }

        protected void updateSkin(){
            GameProfile gameProfile = NMSEntityPlayer.getGameProfile(entityPlayer);
            gameProfile.getProperties().get("textures").clear();
            gameProfile.getProperties().put("textures", new Property("textures", super.attributes.skin.getTexture(), super.attributes.skin.getSignature()));
        }

        protected void updatePose(){
            if(getPose().equals(NPC.Pose.SLEEPING)) entityPlayer.e(new BlockPosition(super.x, super.y, super.z));
            entityPlayer.b(getPose().getEntityPose());
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
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutRelEntityMove(NMSEntityPlayer.getEntityID(entityPlayer), (short)(x * 4096), (short)(y * 4096), (short)(z * 4096), true));
        }

        protected void updateMetadata() {
            DataWatcher dataWatcher = NMSEntityPlayer.getDataWatcher(entityPlayer);
            entityPlayer.i(isGlowing());
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
            dataWatcher.b(DataWatcherRegistry.a.a(0), b);
            //
            //Player
            b = 0x00;
            //byte b = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40;
            NPC.Skin.Parts parts = getSkin().getParts();
            if(parts.isVisible(Skin.Part.CAPE)) b = (byte) (b | 0x01);
            if(parts.isVisible(Skin.Part.JACKET)) b = (byte) (b | 0x02);
            if(parts.isVisible(Skin.Part.LEFT_SLEEVE)) b = (byte) (b | 0x04);
            if(parts.isVisible(Skin.Part.RIGHT_SLEEVE)) b = (byte) (b | 0x08);
            if(parts.isVisible(Skin.Part.LEFT_PANTS)) b = (byte) (b | 0x10);
            if(parts.isVisible(Skin.Part.RIGHT_PANTS)) b = (byte) (b | 0x20);
            if(parts.isVisible(Skin.Part.HAT)) b = (byte) (b | 0x40);
            dataWatcher.b(DataWatcherRegistry.a.a(17), b);
            //
            PacketPlayOutEntityMetadata metadataPacket = new PacketPlayOutEntityMetadata(NMSEntityPlayer.getEntityID(entityPlayer), dataWatcher, true);
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
            PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(NMSEntityPlayer.getEntityID(entityPlayer), equipment);
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
            }, getNPCLib().getTicksUntilTabListHide());
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
            NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityDestroy.createPacket(NMSEntityPlayer.getEntityID(entityPlayer)));
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
            super.npcLib.getNPCPlayerManager(player).changeWorld(this, super.world, world);
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

        private final HashMap<Player, NPC.Personal> players;
        private final HashMap<Player, NPC.Attributes> customAttributes;
        private final Visibility visibility;
        private final Predicate<Player> visibilityRequirement;
        protected Entity nearestEntity, nearestPlayer;
        protected Long lastNearestEntityUpdate, lastNearestPlayerUpdate;
        private boolean autoCreate, autoShow;
        private boolean ownPlayerSkin;

        protected Global(@Nonnull NPCLib npcLib, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull World world, double x, double y, double z, float yaw, float pitch) {
            super(npcLib, plugin, code, world, x, y, z, yaw, pitch);
            Validate.notNull(visibility, "Cannot generate Global NPC instance, Visibility cannot be null.");
            this.players = new HashMap<>();
            this.customAttributes = new HashMap<>();
            this.visibility = visibility;
            this.visibilityRequirement = visibilityRequirement;
            this.autoCreate = true;
            this.autoShow = true;
            np(null);
            ne(null);
            if(visibility.equals(Visibility.EVERYONE)) addPlayers((Collection<Player>) Bukkit.getOnlinePlayers());
        }

        protected Global(@Nonnull NPCLib npcLib, @Nonnull Plugin plugin, @Nonnull String code, @Nonnull Visibility visibility, @Nullable Predicate<Player> visibilityRequirement, @Nonnull Location location){
            this(npcLib, plugin, code, visibility, visibilityRequirement, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public enum Visibility{
            EVERYONE, SELECTED_PLAYERS;
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
            NPC.Personal personal = getNPCLib().generatePlayerPersonalNPC(player, getPlugin(), getPlugin().getName().toLowerCase() + "." + "global_" + getSimpleCode(), getLocation());
            personal.global = this;
            players.put(player, personal);
            customAttributes.put(player, new Attributes(null));
            //
            updateAttributes(player);
            if(autoCreate) personal.create();
            if(autoCreate && autoShow) personal.show();
        }

        public void removePlayers(@Nonnull Collection<Player> players){
            Validate.notNull(players, "Cannot remove a null collection of Players");
            players.forEach(x-> removePlayer(x));
        }

        public void removePlayer(@Nonnull Player player){
            Validate.notNull(player, "Cannot remove a null Player");
            if(!players.containsKey(player)) return;
            NPC.Personal personal = getPersonal(player);
            customAttributes.remove(player);
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

        public void setCustomFollowLookType(Player player, FollowLookType followLookType){ getCustomAttributes(player).setFollowLookType(followLookType); }

        public void resetCustomFollowLookType(Player player){
            getCustomAttributes(player).followLookType = null;
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

        public void resetAllCustomAttributes(Player player) { customAttributes.put(player, new Attributes(null)); }

        private void updateAttributes(Player player){
            NPC.Personal personal = getPersonal(player);
            NPC.Attributes A = getAttributes();
            NPC.Attributes cA = getCustomAttributes(player);
            personal.updateGlobalLocation(this);
            if(ownPlayerSkin && !personal.getSkin().getPlayerName().equals(player.getName())) personal.setSkin(player, skin -> personal.forceUpdate());
            else personal.setSkin(cA.skin != null ? cA.skin : A.skin);
            personal.setCollidable(cA.collidable != null ? cA.collidable : A.collidable);
            personal.setText(cA.text != null ? cA.text : A.text);
            personal.setHideDistance(cA.hideDistance != null ? cA.hideDistance : A.hideDistance);
            personal.setGlowing(cA.glowing != null ? cA.glowing : A.glowing);
            personal.setGlowingColor(cA.glowingColor != null ? cA.glowingColor : A.glowingColor);
            personal.setFollowLookType(cA.followLookType != null ? cA.followLookType : A.followLookType);
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
            players.forEach((player, npc) -> destroy(player));
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

        public void setOwnPlayerSkin(){
            setOwnPlayerSkin(true);
        }

        public NPC.Personal getPersonal(Player player){
            Validate.isTrue(players.containsKey(player), "Player is not added to this Global NPC");
            return players.get(player);
        }

        public NPC.Attributes getCustomAttributes(Player player){
            Validate.isTrue(customAttributes.containsKey(player), "Player is not added to this Global NPC");
            return customAttributes.get(player);
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
    }

    /*
                    Enums and Classes
     */

    /**
     * Set the follow look type to the NPC with {@link NPC#setFollowLookType(FollowLookType)}
     * @see NPC#setFollowLookType(FollowLookType)
     * @see NPC#getFollowLookType()
     * @since 2021.1
     */
    public enum FollowLookType{
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

        private int id;

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

    public static class Skin {

        protected static final Skin STEVE;
        protected static final HashMap<String, NPC.Skin> SKIN_CACHE;

        static{
            SKIN_CACHE = new HashMap<>();
            STEVE = new Skin(
                    "ewogICJ0aW1lc3RhbXAiIDogMTYyMTcxNTMxMjI5MCwKICAicHJvZmlsZUlkIiA6ICJiNTM5NTkyMjMwY2I0MmE0OWY5YTRlYmYxNmRlOTYwYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYXJpYW5hZmFnIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhNGFmNzE4NDU1ZDRhYWI1MjhlN2E2MWY4NmZhMjVlNmEzNjlkMTc2OGRjYjEzZjdkZjMxOWE3MTNlYjgxMGIiCiAgICB9CiAgfQp9",
                    "otpbxDm9B+opW7jEzZF8BVDeZSqaqdF0dyLlnlyMh7Q5ysJFDL48/9J/IOHp8JqNm1oarmVdvxrroy9dlNI2Mz4BVuJM2pcCOJwk2h+aZ4dzNZGxst+MYNPSw+i4sMoYu7OV07UVHrQffolFF7MiaBUst1hFwM07IpTE6UtIQz4rqWisXe9Iz5+ooqX4wj0IB3dPntsh6u5nVlL8acWCBDAW4YqcPt2Y4CKK+KtskjzusjqGAdEO+4lRcW1S0ldo2RNtUHEzZADWQcADjg9KKiKq9QIpIpYURIoIAA+pDGb5Q8L5O6CGI+i1+FxqXbgdBvcm1EG0OPdw9WpSqAxGGeXSwlzjILvlvBzYbd6gnHFBhFO+X7iwRJYNd+qQakjUa6ZwR8NbkpbN3ABb9+6YqVkabaEmgfky3HdORE+bTp/AT6LHqEMQo0xdNkvF9gtFci7RWhFwuTLDvQ1esby1IhlgT+X32CPuVHuxEvPCjN7+lmRz2OyOZ4REo2tAIFUKakqu3nZ0NcF98b87wAdA9B9Qyd2H/rEtUToQhpBjP732Sov6TlJkb8echGYiLL5bu/Q7hum72y4+j2GNnuRiOJtJidPgDqrYMg81GfenfPyS6Ynw6KhdEhnwmJ1FJlJhYvXZyqZwLAV1c26DNYkrTMcFcv3VXmcd5/2Zn9FnZtw=",
                    "Steve");
        }

        private String texture;
        private String signature;
        private String playerName;
        private NPC.Skin.Parts parts;

        public Skin(String texture, String signature){
            this.texture = texture;
            this.signature = signature;
            this.playerName = null;
            this.parts = new NPC.Skin.Parts();
        }

        public Skin(String texture, String signature, String playerName){
            this(texture, signature);
            setPlayerName(playerName);
        }

        public Skin(String[] data){
            this(data[0], data[1]);
        }

        public Skin(String[] data, String playerName){
            this(data[0], data[1], playerName);
        }

        public void setPlayerName(String playerName){
            this.playerName = playerName;
        }

        /**
         * @since 2022.2
         * @param npc
         */
        public void applyNPC(NPC npc){
            applyNPC(npc, false);
        }

        /**
         * @since 2022.4
         * @param npc
         * @param forceUpdate
         */
        public void applyNPC(NPC npc, boolean forceUpdate){
            npc.setSkin(this);
            if(forceUpdate) npc.forceUpdate();
        }

        public void applyNPCs(Collection<NPC> npcs){
            applyNPCs(npcs, false);
        }

        public void applyNPCs(Collection<NPC> npcs, boolean forceUpdate){
            npcs.forEach(x-> applyNPC(x, forceUpdate));
        }

        public String getTexture() {
            return texture;
        }

        public String getSignature() {
            return signature;
        }

        public String getPlayerName() {
            return playerName;
        }

        public NPC.Skin.Parts getParts() {
            return parts;
        }

        public String[] getData() { return new String[]{texture, signature}; }

        public static void fetchSkinAsync(String playerName, Consumer<NPC.Skin> action){
            Plugin plugin = NPCLib.getInstance().getPlugin();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, ()->{
                Player player = Bukkit.getServer().getPlayer(playerName);
                if(Bukkit.getServer().getOnlineMode() && player != null) action.accept(new Skin(getSkinGameProfile(player), playerName));
                else if(SKIN_CACHE.containsKey(playerName)){
                    action.accept(SKIN_CACHE.get(playerName));
                    return;
                }
                else{
                    try {
                        String uuid = getUUID(playerName);
                        String[] data = getSkinMojangServer(uuid);
                        Skin skin = new Skin(data, playerName);
                        SKIN_CACHE.put(playerName, skin);
                        action.accept(skin);
                    }
                    catch (Exception e) {
                        action.accept(null);
                    }
                }
            });
        }

        public static void fetchSkinAsync(Player player, Consumer<NPC.Skin> action){
            fetchSkinAsync(player.getName(), action);
        }

        private static String[] getSkinPlayerName(String name) {
            Player player = Bukkit.getServer().getPlayer(name);
            if(Bukkit.getServer().getOnlineMode() && player != null) return getSkinGameProfile(player);
            try { return getSkinMojangServer(getUUID(name)); }
            catch (Exception e) { return NPC.Skin.getSteveSkin().getData(); }
        }

        private static String[] getSkinGameProfile(Player player){
            try{
                EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
                GameProfile profile = NMSEntityPlayer.getGameProfile(p);
                Property property = profile.getProperties().get("textures").iterator().next();
                String texture = property.getValue();
                String signature = property.getSignature();
                return new String[]{texture, signature};
            }
            catch (Exception e){ return NPC.Skin.getSteveSkin().getData(); }
        }

        private static String[] getSkinMojangServer(String uuid) throws IOException {
            URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader2 = new InputStreamReader(url2.openStream());
            JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            String texture = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            return new String[]{texture, signature};
        }

        private static String getUUID(String name) throws IOException {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();
            return uuid;
        }

        public static Skin getSteveSkin(){ return STEVE; }

        @Deprecated
        public static Skin getDefaultSkin() { return getSteveSkin(); }


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
        public static class Parts{

            private HashMap<Part, Boolean> parts;

            protected Parts(){
                parts = new HashMap<>();
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
            private FollowLookType lastFollowLookType;
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
                this.lastFollowLookType = npc.getFollowLookType();
                this.lastNPCPose = npc.getPose();
                if(lookToEnd) npc.setFollowLookType(NPC.FollowLookType.NONE);
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
                boolean debug = false;
                if(debug && npc instanceof NPC.Personal){
                    Personal personal = (Personal) npc;
                    personal.getPlayer().sendMessage("", "", "", "", "", "", "", "", "");
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
                if(lookToEnd) npc.setFollowLookType(lastFollowLookType);
            }

            public void resume(){
                this.pause = false;
                if(lookToEnd) npc.setFollowLookType(NPC.FollowLookType.NONE);
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
                    npc.setFollowLookType(lastFollowLookType);
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

                protected Player(org.bukkit.entity.Player player, NPC npc) {
                    super(npc);
                    this.player = player;
                }

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

            protected Hide(org.bukkit.entity.Player player, NPC npc) {
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

            protected Interact(org.bukkit.entity.Player player, NPC npc, NPC.Interact.ClickType clickType) {
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

            protected Show(org.bukkit.entity.Player player, NPC npc) {
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

        public abstract static class ClickAction implements ClickActionInterface {

            private final NPC npc;
            private final NPC.Interact.Actions.Type actionType;
            private final NPC.Interact.ClickType clickType;

            protected ClickAction(NPC npc, NPC.Interact.Actions.Type actionType, NPC.Interact.ClickType clickType) {
                this.npc = npc;
                this.actionType = actionType;
                this.clickType = clickType;
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
                return clickType;
            }

        }

        interface ClickActionInterface{
            void execute(Player player);
        }

        public static class Actions{

            private Actions() {}

            public enum Type{
                CUSTOM_ACTION,
                SEND_MESSAGE,
                SEND_ACTIONBAR_MESSAGE,
                SEND_TITLE_MESSAGE,
                RUN_PLAYER_COMMAND,
                RUN_CONSOLE_COMMAND,
                CONNECT_BUNGEE_SERVER,
                TELEPORT_TO_LOCATION;
            }

            public static class Custom extends ClickAction{

                private final BiConsumer<NPC, Player> customAction;

                protected Custom(NPC npc, NPC.Interact.ClickType clickType, BiConsumer<NPC, Player> customAction) {
                    super(npc, NPC.Interact.Actions.Type.CUSTOM_ACTION, clickType);
                    this.customAction = customAction;
                }

                @Override
                public void execute(Player player) {
                    customAction.accept(getNPC(), player);
                }

            }

            public static class Message extends ClickAction{

                private final String[] messages;

                protected Message(NPC npc, NPC.Interact.ClickType clickType, String... message) {
                    super(npc, NPC.Interact.Actions.Type.SEND_MESSAGE, clickType);
                    this.messages = message;
                }

                @Override
                public void execute(Player player) {
                    Arrays.stream(messages).toList().forEach(x-> player.sendMessage(getReplacedString(player,x)));
                }
            }

            public static abstract class Command extends ClickAction {

                private final String command;

                protected Command(NPC npc, NPC.Interact.Actions.Type actionType, NPC.Interact.ClickType clickType, String command) {
                    super(npc, actionType, clickType);
                    this.command = command;
                }

                protected String getCommand() {
                    return command;
                }
            }

            public static class PlayerCommand extends NPC.Interact.Actions.Command{

                protected PlayerCommand(NPC npc, NPC.Interact.ClickType clickType, String command) {
                    super(npc, NPC.Interact.Actions.Type.RUN_PLAYER_COMMAND, clickType, command);
                }

                @Override
                public void execute(Player player) {
                    Bukkit.getServer().dispatchCommand(player, getReplacedString(player, super.getCommand()));
                }
            }

            public static class ConsoleCommand extends NPC.Interact.Actions.Command{

                protected ConsoleCommand(NPC npc, NPC.Interact.ClickType clickType, String command) {
                    super(npc, NPC.Interact.Actions.Type.RUN_CONSOLE_COMMAND, clickType, command);
                }

                @Override
                public void execute(Player player) {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), getReplacedString(player, super.getCommand()));
                }
            }

            public static class Title extends ClickAction{

                private final String title;
                private final String subtitle;
                private final Integer fadeIn;
                private final Integer stay;
                private final Integer fadeOut;

                protected Title(NPC npc, NPC.Interact.ClickType clickType, String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
                    super(npc, NPC.Interact.Actions.Type.SEND_TITLE_MESSAGE, clickType);
                    this.title = title;
                    this.subtitle = subtitle;
                    this.fadeIn = fadeIn;
                    this.stay = stay;
                    this.fadeOut = fadeOut;
                }

                @Override
                public void execute(Player player) {
                    player.sendTitle("§f" + getReplacedString(player,title), getReplacedString(player,subtitle), fadeIn, stay, fadeOut);
                }
            }

            public static class ActionBar extends ClickAction{

                private final String message;

                public ActionBar(NPC npc, NPC.Interact.ClickType clickType, String message) {
                    super(npc, NPC.Interact.Actions.Type.SEND_ACTIONBAR_MESSAGE, clickType);
                    this.message = message;
                }

                @Override
                public void execute(Player player) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(getReplacedString(player, message)));
                }
            }

            public static class BungeeServer extends ClickAction{

                private final String server;

                protected BungeeServer(NPC npc, NPC.Interact.ClickType clickType, String server) {
                    super(npc, NPC.Interact.Actions.Type.CONNECT_BUNGEE_SERVER, clickType);
                    this.server = server;
                }

                @Override
                public void execute(Player player) {
                    if(!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(PlayerNPCPlugin.getInstance(), "BungeeCord")) Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(PlayerNPCPlugin.getInstance(), "BungeeCord");
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("Connect");
                    out.writeUTF(server);
                    player.sendPluginMessage(PlayerNPCPlugin.getInstance(), "BungeeCord", out.toByteArray());
                }
            }

            public static class TeleportToLocation extends ClickAction{

                private final Location location;

                public TeleportToLocation(NPC npc, NPC.Interact.ClickType clickType, Location location) {
                    super(npc, Type.TELEPORT_TO_LOCATION, clickType);
                    this.location = location;
                }

                @Override
                public void execute(Player player) {
                    player.teleport(location);
                }
            }

        }

        public enum ClickType{
            RIGHT_CLICK, LEFT_CLICK;

            public boolean isRightClick(){ return this.equals(ClickType.RIGHT_CLICK); }

            public boolean isLeftClick(){ return this.equals(ClickType.LEFT_CLICK); }

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
            String replacedText = NPC.Placeholders.replace(npc, player, text);
            for(EntityArmorStand as : lines.get(line)){
                as.e(true); //setNoGravity
                as.j(true); //setInvisible
                as.a(new ChatMessage(replacedText)); //setCustomName
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
            if(npc instanceof NPC.Personal){
                NPC.Personal personal = (NPC.Personal) npc;
                if(personal.isHiddenText()) return;
                if(!personal.isInRange()) return;
                if(!personal.isShownOnClient()) return;
            }
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
                    playerConnection.a(new PacketPlayOutEntityTeleport(armor));
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
                NPC.Skin.getDefaultSkin(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                50.0,
                false,
                NPC.Color.WHITE,
                NPC.FollowLookType.NONE,
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
        protected List<String> text;
        protected HashMap<NPC.Slot, ItemStack> slots;
        protected Boolean collidable;
        protected Double hideDistance;
        protected Boolean glowing;
        protected NPC.Color glowingColor;
        protected NPC.FollowLookType followLookType;
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
                           List<String> text,
                           HashMap<NPC.Slot, ItemStack> slots,
                           boolean collidable,
                           Double hideDistance,
                           boolean glowing,
                           NPC.Color glowingColor,
                           NPC.FollowLookType followLookType,
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
            this.text = text;
            this.slots = slots;
            this.collidable = collidable;
            this.hideDistance = hideDistance;
            this.glowing = glowing;
            this.glowingColor = glowingColor;
            this.followLookType = followLookType;
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
            this.glowingColor = DEFAULT.getGlowingColor();
            this.followLookType = DEFAULT.getFollowLookType();
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
            this.glowingColor = npc.getAttributes().getGlowingColor();
            this.followLookType = npc.getAttributes().getFollowLookType();
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
            npc.setCollidable(this.collidable);
            npc.setText(this.text);
            npc.setHideDistance(this.hideDistance);
            npc.setGlowing(this.glowing);
            npc.setGlowingColor(this.glowingColor);
            npc.setFollowLookType(this.followLookType);
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

        public static NPC.Skin getDefaultSkin(){
            return DEFAULT.getSkin();
        }

        protected void setSkin(@Nullable NPC.Skin skin) {
            if(skin == null) skin = NPC.Skin.getDefaultSkin();
            this.skin = skin;
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

        public NPC.FollowLookType getFollowLookType() {
            return followLookType;
        }

        public static NPC.FollowLookType getDefaultFollowLookType(){
            return DEFAULT.getFollowLookType();
        }

        protected void setFollowLookType(@Nullable NPC.FollowLookType followLookType) {
            if(followLookType == null) followLookType = NPC.FollowLookType.NONE;
            this.followLookType = followLookType;
        }

        public static void setDefaultFollowLookType(@Nullable NPC.FollowLookType followLookType){
            DEFAULT.setFollowLookType(followLookType);
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

    /**
     * @since 2022.2
     */
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

        public static void addPlaceholder(@Nonnull String placeholder, @Nonnull BiFunction<NPC, Player, String> replacement){
            Validate.notNull(placeholder, "Placeholder cannot be null.");
            Validate.notNull(replacement, "Replacement cannot be null.");
            Validate.isTrue(!placeholders.containsKey(placeholder), "Placeholder \"" + placeholder + "\" settled previously");
            placeholders.put(placeholder, replacement);
        }

        public static boolean existsPlaceholder(@Nonnull String placeholder){
            Validate.notNull(placeholder, "Placeholder cannot be null.");
            return placeholders.containsKey(placeholder);
        }

        public static String replace(@Nonnull NPC npc, @Nonnull Player player, @Nonnull String string){
            Validate.notNull(npc, "NPC cannot be null.");
            Validate.notNull(player, "Player cannot be null.");
            Validate.notNull(string, "String cannot be null.");
            for(String placeholder : placeholders.keySet()){
                if(!string.contains("{" + placeholder + "}")) continue;
                string = r(string, placeholder, placeholders.get(placeholder).apply(npc, player));
            }
            return string;
        }

        private static String r(String string, String placeHolder, String value){
            return string.replaceAll("\\{" + placeHolder +"\\}", value);
        }

    }

}
