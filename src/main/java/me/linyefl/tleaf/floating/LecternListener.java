package me.linyefl.tleaf.floating;

import io.papermc.paper.event.player.PlayerLecternPageChangeEvent;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;

public class LecternListener implements Listener {

    private static final String PERMISSION = "tleaf-floating.use";

    private final TLeafFloating plugin;

    public LecternListener(TLeafFloating plugin) {
        this.plugin = plugin;
    }

    // 放书 / 翻页 / 取书统一处理
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLecternChange(PlayerLecternPageChangeEvent event) {
        LecternManager manager = plugin.getLecternManager();
        Block lectern = event.getLectern().getBlock();
        ItemStack book = event.getBook();
        if (book == null) {
            manager.remove(lectern);
        } else if (event.getPlayer().hasPermission(PERMISSION)) {
            manager.refresh(lectern, book, event.getPlayer().getUniqueId());
        }
    }

    // 取书兜底
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTakeBook(PlayerTakeLecternBookEvent event) {
        plugin.getLecternManager().remove(event.getLectern().getBlock());
    }

    // 敲掉讲台
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LECTERN) {
            plugin.getLecternManager().remove(event.getBlock());
        }
    }

    // 扔道具：染料变色 / 骨粉闪烁 / 荧光墨囊发光 / 钻石炫彩
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        Material type = drop.getItemStack().getType();

        // 只处理四种道具，其他直接忽略
        int action = actionOf(type);
        if (action == 0) return;

        Player player = event.getPlayer();
        int distance = (int) plugin.getFloatingConfig().getRayDistance();
        Block target = player.getTargetBlockExact(distance);
        if (target == null || target.getType() != Material.LECTERN
                || !plugin.getLecternManager().hasDisplay(target)) {
            return;
        }

        LecternManager manager = plugin.getLecternManager();
        switch (action) {
            case 1: { // 染料：变色
                boolean ok = manager.setColor(
                        target, DyeColorUtil.toHex(dyeOf(type)), player.getUniqueId());
                if (ok) {
                    drop.remove();
                    send(player, "color-success");
                } else {
                    send(player, "color-denied");
                }
                break;
            }
            case 2: { // 骨粉：闪烁开关
                int r = manager.toggleBlink(target, player.getUniqueId());
                if (r == -1) {
                    send(player, "denied");
                } else {
                    drop.remove();
                    send(player, r == 1 ? "blink-on" : "blink-off");
                }
                break;
            }
            case 3: { // 荧光墨囊：发光开关
                int r = manager.toggleGlow(target, player.getUniqueId());
                if (r == -1) {
                    send(player, "denied");
                } else {
                    drop.remove();
                    send(player, r == 1 ? "glow-on" : "glow-off");
                }
                break;
            }
            case 4: { // 钻石：炫彩开关
                int r = manager.toggleRainbow(target, player.getUniqueId());
                if (r == -1) {
                    send(player, "denied");
                } else {
                    drop.remove();
                    send(player, r == 1 ? "rainbow-on" : "rainbow-off");
                }
                break;
            }
        }
    }

    // 发送消息（messages.yml 里留空则静默）
    private void send(Player player, String key) {
        String msg = plugin.getMessage(key);
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg);
        }
    }

    // 0 = 不处理，1 染料，2 骨粉，3 荧光墨囊，4 钻石
    private int actionOf(Material type) {
        if (isDye(type)) return 1;
        switch (type) {
            case BONE_MEAL: return 2;
            case GLOW_INK_SAC: return 3;
            case DIAMOND: return 4;
            default: return 0;
        }
    }

    private boolean isDye(Material type) {
        switch (type) {
            case WHITE_DYE:
            case ORANGE_DYE:
            case MAGENTA_DYE:
            case LIGHT_BLUE_DYE:
            case YELLOW_DYE:
            case LIME_DYE:
            case PINK_DYE:
            case GRAY_DYE:
            case LIGHT_GRAY_DYE:
            case CYAN_DYE:
            case PURPLE_DYE:
            case BLUE_DYE:
            case BROWN_DYE:
            case GREEN_DYE:
            case RED_DYE:
            case BLACK_DYE:
                return true;
            default:
                return false;
        }
    }

    private DyeColor dyeOf(Material type) {
        switch (type) {
            case WHITE_DYE:      return DyeColor.WHITE;
            case ORANGE_DYE:     return DyeColor.ORANGE;
            case MAGENTA_DYE:    return DyeColor.MAGENTA;
            case LIGHT_BLUE_DYE: return DyeColor.LIGHT_BLUE;
            case YELLOW_DYE:     return DyeColor.YELLOW;
            case LIME_DYE:       return DyeColor.LIME;
            case PINK_DYE:       return DyeColor.PINK;
            case GRAY_DYE:       return DyeColor.GRAY;
            case LIGHT_GRAY_DYE: return DyeColor.LIGHT_GRAY;
            case CYAN_DYE:       return DyeColor.CYAN;
            case PURPLE_DYE:     return DyeColor.PURPLE;
            case BLUE_DYE:       return DyeColor.BLUE;
            case BROWN_DYE:      return DyeColor.BROWN;
            case GREEN_DYE:      return DyeColor.GREEN;
            case RED_DYE:        return DyeColor.RED;
            case BLACK_DYE:      return DyeColor.BLACK;
            default:             return DyeColor.WHITE;
        }
    }
}