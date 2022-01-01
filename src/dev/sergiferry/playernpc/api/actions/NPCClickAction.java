package dev.sergiferry.playernpc.api.actions;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;

public abstract class NPCClickAction implements ClickActionInterface{

    private final NPC npc;
    private final ActionType actionType;
    private final NPCInteractEvent.ClickType clickType;

    protected NPCClickAction(NPC npc, ActionType actionType, NPCInteractEvent.ClickType clickType) {
        this.npc = npc;
        this.actionType = actionType;
        this.clickType = clickType;
    }

    enum ActionType{
        CUSTOM_ACTION,
        SEND_MESSAGE,
        RUN_PLAYER_COMMAND,
        RUN_CONSOLE_COMMAND,
    }

    public NPC getNPC() {
        return npc;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public NPCInteractEvent.ClickType getClickType() {
        return clickType;
    }
}

interface ClickActionInterface{
    void execute();
}
