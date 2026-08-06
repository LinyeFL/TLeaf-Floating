package me.linyefl.tleaf.floating;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.PermissionAttachmentInfo;
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

    // 主字实体的持久标记（重启/区块重载后靠它找回旧实体，防叠字）
    private final NamespacedKey keyLectern;   // 值 = 讲台坐标 "世界:x:y:z"
    private final NamespacedKey keyOwner;     // 值 = 创建者 UUID
    private final NamespacedKey keyTitle;     // 值 = 显示文字
    private final NamespacedKey keyColor;     // 值 = 当前颜色 hex

    public LecternManager(TLeafFloating plugin) {
        this.plugin = plugin;
        this.keyLectern = new NamespacedKey(plugin, "lectern");
        this.keyOwner = new NamespacedKey(plugin, "owner");
        this.keyTitle = new NamespacedKey(plugin, "title");
        this.keyColor = new NamespacedKey(plugin, "color");
    }

    private static class Entry {
        TextDisplay main;                                  // 主字
        List<TextDisplay> glowLayers = new ArrayList<>();  // 光晕层（发光时才创建）
        String color;
        String title;
        UUID ownerUuid;   // 创建者（放书玩家）
        int blinkTask = -1;
        long blinkTick;   // 渐变闪烁计时（tick）
        int rainbowTask = -1;
        float rainbowHue;
        boolean glowOn;
        int breathTask = -1;
        long breathTick;
    }

    // 取显示文字：优先自定义名，其次成书标题；未改名的书与笔返回 null
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

    // 放书 / 翻页 / 取书统一处理。返回 false = 达到数量上限，本次不创建
    public boolean refresh(Block lectern, ItemStack book, UUID playerUuid, int limit) {
        String title = getTitle(book);
        if (title == null) {
            remove(lectern);
            return true;
        }
        Entry entry = getOrRebind(lectern);
        if (entry == null) {
            // 真正要新建：先查数量上限（countByOwner 实时扫全服持久标记，重启后也准）
            if (countByOwner(playerUuid) >= limit) {
                return false;
            }
            displays.put(lectern, create(lectern, title, playerUuid));
            return true;
        }
        // 已有显示：书名变了才刷新文字和染色权
        if (!entry.title.equals(title)) {
            entry.title = title;
            entry.ownerUuid = playerUuid;
            entry.main.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, title);
            if (entry.blinkTask == -1 && entry.rainbowTask == -1 && !entry.glowOn) {
                applyColor(entry, entry.color);
            }
        }
        return true;
    }

    // 移除某个讲台的显示（取书 / 敲讲台 / 书没了）
    public void remove(Block lectern) {
        Entry entry = displays.remove(lectern);
        if (entry != null) {
            stopBlink(entry);
            stopRainbow(entry);
            stopGlow(entry);
            if (entry.main.isValid()) entry.main.remove();
            return;
        }
        // Map 无绑定（重启后未翻页）：持久实体可能仍在，兜底销毁，不留幽灵
        TextDisplay existing = findExisting(lectern);
        if (existing != null) existing.remove();
    }

    // 停服清理：只取消任务；主字保留持久（重启后标题还在），光晕层随 stopGlow 销毁
    public void removeAll() {
        for (Entry entry : displays.values()) {
            stopBlink(entry);
            stopRainbow(entry);
            stopGlow(entry);
        }
        displays.clear();
    }

    // 扔染料变色：稳定显示，停掉发光/闪烁/炫彩。只有创建者能改色
    public boolean setColor(Block lectern, String hexColor, UUID playerUuid) {
        Entry entry = getOrRebind(lectern);
        if (entry == null) return false;
        if (!entry.ownerUuid.equals(playerUuid)) return false;
        entry.color = hexColor;
        stopBlink(entry);
        stopRainbow(entry);
        stopGlow(entry);
        applyColor(entry, entry.color);
        entry.main.getPersistentDataContainer().set(keyColor, PersistentDataType.STRING, hexColor);
        return true;
    }

    // 扔骨粉开关闪烁（渐变闪白）
    public Boolean toggleBlink(Block lectern, UUID playerUuid) {
        Entry entry = getOrRebind(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.blinkTask == -1) {
            stopRainbow(entry);
            stopGlow(entry);
            startBlink(entry);
            return true;
        } else {
            stopBlink(entry);
            applyColor(entry, entry.color);
            return false;
        }
    }

    // 扔荧光墨囊开关发光（光晕+呼吸）
    public Boolean toggleGlowing(Block lectern, UUID playerUuid) {
        Entry entry = getOrRebind(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.glowOn) {
            stopGlow(entry);
            return false;
        } else {
            stopBlink(entry);
            stopRainbow(entry);
            entry.glowOn = true;
            applyColor(entry, entry.color);
            spawnGlowLayers(entry);
            startBreath(entry);
            return true;
        }
    }

    // 扔钻石开关炫彩
    public Boolean toggleRainbow(Block lectern, UUID playerUuid) {
        Entry entry = getOrRebind(lectern);
        if (entry == null) return null;
        if (!entry.ownerUuid.equals(playerUuid)) return null;
        if (entry.rainbowTask == -1) {
            stopBlink(entry);
            stopGlow(entry);
            startRainbow(entry);
            return true;
        } else {
            stopRainbow(entry);
            applyColor(entry, entry.color);
            return false;
        }
    }

    public boolean hasDisplay(Block lectern) {
        Entry entry = displays.get(lectern);
        if (entry != null && entry.main.isValid()) return true;
        return findExisting(lectern) != null;  // 重启后未翻页也能识别
    }

    // 统计某个玩家在全世界拥有的悬浮标题数量（扫带持久标记的主字实体，重启后也准）
    public int countByOwner(UUID uuid) {
        String owner = uuid.toString();
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof TextDisplay)) continue;
                PersistentDataContainer pdc = e.getPersistentDataContainer();
                if (!pdc.has(keyLectern, PersistentDataType.STRING)) continue;
                if (owner.equals(pdc.get(keyOwner, PersistentDataType.STRING))) {
                    count++;
                }
            }
        }
        return count;
    }

    // 玩家的悬浮标题数量上限：bypass 无限 > limit.<数字> 权限取最大 > config 默认
    public int limitOf(Player player) {
        if (player.hasPermission("tleaf-floating.bypass")) return Integer.MAX_VALUE;
        int limit = plugin.getFloatingConfig().getDefaultLimit();
        String prefix = "tleaf-floating.limit.";
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String perm = pai.getPermission();
            if (perm.startsWith(prefix)) {
                try {
                    int v = Integer.parseInt(perm.substring(prefix.length()));
                    if (v > limit) limit = v;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return limit;
    }

    // 取有效绑定；区块重载后旧引用失效，自动找持久实体重建绑定（不新建，防叠字）
    private Entry getOrRebind(Block lectern) {
        Entry entry = displays.get(lectern);
        if (entry != null && entry.main.isValid()) return entry;
        if (entry != null) {
            stopBlink(entry);
            stopRainbow(entry);
            stopGlow(entry);
            displays.remove(lectern);
        }
        TextDisplay existing = findExisting(lectern);
        if (existing == null) return null;
        Entry bound = new Entry();
        bound.main = existing;
        PersistentDataContainer pdc = existing.getPersistentDataContainer();
        bound.title = pdc.get(keyTitle, PersistentDataType.STRING);
        bound.color = pdc.get(keyColor, PersistentDataType.STRING);
        String owner = pdc.get(keyOwner, PersistentDataType.STRING);
        bound.ownerUuid = null;
        if (owner != null) {
            try {
                bound.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bound.title == null) bound.title = "";
        if (bound.color == null) bound.color = plugin.getFloatingConfig().getDefaultColor();
        displays.put(lectern, bound);
        return bound;
    }

    private Entry create(Block lectern, String title, UUID ownerUuid) {
        FloatingConfig cfg = plugin.getFloatingConfig();
        World world = lectern.getWorld();
        Location loc = lectern.getLocation().add(0.5, 1.0 + cfg.getYOffset(), 0.5);

        // 持久显示：讲台附近已有我们创建的实体 → 复用，不叠字
        TextDisplay existing = findExisting(lectern);
        if (existing != null) {
            Entry entry = new Entry();
            entry.main = existing;
            entry.title = title;
            entry.ownerUuid = ownerUuid;
            entry.color = cfg.getDefaultColor();
            applyColor(entry, entry.color);
            return entry;
        }

        Entry entry = new Entry();
        entry.title = title;
        entry.ownerUuid = ownerUuid;
        entry.color = cfg.getDefaultColor();

        // 主字（持久：随区块保存，走远回来标题不消失）
        TextDisplay main = world.spawn(loc, TextDisplay.class);
        setupBase(main);
        main.setShadowed(cfg.isShadowed());
        main.setTextOpacity((byte) 250);
        main.setTransformation(makeTransform((float) cfg.getTextScale(), 0f));
        PersistentDataContainer pdc = main.getPersistentDataContainer();
        pdc.set(keyLectern, PersistentDataType.STRING, lecternKey(lectern));
        pdc.set(keyOwner, PersistentDataType.STRING, ownerUuid.toString());
        pdc.set(keyTitle, PersistentDataType.STRING, title);
        pdc.set(keyColor, PersistentDataType.STRING, cfg.getDefaultColor());
        entry.main = main;

        applyColor(entry, entry.color);
        return entry;
    }

    // 找讲台位置已存在的、带我们标记的主字实体
    private TextDisplay findExisting(Block lectern) {
        String key = lecternKey(lectern);
        Location center = lectern.getLocation().add(0.5, 1.0, 0.5);
        for (Entity e : lectern.getWorld().getNearbyEntities(center, 2.0, 3.0, 2.0)) {
            if (!(e instanceof TextDisplay)) continue;
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (pdc.has(keyLectern, PersistentDataType.STRING)
                    && key.equals(pdc.get(keyLectern, PersistentDataType.STRING))) {
                return (TextDisplay) e;
            }
        }
        return null;
    }

    private String lecternKey(Block lectern) {
        return lectern.getWorld().getName() + ":" + lectern.getX() + ":" + lectern.getY() + ":" + lectern.getZ();
    }

    private void setupBase(TextDisplay d) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setDefaultBackground(false);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setSeeThrough(false);
        d.setLineWidth(2000);
    }

    // 光晕层 z 轴偏移：负值垫到主字后面
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

    // 渲染主字 + 光晕层文字（光晕层用混白后的霓虹色）
    private void applyColor(Entry entry, String hex) {
        applyMainColor(entry, hex);
        if (entry.glowLayers.isEmpty()) return;
        FloatingConfig cfg = plugin.getFloatingConfig();
        String glowHex = blendWhite(hex, cfg.getGlowWhiteMix());
        Component glowText = Component.text(entry.title).color(TextColor.fromHexString(glowHex));
        for (TextDisplay g : entry.glowLayers) {
            g.text(glowText);
        }
    }

    // 主字单独设色（渐变闪烁逐帧调用，不碰光晕层）
    private void applyMainColor(Entry entry, String hex) {
        entry.main.text(Component.text(entry.title).color(TextColor.fromHexString(hex)));
    }

    // 光晕颜色 = 当前色与白色按比例混合
    private String blendWhite(String hex, double mix) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        r = (int) Math.round(r * (1 - mix) + 255 * mix);
        g = (int) Math.round(g * (1 - mix) + 255 * mix);
        b = (int) Math.round(b * (1 - mix) + 255 * mix);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    // 发光开启：按需创建光晕层（初始就显示目标大小）
    private void spawnGlowLayers(Entry entry) {
        if (!entry.glowLayers.isEmpty()) return;
        FloatingConfig cfg = plugin.getFloatingConfig();
        World world = entry.main.getWorld();
        Location loc = entry.main.getLocation();
        List<Double> scales = cfg.getGlowScales();
        for (int i = 0; i < scales.size(); i++) {
            TextDisplay g = world.spawn(loc, TextDisplay.class);
            setupBase(g);
            g.setShadowed(false);
            g.setTextOpacity((byte) cfg.getGlowOpacity());
            g.setTransformation(makeTransform((float) (cfg.getTextScale() * scales.get(i)), glowZ(i)));
            g.setPersistent(false);  // 光晕层不持久：区块卸载即消失，不占存档
            entry.glowLayers.add(g);
        }
        applyColor(entry, entry.color);
    }

    // 关发光：销毁光晕层
    private void destroyGlowLayers(Entry entry) {
        for (TextDisplay g : entry.glowLayers) {
            if (g.isValid()) g.remove();
        }
        entry.glowLayers.clear();
    }

    // 设置光晕层不透明度（呼吸灯逐帧调用）
    private void setGlowOpacity(Entry entry, int opacity) {
        byte op = (byte) Math.max(0, Math.min(255, opacity));
        for (TextDisplay g : entry.glowLayers) {
            g.setTextOpacity(op);
        }
    }

    // —— 闪烁：渐变闪白（当前色 ↔ 白 正弦平滑过渡）——
    private void startBlink(Entry entry) {
        stopBlink(entry);
        FloatingConfig cfg = plugin.getFloatingConfig();
        long interval = Math.max(1, cfg.getBlinkInterval());
        entry.blinkTick = 0;
        entry.blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entry.blinkTick += interval;
            double seconds = entry.blinkTick / 20.0;
            double phase = seconds * Math.PI * 2.0 / Math.max(0.1, cfg.getBlinkPeriod());
            double k = 0.5 + 0.5 * Math.sin(phase);   // 0~1 正弦，平滑到白再回来
            applyMainColor(entry, lerpHex(entry.color, "#FFFFFF", k));
        }, interval, interval).getTaskId();
    }

    private void stopBlink(Entry entry) {
        if (entry.blinkTask != -1) {
            Bukkit.getScheduler().cancelTask(entry.blinkTask);
            entry.blinkTask = -1;
        }
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
            applyMainColor(entry, hsvToHex(entry.rainbowHue));
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
            setGlowOpacity(entry, cfg.getGlowOpacity());
            return;
        }
        long interval = Math.max(1, cfg.getBreathingInterval());
        entry.breathTick = 0;
        entry.breathTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entry.breathTick += interval;
            double seconds = entry.breathTick / 20.0;
            double phase = seconds * Math.PI * 2.0 / Math.max(0.1, cfg.getBreathingPeriod());
            double k = 0.5 + 0.5 * Math.sin(phase);
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

    // 关闭发光：停呼吸、销毁光晕层、复位不透明度
    private void stopGlow(Entry entry) {
        if (!entry.glowOn && entry.breathTask == -1) return;
        stopBreath(entry);
        entry.glowOn = false;
        destroyGlowLayers(entry);
        setGlowOpacity(entry, plugin.getFloatingConfig().getGlowOpacity());
    }

    // 两个 hex 颜色线性插值：k=0 → from 色，k=1 → to 色
    private String lerpHex(String from, String to, double k) {
        int[] a = parseHex(from);
        int[] b = parseHex(to);
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * k);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * k);
        int bl = (int) Math.round(a[2] + (b[2] - a[2]) * k);
        return String.format("#%02X%02X%02X", r, g, bl);
    }

    // #RRGGBB 或 #RGB 解析成 [r, g, b]
    private int[] parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    // HSV 转十六进制：s=1, v=1 的色环
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