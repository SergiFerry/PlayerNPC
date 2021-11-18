package dev.sergiferry.playernpc.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import dev.sergiferry.playernpc.api.events.NPCHideEvent;
import dev.sergiferry.playernpc.api.events.NPCShowEvent;
import dev.sergiferry.playernpc.nms.craftbukkit.*;
import dev.sergiferry.playernpc.nms.minecraft.NMSPacketPlayOutEntityDestroy;
import dev.sergiferry.playernpc.utils.ColorUtils;
import dev.sergiferry.playernpc.utils.SkinFetcher;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftServer;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftWorld;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
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

/**
 * NPC instance per player. An NPC can only be seen by one player. This is because of personalization purposes.
 * With this instance you can create customizable Player NPCs that can be interacted with.
 * NPCs will be only visible to players after creating the EntityPlayer, and show it to the player.
 *
 * @author  SergiFerry
 * @since 2021.1
 */
public class NPC {

    private static final String DEFAULT_TAB_NAME = "§8[NPC] {uuid}";

    private NPCLib npcLib;
    private String code;
    private Player player;
    private boolean canSee;
    private World world;
    private Double x, y, z;
    private Float yaw, pitch;
    private EntityPlayer entityPlayer;
    private NPCSkin skin;
    private NPCHologram npcHologram;
    private List<String> text;
    private HashMap<NPCSlot, ItemStack> slots;
    private boolean collidable;
    private boolean hiddenText;
    private Double hideDistance;
    private boolean hiddenToPlayer;
    private boolean glowing;
    private EnumChatFormat color;
    private FollowLookType followLookType;
    private boolean showOnTabList;
    private String customTabListName;
    private boolean shownOnTabList;
    private NPCPose npcPose;

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
     * @see NPCLib#getInstance()
     * @see NPCLib#generateNPC(Player, String, Location)
     * @see NPC#create()
     * @see NPC#show()
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
        this.collidable = false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.text = new ArrayList<>();
        this.hiddenText = false;
        this.hideDistance = 0.0;
        this.npcHologram = null;
        this.hiddenToPlayer = true;
        this.glowing = false;
        this.skin = NPCSkin.DEFAULT;
        this.color = EnumChatFormat.p; //By default, the color is WHITE
        this.followLookType = FollowLookType.NONE;
        this.slots = new HashMap<>();
        this.showOnTabList = false;
        this.shownOnTabList = false;
        this.npcPose = NPCPose.STANDING;
        this.customTabListName = DEFAULT_TAB_NAME; //The placeholder {uuid} will be replaced by a random id.
        npcLib.getNPCPlayerManager(player).set(code, this);
        Bukkit.getScheduler().scheduleSyncDelayedTask(npcLib.getPlugin(), ()-> {
            hideDistance = npcLib.getDefaultHideDistance();
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
     * @see     NPC#show()
     */
    public NPC create(){
        Validate.notNull(skin, "Failed to create the NPC. The NPCSkin has not been configured.");
        Validate.isTrue(entityPlayer == null, "Failed to create the NPC. This NPC has already been created before.");
        MinecraftServer server = NMSCraftServer.getMinecraftServer();
        WorldServer worldServer = NMSCraftWorld.getWorldServer(world);
        UUID uuid = UUID.randomUUID();
        GameProfile gameProfile = new GameProfile(uuid, customTabListName.replaceAll("\\{uuid\\}", uuid.toString().split("-")[1]));
        this.entityPlayer = new EntityPlayer(server, worldServer, gameProfile);
        entityPlayer.setLocation(x, y, z, yaw, pitch);
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
     * @throws  IllegalArgumentException if {@param location} is {@code null}
     * @throws  IllegalArgumentException if {@param location}'s {@link Location#getWorld()} is not the same as {@link NPC#world}
     * @throws IllegalArgumentException if {@link NPC#isCreated()} equals {@code false}
     *
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
     * @see     NPC#isCreated()
     */
    public NPC teleport(double x, double y, double z){
        return teleport(x, y, z, yaw, pitch);
    }

    /**
     * Sets the equipment of the {@link NPC} with the {@link ItemStack} at the specified {@link NPCSlot}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param slot The slot that the item will be equipped.
     * @param itemStack The itemStack that will be equipped.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code slot} equals {@code null}
     *
     * @see NPC#update()
     */
    public NPC setItem(@Nonnull NPCSlot slot, @Nullable ItemStack itemStack){
        Validate.notNull(slot, "Failed to set item, NPCSlot cannot be null");
        if(itemStack == null) itemStack = new ItemStack(Material.AIR);
        this.slots.put(slot, itemStack);
        return this;
    }

    /**
     * Clears the equipment of the {@link NPC} at the specified {@link NPCSlot}
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @param slot The slot that will be cleared.
     * @return The {@link NPC} instance.
     * @throws IllegalArgumentException if {@code slot} equals {@code null}
     *
     * @see NPC#update()
     */
    public NPC clearEquipment(@Nonnull NPCSlot slot){
        this.slots.put(slot, new ItemStack(Material.AIR));
        return this;
    }

    /**
     * Clears the equipment of the {@link NPC}
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC clearEquipment(){
        Arrays.stream(NPCSlot.values()).forEach(x-> clearEquipment(x));
        return this;
    }

    /**
     * Updates the {@link NPC#npcHologram}. If the amount of lines is different at the previous text, use {@link NPC#forceUpdateText()}
     *
     * @return  The {@link NPC} instance.
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
     *
     * @see NPCLib#removeNPC(Player, NPC)
     * @see NPCLib#removeNPC(Player, String)
     */
    public NPC destroy(){
        if(entityPlayer != null){
            if(canSee) hide();
            entityPlayer = null;
        }
        if(npcHologram != null) npcHologram.hide();
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
     * @throws IllegalArgumentException if {@code entity} equals {@code null}
     *
     * @see NPC#update()
     */
    public NPC lookAt(@Nonnull Location location){
        Validate.notNull(location, "Failed to set look direction. The location cannot be null.");
        Validate.notNull(entityPlayer, "Failed to set look direction. The NPC has not been created yet.");
        Validate.isTrue(location.getWorld().getName().equals(getWorld().getName()), "The location must be in the same world as NPC");
        Location npcLocation = new Location(world, x, y, z, yaw, pitch);
        Vector dirBetweenLocations = location.toVector().subtract(npcLocation.toVector());
        npcLocation.setDirection(dirBetweenLocations);
        this.yaw = npcLocation.getYaw();
        this.pitch = npcLocation.getPitch();
        entityPlayer.setYRot(npcLocation.getYaw());
        entityPlayer.setXRot(npcLocation.getPitch());
        return this;
    }

    /**
     * Hides or shows the text above the {@link NPC}, but without losing the text information.
     *
     * @param b boolean if the text will be hidden or not
     * @return The {@link NPC} instance.
     */
    public NPC setHideText(boolean b){
        boolean a = hiddenText;
        this.hiddenText = b;
        if(a == b) return this;
        if(npcHologram == null) return this;
        if(b) hideText();
        else showText();
        return this;
    }

    /**
     * Sets whether the {@link NPC} is collidable or not.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
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
     * @see NPC#forceUpdate()
     */
    public NPC clearSkin(){
        return setSkin((NPCSkin) null);
    }

    /**
     * Sets the {@link NPCPose} of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC setPose(NPCPose npcPose){
        if(npcPose == null) npcPose = NPCPose.STANDING;
        this.npcPose = npcPose;
        return this;
    }

    /**
     * Sets the {@link NPCPose} of the {@link NPC} as {@link NPCPose#CROUCHING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC setCrouching(boolean b){
        if(b) return setPose(NPCPose.CROUCHING);
        else if(this.npcPose.equals(NPCPose.CROUCHING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link NPCPose} of the {@link NPC} as {@link NPCPose#SWIMMING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC setSwimming(boolean b){
        if(b) return setPose(NPCPose.SWIMMING);
        else if(this.npcPose.equals(NPCPose.SWIMMING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link NPCPose} of the {@link NPC} as {@link NPCPose#SLEEPING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC setSleeping(boolean b){
        if(b) return setPose(NPCPose.SLEEPING);
        else if(this.npcPose.equals(NPCPose.SLEEPING)) return resetPose();
        return this;
    }

    /**
     * Sets the {@link NPCPose} of the {@link NPC} as {@link NPCPose#STANDING}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#update()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
     * @see NPC#update()
     */
    public NPC resetPose(){
        return setPose(NPCPose.STANDING);
    }

    /**
     * Clears the text above the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdateText()} to show it to the {@link Player}
     *
     * @return The {@link NPC} instance.
     *
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
     * @see NPC#updateText()
     * @see NPC#forceUpdateText()
     */
    public NPC setText(@Nonnull List<String> text){
        if(npcHologram == null) npcHologram = new NPCHologram(this, text);
        this.text = text;
        npcHologram.setText(text);
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
     * @see NPC#updateText()
     * @see NPC#forceUpdateText()
     */
    public NPC setText(@Nonnull String text){
        return setText(Arrays.asList(text));
    }

    /**
     * Sets the glowing color of the {@link NPC}.
     * If {@link NPC#isCreated()}, you must use {@link NPC#forceUpdate()} to show it to the {@link Player}
     *
     * @param color The glowing color.
     * @return The {@link NPC} instance.
     *
     * @see NPC#forceUpdate()
     * @see NPC#setGlowing(boolean)
     * @see NPC#setGlowing(boolean, ChatColor)
     */
    public NPC setGlowingColor(@Nullable ChatColor color){
        if(color == null) color = ChatColor.WHITE;
        Validate.isTrue(color.isColor(), "Error setting glow color. It's not a color.");
        this.color = ColorUtils.getEnumChatFormat(color);
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
     * @see NPC#forceUpdate()
     * @see NPC#setCustomTabListName(String, boolean)
     * @see NPC#setShowOnTabList(boolean)
     */
    public NPC setCustomTabListName(@Nullable String name){
        if(name == null) name = DEFAULT_TAB_NAME;
        final String finalName = getReplacedCustomName(name);
        Validate.isTrue(finalName.length() <= 16, "Error setting custom tab list name. Name must be 16 or less characters.");
        Validate.isTrue(getNpcLib().getNPCPlayerManager(player).getNPCs(world).stream().filter(x-> x.getReplacedCustomName().equals(finalName)).findAny().orElse(null) == null, "Error setting custom tab list name. There's another NPC with that name already.");
        this.customTabListName = finalName;
        return this;
    }

    /**
     * Sets the following look type of the {@link NPC}.
     *
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
     *
     */
    public NPC setHideDistance(double hideDistance) {
        this.hideDistance = hideDistance;
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
        GameProfile gameProfile = entityPlayer.getProfile();
        gameProfile.getProperties().get("textures").clear();
        gameProfile.getProperties().put("textures", new Property("textures", skin.getTexture(), skin.getSignature()));
        return this;
    }


    private NPC updatePose(){
        if(npcPose.equals(NPCPose.SLEEPING)) entityPlayer.e(new BlockPosition(x, y, z));
        entityPlayer.setPose(npcPose.getEntityPose());
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
        if(followLookType.equals(FollowLookType.PLAYER)) lookAt(player);
        else if(followLookType.equals(FollowLookType.NEAREST_PLAYER) || followLookType.equals(FollowLookType.NEAREST_ENTITY)){
            Bukkit.getScheduler().scheduleSyncDelayedTask(getNpcLib().getPlugin(), ()-> {
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
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        List<Pair<EnumItemSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
        for(NPCSlot slot : NPCSlot.values()){
            EnumItemSlot nmsSlot = slot.getNmsEnum(EnumItemSlot.class);
            if(!slots.containsKey(slot)) slots.put(slot, new ItemStack(Material.AIR));
            ItemStack item = slots.get(slot);
            net.minecraft.world.item.ItemStack craftItem = null;
            try{ craftItem = (net.minecraft.world.item.ItemStack) NMSCraftItemStack.getCraftItemStackAsNMSCopy().invoke(null, item); }
            catch (Exception e){}
            Validate.notNull(craftItem, "Error at NMSCraftItemStack");
            equipment.add(new Pair(nmsSlot, craftItem));
        }
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(entityPlayer.getId(), equipment);
        connection.sendPacket(packet);
    }

    private void updatePlayerRotation(){
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        connection.sendPacket(new PacketPlayOutEntity.PacketPlayOutEntityLook(entityPlayer.getId(), (byte) ((yaw * 256 / 360)), (byte) ((pitch * 256 / 360)), false));
        connection.sendPacket(new PacketPlayOutEntityHeadRotation(entityPlayer, (byte) (yaw * 256 / 360)));
    }

    private void updateLocation(){
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        connection.sendPacket( new PacketPlayOutEntityTeleport(entityPlayer));
    }

    private void updateScoreboard(){
        GameProfile gameProfile = entityPlayer.getProfile();
        Scoreboard scoreboard = null;
        try{ scoreboard = (Scoreboard) NMSCraftScoreboard.getCraftScoreBoardGetHandle().invoke(NMSCraftScoreboard.getCraftScoreBoardClass().cast(player.getScoreboard())); }catch (Exception e){}
        Validate.notNull(scoreboard, "Error at NMSCraftScoreboard");
        ScoreboardTeam scoreboardTeam = scoreboard.getTeam(gameProfile.getName()) == null ? new ScoreboardTeam(scoreboard, gameProfile.getName()) : scoreboard.getTeam(gameProfile.getName());
        scoreboardTeam.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.b); //EnumNameTagVisibility.NEVER
        scoreboardTeam.setColor(color);
        ScoreboardTeamBase.EnumTeamPush var1 = ScoreboardTeamBase.EnumTeamPush.b;
        if(collidable) var1 = ScoreboardTeamBase.EnumTeamPush.a;
        scoreboardTeam.setCollisionRule(var1);
        scoreboardTeam.getPlayerNameSet().add(gameProfile.getName());
        scoreboard.addPlayerToTeam(gameProfile.getName(), scoreboardTeam);
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        connection.sendPacket(PacketPlayOutScoreboardTeam.a(scoreboardTeam, true));
        connection.sendPacket(PacketPlayOutScoreboardTeam.a(scoreboardTeam, false));
    }

    private void updateMetadata(){
        try {
            DataWatcher dataWatcher = entityPlayer.getDataWatcher();
            entityPlayer.setGlowingTag(glowing);
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
            dataWatcher.set(DataWatcherRegistry.a.a(17), b);
            //
            PacketPlayOutEntityMetadata metadataPacket = new PacketPlayOutEntityMetadata(entityPlayer.getId(), dataWatcher, true);
            PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
            connection.sendPacket(metadataPacket);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPacket(){
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.a, entityPlayer)); //EnumPlayerInfoAction.ADD_PLAYER
        connection.sendPacket(new PacketPlayOutNamedEntitySpawn(entityPlayer));
        shownOnTabList = true;
        updatePlayerRotation();
        if(showOnTabList) return;
        Bukkit.getScheduler().scheduleSyncDelayedTask(getNpcLib().getPlugin(), ()-> {
            Packet packet2 = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer); //EnumPlayerInfoAction.REMOVE_PLAYER
            connection.sendPacket(packet2);
            shownOnTabList = false;
        }, 10);
    }

    private void showToPlayer(){
        if(!hiddenToPlayer) return;
        createPacket();
        hiddenToPlayer = false;
        if(text.size() > 0) updateText();
        Bukkit.getScheduler().scheduleSyncDelayedTask(getNpcLib().getPlugin(), () -> {
            update();
        }, 1);
    }

    private void hideToPlayer(){
        if(hiddenToPlayer) return;
        if(shownOnTabList){
            Packet packet2 = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.e, entityPlayer); //EnumPlayerInfoAction.REMOVE_PLAYER
            NMSCraftPlayer.sendPacket(player, packet2);
            shownOnTabList = false;
        }
        PlayerConnection connection = NMSCraftPlayer.getPlayerConnection(player);
        connection.sendPacket(NMSPacketPlayOutEntityDestroy.createPacket(entityPlayer.getId()));
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

    public Location getLocation(){
        return new Location(getWorld(), getX(), getY(), getZ(), getYaw(), getPitch());
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isCreated(){
        return entityPlayer != null;
    }

    public boolean canBeCreated(){
        return skin != null && entityPlayer == null;
    }

    public boolean canSee() {
        return canSee;
    }

    public boolean isHiddenText() {
        return hiddenText;
    }

    protected HashMap<NPCSlot, ItemStack> getEquipment(){
        return slots;
    }

    public ItemStack getEquipment(NPCSlot npcSlot){
        return slots.get(npcSlot);
    }

    public boolean isShown(){
        return canSee;
    }

    public boolean isShownOnClient() { return canSee && !hiddenToPlayer; }

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

    protected NPCHologram getNpcHologram() {
        return npcHologram;
    }

    protected EntityPlayer getEntityPlayer() {
        return entityPlayer;
    }

    public NPCLib getNpcLib() {
        return npcLib;
    }

    public String getCode() {
        return code;
    }

    public List<String> getText() {
        return text;
    }

    public NPCSkin getSkin() {
        return skin;
    }

    public boolean isCollidable() {
        return collidable;
    }

    public Double getHideDistance() {
        return hideDistance;
    }

    public ChatColor getGlowingColor() {
        return ColorUtils.getChatColor(color);
    }

    public boolean isShowOnTabList() {
        return showOnTabList;
    }

    public String getCustomTabListName() {
        return customTabListName;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public FollowLookType getFollowLookType() {
        return followLookType;
    }

    public NPCPose getPose() {
        return npcPose;
    }

    private String getReplacedCustomName(){
        return getReplacedCustomName(customTabListName, entityPlayer.getProfile().getId());
    }

    private String getReplacedCustomName(String name){
        return getReplacedCustomName(name, entityPlayer.getProfile().getId());
    }

    private String getReplacedCustomName(String name, UUID uuid){
        return name.replaceAll("\\{uuid\\}", uuid.toString().split("-")[1]);
    }

    /*
                    Enums
     */

    /**
     * Set the follow look type to the NPC with {@link NPC#setFollowLookType(FollowLookType)}
     */
    public enum FollowLookType{
        /** The NPC will not move the look direction automatically. */
        NONE,
        /** The NPC will move the look direction automatically to the player that see the NPC. */
        PLAYER,
        /** The NPC will move the look direction automatically to the nearest player to the NPC location. */
        NEAREST_PLAYER,
        /** The NPC will move the look direction automatically to the nearest entity to the NPC location. */
        NEAREST_ENTITY,
        ;
    }

    public enum NPCPose{
        STANDING(EntityPose.a),
        SLEEPING(EntityPose.c),
        SWIMMING(EntityPose.d),
        CROUCHING(EntityPose.f),
        ;

        private EntityPose entityPose;

        NPCPose(EntityPose entityPose){
            this.entityPose = entityPose;
        }

        public EntityPose getEntityPose() {
            return entityPose;
        }
    }
}
