package dev.sergiferry.playernpc.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.sergiferry.playernpc.api.NPCSkin;
import dev.sergiferry.spigot.nms.craftbukkit.NMSCraftPlayer;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.UUID;

/**
 * Creado por SergiFerry el 27/06/2021
 */
public class SkinFetcher {

    public static String[] getSkin(String name) {
        Player player = Bukkit.getServer().getPlayer(name);
        if(Bukkit.getServer().getOnlineMode() && player != null) return getSkin(player);
        try {
            String uuid = getUUIDString(name);
            URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader2 = new InputStreamReader(url2.openStream());
            JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            String texture = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            return new String[]{texture, signature};
        } catch (Exception e) {
            return NPCSkin.getDefaultSkin().getData();
        }
    }

    public static String[] getSkin(Player player){
        try{
            EntityPlayer p = NMSCraftPlayer.getEntityPlayer(player);
            GameProfile profile = p.fp();
            Property property = profile.getProperties().get("textures").iterator().next();
            String texture = property.getValue();
            String signature = property.getSignature();
            return new String[]{texture, signature};
        }
        catch (Exception e){
            return NPCSkin.getDefaultSkin().getData();
        }
    }

    public static UUID getUUID(String name) {
        return UUID.fromString(getUUIDString(name));
    }

    private static String getUUIDString(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();
            return uuid;
        } catch (Exception e) {
            return null;
        }
    }
}
