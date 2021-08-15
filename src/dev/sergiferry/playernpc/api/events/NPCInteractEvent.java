package dev.sergiferry.playernpc.api.events;

import dev.sergiferry.playernpc.api.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;

/**
 * Creado por SergiFerry.
 */
public class NPCInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final NPC npc;
    private boolean isCancelled;
    private ClickType clickType;

    public NPCInteractEvent(Player player, NPC npc, ClickType clickType) {
        this.player = player;
        this.npc = npc;
        this.clickType = clickType;
        Bukkit.getPluginManager().callEvent(this);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public NPC getNpc() {
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

    public ClickType getClickType() {
        return clickType;
    }

    public boolean isRightClick(){ return clickType.equals(ClickType.RIGHT_CLICK); }

    public boolean isLeftClick(){ return clickType.equals(ClickType.LEFT_CLICK); }

    public enum ClickType{
        RIGHT_CLICK, LEFT_CLICK;

        public static ClickType getAction(Action action){
            if(action.equals(Action.LEFT_CLICK_AIR) || action.equals(Action.LEFT_CLICK_BLOCK)) return ClickType.LEFT_CLICK;
            if(action.equals(Action.RIGHT_CLICK_AIR) || action.equals(Action.RIGHT_CLICK_BLOCK)) return ClickType.RIGHT_CLICK;
            return null;
        }
    }
}
