package dev.sergiferry.playernpc.command;

import dev.sergiferry.playernpc.PlayerNPCPlugin;
import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.NPCAttributes;
import dev.sergiferry.playernpc.api.NPCLib;
import dev.sergiferry.playernpc.api.NPCSkin;
import dev.sergiferry.spigot.SpigotPlugin;
import dev.sergiferry.spigot.commands.CommandInstance;
import dev.sergiferry.playernpc.utils.ClickableText;
import dev.sergiferry.playernpc.utils.SkinFetcher;
import dev.sergiferry.playernpc.utils.MathUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Creado por SergiFerry el 26/06/2021
 */
public class NPCCommand extends CommandInstance {

    public NPCCommand(SpigotPlugin plugin) {
        super(plugin,"npc");
        setTabCompleter(new NPCCommandCompleter(this));
    }

    @Override
    public void onExecute(CommandSender sender, Command command, String label, String[] args) {
        final Player playerSender = (sender instanceof Player) ? (Player) sender : null;
        if(!sender.isOp()){
            sender.sendMessage(getPrefix() + "You don't have permission to do this.");
            return;
        }
        NPCLib npcLib = NPCLib.getInstance();
        if(args.length == 0){
            if(playerSender != null) sendHelpList(playerSender, 1);
            else error(sender);
            return;
        }
        if(args.length < 3){
            if(args[0].equals("help")){
                int asd = 1;
                if(args.length == 2 && MathUtils.isInteger(args[1])) asd = Integer.valueOf(args[1]);
                if(playerSender != null) sendHelpList(playerSender, asd);
                else error(sender);
                return;
            }
            NPCCommands commands = NPCCommands.getCommand(args[0]);
            if(commands != null) new ClickableText("§c§lError §8| §7Use §e" + commands.getCommand(), "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, commands.getCommand()).send(sender);
            else error(sender, "Incorrect command. Use §e/npc help");
            return;
        }
        Player playerTarget = getPlugin().getServer().getPlayerExact(args[1]);
        if(playerTarget == null || !playerTarget.isOnline()){
            error(sender, "This player is not online.");
            return;
        }
        String id = args[2];
        if(args[0].equals(NPCCommands.GENERATE.getArgument())){
            if(npcLib.hasNPC(playerTarget, id)){
                error(sender, "The NPC with id §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 is already generated.");
                return;
            }
            NPC npc = npcLib.generateNPC(playerTarget, id, playerSender != null ? playerSender.getLocation() : new Location(playerTarget.getWorld(), 0, 0, 0));
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been generated.");
            if(playerSender != null) sendNPCProgress(playerSender, npc);
            return;
        }
        NPC npc =  npcLib.getNPC(playerTarget, id);
        if(npc == null){
            new ClickableText("§c§lError §8| §7This NPC doesn't exist. To generate the NPC use §e" + NPCCommands.GENERATE.getCommand(), "§eClick to write the command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.GENERATE.getCommand()).send(sender);
            return;
        }
        if(args[0].equals(NPCCommands.CREATE.getArgument())){
            if(npc.isCreated()){
                error(sender,"This NPC is already created.");
                return;
            }
            if(npc.getSkin() == null){
                error(sender,"You must set skin before creating.");
                return;
            }
            npc.create();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been created.");
            new ClickableText("§7To show the NPC to the player use §e" + NPCCommands.SHOW.getCommand(npc), "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.SHOW.getCommand(npc)).send(sender);
            return;
        }
        else if(args[0].equals(NPCCommands.SHOW.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            if(npc.isShown()){
                error(sender, "This NPC is already visible.");
                return;
            }
            npc.show();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been shown.");
        }
        else if(args[0].equals(NPCCommands.HIDE.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            if(!npc.isShown()){
                error(sender, "This NPC is not visible.");
                return;
            }
            npc.hide();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been hidden.");
        }
        else if(args[0].equals(NPCCommands.UPDATE.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            npc.update();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been updated.");
        }
        else if(args[0].equals(NPCCommands.FORCEUPDATE.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            npc.forceUpdate();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been force updated.");
        }
        else if(args[0].equals(NPCCommands.UPDATETEXT.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            npc.updateText();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been updated text.");
        }
        if(args[0].equals(NPCCommands.FORCEUPDATETEXT.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            npc.forceUpdateText();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been force updated text.");
        }
        else if(args[0].equals(NPCCommands.DESTROY.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            npc.destroy();
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been destroyed.");
        }
        else if(args[0].equals(NPCCommands.REMOVE.getArgument())){
            npcLib.removeNPC(playerTarget, npc);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been removed.");
        }
        else if(args[0].equals(NPCCommands.TELEPORT.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.TELEPORT.getCommand(npc));
                return;
            }
            Location look = null;
            if(MathUtils.isInteger(args[3])){
                if(args.length < 6 || !MathUtils.isInteger(args[4]) || !MathUtils.isInteger(args[5])){
                    error(sender, "Use " + NPCCommands.TELEPORT.getCommand(npc));
                    return;
                }
                Integer x = Integer.valueOf(args[3]);
                Integer y = Integer.valueOf(args[4]);
                Integer z = Integer.valueOf(args[5]);
                look = new Location(npc.getWorld(), x, y, z);
            }
            else {
                Player lookPlayer = Bukkit.getPlayerExact(args[3]);
                if (lookPlayer == null || !lookPlayer.isOnline()) {
                    error(sender, "That player is not online.");
                    return;
                }
                if(!lookPlayer.getWorld().getName().equals(npc.getWorld().getName())){
                    error(sender, "That player is not in the same world as NPC.");
                    return;
                }
                look = lookPlayer.getLocation();
            }
            if(look == null){
                error(sender, "We can't find that location.");
                return;
            }
            npc.teleport(look);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been teleported.");
        }
        else if(args[0].equals(NPCCommands.LOOKAT.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.LOOKAT.getCommand(npc));
                return;
            }
            Location look = null;
            if(MathUtils.isInteger(args[3])){
                if(args.length < 6 || !MathUtils.isInteger(args[4]) || !MathUtils.isInteger(args[5])){
                    error(sender, "Use " + NPCCommands.LOOKAT.getCommand(npc));
                    return;
                }
                Integer x = Integer.valueOf(args[3]);
                Integer y = Integer.valueOf(args[4]);
                Integer z = Integer.valueOf(args[5]);
                look = new Location(npc.getWorld(), x, y, z);
            }
            else {
                Player lookPlayer = Bukkit.getPlayerExact(args[3]);
                if (lookPlayer == null || !lookPlayer.isOnline()) {
                    error(sender, "That player is not online.");
                    return;
                }
                if(!lookPlayer.getWorld().getName().equals(npc.getWorld().getName())){
                    error(sender, "That player is not in the same world as NPC.");
                    return;
                }
                look = lookPlayer.getLocation();
            }
            if(look == null){
                error(sender, "We can't find that location.");
                return;
            }
            npc.lookAt(look);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set look at.");
            new ClickableText("§7You need to do §e" + NPCCommands.UPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.UPDATE.getCommand(npc)).send(sender);
            return;
        }
        else if(args[0].equals(NPCCommands.SETGLOWING.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETGLOWING.getCommand(npc));
                return;
            }
            Boolean bo = Boolean.valueOf(args[3]);
            if(npc.isGlowing() == bo){
                error(sender, "The glowing attribute was §e" + bo + "§7 yet");
                return;
            }
            npc.setGlowing(bo);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set glowing as §e" + bo.toString().toLowerCase());
            if(npc.isCreated()){
                new ClickableText("§7You need to do §e" + NPCCommands.UPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.UPDATE.getCommand(npc)).send(sender);
            }
            return;
        }
        else if(args[0].equals(NPCCommands.SETPOSE.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETPOSE.getCommand(npc));
                return;
            }
            try{
                NPC.Pose npcPose = NPC.Pose.valueOf(args[3].toUpperCase());
                if(npc.getPose() == npcPose){
                    error(sender, "The pose was §e" + npcPose.name().toLowerCase() + "§7 yet");
                    return;
                }
                if(npcPose.isDeprecated()){
                    error(sender, "This pose is deprecated, only for developers.");
                    return;
                }
                npc.setPose(npcPose);
                sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set pose as §e" + npcPose.name().toLowerCase());
                if(npc.isCreated()){
                    new ClickableText("§7You need to do §e" + NPCCommands.UPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.UPDATE.getCommand(npc)).send(sender);
                }
            }
            catch (Exception e){
                error(sender, "The pose is not valid");
            }
        }
        else if(args[0].equals(NPCCommands.SETSKIN.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETSKIN.getCommand(npc));
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), ()-> {
                try{
                    sender.sendMessage(getPrefix() + "§7Trying to fetch skin of " + args[3]);
                    String[] skin = SkinFetcher.getSkin(args[3]);
                    if(skin != null){
                        npc.setSkin(new NPCSkin(skin[0], skin[1], args[3]));
                        sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set skin to " + args[3]);
                        if(npc.isCreated()){
                            new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATE.getCommand(npc)).send(sender);
                            return;
                        }
                        if(playerSender != null) sendNPCProgress(playerSender, npc);
                    }
                    else{
                        error(sender, "Incorrect player name.");
                    }
                }
                catch (Exception e){
                    error(sender, "Mojang API didn't respond.");
                }

            });
        }
        else if(args[0].equals(NPCCommands.SETTEXT.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETTEXT.getCommand(npc));
                return;
            }
            List<String> list = new ArrayList<>();
            for(int i = 3; i < args.length; i++){
                list.add(args[i].replaceAll("_", " ").replaceAll("&", "§"));
            }
            int as = npc.getText().size();
            npc.setText(list);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set Text to §e" + list.size() + "§7 lines.");
            if(npc.isCreated()){
                boolean same = as == npc.getText().size();
                new ClickableText("§7You need to do §e" + (same ? NPCCommands.UPDATETEXT.getCommand(npc) : NPCCommands.FORCEUPDATETEXT.getCommand(npc)) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, (same ? NPCCommands.UPDATETEXT.getCommand(npc) : NPCCommands.FORCEUPDATETEXT.getCommand(npc))).send(sender);
                return;
            }
            if(playerSender != null) sendNPCProgress(playerSender, npc);
        }
        else if(args[0].equals(NPCCommands.SETCUSTOMTABLISTNAME.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETCUSTOMTABLISTNAME.getCommand(npc));
                return;
            }
            try{
                npc.setCustomTabListName(args[3].replaceAll("_", " ").replaceAll("&", "§"));
            }
            catch (Exception e){
                playerTarget.sendMessage(getPrefix() + "§cThis name is not valid. Remember that cannot be larger than 16 characters, and it can't be 2 NPCs with the same custom tab list name.");
                return;
            }
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set custom tab list name to §e" + npc.getCustomTabListName());
            if(!npc.isShowOnTabList()){
                new ClickableText("§7You need to do §e" + NPCCommands.SETSHOWONTABLIST.getCommand(npc, "true") + " §7to show the custom tab list name on the tab list.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.SETSHOWONTABLIST.getCommand(npc, "true")).send(playerTarget);
            }
            else{
                if(npc.isCreated()){
                    new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATE.getCommand(npc)).send(sender);
                    return;
                }
                if(playerSender != null) sendNPCProgress(playerSender, npc);
            }
        }
        else if(args[0].equals(NPCCommands.SETCOLLIDABLE.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETCOLLIDABLE.getCommand(npc));
                return;
            }
            Boolean bo = Boolean.valueOf(args[3]);
            if(npc.isCollidable() == bo){
                error(sender, "The collidable attribute was §e" + bo + "§7 yet");
                return;
            }
            npc.setCollidable(bo);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set collidable as §e" + bo);
            if(npc.isCreated()){
                new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATE.getCommand(npc)).send(sender);
                return;
            }
            if(playerSender != null) sendNPCProgress(playerSender, npc);
        }
        else if(args[0].equals(NPCCommands.SETGLOWCOLOR.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETGLOWCOLOR.getCommand(npc));
                return;
            }
            ChatColor color = null;
            try{color = ChatColor.valueOf(args[3].toUpperCase());}catch (Exception e){}
            if(color == null){
                error(sender, "This color is not valid.");
                return;
            }
            npc.setGlowingColor(color);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set glow color as §f" + color + color.name().toLowerCase());
            if(npc.isCreated()){
                new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATE.getCommand(npc)).send(sender);
                return;
            }
            if(playerSender != null) sendNPCProgress(playerSender, npc);
        }
        else if(args[0].equals(NPCCommands.SETFOLLOWLOOKTYPE.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETFOLLOWLOOKTYPE.getCommand(npc));
                return;
            }
            NPC.FollowLookType followLookType = null;
            try{followLookType = NPC.FollowLookType.valueOf(args[3].toUpperCase());}catch (Exception e){}
            if(followLookType == null){
                error(sender, "This follow look type is not valid.");
                return;
            }
            npc.setFollowLookType(followLookType);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set follow look type as §e" + followLookType.name().toLowerCase());
            if(playerSender != null) sendNPCProgress(playerSender, npc);
        }
        else if(args[0].equals(NPCCommands.SETTEXTOPACITY.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETTEXTOPACITY.getCommand(npc));
                return;
            }
            NPC.TextOpacity textOpacity = null;
            try{textOpacity = NPC.TextOpacity.valueOf(args[3].toUpperCase());}catch (Exception e){}
            if(textOpacity == null){
                error(sender, "This text opacity type is not valid.");
                return;
            }
            npc.setTextOpacity(textOpacity);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set text opacity as §e" + textOpacity.name().toLowerCase());
            if(npc.isCreated()) new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATETEXT.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATETEXT.getCommand(npc)).send(sender);
        }
        else if(args[0].equals(NPCCommands.SETLINEOPACITY.getArgument())){
            if(args.length < 5){
                error(sender, "Use " + NPCCommands.SETLINEOPACITY.getCommand(npc));
                return;
            }
            if(!MathUtils.isInteger(args[3])){
                error(sender, "This line is not valid.");
                return;
            }
            Integer line = Integer.parseInt(args[3]);
            if(args[4].equalsIgnoreCase("reset")){
                npc.resetLineOpacity(line);
                sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been reset line opacity for the line §e" + line);
                if(npc.isCreated()) new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATETEXT.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATETEXT.getCommand(npc)).send(sender);
                return;
            }
            NPC.TextOpacity textOpacity = null;
            try{textOpacity = NPC.TextOpacity.valueOf(args[4].toUpperCase());}catch (Exception e){}
            if(textOpacity == null){
                error(sender, "This text opacity type is not valid.");
                return;
            }
            npc.setLineOpacity(line, textOpacity);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set line opacity as §e" + textOpacity.name().toLowerCase() + " §7for the line §e" + line);
            if(npc.isCreated()) new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATETEXT.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATETEXT.getCommand(npc)).send(sender);
        }
        else if(args[0].equals(NPCCommands.SETSHOWONTABLIST.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETSHOWONTABLIST.getCommand(npc));
                return;
            }
            Boolean bo = Boolean.valueOf(args[3]);
            if(npc.isShowOnTabList() == bo){
                error(sender, "The show on tab list attribute was §e" + bo + "§7 yet");
                return;
            }
            npc.setShowOnTabList(bo);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set show on tab list as §e" + bo.toString().toLowerCase());
            if(npc.isCreated()){
                new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATE.getCommand(npc)).send(sender);
                return;
            }
            sendNPCProgress(playerTarget, npc);
        }
        else if(args[0].equals(NPCCommands.SETTEXTALIGNMENT.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETTEXTALIGNMENT.getCommand(npc));
                return;
            }
            Vector look = null;
            if(MathUtils.isDouble(args[3])){
                if(args.length < 6 || !MathUtils.isDouble(args[4]) || !MathUtils.isDouble(args[5])){
                    error(sender, "Use " + NPCCommands.SETTEXTALIGNMENT.getCommand(npc));
                    return;
                }
                Double x = Double.valueOf(args[3]);
                Double y = Double.valueOf(args[4]);
                Double z = Double.valueOf(args[5]);
                look = new Vector(x, y, z);
            }
            else if(args[3].equalsIgnoreCase("reset")) look = NPCAttributes.getDefaultTextAlignment();
            if(look == null){
                error(sender, "This value is not valid.");
                return;
            }
            npc.setTextAlignment(look);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set text alignment as (§e" + look.getX() + "§7, §e" + look.getY() + "§7, §e" + look.getZ() + "§7)");
            if(npc.isCreated()) new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATETEXT.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATETEXT.getCommand(npc)).send(sender);
        }
        else if(args[0].equals(NPCCommands.SETLINESPACING.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETLINESPACING.getCommand(npc));
                return;
            }
            Double d = null;
            if(MathUtils.isDouble(args[3])) d = Double.parseDouble(args[3]);
            else if(args[3].equalsIgnoreCase("reset")) d = NPCAttributes.getDefaultLineSpacing();
            if(d == null){
                error(sender, "This value is not valid.");
                return;
            }
            npc.setLineSpacing(d);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set line spacing as §e" + npc.getLineSpacing());
            if(npc.isCreated()) new ClickableText("§7You need to do §e" + NPCCommands.FORCEUPDATETEXT.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.FORCEUPDATETEXT.getCommand(npc)).send(sender);
        }
        else if(args[0].equals(NPCCommands.SETHIDETEXT.getArgument())){
            if(!npc.isCreated()){
                errorCreated(sender, npc);
                return;
            }
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETHIDETEXT.getCommand(npc));
                return;
            }
            Boolean bo = Boolean.valueOf(args[3]);
            if(npc.isHiddenText() == bo){
                error(sender, "The hide text attribute was " + bo + " yet");
                return;
            }
            npc.setHideText(bo);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set hide text as §e" + bo);
        }
        else if(args[0].equals(NPCCommands.SETITEM.getArgument())){
            if(args.length < 4){
                error(sender, "Use " + NPCCommands.SETITEM.getCommand(npc));
                return;
            }
            ItemStack itemStack = playerTarget.getInventory().getItemInMainHand();
            NPC.Slot npcSlot = null;
            try{ npcSlot = NPC.Slot.valueOf(args[3].toUpperCase());}
            catch (Exception e){}
            if(npcSlot == null){
                error(sender, "Incorrect slot. Use one of the suggested.");
                return;
            }
            if(args.length > 4){
                Material material = null;
                try{ material = Material.valueOf(args[4]); }
                catch (Exception e){}
                if(material == null){
                    error(sender, "This material is not recognized.");
                    return;
                }
                itemStack = new ItemStack(material);
            }
            npc.setItem(NPC.Slot.valueOf(args[3].toUpperCase()), itemStack);
            sender.sendMessage(getPrefix() + "§7The NPC §a" + id + "§7 for the player §b" + playerTarget.getName() + "§7 has been set item on §e" + args[3] + "§7 as §c" + itemStack.getType().name());
            if(npc.isCreated()){
                new ClickableText("§7You need to do §e" + NPCCommands.UPDATE.getCommand(npc) + " §7to show it to the player.", "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.UPDATE.getCommand(npc)).send(sender);
                return;
            }
            if(playerSender != null) sendNPCProgress(playerSender, npc);
        }
    }

    private void error(CommandSender sender){
        error(sender, "Invalid command.");
    }

    private void error(CommandSender sender, String text){
        sender.sendMessage("§c§lError §8| §7" + text);
    }

    private void errorCreated(CommandSender sender, NPC npc){
        error(sender, "This NPC is not created yet.");
        if(sender instanceof Player) new ClickableText("§7To create it use §e" + NPCCommands.CREATE.getCommand(npc), "§eClick to write command.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.CREATE.getCommand(npc)).send(sender);
    }

    private String t(){ return "§a✔ §7"; }
    private String f(){ return "§c✘ §7"; }
    private String n(){ return "§e♦ §7"; }
    private String w(){ return "§e⚠ §c"; };
    private String i(boolean b){ return b? t() : f(); }
    private String c(){ return "§eClick to write this command."; }

    private String ts(List<String> lines){
        String s = "";
        for(String a: lines){
            s = s + "§7, " + a;
        }
        s = s.replaceFirst("§7, ", "");
        return s;
    }

    private void sendHelpList(Player player, Integer pag){
        int total = NPCCommands.values().length;
        int perpag = 4;
        int maxpag = ((total - 1) / perpag) + 1;
        if(pag > maxpag) return;
        for(int i = 0; i < 100; i++)  player.sendMessage("");
        player.sendMessage(" §c§lPlayerNPC commands §7(Page " + pag + "/" + maxpag + ")");
        player.sendMessage("");
        int aas = 0;
        for(int i = 0; i < perpag; i++) {
            if (total <= (i + ((pag - 1) * perpag))) {
                break;
            }
            int id = (i + ((pag - 1) * perpag));
            NPCCommands npcCommands = NPCCommands.values()[id];
            new ClickableText("§8• ").add((npcCommands.isImportant() ? "§6§l" : "§e") + npcCommands.getDescription(), (npcCommands.isImportant() ? "§6" : "§e") + npcCommands.getCommand() + "\n" + npcCommands.getHover() + "\n\n" + c(), new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, npcCommands.getCommand())).send(player);
            aas++;
        }
        int ass = perpag - aas;
        for(int i = 0; i < ass; i++){
            player.sendMessage("");
        }
        player.sendMessage("");
        boolean next = total > ((pag) * perpag);
        boolean previous = pag > 1;
        if(!next && !previous) return;
        BaseComponent baseComponent = new TextComponent("  ");
        if(previous){
            TextComponent componentChatMessage = new TextComponent("§c§l[PREVIOUS]");
            componentChatMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npc help " + (pag - 1)));
            componentChatMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§eClick to go to the previous page.")));
            baseComponent.addExtra(componentChatMessage);
        }
        else{
            baseComponent.addExtra("§8§l[PREVIOUS]");
        }
        baseComponent.addExtra("    ");
        if(next){
            TextComponent componentChatMessage = new TextComponent("§a§l[NEXT]");
            componentChatMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npc help " + (pag + 1)));
            componentChatMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§eClick to go to the next page.")));
            baseComponent.addExtra(componentChatMessage);
        }
        else{
            baseComponent.addExtra("§8§l[NEXT]");
        }
        player.spigot().sendMessage(ChatMessageType.CHAT, baseComponent);
        player.sendMessage("");
    }

    public enum NPCCommands{

        GENERATE(
                "generate",
                "(player) (id)",
                true,
                "Generate an NPC",

                 "\n§7It generates the NPC object with the id for the player." +
                        "\n§7This will not create the EntityPlayer or show it to player." +
                        "\n§cThis is the first step to spawn an NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID is a custom string you decide."
        ),
        SETTEXT(
                "settext",
                "(player) (id) (text)...",
                false,
                "Set the text of an NPC",

                 "\n§7This sets the text above the NPC. No need to set it." +
                        "\n§7Use \"_\" for the spaces and \" \" for a new line." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(text) §7The text will see above the NPC."
        ),
        SETSKIN(
                "setskin",
                "(player) (id) (skin)",
                false,
                "Set the skin of an NPC",

                "\n§7This sets the NPC skin. §8By default is Steve skin." +
                        "\n§7You can set both online or offline player's skin." +
                        "\n§7With the API you can set any skin texture." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(skin) §7The name of the player skin"
        ),
        SETITEM(
                "setitem",
                "(player) (id) (slot) [material]",
                false,
                "Set the equipment of an NPC",

                "\n§7This sets the equipment of NPC. §7No need to set it." +
                        "\n§cThis will use the item on your main hand." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(slot) §7The slot of the NPC will have the item." +
                        "\n§8Slots: §7helmet, chestplate, leggings, boots, mainhand, offhand" +
                        "\n§8• §a[material] §7The material of the item (if not will use your hand)."
        ),
        SETCOLLIDABLE(
                "setcollidable",
                "(player) (id) (boolean)",
                false,
                "Set the collision of an NPC",

                "\n§7This sets if the NPC will be collidable or not." +
                        "\n§7No need to set it. By default will not have collission." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(boolean) §7Value that can be true or false"
        ),
        SETGLOWCOLOR(
                "setglowcolor",
                "(player) (id) (color)",
                false,
                "Set the glow color of an NPC",

                "\n§7This sets the glow color of an NPC." +
                        "\n§7No need to set it. By default will be white." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(color) §7The name of the color."
        ),
        SETCUSTOMTABLISTNAME(
                "setcustomtablistname",
                "(player) (id) (text)",
                false,
                "Set custom tab list name of an NPC",

                "\n§7This sets the custom tab list name of an NPC." +
                        "\n§7No need to set it. By default will be §8[NPC] UUID" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(text) §7Name that will show on tab list."
        ),
        SETSHOWONTABLIST(
                "setshowontablist",
                "(player) (id) (boolean)",
                false,
                "Set show on tab list of an NPC",

                "\n§7This sets if the NPC will be shown on tab list." +
                        "\n§7No need to set it. By default will not be visible." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(boolean) §7Value that can be true or false"
        ),
        SETLINESPACING(
                "setlinespacing",
                "(player) (id) (double/reset)",
                false,
                "Set line spacing of an NPC",

                "\n§7This sets the line spacing of the Hologram of an NPC." +
                        "\n§7No need to set it. By default will be 0.27" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(double) §7Value of the line spacing for an NPC"
        ),
        SETTEXTALIGNMENT(
                "settextalignment",
                "(player) (id) (vector/reset)",
                false,
                "Set text alignment of an NPC",

                "\n§7This sets the text alignment of the Hologram of an NPC." +
                        "\n§7No need to set it. By default will be (0.0, 1.75, 0.0)" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(vector) §7The vector added to NPC location."
        ),
        SETTEXTOPACITY(
                "settextopacity",
                "(player) (id) (textopacity)",
                false,
                "Set text opacity of an NPC",

                "\n§7This sets the text opacity of the Hologram of an NPC." +
                        "\n§7No need to set it. By default will be LOWEST" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(textopacity) §7Value that can be one of the suggested." +
                        "\n§8TextOpacity: §7lowest, low, medium, hard, harder, full"
        ),
        SETLINEOPACITY(
                "setlineopacity",
                "(player) (id) (line) (textopacity)",
                false,
                "Set line opacity of an NPC",

                "\n§7This sets the text opacity of a line at the Hologram of an NPC." +
                        "\n§7No need to set it. By default will be LOWEST" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(line) §7The line number that will be affected." +
                        "\n§8• §a(textopacity) §7Value that can be one of the suggested." +
                        "\n§8TextOpacity: §7lowest, low, medium, hard, harder, full"
        ),
        SETFOLLOWLOOKTYPE(
                "setfollowlooktype",
                "(player) (id) (followlooktype)",
                false,
                "Set follow look type of an NPC",

                "\n§7This sets the NPC follow look type." +
                        "\n§7No need to set it. By default will be NONE." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(followlooktype) §7Value that can be one of the suggested" +
                        "\n§8FollowLookType: §7none, player, nearest_player, nearest_entity"
        ),
        CREATE(
                "create",
                "(player) (id)",
                true,
                "Create an NPC",

                "\n§7This will create the EntityPlayer object, but will not show it" +
                        "\n§7to the player. §cBefore doing this, you must generate and set the NPC attributes." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        SHOW(
                "show",
                "(player) (id)",
                true,
                "Show an NPC",

                "\n§7This will show the EntityPlayer to the Player." +
                        "\n§cBefore doing this you must create the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        TELEPORT(
                "teleport",
                "(player) (id) (player/location)",
                false,
                "Teleport an NPC",

                "\n§7This will teleport the NPC to your location." +
                        "\n§cBefore doing this you must create the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(player/location) §7The name of the player or location (x y z)"
        ),
        LOOKAT(
                "lookat",
                "(player) (id) (player/location)",
                false,
                "Set look at of an NPC",

                "\n§7This will change the NPC look direction." +
                        "\n§cBefore doing this you must create the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(player/location) §7The name of the player or location (x y z)"
        ),
        SETGLOWING(
                "setglowing",
                "(player) (id) (boolean)",
                false,
                "Set glowing of an NPC",

                "\n§7This sets whether if the NPC will be glowing or not." +
                        "\n§7No need to set it. By default will be false." +
                        "\n§cIf EntityPlayer is created, you must do force update." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(boolean) §7Value that can be true or false."
        ),
        SETPOSE(
                "setpose",
                "(player) (id) (npcpose)",
                false,
                "Set pose of an NPC",

                "\n§7This sets the pose of an NPC" +
                        "\n§7No need to set it. By default will be STANDING." +
                        "\n§7Poses: standing, swimming, crouching, sleeping" +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(npcpose) §7The pose of the NPC." +
                        "\n§8NPCPose: §7standing, gliding, sleeping, swimming, crouching"
        ),
        HIDE(
                "hide",
                "(player) (id)",
                true,
                "Hide an NPC",

                "\n§7This will hide the EntityPlayer from the Player." +
                        "\n§cBefore doing this you must create the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        SETHIDETEXT(
                "sethidetext",
                "(player) (id) (boolean)",
                false,
                "Hide the NPC Text",

                "\n§7This will hide or show the text above the NPC." +
                        "\n§cBefore doing this you must create the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation." +
                        "\n§8• §a(boolean) §7Value that can be true or false"
        ),
        DESTROY(
                "destroy",
                "(player) (id)",
                true,
                "Destroy an NPC",

                "\n§7This will destroy the EntityPlayer," +
                        "\n§7but it can be created after." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        REMOVE(
                "remove",
                "(player) (id)",
                true,
                "Remove an NPC",

                "\n§7This will destroy the NPC object." +
                        "\n§cAll the NPC info will be removed." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        UPDATE(
                "update",
                "(player) (id)",
                true,
                "Update an NPC",

                "\n§7This will update the client of the player." +
                        "\n§cSome changes will need this to be visible." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        FORCEUPDATE(
                "forceupdate",
                "(player) (id)",
                true,
                "Force update an NPC",

                "\n§7This will update the client of the player." +
                        "\n§cSome changes will need this to be visible." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        UPDATETEXT(
                "updatetext",
                "(player) (id)",
                true,
                "Update the NPC Text",

                "\n§7This will update the text above the NPC." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        FORCEUPDATETEXT(
                "forceupdatetext",
                "(player) (id)",
                true,
                "Force update the NPC Text",

                "\n§7This will update the text above the NPC." +
                        "\n§cIf the text have different amount of lines you must do this." +
                        "\n" +
                        "\n§7Variables:" +
                        "\n§8• §a(player) §7The name of the player that will see the NPC" +
                        "\n§8• §a(id) §7The ID of the NPC you decided on generation."
        ),
        ;

        private String argument;
        private String arguments;
        private boolean important;
        private String description;
        private String hover;

        NPCCommands(String argument, String arguments, boolean important, String description, String hover){
            this.argument = argument;
            this.arguments = arguments;
            this.important = important;
            this.description = description;
            this.hover = hover;
        }

        public String getArgument() {
            return argument;
        }

        public String getArguments() {
            return arguments;
        }

        public boolean isImportant() {
            return important;
        }

        public String getDescription() {
            return description;
        }

        public String getHover() {
            return hover;
        }

        public String getCommand(){
            return "/npc " + argument + " " + arguments;
        }

        public String getCommand(NPC npc){
            String args = arguments;
            args = args.replaceAll("\\(player\\)", npc.getPlayer().getName()).replaceAll("\\(id\\)", npc.getCode());
            return "/npc " + argument + " " + args;
        }

        public String getCommand(NPC npc, String arguments){
            return "/npc " + argument + " " + npc.getPlayer().getName() + " " + npc.getCode() + " " + arguments;
        }

        public static NPCCommands getCommand(String argument){
            return Arrays.stream(NPCCommands.values()).filter(x-> x.getArgument().equalsIgnoreCase(argument)).findAny().orElse(null);
        }
    }

    private void sendNPCProgress(Player sender, NPC npc){
        Player player = npc.getPlayer();
        String id = npc.getCode();
        if(npc == null) return;
        if(npc.isCreated()) return;
        String equip = "§cNone";
        HashMap<NPC.Slot, ItemStack> equipment = new HashMap<>();
        Arrays.stream(NPC.Slot.values()).filter(x-> npc.getEquipment(x) != null && !npc.getEquipment(x).getType().equals(Material.AIR)).forEach(x-> equipment.put(x, npc.getEquipment(x)));
        if(!equipment.isEmpty()){
            equip = "";
            for(NPC.Slot slot : equipment.keySet()){
                equip = equip + "\n    §8• §7" + slot.name().substring(0, 1).toUpperCase() + slot.name().substring(1).toLowerCase() + ": §e" + equipment.get(slot).getType().name();
            }
        }
        ChatColor color = npc.getGlowingColor();
        String colorName = color.name();
        new ClickableText("§eHover to see " + player.getName() + "'s " + id + " NPC creation progress.",
                "§e" + player.getName() + "'s " + id + " NPC creation progress:" +
                        "\n" +
                        "\n" + i(npc.getLocation() != null) + "Location: §e" + npc.getWorld().getName() + ", x:" + MathUtils.getFormat(npc.getX()) + ", y:" + MathUtils.getFormat(npc.getY()) + ", z:" + MathUtils.getFormat(npc.getZ()) +
                        "\n" + n() + "Text: " + (npc.getText().isEmpty() ? "§cNone" : ts(npc.getText())) +
                        "\n" + i(npc.getSkin() != null) + "Skin: " + (npc.getSkin() != null ?  "§a" + (npc.getSkin().getPlayerName() != null ? npc.getSkin().getPlayerName() : "Setted") : "§cNone") +
                        "\n" + n() + "Items: " + equip +
                        "\n" + n() + "Glow color: §f" + color + colorName.substring(0,1) + colorName.substring(1) +
                        "\n" + t() + "Collision: " + (npc.isCollidable() ? "§aTrue" : "§cFalse") +
                        "\n" +
                        "\n" + (npc.canBeCreated() ? "§aYou can create it with " + NPCCommands.CREATE.getCommand(npc) : "§cYou still can't create the NPC.")
        ).send(player);
        if(!npc.canBeCreated()){
            String remain = "§cAttributes you must set before creating the NPC\n§8• §7Skin: §e" + NPCCommands.SETSKIN.getCommand(npc);
            new ClickableText(f() + "§7You must set some attributes before you can create it.", remain).send(sender);
            return;
        }
        new ClickableText(t() + "§aClick here to create the NPC " + id + " for " + player.getName(), "§eClick to create it.", ClickEvent.Action.SUGGEST_COMMAND, NPCCommands.CREATE.getCommand(npc)).send(sender);
    }

    public String getPrefix() { return PlayerNPCPlugin.getPrefix(); }
}
class NPCCommandCompleter implements TabCompleter {
    private CommandInstance commandInstance;

    public NPCCommandCompleter(CommandInstance commandInstance){
        this.commandInstance = commandInstance;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if(!label.equalsIgnoreCase(getCommandInstance().getCommandLabel())) return null;
        List<String> strings = new ArrayList<>();
        final Player playerSender = (sender instanceof Player) ? (Player) sender : null;
        if(!sender.isOp()) return strings;
        if(args.length == 1){
            Arrays.asList(NPCCommand.NPCCommands.values()).stream().filter(x-> x.getArgument().startsWith(args[0])).forEach(x-> strings.add(x.getArgument()));
            if("help".startsWith(args[0]) || strings.isEmpty()) strings.add("help");
            return strings;
        }
        if(args.length == 2){
            if(args[0].equalsIgnoreCase("help")){
                int maxpag = ((NPCCommand.NPCCommands.values().length - 1) / 4) + 1;
                for(int i = 1; i <= maxpag; i++) strings.add("" + i);
                return strings;
            }
            Bukkit.getOnlinePlayers().stream().filter(x-> x.getName().toLowerCase().startsWith(args[1].toLowerCase())).forEach(x-> strings.add(x.getName()));
            return strings;
        }
        Player to = getCommandInstance().getPlugin().getServer().getPlayer(args[1]);
        if(to == null) return strings;
        if(args.length == 3) {
            if(args[0].equalsIgnoreCase(NPCCommand.NPCCommands.GENERATE.getArgument())) return strings;
            NPCLib.getInstance().getAllNPCs(to).stream().filter(x-> x.getCode().startsWith(args[2])).forEach(x-> strings.add(x.getCode()));
            return strings;
        }
        if(args.length >= 4){
            NPC npc = NPCLib.getInstance().getNPC(to, args[2]);
            if(npc == null) return strings;
            String arg0 = args[0];
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETHIDETEXT.getArgument()) || arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETCOLLIDABLE.getArgument()) || arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETGLOWING.getArgument()) || arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETSHOWONTABLIST.getArgument())){
                if(args.length == 4){
                    Set.of("true", "false").stream().filter(x-> x.toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x));
                    return strings;
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETGLOWCOLOR.getArgument())){
                if(args.length == 4){
                    Arrays.stream(ChatColor.values()).filter(x-> x.isColor() && x.name().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                    return strings;
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETSKIN.getArgument())){
                if(args.length == 4){
                    Bukkit.getOnlinePlayers().stream().filter(x-> x.getName().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.getName()));
                    return strings;
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETFOLLOWLOOKTYPE.getArgument())){
                if(args.length == 4){
                    Arrays.stream(NPC.FollowLookType.values()).filter(x-> x.name().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETTEXTOPACITY.getArgument())){
                if(args.length == 4){
                    Arrays.stream(NPC.TextOpacity.values()).filter(x-> x.name().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETLINEOPACITY.getArgument())){
                if(args.length == 4){
                    List<Integer> lines = new ArrayList<>();
                    for(int i = 1; i <= npc.getText().size(); i++) lines.add(i);
                    lines.stream().filter(x-> x.toString().startsWith(args[3])).forEach(x-> strings.add(x.toString()));
                }
                if(args.length == 5){
                    Arrays.stream(NPC.TextOpacity.values()).filter(x-> x.name().toLowerCase().startsWith(args[4].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                    if("reset".startsWith(args[4].toLowerCase())) strings.add("reset");
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETPOSE.getArgument())){
                if(args.length == 4){
                    Arrays.stream(NPC.Pose.values()).filter(x-> !x.isDeprecated() && x.name().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.LOOKAT.getArgument()) || arg0.equalsIgnoreCase(NPCCommand.NPCCommands.TELEPORT.getArgument())){
                if(args.length == 4){
                    npc.getWorld().getPlayers().stream().filter(x-> x.getName().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.getName()));
                    String x = playerSender != null ? "" + playerSender.getLocation().getBlockX() : "0";
                    if(x.startsWith(args[3])) strings.add(x);
                    return strings;
                }
                if(args.length == 5 && MathUtils.isInteger(args[3])){
                    String x = playerSender != null ? "" + playerSender.getLocation().getBlockY() : "0";
                    if(x.startsWith(args[4])) strings.add(x);
                }
                if(args.length == 6 && MathUtils.isInteger(args[3]) && MathUtils.isInteger(args[4])){
                    String x = playerSender != null ? "" + playerSender.getLocation().getBlockZ() : "0";
                    if(x.startsWith(args[5])) strings.add(x);
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETTEXTALIGNMENT.getArgument())){
                if(args.length == 4){
                    if("reset".startsWith(args[3])) strings.add("reset");
                    if(Double.valueOf(npc.getTextAlignment().getX()).toString().startsWith(args[3])) strings.add("" + npc.getTextAlignment().getX());
                    return strings;
                }
                if(args.length == 5 && MathUtils.isDouble(args[3])){
                    if(Double.valueOf(npc.getTextAlignment().getY()).toString().startsWith(args[4])) strings.add("" + npc.getTextAlignment().getY());
                }
                if(args.length == 6 && MathUtils.isDouble(args[3]) && MathUtils.isDouble(args[4])){
                    if(Double.valueOf(npc.getTextAlignment().getZ()).toString().startsWith(args[5])) strings.add("" + npc.getTextAlignment().getZ());
                }
            }
            if(arg0.equalsIgnoreCase(NPCCommand.NPCCommands.SETITEM.getArgument())){
                if(args.length == 4){
                    Arrays.stream(NPC.Slot.values()).filter(x-> x.name().toLowerCase().startsWith(args[3].toLowerCase())).forEach(x-> strings.add(x.name().toLowerCase()));
                    return strings;
                }
            }
        }
        return strings;
    }

    public CommandInstance getCommandInstance() {
        return commandInstance;
    }
}
