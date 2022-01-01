package dev.sergiferry.playernpc.api;

import com.google.common.annotations.Beta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import dev.sergiferry.playernpc.api.actions.*;
import dev.sergiferry.playernpc.api.events.NPCHideEvent;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import dev.sergiferry.playernpc.api.events.NPCShowEvent;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftItemStack;
import dev.sergiferry.playernpc.nms.craftbukkit.NMSCraftScoreboard;
import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.playernpc.utils.ColorUtils;
import dev.sergiferry.playernpc.utils.SkinFetcher;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.reflect.FieldUtils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    private final World world;
    private Double x, y, z;
    private Float yaw, pitch;
    private EntityPlayer entityPlayer;
    private final UUID tabListID;
    private NPCHologram npcHologram;
    private boolean canSee;
    private boolean hiddenText;
    private boolean hiddenToPlayer;
    private boolean shownOnTabList;
    private String customTabListName;
    private List<NPCClickAction> clickActions;
    private NPC.TextOpacity textOpacity;
    private HashMap<Integer, NPC.TextOpacity> linesOpacity;

    // NPCAttributes
    private NPCSkin skin;
    private List<String> text;
    private HashMap<NPC.Slot, ItemStack> slots;
    private boolean collidable;
    private Double hideDistance;
    private boolean glowing;
    private ChatColor color;
    private NPC.FollowLookType followLookType;
    private boolean showOnTabList;
    private Long interactCooldown;
    private Double lineSpacing;
    private Vector textAlignment;
    private NPC.Pose npcPose;

    /**
     * This constructor can only be invoked by using {@link NPCLib#generateNPC(Player, String, Location)}
     * <p><strong>This only generates the NPC instance, you must {@link NPC#create()} and {@link NPC#show()} it after.</strong></p>
     *
     * @param npcLib always is {@link NPCLib#getInstance()}
     * @param player the {@link Player} that will see the NPC
     * @param code an {@link String} that will let find this {@link NPC} instance at {@link NPCPlayerManager#getNPC(String)}
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
        this.tabListID = UUID.randomUUID();
        this.clickActions = new ArrayList<>();
        this.textOpacity = TextOpacity.LOWEST;
        this.linesOpacity = new HashMap<>();

        //NPC Attributes
        NPCAttributes npcAttributes = new NPCAttributes();
        this.hideDistance = 0.0;
        this.skin = npcAttributes.getSkin();
        this.text = npcAttributes.getText();
        this.slots = (HashMap<NPC.Slot, ItemStack>) npcAttributes.getSlots().clone();
        this.collidable = npcAttributes.isCollidable();
        this.glowing = npcAttributes.isGlowing();
        this.color = npcAttributes.getGlowingColor();
        this.followLookType = npcAttributes.getFollowLookType();
        this.customTabListName = npcAttributes.getCustomTabListName();
        this.showOnTabList = npcAttributes.isShowOnTabList();
        this.interactCooldown = npcAttributes.getInteractCooldown();
        this.lineSpacing = npcAttributes.getLineSpacing();
        this.textAlignment = npcAttributes.getTextAlignment().clone();
        this.npcPose = npcAttributes.getPose();
        npcLib.getNPCPlayerManager(player).set(code, this);
        Bukkit.getScheduler().scheduleSyncDelayedTask(npcLib.getPlugin(), ()-> {
            hideDistance = NPCAttributes.getDefault().getHideDistance();
        },1);
    }

    /**
     * This constructor can only be invoked by using {@link NPCLib#generateNPC(Player, String, Location)}
     * <p><strong>This only generates the NPC instance, you must {@link NPC#create()} and {@link NPC#show()} it after.</strong></p>
     *
     * @param npcLib always is {@link NPCLib#getInstance()}
     * @param player the {@link Player} that will see the NPC
     * @param code an {@link String} that will let find this {@link NPC} instance at {@link NPCPlayerManager#getNPC(String)}
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
        Validate.notNull(skin, "Failed to create the NPC. The NPCSkin has not been configured.");
        Validate.isTrue(entityPlayer == null, "Failed to create the NPC. This NPC has already been created before.");
        MinecraftServer server = NMSCraftServer.getMinecraftServer();
        WorldServer worldServer = NMSCraftWorld.getWorldServer(world);
        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, getReplacedCustomName());
        this.entityPlayer = new EntityPlayer(server, worldServer, gameProfile);
        entityPlayer.a(x, y, z, yaw, pitch);                                                                            //setLocation
        this.npcHologram = new NPCHologram(this);
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
        updateLocation();
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
    public NPC teleport(double x, double y, double z, float yaw, float pitch){
        Validate.notNull(entityPlayer, "Failed to move the NPC. The NPC has not been created yet.");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
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
        Validate.isTrue(entity.getWorld().getName().equals(world.getName()), "Entity must be at the same world.");
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
        Validate.isTrue(location.getWorld().getName().equals(world.getName()), "Location must be at the same world.");
        return teleport(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
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
        return teleport(x, y, z, yaw, pitch);
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
        Validate.notNull(slot, "Failed to set item, NPCSlot cannot be null");
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
        this.slots.put(slot, new ItemStack(Material.AIR));
        return this;
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
        if(entityPlayer != null){
            if(canSee) hide();
            entityPlayer = null;
        }
        if(npcHologram != null) npcHologram.removeHologram();
        return this;
    }

    /**
     * Shows the NPC to the player. It must be {@link NPC#isCreated()} to show it.
     * To hide it after, use {@link NPC#hide()}. This method calls {@link NPCShowEvent}.
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
        NPCShowEvent npcShowEvent = new NPCShowEvent(getPlayer(), this);
        if(npcShowEvent.isCancelled()) return this;
        canSee = true;
        if(!isInView() || !isInRange()){
            hiddenToPlayer = true;
            return this;
        }
        showToPlayer();
        return this;
    }

    /**
     * Hides the NPC from the player. It must be {@link NPC#isCreated()} to hide it.
     * To show it again, use {@link NPC#show()}. This method calls {@link NPCHideEvent}.
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
        NPCHideEvent npcHideEvent = new NPCHideEvent(getPlayer(), this);
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
        this.yaw = npcLocation.getYaw(); //yRot
        this.pitch = npcLocation.getPitch(); //xRot
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
     * Sets the {@link NPCSkin} of the {@link NPC}.
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
        return setSkin(new NPCSkin(texture, signature));
    }

    /**
     * Sets the {@link NPCSkin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param texture Texture of the skin
     * @param signature Signature of the skin
     * @param playerName Name of the skin owner, this is not necessary, but it will store it on the {@link NPCSkin} instance.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nonnull String texture, @Nonnull String signature, @Nullable String playerName){
        return setSkin(new NPCSkin(texture, signature, playerName));
    }

    /**
     * Sets the {@link NPCSkin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param playerName Name of the skin owner. It will fetch skin even if the player is not online.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable String playerName){
        if(playerName == null) return setSkin(NPCSkin.DEFAULT);
        String[] skin = SkinFetcher.getSkin(playerName);
        Validate.notNull(skin, "Failed to set NPC Skin. The Mojang API didn't respond.");
        return setSkin(skin[0], skin[1], playerName);
    }

    /**
     * Sets the {@link NPCSkin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param playerSkin Player that is online, that will fetch skin.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable Player playerSkin){
        if(playerSkin == null) return setSkin(NPCSkin.DEFAULT);
        Validate.isTrue(playerSkin.isOnline(), "Failed to set NPC skin. Player must be online.");
        return setSkin(playerSkin.getName());
    }

    /**
     * Sets the {@link NPCSkin} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param npcSkin NPCSkin with the texture and signature.
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC setSkin(@Nullable NPCSkin npcSkin){
        if(npcSkin == null) npcSkin = NPCSkin.DEFAULT;
        this.skin = npcSkin;
        return this;
    }

    /**
     * Sets the {@link NPCSkin} of the {@link NPC} as the Default minecraft skin.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @since 2021.1
     * @see NPC#forceUpdate()
     */
    public NPC clearSkin(){
        return setSkin((NPCSkin) null);
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
    public NPC setLineOpacity(int line, @Nullable NPC.TextOpacity textOpacity){
        if(textOpacity == null) textOpacity = TextOpacity.LOWEST;
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
        return setLineOpacity(line, NPC.TextOpacity.LOWEST);
    }

    /**
     *
     * @param textOpacity
     * @return
     * @see NPC#forceUpdateText()
     */
    public NPC setTextOpacity(@Nullable NPC.TextOpacity textOpacity){
        if(textOpacity == null) textOpacity = TextOpacity.LOWEST;
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
        return setTextOpacity(TextOpacity.LOWEST);
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
        if(color == null) color = ChatColor.WHITE;
        Validate.isTrue(color.isColor(), "Error setting glow color. It's not a color.");
        this.color = color;
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
        if(name == null) name = NPCAttributes.getDefaultTabListName();
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
     * Sets the line spacing of the {@link NPCHologram}.
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
        if(lineSpacing < NPCAttributes.VARIABLE_MIN_LINE_SPACING) lineSpacing = NPCAttributes.VARIABLE_MIN_LINE_SPACING;
        else if(lineSpacing > NPCAttributes.VARIABLE_MAX_LINE_SPACING) lineSpacing = NPCAttributes.VARIABLE_MAX_LINE_SPACING;
        this.lineSpacing = lineSpacing;
        return this;
    }

    /**
     * Sets the line spacing of the {@link NPCHologram} to the default value ({@link NPCAttributes#getDefaultLineSpacing()}).
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
        return setLineSpacing(NPCAttributes.getDefault().getLineSpacing());
    }

    /**
     *
     * @since 2022.1
     */
    public NPC setTextAlignment(@Nonnull Vector vector){
        Validate.notNull(vector, "Failed to set text alignment. Vector cannot be null.");
        if(vector.getX() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getX() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        if(vector.getY() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        else if(vector.getY() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        if(vector.getZ() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getZ() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        this.textAlignment = vector;
        return this;
    }

    /**
     *
     * @since 2022.1
     */
    public NPC resetTextAlignment(){
        this.textAlignment = NPCAttributes.getDefault().getTextAlignment();
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
        return setInteractCooldown(NPCAttributes.getDefault().getInteractCooldown());
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addCustomClickAction(@Nullable NPCInteractEvent.ClickType clickType, @Nonnull CustomAction customAction){
        return addClickAction(new NPCCustomClickAction(this, clickType,customAction));
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addCustomClickAction(@Nonnull CustomAction customAction){
        return addCustomClickAction(null, customAction);
    }

    /**
     *
     * @since 2022.1
     */
    public NPC addMessageClickAction(@Nullable NPCInteractEvent.ClickType clickType, @Nonnull String... message){
        return addClickAction(new NPCMessageClickAction(this, clickType, message));
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
    public NPC addRunPlayerCommandClickAction(@Nonnull NPCInteractEvent.ClickType clickType, @Nonnull String command){
        return addClickAction(new NPCRunPlayerCommandClickAction(this, clickType, command));
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
    public NPC addRunConsoleCommandClickAction(@Nonnull NPCInteractEvent.ClickType clickType, @Nonnull String command){
        return addClickAction(new NPCRunConsoleCommandClickAction(this, clickType, command));
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
     * @since 2022.1
     */
    public NPC resetClickActions(@Nonnull NPCInteractEvent.ClickType clickType){
        List<NPCClickAction> remove = this.clickActions.stream().filter(x-> x.getClickType() != null && x.getClickType().equals(clickType)).collect(Collectors.toList());
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

    protected NPC interact(@Nonnull Player player, @Nonnull NPCInteractEvent.ClickType clickType){
        if(player == null || player.getUniqueId() != this.player.getUniqueId()) return this;
        NPCInteractEvent npcInteractEvent = new NPCInteractEvent(player, this, clickType);
        if(npcInteractEvent.isCancelled()) return this;
        getClickActions(clickType).forEach(x-> x.execute());
        return this;
    }

    protected NPC setClickActions(@Nonnull List<NPCClickAction> clickActions){
        this.clickActions = clickActions;
        return this;
    }

    protected NPC setSlots(HashMap<NPC.Slot, ItemStack> slots){
        this.slots = slots;
        return this;
    }

    /**
     * Sets the {@link NPC#entityPlayer}'s {@link GameProfile}'s property "textures"
     * as the {@link NPC#skin} previously setted with {@link NPC#setSkin(NPCSkin)}.
     * If the {@link NPC#entityPlayer} is {@link NPC#isCreated()}, you need
     * to do {@link NPC#update()} to send changes to the {@link NPC#player}'s client.
     *
     * @return  the {@link NPC} instance.
     * @see     NPC#isCreated()
     * @see     NPC#setSkin(NPCSkin)
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
        updateLocation();
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
        scoreboardTeam.a(ColorUtils.getEnumChatFormat(color)); //setColor
        ScoreboardTeamBase.EnumTeamPush var1 = ScoreboardTeamBase.EnumTeamPush.b;                                       //EnumTeamPush.NEVER
        if(collidable) var1 = ScoreboardTeamBase.EnumTeamPush.a;                                                        //EnumTeamPush.ALWAYS
        scoreboardTeam.a(var1);
        scoreboardTeam.g().add(gameProfile.getName());
        scoreboard.a(gameProfile.getName(), scoreboardTeam);
        NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, true));
        NMSCraftPlayer.sendPacket(player, PacketPlayOutScoreboardTeam.a(scoreboardTeam, false));
    }

    private void updateMetadata(){
        try {
            DataWatcher dataWatcher = entityPlayer.ai();
            entityPlayer.i(glowing);
            Map<Integer, DataWatcher.Item<?>> map = (Map<Integer, DataWatcher.Item<?>>) FieldUtils.readDeclaredField(dataWatcher, "f", true);
            DataWatcher.Item item = map.get(0);
            byte initialBitMask = (Byte) item.b();
            //http://wiki.vg/Entities#Entity
            byte bitMaskIndex = (byte) 0x40;
            if (glowing) item.a((byte) (initialBitMask | 1 << bitMaskIndex));
            else item.a((byte) (initialBitMask & ~(1 << bitMaskIndex)));
            bitMaskIndex = (byte) 0x01;
            item.a((byte) (initialBitMask & ~(1 << bitMaskIndex)));
            //
            byte b = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40;
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
     *
     * @since 2022.1
     */
    protected NPC addClickAction(@Nonnull NPCClickAction clickAction){
        this.clickActions.add(clickAction);
        return this;
    }

    /*
                             Getters
    */

    /**
     * @return if the NPC is in view (fov of 60) to the player.
     */
    public boolean isInView(){
        Vector dir = getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        return dir.dot(player.getEyeLocation().getDirection()) >= Math.cos(Math.toRadians(60.0D));
    }

    /**
     * @return if the NPC is at the hideDistance or less, and in the same world.
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
    protected NPCHologram getNpcHologram() {
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
    public NPCSkin getSkin() {
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
    public ChatColor getGlowingColor() {
        return color;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    protected EnumChatFormat getEnumGlowingColor(){
        return ColorUtils.getEnumChatFormat(color);
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
    public NPC.TextOpacity getLineOpacity(Integer line){
        return linesOpacity.containsKey(line) ? linesOpacity.get(line) : NPC.TextOpacity.LOWEST;
    }

    /**
     *
     * @return
     * @since 2022.1
     */
    public TextOpacity getTextOpacity() {
        return textOpacity;
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
    public NPCAttributes getAttributes() {
        return new NPCAttributes(this);
    }

    /**
     *
     * @since 2022.1
     */
    protected List<NPCClickAction> getClickActions() {
        return clickActions;
    }

    /**
     *
     * @since 2022.1
     */
    protected List<NPCClickAction> getClickActions(@Nonnull NPCInteractEvent.ClickType clickType){
        return this.clickActions.stream().filter(x-> clickType == null || x.getClickType() == null || x.getClickType().equals(clickType)).collect(Collectors.toList());
    }

    /**
     *
     * @since 2022.1
     */
    protected HashMap<Integer, NPC.TextOpacity> getLinesOpacity() {
        return linesOpacity;
    }

    /*
                    Enums
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
     * @since 2022.1
     */
    public enum TextOpacity {
        LOWEST(1),
        LOW(2),
        MEDIUM(3),
        HARD(4),
        HARDER(6),
        FULL(10)
        ;

        private int times;

        TextOpacity(int times){ this.times = times; }

        protected int getTimes() { return times; }

        public static TextOpacity getTextOpacity(String name){ return Arrays.stream(TextOpacity.values()).filter(x-> x.name().equalsIgnoreCase(name)).findAny().orElse(null); }
    }
}
