package dev.sergiferry.playernpc.command;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Creado por SergiFerry el 26/06/2021
 */
public abstract class CommandInstance implements CommandInterface, CommandExecutor {

    private Plugin plugin;
    private String commandLabel;

    public CommandInstance(Plugin plugin, String commandLabel) {
        this.plugin = plugin;
        this.commandLabel = commandLabel;
        getCommand().setExecutor(this);
    }

    public String getCommandLabel() {
        return commandLabel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase(commandLabel))
            onExecute(sender, command, label, args);
        return true;
    }

    public PluginCommand getCommand() {
        return plugin.getServer().getPluginCommand(getCommandLabel());
    }

    public Plugin getPlugin() {
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
        if (!label.equalsIgnoreCase(getCommandInstance().getCommandLabel())) return null;
        return new ArrayList<>();
    }

    public CommandInstance getCommandInstance() {
        return commandInstance;
    }

}