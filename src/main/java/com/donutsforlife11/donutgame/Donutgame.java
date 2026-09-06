package com.donutsforlife11.donutgame;

import org.bukkit.plugin.java.JavaPlugin;

import com.donutsforlife11.donutgame.internal.file.FileService;

public final class Donutgame extends JavaPlugin {
    private FileService fileService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        fileService = new FileService(this);
        fileService.reload();

        getLogger().info(
            "Donutgame enabled with "
                + fileService.gameModules().size()
                + " discovered module(s) and "
                + fileService.gameMaps().size()
                + " discovered map(s)."
        );
    }

    @Override
    public void onDisable() {
        if (fileService != null) {
            fileService.closeModuleLoaders();
        }
    }

    public FileService fileService() {
        return fileService;
    }
}
