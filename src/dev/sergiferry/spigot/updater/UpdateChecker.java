package dev.sergiferry.spigot.updater;
import dev.sergiferry.spigot.SpigotPlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private SpigotPlugin plugin;

    public UpdateChecker(SpigotPlugin plugin) {
        this.plugin = plugin;
    }

    public void getLatestVersion(Consumer<String> consumer){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->{
            try{
                InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + plugin.getSpigotResourceID()).openStream();
                Scanner scanner = new Scanner(inputStream);
                if(scanner.hasNext()) consumer.accept(scanner.next());
            }
            catch (IOException exception){
                plugin.getServer().getConsoleSender().sendMessage("§cUnable to check for updates. Please visit " + plugin.getSpigotResource());
            }
        });
    }

}
