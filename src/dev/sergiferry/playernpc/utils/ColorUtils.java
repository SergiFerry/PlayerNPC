package dev.sergiferry.playernpc.utils;

import net.minecraft.EnumChatFormat;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Creado por SergiFerry el 12/07/2021
 */
public class ColorUtils {

    private static Map<ChatColor, EnumChatFormat> colorEnumChatFormatMap;

    public static void load(){
        colorEnumChatFormatMap = new HashMap<>();
        colorEnumChatFormatMap.put(ChatColor.BLACK, EnumChatFormat.a);
        colorEnumChatFormatMap.put(ChatColor.DARK_BLUE, EnumChatFormat.b);
        colorEnumChatFormatMap.put(ChatColor.DARK_GREEN, EnumChatFormat.c);
        colorEnumChatFormatMap.put(ChatColor.DARK_AQUA, EnumChatFormat.d);
        colorEnumChatFormatMap.put(ChatColor.DARK_RED, EnumChatFormat.e);
        colorEnumChatFormatMap.put(ChatColor.DARK_PURPLE, EnumChatFormat.f);
        colorEnumChatFormatMap.put(ChatColor.GOLD, EnumChatFormat.g);
        colorEnumChatFormatMap.put(ChatColor.GRAY, EnumChatFormat.h);
        colorEnumChatFormatMap.put(ChatColor.DARK_GRAY, EnumChatFormat.i);
        colorEnumChatFormatMap.put(ChatColor.BLUE, EnumChatFormat.j);
        colorEnumChatFormatMap.put(ChatColor.GREEN, EnumChatFormat.k);
        colorEnumChatFormatMap.put(ChatColor.AQUA, EnumChatFormat.l);
        colorEnumChatFormatMap.put(ChatColor.RED, EnumChatFormat.m);
        colorEnumChatFormatMap.put(ChatColor.LIGHT_PURPLE, EnumChatFormat.n);
        colorEnumChatFormatMap.put(ChatColor.YELLOW, EnumChatFormat.o);
        colorEnumChatFormatMap.put(ChatColor.WHITE, EnumChatFormat.p);
    }

    public static EnumChatFormat getEnumChatFormat(ChatColor color){
        if(colorEnumChatFormatMap == null) load();
        if(colorEnumChatFormatMap.containsKey(color)) return colorEnumChatFormatMap.get(color);
        else return null;
    }

    public static ChatColor getChatColor(EnumChatFormat format){
        if(colorEnumChatFormatMap == null) load();
        if(colorEnumChatFormatMap.containsValue(format)) return colorEnumChatFormatMap.keySet().stream().filter(x-> colorEnumChatFormatMap.get(x).equals(format)).findAny().orElse(null);
        else return null;
    }

}
