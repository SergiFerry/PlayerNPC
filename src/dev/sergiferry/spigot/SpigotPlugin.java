package dev.sergiferry.spigot;

import dev.sergiferry.spigot.metrics.Metrics;
import dev.sergiferry.spigot.nms.NMSUtils;
import dev.sergiferry.spigot.updater.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class SpigotPlugin extends JavaPlugin implements Listener {

    private int spigotResourceID;
    private int bStatsResourceID;
    private String lastSpigotVersion;
    private Metrics metrics;
    private static String prefix;

    public SpigotPlugin(int spigotResourceID){
        this.spigotResourceID = spigotResourceID;
    }

    public abstract void enable();

    public abstract void disable();

    @Override
    public void onEnable(){
        try{
            NMSUtils.load();
            enable();
            new UpdateChecker(this).getLatestVersion(version -> {
                if(this.getDescription().getVersion().equalsIgnoreCase(version)) return;
                Integer higher = isHigherVersion(getDescription().getVersion(), version);
                if(higher != -1) return;
                this.lastSpigotVersion = version;
                getServer().getConsoleSender().sendMessage("§e" + getDescription().getName() + " version " + version + " is available (currently running " + getDescription().getVersion() + ").§7\n§ePlease download it at " + getSpigotResource());
            });
            Bukkit.getConsoleSender().sendMessage(getPrefix() + "The plugin has been enabled successfully");
            Bukkit.getConsoleSender().sendMessage(getPrefix() + "Plugin created by §6SergiFerry");
        }
        catch (Exception e){
            Bukkit.getConsoleSender().sendMessage(getPrefix() + "§cThere was an error enabling the plugin");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable(){
        try{ disable(); }
        catch (Exception e){}
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event){
        if(!event.getPlayer().isOp()) return;
        if(getLastSpigotVersion() == null) return;
        Bukkit.getScheduler().runTaskLater(this, () ->{
            event.getPlayer().sendMessage(getPrefix() + "§7" + getDescription().getName() + " version §e" + getLastSpigotVersion() + "§7 is available (currently running " + getDescription().getVersion() + "). Please download it at: §e" + getSpigotResource());
        }, 20);
    }

    public int isHigherVersion(String version1, String version2){
        String[] string1Vals = version1.split("\\.");
        String[] string2Vals = version2.split("\\.");
        int length = Math.max(string1Vals.length, string2Vals.length);
        for (int i = 0; i < length; i++) {
            Integer v1 = (i < string1Vals.length)?Integer.parseInt(string1Vals[i]):0;
            Integer v2 = (i < string2Vals.length)?Integer.parseInt(string2Vals[i]):0;
            if (v1 > v2) return 1; //Version1 bigger than version2
            else if(v1 < v2) return -1; //Version1 smaller than version2
        }
        return 0; //Both are equal
    }

    public void setupMetrics(int bStatsResourceID){
        this.bStatsResourceID = bStatsResourceID;
        this.metrics = new Metrics(this, bStatsResourceID);
    }

    public boolean hasPermission(Player player, String s){
        return player.isOp() || player.hasPermission(getDescription().getName().toLowerCase() + ".*") || (s != null ? player.hasPermission(getDescription().getName().toLowerCase() + "." + s) : false);
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static void setPrefix(String prefix) {
        SpigotPlugin.prefix = prefix;
    }

    public static String getPrefix(){ return prefix; }

    public String getSpigotResource() { return "https://www.spigotmc.org/resources/" + getDescription().getName().toLowerCase() +"."  + spigotResourceID + "/"; }

    public String getLastSpigotVersion() {
        return lastSpigotVersion;
    }

    public int getSpigotResourceID() {
        return spigotResourceID;
    }
}
