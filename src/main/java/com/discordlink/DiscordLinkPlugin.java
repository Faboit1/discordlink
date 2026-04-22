package com.discordlink;

import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordLinkPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        LinkCommand linkCommand = new LinkCommand(this);
        var cmd = getCommand("link");
        if (cmd == null) {
            getLogger().severe("Could not register /link — is plugin.yml correct?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        cmd.setExecutor(linkCommand);
        getLogger().info("DiscordLink enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiscordLink disabled.");
    }
}
