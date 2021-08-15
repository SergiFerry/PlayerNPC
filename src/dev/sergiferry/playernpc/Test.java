package dev.sergiferry.playernpc;

import dev.sergiferry.playernpc.api.NPC;
import dev.sergiferry.playernpc.api.NPCLib;
import dev.sergiferry.playernpc.api.NPCSkin;
import dev.sergiferry.playernpc.api.NPCSlot;
import dev.sergiferry.playernpc.api.events.NPCInteractEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Creado por SergiFerry el 25/06/2021
 */
public class Test implements Listener {

    private PlayerNPCPlugin playerNPCPlugin;
    private NPCLib npcLib;

    public Test(PlayerNPCPlugin playerNPCLib){
        this.playerNPCPlugin = playerNPCLib;
        this.npcLib = NPCLib.getInstance();
        getPlayerNPCPlugin().getServer().getPluginManager().registerEvents(this, getPlayerNPCPlugin());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event){
        if(!event.isSneaking()) return;
        Player player = event.getPlayer();
        NPC npc = npcLib.getNPC(player, "caca");
        if(npc != null){
            if(npc.canSee()){
                npc.hide();
            }
            else{
                npc.show();
            }
        }
        else{
            npc = npcLib.generateNPC(player, "caca", player.getLocation());
            if(npc.canSee()){
                npc.lookAt(player.getLocation());
                npc.update();
                return;
            }
            npc.setSkin(new NPCSkin(
                    "ewogICJ0aW1lc3RhbXAiIDogMTYwOTAwNTcwNjQzNSwKICAicHJvZmlsZUlkIiA6ICI3NzI3ZDM1NjY5Zjk0MTUxODAyM2Q2MmM2ODE3NTkxOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJsaWJyYXJ5ZnJlYWsiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTRhMjA2NjQxOGJlNTBhNGQ3ZjVlZGNiN2NlN2U3MDM1MjFlMDhhZjQzMmE1ZTUwZGFjMGY2N2NjNTc1NzIwOSIKICAgIH0KICB9Cn0=",
                    "teKMX3D824hYN+/PSb7M/pLQjpto9NRJCnGIMokItxVeTc1pyNQZ5Wu/riShmkU5Aeloh/xDMhFUx+sloJ4FwfS+wYbOKpX7u+wz9xURsgozgMCzhUsQNlXXAqyT1mObfqnGH0dz9I36HH/NGSnh25A8PSxfswAXtvSGhRBxorKHygbVYMNMOj/BE+SUtQa4kEz/JCU+94g/DLaqgSApRNfWBCow4D3EEOT5z+WJ9NX5sM07GJmOXEXPd8Nn8Lm5svo5uPjdxjI8jy0Z76ntXF6ah+k1kOZ7pe5Zsp79Q1fN5gNEYYvCOxj/ECu1k+LoKyHCRiiMA1FlD0JkuLNDHcuRrH8DyTUEGkMyysyxzl7SA6zMsy3G6V79zIYay7jIQj/F2ZgOmOTXwAK9bezuELpNzqWhGIMa1DRFt23BUqpmVmiaoXRsAnNtQdmiBfB+yVttN+Gt9EjPtRhHRmNzw8+aks2HcQRb5As17CLhk3imlX4Ok+3Ud5Sfs8UG3ktNjzvddFVbyBxYOu+4m0PX+A56F85SChX53QTytfuHBusPsEZVi9H7DegrT+o+GCSus8H6jB4XXjQejMdY6ZVnLuEJED42ZhF3pcoKI7U6Iz1+DmyUAzMH0HAAOM3cSDvBd4ySatiMFRHiUVhD5u7nsg39Ea1vXuF+Xezwi/41slU="));
            npc.setItem(NPCSlot.MAINHAND, new ItemStack(Material.IRON_PICKAXE));
            npc.setText("§c§lPlayerNPC", "§7by SergiFerry");
            npc.setCollidable(true);
            npc.setGlowing(false, ChatColor.RED);
            npc.setFollowLookType(NPC.FollowLookType.PLAYER);
            npc.create();
            npc.show();
            player.sendMessage("§eHas creat el NPC");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event){
        Player player = event.getPlayer();
        NPC npc = getNpcLib().getNPC(player, "caca");
        Bukkit.getScheduler().scheduleSyncDelayedTask(getPlayerNPCPlugin(), () ->{
            if(event.getMessage().equals("a")){
                npc.setText("§c§lSergiFerry", "§7is the best", "§7developer");
                npc.setSkin(player);
                npc.forceUpdate();
            }
            else if(event.getMessage().equals("b")){
                npc.teleport(player.getLocation());
            }
            else if(event.getMessage().equals("c")){
                npc.setHideText(true);
            }
            else if(event.getMessage().equals("d")){
                npc.setHideText(false);
            }
            else if(event.getMessage().equals("e")){
                npc.setGlowing(!npc.isGlowing());
                npc.update();
            }
            else if(event.getMessage().equals("f")){
                npc.setCustomTabListName("§eEl Pepe", true);
                npc.forceUpdate();
            }
        });
    }

    @EventHandler
    public void onNPCInteract(NPCInteractEvent event){
        Player player = event.getPlayer();
        NPC npc = event.getNpc();
        NPCInteractEvent.ClickType clickType = event.getClickType();
        player.sendMessage("§e" + clickType.name() +" al NPC " + npc.getCode());
    }

    public PlayerNPCPlugin getPlayerNPCPlugin() {
        return playerNPCPlugin;
    }

    public NPCLib getNpcLib() {
        return npcLib;
    }
}
