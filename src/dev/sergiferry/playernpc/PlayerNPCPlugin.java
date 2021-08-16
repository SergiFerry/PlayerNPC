package dev.sergiferry.playernpc;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.NPCLib;
import dev.sergiferry.playernpc.command.NPCCommand;
import dev.sergiferry.playernpc.metrics.Metrics;
import dev.sergiferry.playernpc.nms.NMSUtils;
import dev.sergiferry.playernpc.updater.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class PlayerNPCPlugin extends JavaPlugin{

    private static final String PREFIX = "§6§lPlayer NPC §8| §7";

    private NPCLib npcLib;
    private Metrics metrics;
    private String lastSpigotVersion;
    private int spigotResourceID;
    private int bStatsResourceID;

    public void onEnable() {
        NMSUtils.load();
        this.npcLib = new NPCLib(this);
        this.spigotResourceID = 93625;
        this.bStatsResourceID = 11918;
        this.metrics = new Metrics(this, bStatsResourceID);
        metrics.addCustomChart(new Metrics.SingleLineChart("npcs", () -> {
            Set<NPC> npcSet = new HashSet<>();
            getServer().getOnlinePlayers().forEach(x-> npcSet.addAll(npcLib.getAllNPCs(x)));
            return npcSet.size();
        }));
        new NPCCommand(this);
        npcLib.onEnable(this);
        new UpdateChecker(this, spigotResourceID).getLatestVersion(version -> {
            if(this.getDescription().getVersion().equalsIgnoreCase(version)) return;
            this.lastSpigotVersion = version;
            getServer().getConsoleSender().sendMessage("§e" + getDescription().getName() + " version " + version + " is available (currently running " + getDescription().getVersion() + ").§7\n§ePlease download it at " + getSpigotResource());
        });
    }

    public void onDisable(){
        npcLib.onDisable(this);
    }

    public static String getPrefix(){
        return PREFIX;
    }

    public String getSpigotResource() { return "https://www.spigotmc.org/resources/" + getDescription().getName().toLowerCase() +"."  + spigotResourceID + "/"; }

    public String getLastSpigotVersion() {
        return lastSpigotVersion;
    }

    public NPCLib getNPCLib() {
        return npcLib;
    }

}
