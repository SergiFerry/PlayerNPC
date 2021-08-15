package dev.sergiferry.playernpc.utils;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

/**
 * Creado por SergiFerry el 26/06/2021
 */
public class ClickableText {

    private TextComponent component;

    public ClickableText(String text, HoverEvent hoverEvent, ClickEvent clickEvent){
        TextComponent textComponent = new TextComponent(text);
        if(clickEvent != null) textComponent.setClickEvent(clickEvent);
        if(hoverEvent != null) textComponent.setHoverEvent(hoverEvent);
        component = textComponent;
    }

    public ClickableText(){
        this("", (HoverEvent) null, null);
    }

    public ClickableText(String text){
        this(text, (HoverEvent) null, null);
    }

    public ClickableText(String text, String hover){
        this(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), null);
    }

    public ClickableText(String text, String hover, ClickEvent clickEvent){
        this(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), clickEvent);
    }

    public ClickableText(String text, String hover, ClickEvent.Action action, String actionString){
        this(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), new ClickEvent(action, actionString));
    }

    public ClickableText(String text, ClickEvent.Action action, String actionString){
        this(text, (HoverEvent) null, new ClickEvent(action, actionString));
    }


    public ClickableText add(String text, HoverEvent hoverEvent, ClickEvent clickEvent){
        TextComponent textComponent = new TextComponent(text);
        if(clickEvent != null) textComponent.setClickEvent(clickEvent);
        if(hoverEvent != null) textComponent.setHoverEvent(hoverEvent);
        component.addExtra(textComponent);
        return this;
    }

    public ClickableText add(String text){
        return add(text, (HoverEvent) null, (ClickEvent) null);
    }

    public ClickableText add(String text, String hover){
        return add(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), null);
    }

    public ClickableText add(String text, String hover, ClickEvent clickEvent){
        return add(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), clickEvent);
    }

    public ClickableText add(String text, ClickEvent.Action action, String actionString){
        return add(text, (HoverEvent) null, new ClickEvent(action, actionString));
    }

    public ClickableText add(String text, String hover, ClickEvent.Action action, String actionString){
        return add(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)), new ClickEvent(action, actionString));
    }

    public TextComponent getTextComponent(){
        return  this.component;
    }

    public ClickableText send(Player player){
        player.spigot().sendMessage(component);
        return this;
    }


}
