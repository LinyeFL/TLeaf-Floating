package me.linyefl.tleaf.floating;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class FloatingConfig {

    private final TLeafFloating plugin;

    private String defaultColor = "#FFFFFF";
    private double textScale = 1.0;
    private double yOffset = 0.6;
    private boolean shadowed = true;
    private long blinkInterval = 1;
    private double blinkSpeed = 1.0;
    private double glowScale = 1.15;
    private String glowColor = "#FFFFFF";
    private double rainbowSpeed = 0.3;
    private long rainbowInterval = 1;
    private double rayDistance = 2.0;

    // messages.yml 消息文案
    private String msgDisplayCreated = "§a悬浮文字已开启";
    private String msgDisplayDenied = "§c你没有权限使用悬浮功能";
    private String msgDisplayRemoved = "";
    private String msgColorSuccess = "§a书名颜色已切换";
    private String msgColorDenied = "§c只有这本书的创建者才能染色";
    private String msgBlinkOn = "§a闪烁已开启";
    private String msgBlinkOff = "§a闪烁已关闭";
    private String msgGlowOn = "§a辉光已开启";
    private String msgGlowOff = "§a辉光已关闭";
    private String msgRainbowOn = "§a炫彩模式已开启";
    private String msgRainbowOff = "§a炫彩模式已关闭";
    private String msgDenied = "§c只有这本书的创建者才能操作";
    private String msgReloadSuccess = "§a配置已重载";
    private String msgReloadDenied = "§c你没有权限执行此命令";

    public FloatingConfig(TLeafFloating plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        defaultColor = cfg.getString("default-color", defaultColor);
        textScale = cfg.getDouble("text-scale", textScale);
        yOffset = cfg.getDouble("y-offset", yOffset);
        shadowed = cfg.getBoolean("shadowed", shadowed);
        blinkInterval = cfg.getLong("blink-interval", blinkInterval);
        blinkSpeed = cfg.getDouble("blink-speed", blinkSpeed);
        glowScale = cfg.getDouble("glow-scale", glowScale);
        glowColor = cfg.getString("glow-color", glowColor);
        rainbowSpeed = cfg.getDouble("rainbow-speed", rainbowSpeed);
        rainbowInterval = cfg.getLong("rainbow-interval", rainbowInterval);
        rayDistance = cfg.getDouble("ray-distance", rayDistance);

        // messages.yml：不存在才生成默认，已存在直接读取
        File msgFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!msgFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(msgFile);
        msgDisplayCreated = msg.getString("display-created", msgDisplayCreated);
        msgDisplayDenied = msg.getString("display-denied", msgDisplayDenied);
        msgDisplayRemoved = msg.getString("display-removed", msgDisplayRemoved);
        msgColorSuccess = msg.getString("color-success", msgColorSuccess);
        msgColorDenied = msg.getString("color-denied", msgColorDenied);
        msgBlinkOn = msg.getString("blink-on", msgBlinkOn);
        msgBlinkOff = msg.getString("blink-off", msgBlinkOff);
        msgGlowOn = msg.getString("glow-on", msgGlowOn);
        msgGlowOff = msg.getString("glow-off", msgGlowOff);
        msgRainbowOn = msg.getString("rainbow-on", msgRainbowOn);
        msgRainbowOff = msg.getString("rainbow-off", msgRainbowOff);
        msgDenied = msg.getString("denied", msgDenied);
        msgReloadSuccess = msg.getString("reload-success", msgReloadSuccess);
        msgReloadDenied = msg.getString("reload-denied", msgReloadDenied);
    }

    public String getDefaultColor() { return defaultColor; }
    public double getTextScale() { return textScale; }
    public double getYOffset() { return yOffset; }
    public boolean isShadowed() { return shadowed; }
    public long getBlinkInterval() { return blinkInterval; }
    public double getBlinkSpeed() { return blinkSpeed; }
    public double getGlowScale() { return glowScale; }
    public String getGlowColor() { return glowColor; }
    public double getRainbowSpeed() { return rainbowSpeed; }
    public long getRainbowInterval() { return rainbowInterval; }
    public double getRayDistance() { return rayDistance; }

    public String getMsgDisplayCreated() { return msgDisplayCreated; }
    public String getMsgDisplayDenied() { return msgDisplayDenied; }
    public String getMsgDisplayRemoved() { return msgDisplayRemoved; }
    public String getMsgColorSuccess() { return msgColorSuccess; }
    public String getMsgColorDenied() { return msgColorDenied; }
    public String getMsgBlinkOn() { return msgBlinkOn; }
    public String getMsgBlinkOff() { return msgBlinkOff; }
    public String getMsgGlowOn() { return msgGlowOn; }
    public String getMsgGlowOff() { return msgGlowOff; }
    public String getMsgRainbowOn() { return msgRainbowOn; }
    public String getMsgRainbowOff() { return msgRainbowOff; }
    public String getMsgDenied() { return msgDenied; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgReloadDenied() { return msgReloadDenied; }
}