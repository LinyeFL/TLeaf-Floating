package me.linyefl.tleaf.floating;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class TLeafFloating extends JavaPlugin {

    private FloatingConfig floatingConfig;
    private LecternManager lecternManager;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.floatingConfig = new FloatingConfig(this);
        this.lecternManager = new LecternManager(this);
        getServer().getPluginManager().registerEvents(new LecternListener(this), this);
        getCommand("tfl").setExecutor(new TLeafCommand(this));
        reloadMessages();
        getLogger().info("TLeaf-Floating 已启用");
    }

    @Override
    public void onDisable() {
        if (lecternManager != null) {
            lecternManager.removeAll();
        }
    }

    // 重载 messages.yml（首次启动自动从 jar 复制默认文件）
    public void reloadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    // 取消息文案，返回 null 表示该条留空（静默）
    public String getMessage(String key) {
        return messages != null ? messages.getString(key, null) : null;
    }

    public FloatingConfig getFloatingConfig() {
        return floatingConfig;
    }

    public LecternManager getLecternManager() {
        return lecternManager;
    }
}