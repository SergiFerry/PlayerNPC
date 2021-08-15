package dev.sergiferry.playernpc.updater;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private Plugin plugin;
    private int resourceID;

    public UpdateChecker(Plugin plugin, int resourceID) {
        this.plugin = plugin;
        this.resourceID = resourceID;
    }

    public void getLatestVersion(Consumer<String> consumer){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->{
            try{
                InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceID).openStream();
                Scanner scanner = new Scanner(inputStream);
                if(scanner.hasNext()) consumer.accept(scanner.next());
            }
            catch (IOException exception){
                plugin.getServer().getConsoleSender().sendMessage("§cUnable to check for updates. Please visit " + ((PlayerNPCPlugin) plugin).getSpigotResource());
            }
        });
    }
}
