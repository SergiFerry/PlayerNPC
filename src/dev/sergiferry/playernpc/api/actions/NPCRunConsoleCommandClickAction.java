package dev.sergiferry.playernpc.api.actions;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import org.bukkit.Bukkit;

public class NPCRunConsoleCommandClickAction extends NPCClickAction{

    private final String command;

    public NPCRunConsoleCommandClickAction(NPC npc, NPCInteractEvent.ClickType clickType, String command) {
        super(npc, ActionType.RUN_CONSOLE_COMMAND, clickType);
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
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
