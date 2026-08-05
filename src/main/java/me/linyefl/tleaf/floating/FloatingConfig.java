package me.linyefl.tleaf.floating;

import org.bukkit.configuration.file.FileConfiguration;

public class FloatingConfig {

    private final TLeafFloating plugin;

    private String defaultColor = "#FFFFFF";
    private double textScale = 1.0;
    private double yOffset = 0.6;
    private boolean shadowed = true;
    private long blinkInterval = 10;
    private double rayDistance = 2.0;
    private double rainbowSpeed = 1.0;
    private int rainbowInterval = 1;

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
        rayDistance = cfg.getDouble("ray-distance", rayDistance);
        rainbowSpeed = cfg.getDouble("rainbow-speed", rainbowSpeed);
        rainbowInterval = cfg.getInt("rainbow-interval", rainbowInterval);
        if (rainbowInterval < 1) rainbowInterval = 1;
    }

    public String getDefaultColor() { return defaultColor; }
    public double getTextScale() { return textScale; }
    public double getYOffset() { return yOffset; }
    public boolean isShadowed() { return shadowed; }
    public long getBlinkInterval() { return blinkInterval; }
    public double getRayDistance() { return rayDistance; }
    public double getRainbowSpeed() { return rainbowSpeed; }
    public int getRainbowInterval() { return rainbowInterval; }
}