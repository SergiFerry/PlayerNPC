package dev.sergiferry.spigot.commands;

import dev.sergiferry.spigot.SpigotPlugin;
import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Creado por SergiFerry el 06/07/2021
 */
public abstract class CommandInstance implements CommandInterface, CommandExecutor {

    private SpigotPlugin plugin;
    private String commandLabel;

    public CommandInstance(SpigotPlugin plugin, String commandLabel) {
        this.plugin = plugin;
        this.commandLabel = commandLabel.toLowerCase();
        getCommand().setExecutor(this);
    }

    public String getCommandLabel() {
        return commandLabel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isCommand(label))
            onExecute(sender, command, label, args);
        return true;
    }

    public boolean isCommand(String s){
        s = s.toLowerCase();
        if(s.equals(commandLabel) || s.equals(getCommand().getPlugin().getName().toLowerCase() + ":" + commandLabel)) return true;
        for(String aliases : getCommand().getAliases()){
            String a = aliases.toLowerCase();
            if(s.equals(a)) return true;
            if(s.equals(getCommand().getPlugin().getName().toLowerCase() + ":" + a)) return true;
        }
        return false;
    }

    public PluginCommand getCommand() {
        return plugin.getCommand(getCommandLabel());
    }

    public SpigotPlugin getPlugin() {
        return plugin;
    }

    public CommandInstance setEmptyTabCompleter() {
        getCommand().setTabCompleter(new EmptyTabCompleter(this));
        return this;
    }

    public CommandInstance setTabCompleter(TabCompleter tabCompleter) {
        getCommand().setTabCompleter(tabCompleter);
        return this;
    }
}

interface CommandInterface {
    void onExecute(CommandSender sender, Command command, String label, String[] args);
}

class EmptyTabCompleter implements TabCompleter {

    private CommandInstance commandInstance;

    public EmptyTabCompleter(CommandInstance commandInstance) {
        this.commandInstance = commandInstance;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!commandInstance.isCommand(label)) return null;
        return new ArrayList<>();
    }

    public CommandInstance getCommandInstance() {
        return commandInstance;
    }

}
