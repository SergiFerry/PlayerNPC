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
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NPC instance per player. An NPC can only be seen by one player. This is because of personalization purposes.
 * With this instance you can create customizable Player NPCs that can be interacted with.
 * NPCs will be only visible to players after creating the EntityPlayer, and show it to the player.
 *
 * @since 2021.1
 * @author  SergiFerry
 */
public class NPC {

    private final NPCLib npcLib;
    private final String code;
    private final Player player;
    private World world;
    private Double x, y, z;
    private Float yaw, pitch;
    private EntityPlayer entityPlayer;
    private final UUID tabListID;
    private NPC.Hologram npcHologram;
    private boolean canSee;
    private boolean hiddenText;
    private boolean hiddenToPlayer;
    private boolean shownOnTabList;
    private double moveSpeed;
    private String customTabListName;
    private List<NPC.Interact.ClickAction> clickActions;
    private HashMap<Integer, NPC.Hologram.Opacity> linesOpacity;
    private NPC.Move.Task moveTask;
    private NPC.Move.Behaviour moveBehaviour;
    private boolean onFire;

    // NPC.Attributes
    private NPC.Skin skin;
    private List<String> text;
    private HashMap<NPC.Slot, ItemStack> slots;
    private boolean collidable;
    private Double hideDistance;
    private boolean glowing;
    private NPC.Color glowingColor;
    private NPC.FollowLookType followLookType;
    private boolean showOnTabList;
    private Long interactCooldown;
    private Double lineSpacing;
    private Vector textAlignment;
    private NPC.Pose npcPose;
    private NPC.Hologram.Opacity textOpacity;

    /**
     * This constructor can only be invoked by using {@link NPCLib#generateNPC(Player, String, Location)}
     * <p><strong>This only generates the NPC instance, you must {@link NPC#create()} and {@link NPC#show()} it after.</strong></p>
     *
     * @param npcLib always is {@link NPCLib#getInstance()}
     * @param player the {@link Player} that will see the NPC
     * @param code an {@link String} that will let find this {@link NPC} instance at {@link NPCLib#getNPC(Player, String)}
     * @param world the {@link World} that the NPC will be
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw Yaw horizontal
     * @param pitch Pitch vertical
     *
     * @since 2021.1
     *
     * @see NPCLib#getInstance()
     * @see NPCLib#generateNPC(Player, String, Location)
     * @see NPC#create()
     * @see NPC#show()
     *
     */
    protected NPC(@Nonnull NPCLib npcLib, @Nonnull Player player, @Nonnull String code, @Nonnull World world, double x, double y, double z, float yaw, float pitch){
        Validate.notNull(npcLib, "Cannot generate NPC instance, NPCLib cannot be null.");
        Validate.notNull(code, "Cannot generate NPC instance, code cannot be null.");
        Validate.notNull(player, "Cannot generate NPC instance, Player cannot be null.");
        Validate.notNull(world, "Cannot generate NPC instance, World cannot be null.");
        this.npcLib = npcLib;
        this.code = code;
        this.player = player;
        this.world = world;
        this.canSee = false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.npcHologram = null;
        this.shownOnTabList = false;
        this.hiddenToPlayer = true;
        this.hiddenText = false;
        this.moveSpeed = NPC.Move.Speed.NORMAL.doubleValue();
        this.tabListID = UUID.randomUUID();
        this.clickActions = new ArrayList<>();
        this.linesOpacity = new HashMap<>();
        this.moveTask = null;
        this.moveBehaviour = new NPC.Move.Behaviour(this);
        this.onFire = false;

        //NPC Attributes
        NPC.Attributes npcAttributes = new NPC.Attributes();
        this.hideDistance = 0.0;
        this.skin = npcAttributes.getSkin();
        this.text = npcAttributes.getText();
        this.slots = (HashMap<NPC.Slot, ItemStack>) npcAttributes.getSlots().clone();
        this.collidable = npcAttributes.isCollidable();
        this.glowing = npcAttributes.isGlowing();
        this.glowingColor = npcAttributes.getGlowingColor();
        this.followLookType = npcAttributes.getFollowLookType();
        this.customTabListName = npcAttributes.getCustomTabListName();
        this.showOnTabList = npcAttributes.isShowOnTabList();
        this.interactCooldown = npcAttributes.getInteractCooldown();
        this.lineSpacing = npcAttributes.getLineSpacing();
        this.textAlignment = npcAttributes.getTextAlignment().clone();
        this.npcPose = npcAttributes.getPose();
        this.textOpacity = NPC.Hologram.Opacity.LOWEST;
        npcLib.getNPCPlayerManager(player).set(code, this);
        Bukkit.getScheduler().scheduleSyncDelayedTask(npcLib.getPlugin(), ()-> {
            hideDistance = NPC.Attributes.getDefault().getHideDistance();
        },1);
    }

    /**
     * This constructor can only be invoked by using {@link NPCLib#generateNPC(Player, String, Location)}
     * <p><strong>This only generates the NPC instance, you must {@link NPC#create()} and {@link NPC#show()} it after.</strong></p>
     *
     * @param npcLib always is {@link NPCLib#getInstance()}
     * @param player the {@link Player} that will see the NPC
     * @param code an {@link String} that will let find this {@link NPC} instance at {@link NPCLib#getNPC(Player, String)}
     * @param location the {@link Location} that the NPC will spawn
     *
     * @since 2021.1
     *
     * @see NPCLib#getInstance()
     * @see NPCLib#generateNPC(Player, String, Location)
     * @see NPC#create()
     * @see NPC#show()
     */
    protected NPC(@Nonnull NPCLib npcLib, @Nonnull Player player, @Nonnull String code, @Nonnull Location location){
        this(npcLib, player, code, location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /*
                    Public access methods
    */

    /**
     * Creates the {@link NPC#entityPlayer} of the {@link NPC}, but it doesn't
     * show it to the player until {@link NPC#show()}.
     *
     * @return  The {@link NPC} instance.
     * @throws  IllegalArgumentException if {@link NPC#skin} equals {@code null}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code true}
     *          because it means that the {@link NPC#entityPlayer} is created yet.
     *
     * @since 2021.1
     *
     * @see     NPC#show()
     */
    public NPC create(){
        Validate.notNull(skin, "Failed to create the NPC. The NPC.Skin has not been configured.");
        Validate.isTrue(entityPlayer == null, "Failed to create the NPC. This NPC has already been created before.");
        MinecraftServer server = NMSCraftServer.getMinecraftServer();
        WorldServer worldServer = NMSCraftWorld.getWorldServer(world);
        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, getReplacedCustomName());
        this.entityPlayer = new EntityPlayer(server, worldServer, gameProfile);
        entityPlayer.a(x, y, z, yaw, pitch);                                                                            //setLocation
        this.npcHologram = new NPC.Hologram(this);
        updateSkin();
        updatePose();
        updateScoreboard();
        return this;
    }

    /**
     * Updates the attributes of the {@link NPC}.
     * Some changes will need {@link NPC#forceUpdate()}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     *
     * @see NPC#update()
     * @see NPC#forceUpdate()
     */
    public NPC update(){
        Validate.notNull(player, "Failed to update the NPC. The NPC does not have the assigned player.");
        Validate.notNull(entityPlayer, "Failed to update the NPC. The NPC has not been created yet.");
        if(!canSee) return this;
        if(!hiddenToPlayer && !isInRange()){
            hideToPlayer();
            return this;
        }
        if(hiddenToPlayer && isInRange() && isInView()){
            showToPlayer();
            return this;
        }
        updatePose();
        updateLook();
        updateSkin();
        //updateLocation();
        updatePlayerRotation();
        updateEquipment();
        updateMetadata();
        return this;
    }

    /**
     * Re-creates the {@link NPC#entityPlayer}, and updates all the attributes.
     * This is useful after doing some big changes to the NPC, like setting the skin, glowing color, or collision rule.
     * Methods that require this force update will be documented. Otherwise use {@link NPC#update()}
     *
     * @return The {@link NPC} instance
     * @throws IllegalArgumentException if {@link NPC#player} equals {@code null}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     * @since 2021.1
     *
     * @see NPC#create()
     * @see NPC#isCreated()
     * @see NPC#update()
     */
    public NPC forceUpdate(){
        Validate.notNull(player, "Failed to force update the NPC. The NPC does not have the assigned player.");
        Validate.notNull(entityPlayer, "Failed to force update the NPC. The NPC has not been created yet.");
        reCreate();
        update();
        forceUpdateText();
        return this;
    }

    /**
     * Teleports {@link NPC#entityPlayer} to the specified coordinate.
     * This method automatically updates to the Player client.
     *
     * @return  The {@link NPC} instance.
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw Yaw horizontal
     * @param pitch Pitch vertical
     *
     * @since 2021.1
     * @see NPC#isCreated()
     * @see NPC#teleport(Entity)
     * @see NPC#teleport(Location)
     * @see NPC#teleport(double, double, double)
     */
    public NPC teleport(World world, double x, double y, double z, float yaw, float pitch){
        Validate.notNull(entityPlayer, "Failed to move the NPC. The NPC has not been created yet.");
        NPC.Events.Teleport npcTeleportEvent = new NPC.Events.Teleport(this, new Location(world, x, y, z, yaw, pitch));
        if(npcTeleportEvent.isCancelled()) return this;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        if(!this.world.equals(world)) changeWorld(world);
        boolean show = canSee;
        reCreate();
        if(npcHologram != null) forceUpdateText();
        if(show) show();
        else if(npcHologram != null) hideText();
        return this;
    }

    /**
     * Teleports {@link NPC#entityPlayer} to {@link Entity#getLocation()}
     * This method automatically updates to the Player client.
     *
     * @param entity The entity that will teleport to.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@link Entity#getWorld()} is different of {@link NPC#getWorld()}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
     * @since 2021.1
     * @see NPC#isCreated()
     * @see NPC#teleport(Location)
     * @see NPC#teleport(double, double, double)
     * @see NPC#teleport(double, double, double, float, float)
     */
    public NPC teleport(@Nonnull Entity entity){
        Validate.notNull(entity, "Entity must be not null.");
        //Validate.isTrue(entity.getWorld().getName().equals(world.getName()), "Entity must be at the same world.");
        return teleport(entity.getLocation());
    }

    /**
     * Teleports {@link NPC#entityPlayer} to the specified {@link Location}.
     * This method automatically updates to the Player client.
     *
     * @param location The location that will teleport to.
     * @return  The {@link NPC} instance.
     * @throws  IllegalArgumentException if location is {@code null}
     * @throws  IllegalArgumentException if location's {@link Location#getWorld()} is not the same as {@link NPC#world}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
     * @since 2021.1
     * @see     NPC#isCreated()
     */
    public NPC teleport(@Nonnull Location location){
        Validate.notNull(location, "Location must be not null.");
        //Validate.isTrue(location.getWorld().getName().equals(world.getName()), "Location must be at the same world.");
        return teleport(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Teleports {@link NPC#entityPlayer} to the specified coordinate.
     * This method automatically updates to the Player client.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return  the {@link NPC} instance.
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
     * @since 2021.1
     * @see     NPC#isCreated()
     */
    public NPC teleport(double x, double y, double z){
        return teleport(world, x, y, z);
    }

    /**
     * @since 2022.2
     * @param world
     * @param x
     * @param y
     * @param z
     * @return
     */
    @Deprecated
    public NPC teleport(World world, double x, double y, double z){
        return teleport(world, x, y, z, yaw, pitch);
    }

    /**
     * @since 2022.2
     * @param x
     * @param y
     * @param z
     * @param yaw
     * @param pitch
     * @return
     */
    public NPC teleport(double x, double y, double z, float yaw, float pitch){
        return teleport(this.world, x, y, z, yaw, pitch);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param slot The slot that the item will be equipped.
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code slot} equals {@code null}
     *
     * @since 2021.1
     * @see NPC#update()
     */
    public NPC setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
        Validate.notNull(slot, "Failed to set item, NPC.Slot cannot be null");
        if(itemStack == null) itemStack = new ItemStack(Material.AIR);
        this.slots.put(slot, itemStack);
        return this;
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setHelmet(@Nullable ItemStack itemStack){
        return setItem(NPC.Slot.HELMET, itemStack);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setChestPlate(@Nullable ItemStack itemStack){
        return setItem(Slot.CHESTPLATE, itemStack);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setLeggings(@Nullable ItemStack itemStack){
        return setItem(Slot.LEGGINGS, itemStack);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setBoots(@Nullable ItemStack itemStack){
        return setItem(Slot.BOOTS, itemStack);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setItemInRightHand(@Nullable ItemStack itemStack){
        return setItem(NPC.Slot.MAINHAND, itemStack);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link Slot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @see NPC#update()
     * @since 2022.1
     */
    public NPC setItemInLeftHand(@Nullable ItemStack itemStack){
        return setItem(NPC.Slot.OFFHAND, itemStack);
    }

    /**
     * Clears the equipment of the {@link NPC} at the specified {@link Slot}
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param slot The slot that will be cleared.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code slot} equals {@code null}
     *
     * @since 2021.1
     * @see NPC#update()
     */
    public NPC clearEquipment(@Nonnull NPC.Slot slot){
        return setItem(slot, null);
    }

    /**
     * Clears the equipment of the {@link NPC}
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     * @since 2021.1
     *
     * @see NPC#update()
     */
    public NPC clearEquipment(){
        Arrays.stream(NPC.Slot.values()).forEach(x-> clearEquipment(x));
        return this;
    }

    /**
     * Updates the {@link NPC#npcHologram}. If the amount of lines is different at the previous text, use {@link NPC#forceUpdateText()}
     *
     * @return  The {@link NPC} instance.
     * @since 2021.1
     *
     * @see NPC#forceUpdateText()
     */
    public NPC updateText(){
        if(npcHologram == null) return this;
        npcHologram.update();
        return this;
    }

    /**
     * Re-creates the {@link NPC#npcHologram}. If the amount of lines is the same as the previous text, use {@link NPC#updateText()}
     *
     * @return  The {@link NPC} instance.
     * @since 2021.1
     *
     * @see NPC#updateText()
     */
    public NPC forceUpdateText(){
        if(npcHologram == null) return this;
        npcHologram.forceUpdate();
        return this;
    }

    /**
     * Destroys the NPC, and can be created after.
     * To definitely destroy and remove the NPC instance use {@link NPCLib#removeNPC(Player, NPC)}
     *
     * @return The {@link NPC} instance
     * @since 2021.1
     *
     * @see NPCLib#removeNPC(Player, NPC)
     * @see NPCLib#removeNPC(Player, String)
     */
    public NPC destroy(){
        cancelMove();
        if(entityPlayer != null){
            if(canSee) hide();
            entityPlayer = null;
        }
        if(npcHologram != null) npcHologram.removeHologram();
        return this;
    }

    /**
     * Shows the NPC to the player. It must be {@link NPC#isCreated()} to show it.
     * To hide it after, use {@link NPC#hide()}. This method calls {@link NPC.Events.Show}.
     *
     * @return The {@link NPC} instance
     * @throws IllegalArgumentException if {@link NPC#getPlayer()} equals {@code null}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
     * @since 2021.1
     * @see NPC#isCreated()
     * @see NPC#create()
     * @see NPC#hide()
     */
    public NPC show(){
        Validate.notNull(player, "Failed to show NPC. The NPC does not have the assigned player.");
        Validate.notNull(entityPlayer, "Failed to show NPC. The NPC has not been created yet.");
        if(canSee && !hiddenToPlayer) return this;
        NPC.Events.Show npcShowEvent = new NPC.Events.Show(getPlayer(), this);
        if(npcShowEvent.isCancelled()) return this;
        canSee = true;
        if(!isInRange() || !isInView()){
            hiddenToPlayer = true;
            return this;
        }
        showToPlayer();
        return this;
    }

    /**
     * Hides the NPC from the player. It must be {@link NPC#isCreated()} to hide it.
     * To show it again, use {@link NPC#show()}. This method calls {@link NPC.Events.Hide}.
     *
     * @return The {@link NPC} instance
     * @throws IllegalArgumentException if {@link NPC#getPlayer()} equals {@code null}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
     * @since 2021.1
     * @see NPC#isCreated()
     * @see NPC#create()
     * @see NPC#show()
     */
    public NPC hide(){
        Validate.notNull(player, "Failed to hide the NPC. The NPC does not have the assigned player.");
        Validate.notNull(entityPlayer, "Failed to hide the NPC. The NPC has not been created yet.");
        if(!canSee) return this;
        NPC.Events.Hide npcHideEvent = new NPC.Events.Hide(getPlayer(), this);
        if(npcHideEvent.isCancelled()) return this;
        hideToPlayer();
        canSee = false;
        return this;
    }

    /**
     * Sets the direction that the {@link NPC} will look to the location of the {@link Entity}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param entity The entity that will look at.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code entity} equals {@code null}
     *
     * @since 2021.1
     * @see NPC#update()
     */
    public NPC lookAt(@Nonnull Entity entity){
        Validate.notNull(entity, "Failed to set look direction. The entity cannot be null");
        return lookAt(entity.getLocation());
    }

    /**
     * Sets the direction that the {@link NPC} will look to the {@link Location}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param location The location that will look at.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code location} equals {@code null}
     * @throws IllegalArgumentException if NPC is not created yet
     * @throws IllegalArgumentException if the location's world is not the same as the NPC's world.
     *
     * @since 2021.1
     * @see NPC#update()
     */
    public NPC lookAt(@Nonnull Location location){
        Validate.notNull(location, "Failed to set look direction. The location cannot be null.");
        Validate.notNull(entityPlayer, "Failed to set look direction. The NPC has not been created yet.");
        Validate.isTrue(location.getWorld().getName().equals(getWorld().getName()), "The location must be in the same world as NPC");
        Location npcLocation = new Location(world, x, y, z, yaw, pitch);
        Vector dirBetweenLocations = location.toVector().subtract(npcLocation.toVector());
        npcLocation.setDirection(dirBetweenLocations);
        return lookAt(npcLocation.getYaw(), npcLocation.getPitch());
    }

    /**
     * Sets the direction that the {@link NPC} will look to the {@link Location}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @since 2022.2
     * @param yaw
     * @param pitch
     * @return The {@link NPC} instance.
     */
    public NPC lookAt(float yaw, float pitch){
        this.yaw = yaw; //yRot
        this.pitch = pitch; //xRot
        entityPlayer.o(yaw); //setYRot
        entityPlayer.p(pitch); //setXRot
        return this;
    }

    /**
     * Hides or shows the text above the {@link NPC}, but without losing the text information.
     *
     * @param hide boolean if the text will be hidden or not
     * @since 2021.1
     * @return The {@link NPC} instance.
     */
    public NPC setHideText(boolean hide){
        boolean a = hiddenText;
        this.hiddenText = hide;
        if(a == hide) return this;
        if(npcHologram == null) return this;
        if(hide) hideText();
        else showText();
        return this;
    }

    /**
     * Sets whether the {@link NPC} is collidable or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     * @since 2021.1
     *
     * @see NPC#forceUpdate()
     */
    public NPC setCollidable(boolean collidable) {
        this.collidable = collidable;
        return this;
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param texture Texture of the skin
     * @param signature Signature of the skin
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nonnull String texture, @Nonnull String signature){
        return setSkin(new NPC.Skin(texture, signature));
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param texture Texture of the skin
     * @param signature Signature of the skin
     * @param playerName Name of the skin owner, this is not necessary, but it will store it on the {@link NPC.Skin} instance.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nonnull String texture, @Nonnull String signature, @Nullable String playerName){
        return setSkin(new NPC.Skin(texture, signature, playerName));
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param playerName Name of the skin owner. It will fetch skin even if the player is not online.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable String playerName){
        if(playerName == null) return setSkin(NPC.Skin.DEFAULT);
        String[] skin = NPC.Skin.getSkin(playerName);
        Validate.notNull(skin, "Failed to set NPC Skin. The Mojang API didn't respond.");
        return setSkin(skin[0], skin[1], playerName);
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param playerSkin Player that is online, that will fetch skin.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable Player playerSkin){
        if(playerSkin == null) return setSkin(NPC.Skin.DEFAULT);
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        return setSkin(playerSkin.getName());
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param npcSkin NPC.Skin with the texture and signature.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable NPC.Skin npcSkin){
        if(npcSkin == null) npcSkin = NPC.Skin.DEFAULT;
        this.skin = npcSkin;
        return this;
    }

    /**
     * Sets the {@link NPC.Skin} of the {@link NPC} as the Default minecraft skin.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC clearSkin(){
        return setSkin((NPC.Skin) null);
    }

    /**
     * Sets the {@link Pose} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.2
     * @see NPC#update()
     */
    public NPC setPose(NPC.Pose npcPose){
        if(npcPose == null) npcPose = NPC.Pose.STANDING;
        this.npcPose = npcPose;
        return this;
    }

    /**
     * Sets the {@link Pose} of the {@link NPC} as {@link Pose#CROUCHING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.2
     * @see NPC#update()
     */
    public NPC setCrouching(boolean b){
        if(b) return setPose(NPC.Pose.CROUCHING);
        else if(this.npcPose.equals(NPC.Pose.CROUCHING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link Pose} of the {@link NPC} as {@link Pose#SWIMMING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.2
     * @see NPC#update()
     */
    public NPC setSwimming(boolean b){
        if(b) return setPose(NPC.Pose.SWIMMING);
        else if(this.npcPose.equals(NPC.Pose.SWIMMING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link Pose} of the {@link NPC} as {@link Pose#SLEEPING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.2
     * @see NPC#update()
     */
    public NPC setSleeping(boolean b){
        if(b) return setPose(NPC.Pose.SLEEPING);
        else if(this.npcPose.equals(NPC.Pose.SLEEPING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link Pose} of the {@link NPC} as {@link Pose#STANDING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.2
     * @see NPC#update()
     */
    public NPC resetPose(){
        return setPose(NPC.Pose.STANDING);
    }

    /**
     * Clears the text above the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdateText()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdateText()
     */
    public NPC clearText(){
        return setText(new ArrayList<>());
    }

    /**
     * Sets the text above the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#updateText()} if there are the same amount of lines or
     * {@link NPC#forceUpdateText()} if there are different amount of lines, to show it to the {@link Player}
     *
     * @param text The text above the NPC, each {@link String} will be one line.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#updateText()
     * @see NPC#forceUpdateText()
     */
    public NPC setText(@Nonnull List<String> text){
        this.text = text;
        if(npcHologram == null) return this;
        int i = 1;
        for(String s : text){
            npcHologram.setLine(i, s);
            i++;
        }
        return this;
    }

    /**
     * Sets the text above the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#updateText()} if there are the same amount of lines or
     * {@link NPC#forceUpdateText()} if there are different amount of lines, to show it to the {@link Player}
     *
     * @param text The text above the NPC, each {@link String} will be one line.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#updateText()
     * @see NPC#forceUpdateText()
     */
    public NPC setText(@Nonnull String... text){
        return setText(Arrays.asList(text));
    }

    /**
     * Sets the text above the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#updateText()} if there are the same amount of lines or
     * {@link NPC#forceUpdateText()} if there are different amount of lines, to show it to the {@link Player}
     *
     * @param text The text above the NPC, it will be only one line.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#updateText()
     * @see NPC#forceUpdateText()
     */
    public NPC setText(@Nonnull String text){
        return setText(Arrays.asList(text));
    }

    /**
     *
     * @param line
     * @param textOpacity
     * @return
     * @since 2022.1
     */
    public NPC setLineOpacity(int line, @Nullable NPC.Hologram.Opacity textOpacity){
        if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
        linesOpacity.put(line, textOpacity);
        return this;
    }

    /**
     *
     * @param line
     * @return
     * @since 2022.1
     */
    public NPC resetLineOpacity(int line){
        return setLineOpacity(line, NPC.Hologram.Opacity.LOWEST);
    }

    /**
     *
     * @param textOpacity
     * @return
     * @see NPC#forceUpdateText()
     */
    public NPC setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity){
        if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
        this.textOpacity = textOpacity;
        return this;
    }

    /**
     *
     * @return
     * @since 2022.1
     * @see NPC#forceUpdateText()
     */
    public NPC resetTextOpacity(){
        return setTextOpacity(NPC.Hologram.Opacity.LOWEST);
    }

    /**
     * Sets the glowing color of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param color The glowing color.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     * @see NPC#setGlowing(boolean)
     * @see NPC#setGlowing(boolean, ChatColor)
     */
    public NPC setGlowingColor(@Nullable ChatColor color){
        return setGlowingColor(NPC.Color.getColor(color));
    }

    /**
     * Sets the glowing color of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param color The glowing color.
     * @return The {@link NPC} instance.
     *
     * @since 2022.2
     * @see NPC#forceUpdate()
     * @see NPC#setGlowing(boolean)
     * @see NPC#setGlowing(boolean, NPC.Color)
     */
    public NPC setGlowingColor(@Nullable Color color){
        if(color == null) color = Color.WHITE;
        this.glowingColor = color;
        return this;
    }

    /**
     * Sets the glowing color of the {@link NPC}, and if it's glowing or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param glowing Whether it's glowing or not.
     * @param color The glowing color.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     * @see NPC#setGlowingColor(ChatColor)
     * @see NPC#setGlowing(boolean)
     */
    public NPC setGlowing(boolean glowing, @Nullable ChatColor color){
        return setGlowing(glowing, NPC.Color.getColor(color));
    }

    /**
     * Sets the glowing color of the {@link NPC}, and if it's glowing or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param glowing Whether it's glowing or not.
     * @param color The glowing color.
     * @return The {@link NPC} instance.
     *
     * @since 2022.2
     * @see NPC#forceUpdate()
     * @see NPC#setGlowingColor(NPC.Color)
     * @see NPC#setGlowing(boolean)
     */
    public NPC setGlowing(boolean glowing, @Nullable Color color){
        setGlowing(glowing);
        setGlowingColor(color);
        return this;
    }

    /**
     * Sets if the {@link NPC} is glowing or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param glowing Whether it's glowing or not.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#update()
     * @see NPC#setGlowingColor(ChatColor)
     * @see NPC#setGlowing(boolean, ChatColor)
     */
    public NPC setGlowing(boolean glowing){
        this.glowing = glowing;
        return this;
    }

    /**
     * Sets the custom tab list name {@link NPC}, and if it's showing or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param name The name will be visible at tab list. "{UUID}" will replace the uuid of the NPC. It cannot be larger
     *             than 16 characters, and it can be the same as another NPC.
     * @param show Whether it's showing or not.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     * @see NPC#setCustomTabListName(String)
     * @see NPC#setShowOnTabList(boolean)
     */
    public NPC setCustomTabListName(@Nullable String name, boolean show){
        setCustomTabListName(name);
        setShowOnTabList(show);
        return this;
    }

    /**
     * Sets if it's showing or not the custom name on tab list.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param show Whether it's showing or not.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     * @see NPC#setCustomTabListName(String, boolean)
     * @see NPC#setCustomTabListName(String)
     */
    public NPC setShowOnTabList(boolean show){
        if(showOnTabList == show) return this;
        this.showOnTabList = show;
        return this;
    }

    /**
     * Sets the custom tab list name {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param name The name will be visible at tab list. "{UUID}" will replace the uuid of the NPC. It cannot be larger
     *             than 16 characters, and it can be the same as another NPC.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     * @see NPC#setCustomTabListName(String, boolean)
     * @see NPC#setShowOnTabList(boolean)
     */
    public NPC setCustomTabListName(@Nullable String name){
        if(name == null) name = NPC.Attributes.getDefaultTabListName();
        final String finalName = getReplacedCustomName(name);
        Validate.isTrue(finalName.length() <= 16, "Error setting custom tab list name. Name must be 16 or less characters.");
        Validate.isTrue(getNPCLib().getNPCPlayerManager(player).getNPCs(world).stream().filter(x-> x.getReplacedCustomName().equals(finalName)).findAny().orElse(null) == null, "Error setting custom tab list name. There's another NPC with that name already.");
        this.customTabListName = name;
        return this;
    }

    /**
     * Sets the following look type of the {@link NPC}.
     *
     * @since 2021.1
     * @return The {@link NPC} instance.
     *
     */
    public NPC setFollowLookType(@Nullable FollowLookType followLookType) {
        if(followLookType == null) followLookType = FollowLookType.NONE;
        this.followLookType = followLookType;
        return this;
    }

    /**
     * Sets the distance of auto hide of the {@link NPC}.
     *
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if hide distance is negative or 0
     *
     * @since 2021.1
     */
    public NPC setHideDistance(double hideDistance) {
        Validate.isTrue(hideDistance > 0.00, "The hide distance cannot be negative or 0");
        this.hideDistance = hideDistance;
        return this;
    }

    /**
     * Sets the line spacing of the {@link NPC.Hologram}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdateText()} to show it to the {@link Player}
     *
     * @param lineSpacing The distance (y-axis) between the ArmorStands in NPCHologram.
     * @return The {@link NPC} instance.
     * @since 2022.1
     *
     * @see NPC#forceUpdateText()
     * @see NPC#resetLineSpacing()
     * @see NPC#getLineSpacing()
     */
    public NPC setLineSpacing(double lineSpacing){
        if(lineSpacing < NPC.Attributes.VARIABLE_MIN_LINE_SPACING) lineSpacing = NPC.Attributes.VARIABLE_MIN_LINE_SPACING;
        else if(lineSpacing > NPC.Attributes.VARIABLE_MAX_LINE_SPACING) lineSpacing = NPC.Attributes.VARIABLE_MAX_LINE_SPACING;
        this.lineSpacing = lineSpacing;
        return this;
    }

    /**
     * Sets the line spacing of the {@link NPC.Hologram} to the default value ({@link NPC.Attributes#getDefaultLineSpacing()}).
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdateText()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     * @since 2022.1
     *
     * @see NPC#forceUpdateText()
     * @see NPC#setLineSpacing(double) 
     * @see NPC#getLineSpacing()
     */
    public NPC resetLineSpacing(){
        return setLineSpacing(NPC.Attributes.getDefault().getLineSpacing());
    }

    /**
     *
     * @since 2022.1
     */
    public NPC setTextAlignment(@Nonnull Vector vector){
        Validate.notNull(vector, "Failed to set text alignment. Vector cannot be null.");
        if(vector.getX() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getX() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(-NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        if(vector.getY() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        else if(vector.getY() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(-NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        if(vector.getZ() > NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getZ() < -NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(-NPC.Attributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        this.textAlignment = vector;
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    public NPC resetTextAlignment(){
        this.textAlignment = NPC.Attributes.getDefault().getTextAlignment();
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    public NPC setInteractCooldown(long milliseconds){
        Validate.isTrue(milliseconds >= 0, "Error setting interact cooldown, cannot be negative.");
        this.interactCooldown = milliseconds;
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    public NPC resetInteractCooldown(){
        return setInteractCooldown(NPC.Attributes.getDefault().getInteractCooldown());
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addCustomClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull NPC.Interact.Actions.Custom.CustomAction customAction){
        return addClickAction(new NPC.Interact.Actions.Custom(this, clickType,customAction));
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addCustomClickAction(@Nonnull NPC.Interact.Actions.Custom.CustomAction customAction){
        return addCustomClickAction(null, customAction);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String... message){
        return addClickAction(new NPC.Interact.Actions.Message(this, clickType, message));
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addMessageClickAction(@Nonnull String... message){
        return addMessageClickAction(null, message);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addRunPlayerCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){
        return addClickAction(new NPC.Interact.Actions.PlayerCommand(this, clickType, command));
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addRunPlayerCommandClickAction(@Nonnull String command){
        return addRunPlayerCommandClickAction(null, command);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addRunConsoleCommandClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String command){
        return addClickAction(new NPC.Interact.Actions.ConsoleCommand(this, clickType, command));
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addRunConsoleCommandClickAction(@Nonnull String command){
        return addRunConsoleCommandClickAction(null, command);
    }

    /**
     *
     * @param clickType
     * @param server
     * @since 2022.2
     */
    public NPC addConnectBungeeServerClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String server){
        return addClickAction(new NPC.Interact.Actions.BungeeServer(this, clickType, server));
    }

    /**
     * @since 2022.2
     * @param server
     * @return
     */
    public NPC addConnectBungeeServerClickAction(@Nonnull String server){
        return addConnectBungeeServerClickAction(null, server);
    }

    /**
     * @since 2022.2
     * @param clickType
     * @param message
     * @return
     */
    public NPC addActionBarMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String message){
        return addClickAction(new NPC.Interact.Actions.ActionBar(this, clickType, message));
    }

    /**
     * @since 2022.2
     * @param message
     * @return
     */
    public NPC addActionBarMessageClickAction(@Nonnull String message){
        return addActionBarMessageClickAction(null, message);
    }

    /**
     * @since 2022.2
     * @param clickType
     * @param title
     * @param subtitle
     * @param fadeIn
     * @param stay
     * @param fadeOut
     * @return
     */
    public NPC addTitleMessageClickAction(@Nullable NPC.Interact.ClickType clickType, @Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){
        return addClickAction(new NPC.Interact.Actions.Title(this, clickType, title, subtitle, fadeIn, stay, fadeOut));
    }

    /**
     * @since 2022.2
     * @param title
     * @param subtitle
     * @param fadeIn
     * @param stay
     * @param fadeOut
     * @return
     */
    public NPC addTitleMessageClickAction(@Nonnull String title, @Nonnull String subtitle, int fadeIn, int stay, int fadeOut){
        return addTitleMessageClickAction(null, title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC resetClickActions(@Nonnull NPC.Interact.ClickType clickType){
        List<NPC.Interact.ClickAction> remove = this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType)).collect(Collectors.toList());
        clickActions.removeAll(remove);
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    public NPC resetClickActions(){
        this.clickActions = new ArrayList<>();
        return this;
    }

    /**
     * @since 2022.2
     * @param end
     * @param lookToEnd
     * @return
     */
    public Move.Task goTo(@Nonnull Location end, boolean lookToEnd){
        Validate.notNull(end, "Cannot move NPC to a null location.");
        Validate.isTrue(end.getWorld().getName().equals(world.getName()), "Cannot move NPC to another world.");
        if(this.moveTask == null){
            this.moveTask = new Move.Task(this, end, lookToEnd);
            return this.moveTask.start();
        }
        return null;
    }

    /**
     * @since 2022.2
     * @param end
     * @param lookToEnd
     * @param moveSpeed
     * @return
     */
    public Move.Task goTo(@Nonnull Location end, boolean lookToEnd, @Nullable Move.Speed moveSpeed){
        setMoveSpeed(moveSpeed);
        return goTo(end, lookToEnd);
    }

    /**
     * @since 2022.2
     * @return
     */
    public NPC cancelMove(){
        if(this.moveTask != null) moveTask.cancel(Move.Task.CancelCause.CANCELLED);
        return clearMoveTask();
    }

    /**
     * @since 2022.2
     * @param x
     * @param y
     * @param z
     * @return
     */
    protected NPC move(double x, double y, double z){
        Validate.isTrue(x < 8 && y < 8 && z < 8, "NPC cannot move 8 blocks or more at once, use teleport instead");
        NPC.Events.Move npcMoveEvent = new NPC.Events.Move(this, new Location(world, this.x + x, this.y + y, this.z + z));
        if(npcMoveEvent.isCancelled()) return this;
        this.x += x;
        this.y += y;
        this.z += z;
        entityPlayer.g(this.x, this.y, this.z);
        if(npcHologram != null) npcHologram.move(new Vector(x, y, z));
        movePacket(x, y, z);
        return this;
    }

    /**
     * @since 2022.2
     * @param moveSpeed
     * @return
     */
    public NPC setMoveSpeed(@Nullable Move.Speed moveSpeed){
        if(moveSpeed == null) moveSpeed = Move.Speed.NORMAL;
        return setMoveSpeed(moveSpeed.doubleValue());
    }

    /**
     * @since 2022.2
     * @param moveSpeed
     * @return
     */
    public NPC setMoveSpeed(double moveSpeed){
        if(moveSpeed <= 0.00) moveSpeed = 0.1;
        this.moveSpeed = moveSpeed;
        return this;
    }

    /**
     * @since 2022.2
     * @param animation
     * @return
     */
    public NPC playAnimation(NPC.Animation animation){
        if(animation.isDeprecated()) return this;
        PacketPlayOutAnimation packet = new PacketPlayOutAnimation(entityPlayer, animation.getId());
        NMSCraftPlayer.sendPacket(player, packet);
        return this;
    }

    public NPC setOnFire(boolean onFire) {
        this.onFire = onFire;
        return this;
    }

    @Deprecated
    public NPC dropItem(ItemStack itemStack){
        return this;
    }

    @Deprecated
    public NPC dropItemInSlot(NPC.Slot slot){
        ItemStack itemStack = getEquipment(slot);
        clearEquipment(slot);
        return dropItem(itemStack);
    }

    @Deprecated
    public NPC dropItemInHand(){
        return dropItemInSlot(Slot.MAINHAND);
    }

    /*
                Protected and private access methods
    */

    protected NPC reCreate(){
        Validate.notNull(player, "Failed to re-create the NPC. The NPC does not have the assigned player.");
        Validate.notNull(entityPlayer, "Failed to re-create the NPC. The NPC has not been created yet.");
        boolean show = canSee;
        hide();
        entityPlayer = null;
        create();
        if(show) show();
        return this;
    }

    protected NPC interact(@Nonnull Player player, @Nonnull NPC.Interact.ClickType clickType){
        if(player == null || player.getUniqueId() != this.player.getUniqueId()) return this;
        NPC.Events.Interact npcInteractEvent = new NPC.Events.Interact(player, this, clickType);
        if(npcInteractEvent.isCancelled()) return this;
        getClickActions(clickType).forEach(x-> x.execute());
        return this;
    }

    protected NPC setClickActions(@Nonnull List<NPC.Interact.ClickAction> clickActions){
        this.clickActions = clickActions;
        return this;
    }

    protected NPC setSlots(HashMap<NPC.Slot, ItemStack> slots){
        this.slots = slots;
        return this;
    }

    /**
     * Sets the {@link NPC#entityPlayer}'s {@link GameProfile}'s property "textures"
     * as the {@link NPC#skin} previously setted with {@link NPC#setSkin(NPC.Skin)}.
     * If the {@link NPC#entityPlayer} is {@link NPC#isCreated()}, you need
     * to do {@link NPC#update()} to send changes to the {@link NPC#player}'s client.
     *
     * @return  the {@link NPC} instance.
     * @see     NPC#isCreated()
     * @see     NPC#setSkin(NPC.Skin)
     * @see     NPC#update()
     */
    private NPC updateSkin(){
        GameProfile gameProfile = entityPlayer.fp();
        gameProfile.getProperties().get("textures").clear();
        gameProfile.getProperties().put("textures", new Property("textures", skin.getTexture(), skin.getSignature()));
        return this;
    }


    private NPC updatePose(){
        if(npcPose.equals(NPC.Pose.SLEEPING)) entityPlayer.e(new BlockPosition(x, y, z));
        entityPlayer.b(npcPose.getEntityPose());
        return this;
    }

    protected NPC updateMove(){
        if(player == null) return this;
        if(entityPlayer == null) return this;
        if(!canSee) return this;
        if(!hiddenToPlayer && !isInRange()){
            hideToPlayer();
            return this;
        }
        if(hiddenToPlayer && isInRange() && isInView()){
            showToPlayer();
            return this;
        }
        updateLook();
        updatePlayerRotation();
        //moveBehaviour.tick();
        //updateLocation();
        return this;
    }

    private NPC updateLook(){
        if(!player.getWorld().getName().equals(getWorld().getName())) return this;
        if(followLookType.equals(FollowLookType.PLAYER)) lookAt(player);
        else if(followLookType.equals(FollowLookType.NEAREST_PLAYER) || followLookType.equals(FollowLookType.NEAREST_ENTITY)){
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), ()-> {
                Entity near = null;
                double var0 = hideDistance;
                final boolean var3 = followLookType.equals(FollowLookType.NEAREST_PLAYER);
                final Location npcLocation = getLocation();
                for(Entity entities : world.getNearbyEntities(npcLocation, hideDistance, hideDistance, hideDistance)){
                    if(var3 && !(entities instanceof Player)) continue;
                    double var1 = entities.getLocation().distance(npcLocation);
                    if(var1 > var0) continue;
                    near = entities;
                    var0 = var1;
                }
                if(near == null) return;
                lookAt(near);
            });
        }
        return this;
    }

    private void updateEquipment(){
        List<Pair<EnumItemSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
        for(NPC.Slot slot : NPC.Slot.values()){
            EnumItemSlot nmsSlot = slot.getNmsEnum(EnumItemSlot.class);
            if(!slots.containsKey(slot)) slots.put(slot, new ItemStack(Material.AIR));
            ItemStack item = slots.get(slot);
            net.minecraft.world.item.ItemStack craftItem = null;
            try{ craftItem = (net.minecraft.world.item.ItemStack) NMSCraftItemStack.getCraftItemStackAsNMSCopy().invoke(null, item); }
            catch (Exception e){}
            Validate.notNull(craftItem, "Error at NMSCraftItemStack");
            equipment.add(new Pair(nmsSlot, craftItem));
        }
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(entityPlayer.ae(), equipment);
        NMSCraftPlayer.sendPacket(player, packet);
    }

    private void updatePlayerRotation(){
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutEntityLook(entityPlayer.ae(), (byte) ((yaw * 256 / 360)), (byte) ((pitch * 256 / 360)), false));
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) (yaw * 256 / 360)));
    }

    private void updateLocation(){
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntityTeleport(entityPlayer));
    }

    private void updateScoreboard(){
        GameProfile gameProfile = entityPlayer.fp();
        Scoreboard scoreboard = null;
        try{ scoreboard = (Scoreboard) NMSCraftScoreboard.getCraftScoreBoardGetHandle().invoke(NMSCraftScoreboard.getCraftScoreBoardClass().cast(player.getScoreboard())); }catch (Exception e){}
        Validate.notNull(scoreboard, "Error at NMSCraftScoreboard");
        ScoreboardTeam scoreboardTeam = scoreboard.f(gameProfile.getName()) == null ? new ScoreboardTeam(scoreboard, gameProfile.getName()) : scoreboard.f(gameProfile.getName());
        scoreboardTeam.a(ScoreboardTeamBase.EnumNameTagVisibility.b); //EnumNameTagVisibility.NEVER
        scoreboardTeam.a(glowingColor.getEnumChatFormat()); //setColor
        ScoreboardTeamBase.EnumTeamPush var1 = ScoreboardTeamBase.EnumTeamPush.b;                                       //EnumTeamPush.NEVER
        if(collidable) var1 = ScoreboardTeamBase.EnumTeamPush.a;                                                        //EnumTeamPush.ALWAYS
        scoreboardTeam.a(var1);
        scoreboardTeam.g().add(gameProfile.getName());
        scoreboard.a(gameProfile.getName(), scoreboardTeam);
        NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, true));
        NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, false));
    }

    private void movePacket(double x, double y, double z) {
        Validate.isTrue(x < 8);
        Validate.isTrue(y < 8);
        Validate.isTrue(z < 8);
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutEntity.PacketPlayOutRelEntityMove(entityPlayer.ae(), (short)(x * 4096), (short)(y * 4096), (short)(z * 4096), true));
    }

    private void updateMetadata(){
        try {
            DataWatcher dataWatcher = entityPlayer.ai();
            entityPlayer.i(glowing);
            Map<Integer, DataWatcher.Item<?>> map = (Map<Integer, DataWatcher.Item<?>>) FieldUtils.readDeclaredField(dataWatcher, "f", true);
            //http://wiki.vg/Entities#Entity
            //https://wiki.vg/Entity_metadata#Entity_Metadata_Format

            //Entity
            DataWatcher.Item item = map.get(0);
            byte initialBitMask = (Byte) item.b();
            byte b = initialBitMask;
            byte bitMaskIndex = (byte) 0x40;
            if(glowing) b = (byte) (b | bitMaskIndex);
            else b = (byte) (b & ~(1 << bitMaskIndex));
            bitMaskIndex = (byte) 0x01;
            if(onFire) b = (byte) (b | bitMaskIndex);
            else b = (byte) (b & ~(1 << bitMaskIndex));
            dataWatcher.b(DataWatcherRegistry.a.a(0), b);
            //
            //Player
            b = 0x00;
            //byte b = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40;
            if(skin.parts.isCape()) b = (byte) (b | 0x01);
            if(skin.parts.isJacket()) b = (byte) (b | 0x02);
            if(skin.parts.isLeftSleeve()) b = (byte) (b | 0x04);
            if(skin.parts.isRightSleeve()) b = (byte) (b | 0x08);
            if(skin.parts.isLeftPants()) b = (byte) (b | 0x10);
            if(skin.parts.isRightPants()) b = (byte) (b | 0x20);
            if(skin.parts.isHat()) b = (byte) (b | 0x40);
            dataWatcher.b(DataWatcherRegistry.a.a(17), b);
            //
            PacketPlayOutEntityMetadata metadataPacket = new PacketPlayOutEntityMetadata(entityPlayer.ae(), dataWatcher, true);
            NMSCraftPlayer.sendPacket(player, metadataPacket);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPacket(){
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.a, entityPlayer)); //EnumPlayerInfoAction.ADD_PLAYER
        NMSCraftPlayer.sendPacket(player, new PacketPlayOutNamedEntitySpawn(entityPlayer));
        shownOnTabList = true;
        updatePlayerRotation();
        if(showOnTabList) return;
        Bukkit.getScheduler().scheduleSyncDelayedTask(getNPCLib().getPlugin(), ()-> {
            if(!isCreated()) return;
            NMSCraftPlayer.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer)); //EnumPlayerInfoAction.REMOVE_PLAYER
            shownOnTabList = false;
        }, 10);
    }

    private void showToPlayer(){
        if(!hiddenToPlayer) return;
        createPacket();
        hiddenToPlayer = false;
        if(text.size() > 0) updateText();
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
        NMSCraftPlayer.sendPacket(player, NMSPacketPlayOutEntityDestroy.createPacket(entityPlayer.ae()));
        if(npcHologram != null) npcHologram.hide();
        hiddenToPlayer = true;
    }

    private NPC hideText(){
        Validate.notNull(player, "Failed to update NPC text. The NPC does not have the assigned player.");
        Validate.notNull(npcHologram, "Failed to update NPC text. The NPCHologram has not been created yet.");
        npcHologram.hide();
        hiddenText = true;
        return this;
    }

    private NPC showText(){
        Validate.notNull(player, "Failed to update NPC text. The NPC does not have the assigned player.");
        Validate.notNull(npcHologram, "Failed to update NPC text. The NPCHologram has not been created yet.");
        if(hiddenText) return this;
        npcHologram.show();
        hiddenText = false;
        return this;
    }

    /**
     * @since 2022.2
     * @param world
     * @return
     */
    protected NPC changeWorld(World world){
        this.world = world;
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    protected NPC addClickAction(@Nonnull NPC.Interact.ClickAction clickAction){
        this.clickActions.add(clickAction);
        return this;
    }

    /**
     * @since 2022.2
     * @return
     */
    protected NPC clearMoveTask(){
        this.moveTask = null;
        return this;
    }

    @Deprecated
    private NPC setFireSeconds(int fireSeconds){
        ((net.minecraft.world.entity.Entity)this.entityPlayer).g(fireSeconds);
        return this;
    }

    /*
                             Getters
    */

    /**
     * @return if the NPC is in view (fov of 60) to the player.
     * @since 2021.1
     */
    public boolean isInView(){
        return isInView(60.0D);
    }

    /**
     * @return if the NPC is in view to the player in the fov.
     * @param fov player's field of view aperture
     * @since 2022.2
     */
    public boolean isInView(double fov){
        Vector dir = getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        return dir.dot(player.getEyeLocation().getDirection()) >= Math.cos(Math.toRadians(fov));
    }

    /**
     * @return if the NPC is at the hideDistance or less, and in the same world.
     * @since 2021.1
     */
    public boolean isInRange(){
        if(!getWorld().getName().equals(player.getWorld().getName())) return false;
        return getLocation().distance(player.getLocation()) < hideDistance;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Location getLocation(){
        return new Location(getWorld(), getX(), getY(), getZ(), getYaw(), getPitch());
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Player getPlayer() {
        return player;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isCreated(){
        return entityPlayer != null;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean canBeCreated(){
        return getSkin() != null && entityPlayer == null;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean canSee() {
        return canSee;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isHiddenText() {
        return hiddenText;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    protected HashMap<NPC.Slot, ItemStack> getEquipment(){
        return slots;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public ItemStack getEquipment(NPC.Slot npcSlot){
        return slots.get(npcSlot);
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isShown(){
        return canSee;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isShownOnClient() { return canSee && !hiddenToPlayer; }

    /**
     * @since 2022.2
     * @return
     */
    public double getMoveSpeed() {
        return moveSpeed;
    }

    /**
     * @since 2022.2
     * @return
     */
    public Move.Task getMoveTask() {
        return moveTask;
    }

    /**
     * @since 2022.2
     * @return
     */
    public Move.Behaviour getMoveBehaviour(){ return moveBehaviour; }

    /**
     *
     * @return
     * @since 2021.1
     */
    public World getWorld() {
        return world;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Double getX() {
        return x;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Double getY() {
        return y;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Double getZ() {
        return z;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Float getYaw() {
        return yaw;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Float getPitch() {
        return pitch;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    protected NPC.Hologram getNpcHologram() {
        return npcHologram;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    protected EntityPlayer getEntityPlayer() {
        return entityPlayer;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public NPCLib getNPCLib() {
        return npcLib;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public String getCode() {
        return code;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public List<String> getText() {
        return text;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public NPC.Skin getSkin() {
        return skin;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isCollidable() {
        return collidable;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Double getHideDistance() {
        return hideDistance;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public Double getLineSpacing(){
        return this.lineSpacing;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    public Vector getTextAlignment() {
        return textAlignment;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    public Long getInteractCooldown() {
        return interactCooldown;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public NPC.Color getGlowingColor() {
        return glowingColor;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    protected HashMap<NPC.Slot, ItemStack> getSlots() {
        return slots;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isShowOnTabList() {
        return showOnTabList;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public String getCustomTabListName() {
        return customTabListName;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public boolean isGlowing() {
        return glowing;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    public FollowLookType getFollowLookType() {
        return followLookType;
    }

    /**
     *
     * @return
     * @since 2021.2
     */
    public NPC.Pose getPose() {
        return npcPose;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    public NPC.Hologram.Opacity getLineOpacity(Integer line){
        return linesOpacity.containsKey(line) ? linesOpacity.get(line) : NPC.Hologram.Opacity.LOWEST;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    public NPC.Hologram.Opacity getTextOpacity() {
        return textOpacity;
    }

    public boolean isOnFire() {
        return onFire;
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    private String getReplacedCustomName(){
        return getReplacedCustomName(customTabListName, tabListID);
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    private String getReplacedCustomName(String name){
        return getReplacedCustomName(name, tabListID);
    }

    /**
     *
     * @return
     * @since 2021.1
     */
    private String getReplacedCustomName(String name, UUID uuid){
        return name.replaceAll("\\{uuid\\}", uuid.toString().split("-")[1]);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC.Attributes getAttributes() {
        return new NPC.Attributes(this);
    }

    /**
     *
     * @since 2022.1
     */
    protected List<NPC.Interact.ClickAction> getClickActions() {
        return clickActions;
    }

    /**
     *
     * @since 2022.1
     */
    protected List<NPC.Interact.ClickAction> getClickActions(@Nonnull NPC.Interact.ClickType clickType){
        return this.clickActions.stream().filter(x-> clickType == null || x.getClickType() == null || x.getClickType().equals(clickType)).collect(Collectors.toList());
    }

    /**
     *
     * @since 2022.1
     */
    protected HashMap<Integer, NPC.Hologram.Opacity> getLinesOpacity() {
        return linesOpacity;
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

    public static class Skin{

        protected static final Skin DEFAULT = new Skin(
                "ewogICJ0aW1lc3RhbXAiIDogMTYyMTcxNTMxMjI5MCwKICAicHJvZmlsZUlkIiA6ICJiNTM5NTkyMjMwY2I0MmE0OWY5YTRlYmYxNmRlOTYwYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYXJpYW5hZmFnIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhNGFmNzE4NDU1ZDRhYWI1MjhlN2E2MWY4NmZhMjVlNmEzNjlkMTc2OGRjYjEzZjdkZjMxOWE3MTNlYjgxMGIiCiAgICB9CiAgfQp9",
                "otpbxDm9B+opW7jEzZF8BVDeZSqaqdF0dyLlnlyMh7Q5ysJFDL48/9J/IOHp8JqNm1oarmVdvxrroy9dlNI2Mz4BVuJM2pcCOJwk2h+aZ4dzNZGxst+MYNPSw+i4sMoYu7OV07UVHrQffolFF7MiaBUst1hFwM07IpTE6UtIQz4rqWisXe9Iz5+ooqX4wj0IB3dPntsh6u5nVlL8acWCBDAW4YqcPt2Y4CKK+KtskjzusjqGAdEO+4lRcW1S0ldo2RNtUHEzZADWQcADjg9KKiKq9QIpIpYURIoIAA+pDGb5Q8L5O6CGI+i1+FxqXbgdBvcm1EG0OPdw9WpSqAxGGeXSwlzjILvlvBzYbd6gnHFBhFO+X7iwRJYNd+qQakjUa6ZwR8NbkpbN3ABb9+6YqVkabaEmgfky3HdORE+bTp/AT6LHqEMQo0xdNkvF9gtFci7RWhFwuTLDvQ1esby1IhlgT+X32CPuVHuxEvPCjN7+lmRz2OyOZ4REo2tAIFUKakqu3nZ0NcF98b87wAdA9B9Qyd2H/rEtUToQhpBjP732Sov6TlJkb8echGYiLL5bu/Q7hum72y4+j2GNnuRiOJtJidPgDqrYMg81GfenfPyS6Ynw6KhdEhnwmJ1FJlJhYvXZyqZwLAV1c26DNYkrTMcFcv3VXmcd5/2Zn9FnZtw=",
                "Steve");

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

        public Skin(String playerName){ this(Skin.getSkin(playerName), playerName); }

        public Skin(Player player){ this(player.getName()); }

        public Skin setPlayerName(String playerName){
            this.playerName = playerName;
            return this;
        }

        /**
         * @since 2022.2
         * @param npc
         * @return
         */
        public NPC applyNPC(NPC npc){
            return npc.setSkin(this);
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

        public static String[] getSkin(String name) {
            Player player = Bukkit.getServer().getPlayer(name);
            if(Bukkit.getServer().getOnlineMode() && player != null) return getSkin(player);
            try {
                String uuid = getUUIDString(name);
                URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                InputStreamReader reader2 = new InputStreamReader(url2.openStream());
                JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
                String texture = property.get("value").getAsString();
                String signature = property.get("signature").getAsString();
                return new String[]{texture, signature};
            } catch (Exception e) { return NPC.Skin.getDefaultSkin().getData(); }
        }

        private static String[] getSkin(Player player){
            try{
                EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
                GameProfile profile = p.fp();
                Property property = profile.getProperties().get("textures").iterator().next();
                String texture = property.getValue();
                String signature = property.getSignature();
                return new String[]{texture, signature};
            }
            catch (Exception e){ return NPC.Skin.getDefaultSkin().getData(); }
        }

        private static UUID getUUID(String name) {
            return UUID.fromString(getUUIDString(name));
        }

        private static String getUUIDString(String name) {
            try {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                InputStreamReader reader = new InputStreamReader(url.openStream());
                String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();
                return uuid;
            } catch (Exception e) { return null; }
        }

        public static Skin getDefaultSkin(){ return DEFAULT; }

        public static Skin getPlayerSkin(Player player){ return new Skin(player); }

        public static Skin getPlayerSkin(String playerName){ return new Skin(playerName); }

        public static class Parts{

            private boolean cape;
            private boolean jacket;
            private boolean leftSleeve;
            private boolean rightSleeve;
            private boolean leftPants;
            private boolean rightPants;
            private boolean hat;

            protected Parts(){
                enableAll();
            }

            public void enableAll(){
                this.cape = true;
                this.jacket = true;
                this.leftSleeve = true;
                this.rightSleeve = true;
                this.leftPants = true;
                this.rightPants = true;
                this.hat = true;
            }

            public void disableAll(){
                this.cape = false;
                this.jacket = false;
                this.leftSleeve = false;
                this.rightSleeve = false;
                this.leftPants = false;
                this.rightPants = false;
                this.hat = false;
            }

            public boolean isCape() {
                return cape;
            }

            public void setCape(boolean cape) {
                this.cape = cape;
            }

            public boolean isJacket() {
                return jacket;
            }

            public void setJacket(boolean jacket) {
                this.jacket = jacket;
            }

            public boolean isLeftSleeve() {
                return leftSleeve;
            }

            public void setLeftSleeve(boolean leftSleeve) {
                this.leftSleeve = leftSleeve;
            }

            public boolean isRightSleeve() {
                return rightSleeve;
            }

            public void setRightSleeve(boolean rightSleeve) {
                this.rightSleeve = rightSleeve;
            }

            public boolean isLeftPants() {
                return leftPants;
            }

            public void setLeftPants(boolean leftPants) {
                this.leftPants = leftPants;
            }

            public boolean isRightPants() {
                return rightPants;
            }

            public void setRightPants(boolean rightPants) {
                this.rightPants = rightPants;
            }

            public boolean isHat() {
                return hat;
            }

            public void setHat(boolean hat) {
                this.hat = hat;
            }
        }

    }

    public static class Move{

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

        public static class Path{

            private NPC npc;
            private boolean repetitive;
            private boolean backToStart;
            private Location start;
            private List<Location> locations;

            private Path(NPC npc) {
                this.npc = npc;
            }

            public void start(){
                this.start = npc.getLocation();
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
                if(type == null || type.equals(Type.NONE)) return;
                if(type.equals(Type.FOLLOW_ENTITY) || type.equals(Type.FOLLOW_PLAYER) || type.equals(Type.FOLLOW_NPC)){
                    if(type.equals(Type.FOLLOW_ENTITY) && followEntity == null) return;
                    if(type.equals(Type.FOLLOW_NPC) && followNPC == null) return;
                    Location target = null;
                    if(type.equals(Type.FOLLOW_ENTITY)) target = followEntity.getLocation();
                    if(type.equals(Type.FOLLOW_PLAYER)) target = npc.player.getLocation();
                    if(type.equals(Type.FOLLOW_NPC)) target = followNPC.getLocation();
                    if(target == null) return;
                    if(!target.getWorld().equals(npc.getWorld())) return;
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
                if(type.equals(Type.CUSTOM_PATH)){
                    if(path == null) return;
                    return;
                }
            }

            public void setFollowEntity(Entity entity){
                setFollowEntity(entity, followMinDistance, followMaxDistance);
            }

            public void setFollowEntity(Entity entity, double followMinDistance){
                setFollowEntity(entity, followMinDistance, followMaxDistance);
            }

            public void setFollowEntity(Entity entity, double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_ENTITY);
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
            }

            public void setFollowNPC(NPC npc){
                setFollowNPC(npc, followMinDistance, followMaxDistance);
            }

            public void setFollowNPC(NPC npc, double followMinDistance){
                setFollowNPC(npc, followMinDistance, followMaxDistance);
            }

            public void setFollowNPC(NPC npc, double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_NPC);
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
            }

            public void setFollowPlayer(){
                setFollowPlayer(followMinDistance, followMaxDistance);
            }

            public void setFollowPlayer(double followMinDistance, double followMaxDistance){
                setType(Type.FOLLOW_PLAYER);
                this.followMinDistance = followMinDistance;
                this.followMaxDistance = followMaxDistance;
            }

            private void startTimer(){
                if(taskID != null) return;
                taskID = Bukkit.getScheduler().runTaskTimer(PlayerNPCPlugin.getInstance(), ()->{
                    tick();
                }, 20L, 20L).getTaskId();
            }

            private void finishTimer(){
                if(taskID == null) return;
                Bukkit.getScheduler().cancelTask(taskID);
            }

            private void setType(Type type) {
                this.type = type;
                if(type == Type.NONE) finishTimer();
                else startTimer();
            }

            public Type getType() {
                return type;
            }

            public NPC getNPC() {
                return npc;
            }

            public enum Type{
                NONE, FOLLOW_PLAYER, FOLLOW_ENTITY, FOLLOW_NPC, @Deprecated CUSTOM_PATH;
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
                    else if(isLadder(blockInLegFrom)) uppingLadder = true;
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
                        moveX = moveX * 3;
                        moveZ = moveZ * 3;
                        locY = blockInLeg.getY() + 0.5;
                    }
                    else if(npc.getPose().equals(Pose.SWIMMING)){
                        npc.setPose(Pose.STANDING);
                        npc.update();
                    }
                }
                if(checkSlabCrouching){
                    if((!blockInLeg.getType().isSolid() && isSlab(blockInLeg.getRelative(BlockFace.UP), Slab.Type.TOP)) || (isSlab(blockInLeg) && isSlab(blockInLeg.getRelative(BlockFace.UP).getRelative(BlockFace.UP), Slab.Type.BOTTOM))){
                        if(!npc.getPose().equals(Pose.CROUCHING)){
                            npc.setPose(Pose.CROUCHING);
                            npc.update();
                        }
                        moveX = moveX / 3;
                        moveZ = moveZ / 3;
                    }
                    else{
                        if(npc.getPose().equals(Pose.CROUCHING)){
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
                moveY = locY - npc.getY();
                if(moveY > 1.0) moveY = 1.0;
                if(moveY < -1.0) moveY = -1.0;
                if(checkLadders && uppingLadder){
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
                if(debug){
                    npc.getPlayer().sendMessage("", "", "", "", "", "", "", "", "");
                    npc.getPlayer().sendMessage("Block in leg " + blockInLeg.getType() + " " + blockInLeg.getType().isSolid());
                    npc.getPlayer().sendMessage("Block in foot " + blockInFoot.getType() + " " + blockInFoot.getType().isSolid());
                    npc.getPlayer().sendMessage("moveY " + moveY);
                    if(uppingLadder) npc.getPlayer().sendMessage("§c§lUPPING LADDER");
                    if(jumpingBlock) npc.getPlayer().sendMessage("§c§lJUMPING BLOCK");
                    if(falling) npc.getPlayer().sendMessage("§c§lFALLING");
                }
                if(!npc.isCreated()) return;
                if(lookToEnd){
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
            }
        }
    }

    public static class Events{

        private Events(){}

        public static class FinishMove extends Event {

            private static final HandlerList HANDLERS = new HandlerList();
            private final NPC npc;
            private final Location start;
            private final Location end;
            private final int taskID;
            private final NPC.Move.Task.CancelCause cancelCause;

            protected FinishMove(NPC npc, Location start, Location end, int taskID, NPC.Move.Task.CancelCause cancelCause) {
                this.npc = npc;
                this.start = start;
                this.end = end;
                this.taskID = taskID;
                this.cancelCause = cancelCause;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return npc.getPlayer();
            }

            public NPC getNPC() {
                return npc;
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

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
            }

        }

        public static class Hide extends Event implements Cancellable {

            private static final HandlerList HANDLERS = new HandlerList();
            private final Player player;
            private final NPC npc;
            private boolean isCancelled;

            protected Hide(Player player, NPC npc) {
                this.player = player;
                this.npc = npc;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return player;
            }

            public NPC getNPC() {
                return npc;
            }

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
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

        public static class Interact extends Event implements Cancellable{

            private static final HandlerList HANDLERS = new HandlerList();
            private final Player player;
            private final NPC npc;
            private final NPC.Interact.ClickType clickType;
            private boolean isCancelled;

            protected Interact(Player player, NPC npc, NPC.Interact.ClickType clickType) {
                this.player = player;
                this.npc = npc;
                this.clickType = clickType;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return player;
            }

            public NPC getNPC() {
                return npc;
            }

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
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

            private static final HandlerList HANDLERS = new HandlerList();
            private final NPC npc;
            private final Location to;
            private boolean isCancelled;

            protected Move(NPC npc, Location to) {
                this.npc = npc;
                this.to = to;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return npc.getPlayer();
            }

            public NPC getNPC() {
                return npc;
            }

            public Location getFrom(){
                return npc.getLocation();
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

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
            }

        }

        public static class Show extends Event implements Cancellable {

            private static final HandlerList HANDLERS = new HandlerList();
            private final Player player;
            private final NPC npc;
            private boolean isCancelled;

            protected Show(Player player, NPC npc) {
                this.player = player;
                this.npc = npc;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return player;
            }

            public NPC getNPC() {
                return npc;
            }

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
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

            private static final HandlerList HANDLERS = new HandlerList();
            private final NPC npc;
            private final Location to;
            private boolean isCancelled;

            protected Teleport(NPC npc, Location to) {
                this.npc = npc;
                this.to = to;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return npc.getPlayer();
            }

            public NPC getNPC() {
                return npc;
            }

            public Location getFrom(){
                return npc.getLocation();
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

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
            }

        }

        public static class StartMove extends Event implements Cancellable {

            private static final HandlerList HANDLERS = new HandlerList();
            private final NPC npc;
            private final Location start;
            private final Location end;
            private final int taskID;
            private boolean isCancelled;

            protected StartMove(NPC npc, Location start, Location end, int taskID) {
                this.npc = npc;
                this.start = start;
                this.end = end;
                this.taskID = taskID;
                this.isCancelled = false;
                Bukkit.getPluginManager().callEvent(this);
            }

            public static HandlerList getHandlerList() {
                return HANDLERS;
            }

            public Player getPlayer() {
                return npc.getPlayer();
            }

            public NPC getNPC() {
                return npc;
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

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
            }

        }
    }

    public static class Interact{

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

            protected String getReplacedString(String string){
                string = r(string, "playerName", getNPC().getPlayer().getName());
                string = r(string, "playerUUID", getNPC().getPlayer().getUniqueId().toString());
                string = r(string, "npcCode", getNPC().getCode());
                string = r(string, "world", getNPC().getWorld().getName());
                return string;
            }

            private String r(String command, String placeHolder, String value){
                return command.replaceAll("\\{" + placeHolder +"\\}", value);
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
            void execute();
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
            }

            public static class Custom extends ClickAction{

                private final CustomAction customAction;

                protected Custom(NPC npc, NPC.Interact.ClickType clickType, CustomAction customAction) {
                    super(npc, NPC.Interact.Actions.Type.CUSTOM_ACTION, clickType);
                    this.customAction = customAction;
                }

                @Override
                public void execute() {
                    customAction.execute();
                }

                public abstract static class CustomAction implements CustomActionInterface { }

                interface CustomActionInterface{ void execute(); }
            }

            public static class Message extends ClickAction{

                private final String[] messages;

                protected Message(NPC npc, NPC.Interact.ClickType clickType, String... message) {
                    super(npc, NPC.Interact.Actions.Type.SEND_MESSAGE, clickType);
                    this.messages = message;
                }

                @Override
                public void execute() {
                    Arrays.stream(messages).toList().forEach(x-> getNPC().getPlayer().sendMessage(getReplacedString(x)));
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
                public void execute() {
                    Bukkit.getServer().dispatchCommand(getNPC().getPlayer(), getReplacedString(super.getCommand()));
                }
            }

            public static class ConsoleCommand extends NPC.Interact.Actions.Command{

                protected ConsoleCommand(NPC npc, NPC.Interact.ClickType clickType, String command) {
                    super(npc, NPC.Interact.Actions.Type.RUN_CONSOLE_COMMAND, clickType, command);
                }

                @Override
                public void execute() {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), getReplacedString(super.getCommand()));
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
                public void execute() {
                    getNPC().getPlayer().sendTitle(getReplacedString(title), getReplacedString(subtitle), fadeIn, stay, fadeOut);
                }
            }

            public static class ActionBar extends ClickAction{

                private final String message;

                public ActionBar(NPC npc, NPC.Interact.ClickType clickType, String message) {
                    super(npc, NPC.Interact.Actions.Type.SEND_ACTIONBAR_MESSAGE, clickType);
                    this.message = message;
                }

                @Override
                public void execute() {
                    getNPC().getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(getReplacedString(message)));
                }
            }

            public static class BungeeServer extends ClickAction{

                private final String server;

                protected BungeeServer(NPC npc, NPC.Interact.ClickType clickType, String server) {
                    super(npc, NPC.Interact.Actions.Type.CONNECT_BUNGEE_SERVER, clickType);
                    this.server = server;
                }

                @Override
                public void execute() {
                    if(!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(PlayerNPCPlugin.getInstance(), "BungeeCord")) Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(PlayerNPCPlugin.getInstance(), "BungeeCord");
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("Connect");
                    out.writeUTF(server);
                    getNPC().getPlayer().sendPluginMessage(PlayerNPCPlugin.getInstance(), "BungeeCord", out.toByteArray());
                }
            }

        }

        public enum ClickType{
            RIGHT_CLICK, LEFT_CLICK;

            public boolean isRightClick(){ return this.equals(ClickType.RIGHT_CLICK); }

            public boolean isLeftClick(){ return this.equals(ClickType.LEFT_CLICK); }

        }
    }

    public static class Hologram{
        private NPC npc;
        private Location location;
        private HashMap<Integer, List<EntityArmorStand>> lines;
        private boolean canSee;

        protected Hologram(NPC npc) {
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
            NPC.Hologram.Opacity textOpacity = getLinesOpacity().containsKey(line) ? getLinesOpacity().get(line) : npc.getTextOpacity();
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
            return npc.getPlayer();
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
                "§8[NPC] {uuid}",
                false,
                200L,
                0.27,
                new Vector(0, 1.75, 0),
                NPC.Pose.STANDING,
                NPC.Hologram.Opacity.LOWEST
        );

        protected static final Double VARIABLE_MIN_LINE_SPACING = 0.27;
        protected static final Double VARIABLE_MAX_LINE_SPACING = 1.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_XZ = 2.00;
        protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_Y = 5.00;

        private NPC.Skin skin;
        private List<String> text;
        private HashMap<NPC.Slot, ItemStack> slots;
        private boolean collidable;
        private Double hideDistance;
        private boolean glowing;
        private NPC.Color glowingColor;
        private NPC.FollowLookType followLookType;
        private String customTabListName;
        private boolean showOnTabList;
        private Long interactCooldown;
        private Double lineSpacing;
        private Vector textAlignment;
        private NPC.Pose npcPose;
        private NPC.Hologram.Opacity textOpacity;

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
                              NPC.Hologram.Opacity textOpacity
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
            this.npcPose = npcPose;
            this.textOpacity = textOpacity;
            Arrays.stream(NPC.Slot.values()).forEach(x-> slots.put(x, new ItemStack(Material.AIR)));
        }

        public Attributes(){
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
            this.npcPose = DEFAULT.getPose();
            this.lineSpacing = DEFAULT.getLineSpacing();
            this.textAlignment = DEFAULT.getTextAlignment().clone();
            this.interactCooldown = DEFAULT.getInteractCooldown();
            this.textOpacity = DEFAULT.getTextOpacity();
        }

        public Attributes(@Nonnull NPC npc){
            Validate.notNull(npc, "Cannot create NPC.Attributes from a null NPC. Instead use new NPC.Attributes();");
            this.collidable = npc.isCollidable();
            this.text = npc.getText();
            this.hideDistance = npc.getHideDistance();
            this.glowing = npc.isGlowing();
            this.skin = npc.getSkin();
            this.glowingColor = npc.getGlowingColor();
            this.followLookType = npc.getFollowLookType();
            this.slots = (HashMap<NPC.Slot, ItemStack>) npc.getSlots().clone();
            this.showOnTabList = npc.isShowOnTabList();
            this.npcPose = npc.getPose();
            this.lineSpacing = npc.getLineSpacing();
            this.textAlignment = npc.getTextAlignment().clone();
            this.interactCooldown = npc.getInteractCooldown();
            this.textOpacity = npc.getTextOpacity();
        }

        public void applyNPC(@Nonnull NPC npc, boolean forceUpdate){
            applyNPC(npc);
            if(forceUpdate) npc.forceUpdate();
        }

        public void applyNPC(@Nonnull NPC npc){
            Validate.notNull(npc, "Cannot apply NPC.Attributes to a null NPC.");
            npc.setCollidable(this.collidable);
            npc.setText(this.text);
            npc.setHideDistance(this.hideDistance);
            npc.setGlowing(this.glowing);
            npc.setGlowingColor(this.glowingColor);
            npc.setFollowLookType(this.followLookType);
            npc.setSlots((HashMap<NPC.Slot, ItemStack>) this.slots.clone());
            npc.setCustomTabListName(this.customTabListName);
            npc.setShowOnTabList(this.showOnTabList);
            npc.setPose(this.npcPose);
            npc.setLineSpacing(this.lineSpacing);
            npc.setTextAlignment(this.textAlignment.clone());
            npc.setInteractCooldown(this.interactCooldown);
            npc.setTextOpacity(this.textOpacity);
        }

        public void applyNPC(@Nonnull Collection<NPC> npc){
            applyNPC(npc, false);
        }

        public void applyNPC(@Nonnull Collection<NPC> npc, boolean forceUpdate){
            Validate.notNull(npc, "Cannot apply NPC.Attributes to a null NPC.");
            npc.forEach(x-> applyNPC(x, forceUpdate));
        }

        public boolean equals(@Nonnull NPC npc){
            Validate.notNull(npc, "Cannot verify equals to a null NPC.");
            return equals(npc.getAttributes());
        }

        public boolean equals(@Nonnull NPC.Attributes npc){
            Validate.notNull(npc, "Cannot verify equals to a null NPC.Attributes.");
            return npc.isCollidable() == isCollidable() &&
                    npc.getText().equals(getText()) &&
                    npc.getHideDistance().equals(getHideDistance()) &&
                    npc.isGlowing() == isGlowing() &&
                    npc.getGlowingColor().equals(getGlowingColor()) &&
                    npc.getFollowLookType().equals(getFollowLookType()) &&
                    npc.getSlots().equals(getSlots()) &&
                    npc.getCustomTabListName().equals(getCustomTabListName()) &&
                    npc.isShowOnTabList() == isShowOnTabList() &&
                    npc.getPose().equals(getPose()) &&
                    npc.getLineSpacing().equals(getLineSpacing()) &&
                    npc.getTextAlignment().equals(getTextAlignment()) &&
                    npc.getTextOpacity().equals(getTextOpacity()) &&
                    npc.getInteractCooldown().equals(getInteractCooldown());
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

        public void setSkin(@Nullable NPC.Skin skin) {
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

        public void setText(@Nullable List<String> text) {
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

        public void setHelmet(@Nullable ItemStack itemStack){
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

        public void setChestPlate(@Nullable ItemStack itemStack){
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

        public void setLeggings(@Nullable ItemStack itemStack){
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

        public void setBoots(@Nullable ItemStack itemStack){
            setItem(NPC.Slot.BOOTS, itemStack);
        }

        public static void setDefaultBoots(@Nullable ItemStack itemStack){
            DEFAULT.setBoots(itemStack);
        }

        public void setItem(@Nonnull NPC.Slot slot, @Nullable ItemStack itemStack){
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

        public void setCollidable(boolean collidable) {
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
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#getHideDistance()
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#setDefaultHideDistance(double)
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#getDefaultHideDistance()
         */
        public void setHideDistance(double hideDistance) {
            Validate.isTrue(hideDistance > 0.00, "The hide distance cannot be negative or 0");
            this.hideDistance = hideDistance;
        }

        /**
         * When the player is far enough, the NPC will temporally hide, in order
         * to be more efficient. And when the player approach, the NPC will be unhidden.
         *
         * @param hideDistance the distance in blocks
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#getHideDistance()
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#setHideDistance(double)
         * @see dev.sergiferry.playernpc.api.NPC.Attributes#getDefaultHideDistance()
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

        public void setGlowing(boolean glowing) {
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

        public void setGlowingColor(@Nullable ChatColor color) {
            if(color == null) color = ChatColor.WHITE;
            Validate.isTrue(color.isColor(), "Error setting glow color. It's not a color.");
            this.glowingColor = NPC.Color.getColor(color);
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

        public void setFollowLookType(@Nullable NPC.FollowLookType followLookType) {
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

        public void setCustomTabListName(@Nonnull String customTabListName){
            if(customTabListName == null) customTabListName = "§8[NPC] {uuid}";
            Validate.isTrue(customTabListName.contains("{uuid}"), "Custom tab list name attribute must have {uuid} placeholder.");
            Validate.isTrue(customTabListName.length() <= 16, "Error setting custom tab list name. Name must be 16 or less characters.");
            this.customTabListName = customTabListName;
        }

        public static void setDefaultCustomTabListName(@Nonnull String customTabListName){
            DEFAULT.setCustomTabListName(customTabListName);
        }

        public boolean isShowOnTabList() {
            return showOnTabList;
        }

        public boolean isDefaultShowOnTabList(){
            return DEFAULT.isShowOnTabList();
        }

        public void setShowOnTabList(boolean showOnTabList) {
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

        public void setInteractCooldown(long milliseconds) {
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

        public void setLineSpacing(double lineSpacing) {
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

        public void setTextAlignment(@Nonnull Vector vector) {
            Validate.notNull(vector, "Failed to set text alignment. Vector cannot be null.");
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
            return npcPose;
        }

        public static NPC.Pose getDefaultPose(){
            return DEFAULT.getPose();
        }

        public void setPose(@Nullable NPC.Pose npcPose) {
            if(npcPose == null) npcPose = NPC.Pose.STANDING;
            this.npcPose = npcPose;
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

        public void setTextOpacity(@Nullable NPC.Hologram.Opacity textOpacity) {
            if(textOpacity == null) textOpacity = NPC.Hologram.Opacity.LOWEST;
            this.textOpacity = textOpacity;
        }
    }

}
