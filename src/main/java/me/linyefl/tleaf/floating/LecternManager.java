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

import java.util.ArrayList;
import java.util.List;
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
        TextDisplay main;                                  // 主字
        List<TextDisplay> glowLayers = new ArrayList<>();  // 光晕层（从内到外），默认不生成，发光才 spawn
        String color;
        String title;
        UUID ownerUuid;   // 创建者（放书玩家），只有他能染色/闪烁/发光/炫彩
        int blinkTask = -1;
        boolean blinkOn;
        int rainbowTask = -1;
        float rainbowHue; // 炫彩色相（0.0 ~ 1.0）
        boolean glowOn;   // 发光（光晕+呼吸）
        int breathTask = -1;
        long breathTick;
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
            if (entry.blinkTask == -1 && entry.rainbowTask == -1 && !entry.glowOn) {
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
            stopGlow(entry);
            entry.main.remove();
        }
    }

    // 停服清理
    public void removeAll() {
        for (Entry entry : displays.values()) {
            stopBlink(entry);
            stopRainbow(entry);
            stopGlow(entry);
            entry.main.remove();
        }
        displays.clear();
    }

    // 区块卸载清理：移除该区块内所有讲台的显示实体（避免实体被存进区块存档变成孤儿）
    public void removeChunk(World world, int chunkX, int chunkZ) {
        for (Map.Entry<Block, Entry> e : displays.entrySet()) {
            Block b = e.getKey();
            if (b.getWorld().equals(world)
                    && b.getChunk().getX() == chunkX
                    && b.getChunk().getZ() == chunkZ) {
                remove(b);
            }
        }
    }

    // 扔染料变色：稳定显示，停掉发光/闪烁/炫彩。只有创建者能改色
    public boolean setColor(Block lectern, String hexColor, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null) return false;
        if (!entry.ownerUuid.equals(playerUuid)) return false;
        entry.color = hexColor;
        stopBlink(entry);
        stopRainbow(entry);
        stopGlow(entry);
        applyColor(entry, entry.color);
        return true;
    }

    // 扔骨粉开关闪烁。开 = 当前色 ↔ 白 来回切，关 = 恢复稳定色
    // 返回 null = 无显示/非创建者，true = 已开启，false = 已关闭
    public Boolean toggleBlink(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.blinkTask == -1) {
            stopRainbow(entry);   // 互斥：停炫彩
            stopGlow(entry);      // 互斥：停发光
            startBlink(entry);
            return true;
        } else {
            stopBlink(entry);
            applyColor(entry, entry.color);
            return false;
        }
    }

    // 扔荧光墨囊开关发光（光晕+呼吸）。只有创建者能操作
    // 返回 null = 无显示/非创建者，true = 发光已开，false = 发光已关
    public Boolean toggleGlowing(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.glowOn) {
            stopGlow(entry);
            return false;
        } else {
            stopBlink(entry);     // 互斥：停闪烁
            stopRainbow(entry);   // 互斥：停炫彩
            entry.glowOn = true;
            spawnGlowLayers(entry);            // 按需创建光晕实体（初始缩放 0 不可见）
            applyColor(entry, entry.color);    // 主字 + 光晕同步文字颜色
            applyGlowVisible(entry, true);     // 放大到可见
            startBreath(entry);
            return true;
        }
    }

    // 扔钻石开关炫彩。开 = 色环平滑流动，关 = 恢复稳定色。只有创建者能操作
    // 返回 null = 无显示/非创建者，true = 炫彩已开启，false = 炫彩已关闭
    public Boolean toggleRainbow(Block lectern, UUID playerUuid) {
        Entry entry = displays.get(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.rainbowTask == -1) {
            stopBlink(entry);     // 互斥：停闪烁
            stopGlow(entry);      // 互斥：停发光
            startRainbow(entry);
            return true;
        } else {
            stopRainbow(entry);
            applyColor(entry, entry.color);
            return false;
        }
    }

    public boolean hasDisplay(Block lectern) {
        return displays.containsKey(lectern);
    }

    // 创建显示：只生成主字实体，光晕实体等发光时才创建
    private Entry create(Block lectern, String title, UUID ownerUuid) {
        FloatingConfig cfg = plugin.getFloatingConfig();
        World world = lectern.getWorld();
        Location loc = lectern.getLocation().add(0.5, 1.0 + cfg.getYOffset(), 0.5);

        Entry entry = new Entry();
        entry.title = title;
        entry.ownerUuid = ownerUuid;
        entry.color = cfg.getDefaultColor();

        TextDisplay main = world.spawn(loc, TextDisplay.class);
        setupBase(main);
        main.setShadowed(cfg.isShadowed());
        main.setTextOpacity((byte) 250);
        main.setTransformation(makeTransform((float) cfg.getTextScale(), 0f));
        entry.main = main;

        applyColor(entry, entry.color);
        return entry;
    }

    // 发光开启时按需创建光晕层实体（初始缩放 0 = 不渲染）
    private void spawnGlowLayers(Entry entry) {
        if (!entry.glowLayers.isEmpty()) return;
        FloatingConfig cfg = plugin.getFloatingConfig();
        Block lectern = null;
        for (Map.Entry<Block, Entry> e : displays.entrySet()) {
            if (e.getValue() == entry) {
                lectern = e.getKey();
                break;
            }
        }
        if (lectern == null) return;
        World world = lectern.getWorld();
        Location loc = lectern.getLocation().add(0.5, 1.0 + cfg.getYOffset(), 0.5);

        List<Double> scales = cfg.getGlowScales();
        for (int i = 0; i < scales.size(); i++) {
            TextDisplay g = world.spawn(loc, TextDisplay.class);
            setupBase(g);
            g.setShadowed(false);
            g.setTextOpacity((byte) cfg.getGlowOpacity());
            g.setTransformation(makeTransform(0f, glowZ(i)));
            entry.glowLayers.add(g);
        }
    }

    // 关闭发光：销毁光晕实体
    private void destroyGlowLayers(Entry entry) {
        for (TextDisplay g : entry.glowLayers) {
            g.remove();
        }
        entry.glowLayers.clear();
    }

    private void setupBase(TextDisplay d) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setDefaultBackground(false);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setSeeThrough(false);
        d.setLineWidth(2000); // 长书名不换行
    }

    // 光晕层 z 轴偏移：负值垫到主字后面，外层（越大）越靠后
    private float glowZ(int index) {
        return -(0.02f * (index + 1));
    }

    private Transformation makeTransform(float scale, float zOff) {
        return new Transformation(
                new Vector3f(0f, 0f, zOff),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
    }

    // 渲染主字 + 光晕层文字（光晕层用混白后的霓虹色，列表为空则跳过）
    private void applyColor(Entry entry, String hex) {
        entry.main.text(Component.text(entry.title).color(TextColor.fromHexString(hex)));
        if (entry.glowLayers.isEmpty()) return;
        FloatingConfig cfg = plugin.getFloatingConfig();
        String glowHex = blendWhite(hex, cfg.getGlowWhiteMix());
        Component glowText = Component.text(entry.title).color(TextColor.fromHexString(glowHex));
        for (TextDisplay g : entry.glowLayers) {
            g.text(glowText);
        }
    }

    // 光晕颜色 = 当前色与白色按比例混合（mix 越大越霓虹白）
    private String blendWhite(String hex, double mix) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        r = (int) Math.round(r * (1 - mix) + 255 * mix);
        g = (int) Math.round(g * (1 - mix) + 255 * mix);
        b = (int) Math.round(b * (1 - mix) + 255 * mix);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    // 显示/隐藏光晕层（缩放 0 = 不渲染）
    private void applyGlowVisible(Entry entry, boolean on) {
        FloatingConfig cfg = plugin.getFloatingConfig();
        List<Double> scales = cfg.getGlowScales();
        for (int i = 0; i < entry.glowLayers.size(); i++) {
            TextDisplay g = entry.glowLayers.get(i);
            float s = on ? (float) (cfg.getTextScale() * scales.get(i)) : 0f;
            g.setTransformation(makeTransform(s, glowZ(i)));
        }
    }

    // 设置光晕层不透明度（呼吸灯逐帧调用）
    private void setGlowOpacity(Entry entry, int opacity) {
        byte op = (byte) Math.max(0, Math.min(255, opacity));
        for (TextDisplay g : entry.glowLayers) {
            g.setTextOpacity(op);
        }
    }

    // —— 闪烁：当前色 ↔ 白 来回切 ——
    private void startBlink(Entry entry) {
        stopBlink(entry);
        long interval = plugin.getFloatingConfig().getBlinkInterval();
        entry.blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (entry.blinkOn) {
                applyColor(entry, "#FFFFFF");
            } else {
                applyColor(entry, entry.color);
            }
            entry.blinkOn = !entry.blinkOn;
        }, interval, interval).getTaskId();
    }

    private void stopBlink(Entry entry) {
        if (entry.blinkTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.blinkTask);
            entry.blinkTask = -1;
        }
        entry.blinkOn = false;
    }

    // —— 炫彩：色环平滑流动 ——
    private void startRainbow(Entry entry) {
        stopRainbow(entry);
        FloatingConfig cfg = plugin.getFloatingConfig();
        long interval = Math.max(1, cfg.getRainbowInterval());
        entry.rainbowHue = 0f;
        entry.rainbowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entry.rainbowHue += (float) (cfg.getRainbowSpeed() * interval / 20.0);
            if (entry.rainbowHue >= 1.0f) entry.rainbowHue -= 1.0f;
            applyColor(entry, hsvToHex(entry.rainbowHue));
        }, interval, interval).getTaskId();
    }

    private void stopRainbow(Entry entry) {
        if (entry.rainbowTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.rainbowTask);
            entry.rainbowTask = -1;
        }
    }

    // —— 发光呼吸灯：光晕层不透明度正弦脉动 ——
    private void startBreath(Entry entry) {
        stopBreath(entry);
        FloatingConfig cfg = plugin.getFloatingConfig();
        if (!cfg.isBreathingEnabled()) {
            setGlowOpacity(entry, cfg.getGlowOpacity());  // 不呼吸 = 恒定亮度
            return;
        }
        long interval = Math.max(1, cfg.getBreathingInterval());
        entry.breathTick = 0;
        entry.breathTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entry.breathTick += interval;
            double seconds = entry.breathTick / 20.0;
            double phase = seconds * Math.PI * 2.0 / Math.max(0.1, cfg.getBreathingPeriod());
            double k = 0.5 + 0.5 * Math.sin(phase);  // 0~1 正弦
            int op = (int) Math.round(cfg.getBreathingMinOpacity()
                    + (cfg.getBreathingMaxOpacity() - cfg.getBreathingMinOpacity()) * k);
            setGlowOpacity(entry, op);
        }, interval, interval).getTaskId();
    }

    private void stopBreath(Entry entry) {
        if (entry.breathTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.breathTask);
            entry.breathTask = -1;
        }
    }

    // 关闭发光：停呼吸、销毁光晕实体
    private void stopGlow(Entry entry) {
        if (!entry.glowOn && entry.breathTask == -1 && entry.glowLayers.isEmpty()) return;
        stopBreath(entry);
        entry.glowOn = false;
        destroyGlowLayers(entry);
    }

    // HSV 转十六进制：s=1, v=1 的色环，hue 0.0~1.0 对应 红→橙→黄→绿→青→蓝→紫→红
    private String hsvToHex(float hue) {
        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float p = 0f, q = 1f - f, t = f;
        float r = 0f, g = 0f, b = 0f;
        switch (h) {
            case 0: r = 1f; g = t; b = p; break;
            case 1: r = q; g = 1f; b = p; break;
            case 2: r = p; g = 1f; b = t; break;
            case 3: r = p; g = q; b = 1f; break;
            case 4: r = t; g = p; b = 1f; break;
            default: r = 1f; g = p; b = q; break;
        }
        return String.format("#%02X%02X%02X", (int) (r * 255), (int) (g * 255), (int) (b * 255));
    }
}