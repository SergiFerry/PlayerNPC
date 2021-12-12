package dev.sergiferry.spigot.server;

import java.util.Arrays;

public enum ServerVersion {

    VERSION_1_18("1.18-R0.1-SNAPSHOT"),
    VERSION_1_18_1("1.18.1-R0.1-SNAPSHOT"),
    ;

    private String bukkitVersion;

    ServerVersion(String bukkitVersion){
        this.bukkitVersion = bukkitVersion;
    }

    @Override
    public String toString(){
        return this.getBukkitVersion();
    }

    public String getBukkitVersion() {
        return bukkitVersion;
    }

    public String getMinecraftVersion() { return bukkitVersion.split("-")[0]; }

    public static ServerVersion getVersion(String version){
        return Arrays.stream(ServerVersion.values()).filter(x-> x.getBukkitVersion().equals(version)).findAny().orElse(null);
    }

}
