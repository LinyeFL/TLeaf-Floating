package me.linyefl.tleaf.floating;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FloatingConfig {

    private final TLeafFloating plugin;

    private String defaultColor = "#FFFFFF";
    private double textScale = 1.0;
    private double yOffset = 0.3;          // 改：0.6 → 0.3
    private boolean shadowed = true;
    private long blinkInterval = 10;
    private double rainbowSpeed = 1.0;
    private long rainbowInterval = 2;      // 改：1 → 2
    private double rayDistance = 2.0;

    // 发光（多层光晕）+ 呼吸灯
    private List<Double> glowScales = Arrays.asList(1.05, 1.10, 1.16);
    private int glowOpacity = 150;
    private double glowWhiteMix = 0.25;
    private boolean breathingEnabled = true;
    private double breathingPeriod = 3.0;
    private int breathingMinOpacity = 60;
    private int breathingMaxOpacity = 210;
    private long breathingInterval = 3;    // 改：1 → 3

    // messages.yml 消息文案
    private String msgDisplayCreated = "§a悬浮文字已开启";
    private String msgDisplayDenied = "§c你没有权限使用悬浮功能";
    private String msgDisplayRemoved = "";
    private String msgColorSuccess = "§a书名颜色已切换";
    private String msgColorDenied = "§c只有这本书的创建者才能染色";
    private String msgBlinkOn = "§a闪烁已开启";
    private String msgBlinkOff = "§a闪烁已关闭";
    private String msgGlowOn = "§a发光已开启";
    private String msgGlowOff = "§a发光已关闭";
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
        rainbowSpeed = cfg.getDouble("rainbow-speed", rainbowSpeed);
        rainbowInterval = cfg.getLong("rainbow-interval", rainbowInterval);
        rayDistance = cfg.getDouble("ray-distance", rayDistance);

        // 光晕层缩放（过滤非法值，至少 0.5 倍）
        List<Double> gs = cfg.getDoubleList("glow-scales");
        if (!gs.isEmpty()) {
            List<Double> clean = new ArrayList<>();
            for (double d : gs) {
                if (d >= 0.5) clean.add(d);
            }
            if (!clean.isEmpty()) glowScales = clean;
        }
        glowOpacity = clamp255(cfg.getInt("glow-opacity", glowOpacity));
        glowWhiteMix = Math.max(0.0, Math.min(1.0, cfg.getDouble("glow-white-mix", glowWhiteMix)));
        breathingEnabled = cfg.getBoolean("breathing-enabled", breathingEnabled);
        breathingPeriod = Math.max(0.1, cfg.getDouble("breathing-period", breathingPeriod));
        breathingMinOpacity = clamp255(cfg.getInt("breathing-min-opacity", breathingMinOpacity));
        breathingMaxOpacity = clamp255(cfg.getInt("breathing-max-opacity", breathingMaxOpacity));
        if (breathingMaxOpacity < breathingMinOpacity) {
            int tmp = breathingMaxOpacity;
            breathingMaxOpacity = breathingMinOpacity;
            breathingMinOpacity = tmp;
        }
        breathingInterval = Math.max(1, cfg.getLong("breathing-interval", breathingInterval));

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

    private int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public String getDefaultColor() { return defaultColor; }
    public double getTextScale() { return textScale; }
    public double getYOffset() { return yOffset; }
    public boolean isShadowed() { return shadowed; }
    public long getBlinkInterval() { return blinkInterval; }
    public double getRainbowSpeed() { return rainbowSpeed; }
    public long getRainbowInterval() { return rainbowInterval; }
    public double getRayDistance() { return rayDistance; }

    public List<Double> getGlowScales() { return glowScales; }
    public int getGlowOpacity() { return glowOpacity; }
    public double getGlowWhiteMix() { return glowWhiteMix; }
    public boolean isBreathingEnabled() { return breathingEnabled; }
    public double getBreathingPeriod() { return breathingPeriod; }
    public int getBreathingMinOpacity() { return breathingMinOpacity; }
    public int getBreathingMaxOpacity() { return breathingMaxOpacity; }
    public long getBreathingInterval() { return breathingInterval; }

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