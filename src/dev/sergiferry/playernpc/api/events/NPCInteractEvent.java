package dev.sergiferry.playernpc.api.events;

import dev.sergiferry.playernpc.api.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Creado por SergiFerry.
 */
public class NPCInteractEvent extends Event implements Cancellable{

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final NPC npc;
    private final ClickType clickType;
    private boolean isCancelled;

    public NPCInteractEvent(Player player, NPC npc, ClickType clickType) {
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

    public ClickType getClickType() {
        return clickType;
    }

    public boolean isRightClick(){ return clickType.equals(ClickType.RIGHT_CLICK); }

    public boolean isLeftClick(){ return clickType.equals(ClickType.LEFT_CLICK); }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean arg) {
        isCancelled = arg;
    }

    public enum ClickType{
        RIGHT_CLICK, LEFT_CLICK;

        public boolean isRightClick(){ return this.equals(ClickType.RIGHT_CLICK); }

        public boolean isLeftClick(){ return this.equals(ClickType.LEFT_CLICK); }

    }
}
