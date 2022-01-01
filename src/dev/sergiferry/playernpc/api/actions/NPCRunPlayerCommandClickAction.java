package dev.sergiferry.playernpc.api.actions;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import org.bukkit.Bukkit;

public class NPCRunPlayerCommandClickAction extends NPCClickAction{

    private final String command;

    public NPCRunPlayerCommandClickAction(NPC npc, NPCInteractEvent.ClickType clickType, String command) {
        super(npc, ActionType.RUN_PLAYER_COMMAND, clickType);
        command = command.replaceAll("\\{playerName\\}", npc.getPlayer().getName());
        command = command.replaceAll("\\{playerUUID\\}", npc.getPlayer().getUniqueId().toString());
        command = command.replaceAll("\\{npcCode\\}", npc.getCode());
        command = command.replaceAll("\\{world\\}", npc.getWorld().getName());
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public void execute() {
        Bukkit.getServer().dispatchCommand(getNPC().getPlayer(), command);
    }
}
