package dev.sergiferry.playernpc.nms.craftbukkit;

import dev.sergiferry.playernpc.nms.NMSUtils;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Creado por SergiFerry el 06/07/2021
 */
public class NMSCraftItemStack {

    private static Class<?> craftItemStackClass;
    private static Method craftItemStackAsNMSCopy;

    public static void load() throws ClassNotFoundException, NoSuchMethodException {
        craftItemStackClass = NMSUtils.getCraftBukkitClass("inventory.CraftItemStack");
        craftItemStackAsNMSCopy = craftItemStackClass.getDeclaredMethod("asNMSCopy", ItemStack.class);
    }

    public static net.minecraft.world.item.ItemStack asNMSCopy(ItemStack itemStack) {
        try { return (net.minecraft.world.item.ItemStack) getCraftItemStackAsNMSCopy().invoke(null, itemStack); }
        catch (Exception e) { return null; }
    }

    public static Class<?> getCraftItemStackClass() {
        return craftItemStackClass;
    }

    public static Method getCraftItemStackAsNMSCopy() {
        return craftItemStackAsNMSCopy;
    }
}
