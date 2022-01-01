package dev.sergiferry.playernpc.api.actions;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;

public class NPCCustomClickAction extends NPCClickAction{

    private final CustomAction customAction;

    public NPCCustomClickAction(NPC npc, NPCInteractEvent.ClickType clickType, CustomAction customAction) {
        super(npc, ActionType.CUSTOM_ACTION, clickType);
        this.customAction = customAction;
    }

    @Override
    public void execute() {
        customAction.execute();
    }
}

