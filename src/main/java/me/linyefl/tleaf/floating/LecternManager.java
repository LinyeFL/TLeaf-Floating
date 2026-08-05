package me.linyefl.tleaf.floating;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LecternManager {

    private final TLeafFloating plugin;
    private final Map<Block, Entry> displays = new ConcurrentHashMap<>();

    public LecternManager(TLeafFloating plugin) {
        this.plugin = plugin;
    }

    private static class Entry {
        TextDisplay display;      // 主层：变色/闪烁/炫彩作用于此
        TextDisplay glowDisplay;  // 辉光层：放大纯色字，荧光墨囊开关
        boolean glowOn;           // 辉光当前是否显示
        String color;
        String title;
        UUID ownerUuid;   // 创建者（放置书籍的玩家），只有他能操作
        int blinkTask = -1;
        double blinkPhase;        // 呼吸灯相位
        int rainbowTask = -1;
        float rainbowHue;
    }

    // 取显示文字：优先自定义名（改名控制），其次成书标题；未改名的书与笔返回 null
    public String getTitle(ItemStack book) {
        if (book == null) return null;
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;
        if (meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        if (book.getType() == Material.WRITTEN_BOOK && meta instanceof BookMeta) {
            return ((BookMeta) meta).getTitle();
        }
        return null;
    }

    // 有书则创建/更新显示，没书则移除
    public void refresh(Block lectern, ItemStack book, UUID playerUuid) {
        String title = getTitle(book);
        if (title == null) {
            remove(lectern);
            return;
        }
        Entry entry = displays.get(lectern);
        if (entry == null) {
            displays.put(lectern, create(lectern, title, playerUuid));
            return;
        }
        // 已有显示：书名变了才刷新文字和染色权，翻页不影响显示
        if (!entry.title.equals(title)) {
            entry.title = title;
            entry.ownerUuid = playerUuid;
            entry.glowDisplay.text(Component.text(title).color(TextColor.fromHexString(plugin.getFloatingConfig().getGlowColor())));
            if (entry.blinkTask == -1 && entry.rainbowTask == -1) {
                applyColor(entry, entry.color);
            }
        }
    }

    // 移除某个讲台的显示
    public void remove(Block lectern) {
        Entry entry = displays.remove(lectern);
        if (entry != null) {
            stopBlink(entry);
            stopRainbow(entry);
            entry.glowDisplay.remove();
            entry.display.remove();
        }
    }

    // 停服清理
    public void removeAll() {
        for (Entry entry : displays.values()) {
            stopBlink(entry);
            stopRainbow(entry);
            entry.glowDisplay.remove();
            entry.display.remove();
        }
        displays.clear();
    }

    // 扔染料：稳定变色不闪（自动停闪烁和炫彩）。只有创建者能改色，成功返回 true
    public boolean setColor(Block lectern, String hexColor, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null) return false;
        if (!entry.ownerUuid.equals(playerUuid)) return false;
        entry.color = hexColor;
        stopBlink(entry);
        stopRainbow(entry);
        applyColor(entry, entry.color);
        return true;
    }

    // 扔骨粉：闪烁开关（呼吸灯）。返回 -1 拒绝 / 0 关闭 / 1 开启
    public int toggleBlink(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null || !entry.ownerUuid.equals(playerUuid)) return -1;
        if (entry.blinkTask != -1) {
            stopBlink(entry);
            if (entry.rainbowTask == -1) applyColor(entry, entry.color);
            return 0;
        }
        stopRainbow(entry);
        startBlink(entry);
        return 1;
    }

    // 扔荧光墨囊：辉光开关。返回 -1 拒绝 / 0 关闭 / 1 开启
    public int toggleGlow(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null || !entry.ownerUuid.equals(playerUuid)) return -1;
        entry.glowOn = !entry.glowOn;
        setGlowVisible(entry, entry.glowOn);
        return entry.glowOn ? 1 : 0;
    }

    // 扔钻石：炫彩开关。返回 -1 拒绝 / 0 关闭 / 1 开启
    public int toggleRainbow(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null || !entry.ownerUuid.equals(playerUuid)) return -1;
        if (entry.rainbowTask != -1) {
            stopRainbow(entry);
            applyColor(entry, entry.color);
            return 0;
        }
        stopBlink(entry);
        startRainbow(entry);
        return 1;
    }

    public boolean hasDisplay(Block lectern) {
        return displays.containsKey(lectern);
    }

    private Entry create(Block lectern, String title, UUID ownerUuid) {
        FloatingConfig cfg = plugin.getFloatingConfig();
        World world = lectern.getWorld();
        Location loc = lectern.getLocation().add(0.5, 1.0 + cfg.getYOffset(), 0.5);

        double scale = cfg.getTextScale();

        // 辉光层：放大的纯色字，垫在主层后面形成外圈光晕。默认隐藏（缩放 0），荧光墨囊打开
        TextDisplay glowDisplay = world.spawn(loc, TextDisplay.class);
        glowDisplay.setBillboard(Display.Billboard.CENTER);
        glowDisplay.setDefaultBackground(false);
        glowDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        glowDisplay.setSeeThrough(false);
        glowDisplay.setShadowed(false);
        glowDisplay.setTextOpacity((byte) 250);
        glowDisplay.setLineWidth(2000);
        glowDisplay.text(Component.text(title).color(TextColor.fromHexString(cfg.getGlowColor())));
        // z 方向 -0.1：把辉光层往远离玩家的方向推一丁点，保证它垫在主层后面
        // 实测若出现光晕盖住主字（渲染顺序反了），把 -0.1 改成 +0.1 即可
        glowDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, -0.1f),
                new Quaternionf(),
                new Vector3f(0f, 0f, 0f),   // 缩放 0 = 初始隐藏（Display 无 setVisible API）
                new Quaternionf()
        ));

        // 主层：正常大小，所有颜色效果作用于此
        TextDisplay display = world.spawn(loc, TextDisplay.class);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setSeeThrough(false);
        display.setShadowed(cfg.isShadowed());
        display.setTextOpacity((byte) 250);
        display.setLineWidth(2000);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f((float) scale, (float) scale, (float) scale),
                new Quaternionf()
        ));

        Entry entry = new Entry();
        entry.display = display;
        entry.glowDisplay = glowDisplay;
        entry.glowOn = false;
        entry.title = title;
        entry.ownerUuid = ownerUuid;
        entry.color = cfg.getDefaultColor();
        applyColor(entry, entry.color);
        return entry;
    }

    // 辉光层显示/隐藏：Display 无 setVisible API，缩放设为 0 即隐藏，恢复正常倍率即显示
    private void setGlowVisible(Entry entry, boolean on) {
        FloatingConfig cfg = plugin.getFloatingConfig();
        float s = on ? (float) (cfg.getTextScale() * cfg.getGlowScale()) : 0f;
        entry.glowDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, -0.1f),
                new Quaternionf(),
                new Vector3f(s, s, s),
                new Quaternionf()
        ));
    }

    private void applyColor(Entry entry, String hex) {
        entry.display.text(Component.text(entry.title).color(TextColor.fromHexString(hex)));
    }

    // 呼吸灯：主色 ↔ 浅化版主色 正弦过渡，不跳变
    private void startBlink(Entry entry) {
        stopBlink(entry);
        FloatingConfig cfg = plugin.getFloatingConfig();
        long interval = Math.max(1L, cfg.getBlinkInterval());
        entry.blinkPhase = 0.0;
        entry.blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double step = cfg.getBlinkSpeed() * interval / 20.0 * Math.PI * 2;
            entry.blinkPhase = (entry.blinkPhase + step) % (Math.PI * 2);
            double factor = 0.5 + 0.5 * Math.sin(entry.blinkPhase); // 0~1 正弦
            applyColor(entry, blendColor(entry.color, factor));
        }, interval, interval).getTaskId();
    }

    private void stopBlink(Entry entry) {
        if (entry.blinkTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.blinkTask);
            entry.blinkTask = -1;
        }
        entry.blinkPhase = 0.0;
    }

    // 主色向"浅化版"插值：factor=0 是主色，factor=1 是浅色（主色向白 60% 混合）
    private String blendColor(String hex, double factor) {
        int[] rgb = hexToRgb(hex);
        int r = (int) Math.round(rgb[0] + (255 - rgb[0]) * 0.6 * factor);
        int g = (int) Math.round(rgb[1] + (255 - rgb[1]) * 0.6 * factor);
        int b = (int) Math.round(rgb[2] + (255 - rgb[2]) * 0.6 * factor);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private int[] hexToRgb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int v = Integer.parseInt(h, 16);
        return new int[] { (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF };
    }

    // 炫彩：HSV 色相连续递增（hue 每 tick 加 speed*interval/20，到 1 转回 0，正好一圈）
    private void startRainbow(Entry entry) {
        stopRainbow(entry);
        FloatingConfig cfg = plugin.getFloatingConfig();
        int interval = (int) Math.max(1L, cfg.getRainbowInterval());
        float step = (float) (cfg.getRainbowSpeed() * interval / 20.0);
        entry.rainbowHue = 0f;
        entry.rainbowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entry.rainbowHue += step;
            if (entry.rainbowHue >= 1f) entry.rainbowHue -= 1f;
            applyColor(entry, hsvToHex(entry.rainbowHue));
        }, interval, interval).getTaskId();
    }

    private void stopRainbow(Entry entry) {
        if (entry.rainbowTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.rainbowTask);
            entry.rainbowTask = -1;
        }
    }

    // HSV → 十六进制色号（饱和度/明度固定 1.0，只动色相）
    private String hsvToHex(float hue) {
        int h = (int) (hue * 360) % 360;
        float r, g, b;
        float c = 1f;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return String.format("#%02X%02X%02X",
                (int) (r * 255), (int) (g * 255), (int) (b * 255));
    }
}