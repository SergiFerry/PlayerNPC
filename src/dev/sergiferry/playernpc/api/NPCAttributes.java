package dev.sergiferry.playernpc.api;

import dev.sergiferry.playernpc.utils.ColorUtils;
import net.minecraft.EnumChatFormat;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Creado por SergiFerry el 13/12/2021
 */
public class NPCAttributes {

    private static final NPCAttributes DEFAULT = new NPCAttributes(
            NPCSkin.getDefaultSkin(),
            new ArrayList<>(),
            new HashMap<>(),
            false,
            50.0,
            false,
            ChatColor.WHITE,
            NPC.FollowLookType.NONE,
            "§8[NPC] {uuid}",
            false,
            200L,
            0.27,
            new Vector(0, 1.75, 0),
            NPC.Pose.STANDING
    );

    protected static final Double VARIABLE_MIN_LINE_SPACING = 0.27;
    protected static final Double VARIABLE_MAX_LINE_SPACING = 1.00;
    protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_XZ = 2.00;
    protected static final Double VARIABLE_MAX_TEXT_ALIGNMENT_Y = 5.00;

    private NPCSkin skin;
    private List<String> text;
    private HashMap<NPC.Slot, ItemStack> slots;
    private boolean collidable;
    private Double hideDistance;
    private boolean glowing;
    private ChatColor color;
    private NPC.FollowLookType followLookType;
    private String customTabListName;
    private boolean showOnTabList;
    private Long interactCooldown;
    private Double lineSpacing;
    private Vector textAlignment;
    private NPC.Pose npcPose;

    private NPCAttributes(NPCSkin skin,
                          List<String> text,
                          HashMap<NPC.Slot, ItemStack> slots,
                          boolean collidable,
                          Double hideDistance,
                          boolean glowing,
                          ChatColor color,
                          NPC.FollowLookType followLookType,
                          String customTabListName,
                          boolean showOnTabList,
                          Long interactCooldown,
                          Double lineSpacing,
                          Vector textAlignment,
                          NPC.Pose npcPose
    ) {
        this.skin = skin;
        this.text = text;
        this.slots = slots;
        this.collidable = collidable;
        this.hideDistance = hideDistance;
        this.glowing = glowing;
        this.color = color;
        this.followLookType = followLookType;
        this.customTabListName = customTabListName;
        this.showOnTabList = showOnTabList;
        this.interactCooldown = interactCooldown;
        this.lineSpacing = lineSpacing;
        this.textAlignment = textAlignment;
        this.npcPose = npcPose;
        Arrays.stream(NPC.Slot.values()).forEach(x-> slots.put(x, new ItemStack(Material.AIR)));
    }

    public NPCAttributes(){
        this.collidable = DEFAULT.isCollidable();
        this.text = DEFAULT.getText();
        this.hideDistance = DEFAULT.getHideDistance();
        this.glowing = DEFAULT.isGlowing();
        this.skin = DEFAULT.getSkin();
        this.color = DEFAULT.getGlowingColor();
        this.followLookType = DEFAULT.getFollowLookType();
        this.slots = (HashMap<NPC.Slot, ItemStack>) DEFAULT.getSlots().clone();
        this.customTabListName = DEFAULT.getCustomTabListName();
        this.showOnTabList = DEFAULT.isShowOnTabList();
        this.npcPose = DEFAULT.getPose();
        this.lineSpacing = DEFAULT.getLineSpacing();
        this.textAlignment = DEFAULT.getTextAlignment().clone();
        this.interactCooldown = DEFAULT.getInteractCooldown();
    }

    public NPCAttributes(@Nonnull NPC npc){
        Validate.notNull(npc, "Cannot create NPCAttributes from a null NPC. Instead use new NPCAttributes();");
        this.collidable = npc.isCollidable();
        this.text = npc.getText();
        this.hideDistance = npc.getHideDistance();
        this.glowing = npc.isGlowing();
        this.skin = npc.getSkin();
        this.color = npc.getGlowingColor();
        this.followLookType = npc.getFollowLookType();
        this.slots = (HashMap<NPC.Slot, ItemStack>) npc.getSlots().clone();
        this.showOnTabList = npc.isShowOnTabList();
        this.npcPose = npc.getPose();
        this.lineSpacing = npc.getLineSpacing();
        this.textAlignment = npc.getTextAlignment().clone();
        this.interactCooldown = npc.getInteractCooldown();
    }

    public void applyNPC(@Nonnull NPC npc, boolean forceUpdate){
        applyNPC(npc);
        if(forceUpdate) npc.forceUpdate();
    }

    public void applyNPC(@Nonnull NPC npc){
        Validate.notNull(npc, "Cannot apply NPCAttributes to a null NPC.");
        npc.setCollidable(this.collidable);
        npc.setText(this.text);
        npc.setHideDistance(this.hideDistance);
        npc.setGlowing(this.glowing);
        npc.setGlowingColor(this.color);
        npc.setFollowLookType(this.followLookType);
        npc.setSlots((HashMap<NPC.Slot, ItemStack>) this.slots.clone());
        npc.setCustomTabListName(this.customTabListName);
        npc.setShowOnTabList(this.showOnTabList);
        npc.setPose(this.npcPose);
        npc.setLineSpacing(this.lineSpacing);
        npc.setTextAlignment(this.textAlignment.clone());
        npc.setInteractCooldown(this.interactCooldown);
    }

    public void applyNPC(@Nonnull Collection<NPC> npc){
        applyNPC(npc, false);
    }

    public void applyNPC(@Nonnull Collection<NPC> npc, boolean forceUpdate){
        Validate.notNull(npc, "Cannot apply NPCAttributes to a null NPC.");
        npc.forEach(x-> applyNPC(x, forceUpdate));
    }

    public boolean equals(@Nonnull NPC npc){
        Validate.notNull(npc, "Cannot verify equals to a null NPC.");
        return equals(npc.getAttributes());
    }

    public boolean equals(@Nonnull NPCAttributes npc){
        Validate.notNull(npc, "Cannot verify equals to a null NPCAttributes.");
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
                npc.getInteractCooldown().equals(getInteractCooldown());
    }

    public static NPCAttributes getDefault(){ return DEFAULT; }

    public static NPCAttributes getNPCAttributes(@Nonnull NPC npc){
        Validate.notNull(npc, "Cannot get NPCAttributes from a null NPC");
         return npc.getAttributes();
    }

    public NPCSkin getSkin() {
        return skin;
    }

    public static NPCSkin getDefaultSkin(){
        return DEFAULT.getSkin();
    }

    public void setSkin(@Nullable NPCSkin skin) {
        if(skin == null) skin = NPCSkin.getDefaultSkin();
        this.skin = skin;
    }

    public static void setDefaultSkin(@Nullable NPCSkin npcSkin){
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
     * @see NPCAttributes#getHideDistance() 
     * @see NPCAttributes#setDefaultHideDistance(double)
     * @see NPCAttributes#getDefaultHideDistance() 
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
     * @see NPCAttributes#getHideDistance()
     * @see NPCAttributes#setHideDistance(double)
     * @see NPCAttributes#getDefaultHideDistance()
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

    protected EnumChatFormat getEnumColor(){
        return ColorUtils.getEnumChatFormat(this.color);
    }

    public ChatColor getGlowingColor(){
        return this.color;
    }

    public static ChatColor getDefaultGlowingColor(){
        return DEFAULT.getGlowingColor();
    }

    public void setGlowingColor(@Nullable ChatColor color) {
        if(color == null) color = ChatColor.WHITE;
        Validate.isTrue(color.isColor(), "Error setting glow color. It's not a color.");
        this.color = color;
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
        if(lineSpacing < NPCAttributes.VARIABLE_MIN_LINE_SPACING) lineSpacing = NPCAttributes.VARIABLE_MIN_LINE_SPACING;
        else if(lineSpacing > NPCAttributes.VARIABLE_MAX_LINE_SPACING) lineSpacing = NPCAttributes.VARIABLE_MAX_LINE_SPACING;
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
        if(vector.getX() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getX() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setX(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        if(vector.getY() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        else if(vector.getY() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y) vector.setY(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_Y);
        if(vector.getZ() > NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
        else if(vector.getZ() < -NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ) vector.setZ(-NPCAttributes.VARIABLE_MAX_TEXT_ALIGNMENT_XZ);
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

    public static void setDefaultPose(NPC.Pose npcPose){
        DEFAULT.setPose(npcPose);
    }

}
