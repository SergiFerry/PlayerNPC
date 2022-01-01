package dev.sergiferry.playernpc.api.actions;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;

public class NPCMessageClickAction extends NPCClickAction{

    private final String[] messages;

    public NPCMessageClickAction(NPC npc, NPCInteractEvent.ClickType clickType, String... message) {
        super(npc, ActionType.SEND_MESSAGE, clickType);
        this.messages = message;
    }

    @Override
    public void execute() {
        getNPC().getPlayer().sendMessage(messages);
    }
}
