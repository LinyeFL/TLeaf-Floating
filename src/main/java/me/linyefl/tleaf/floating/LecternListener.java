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
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

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
        FloatingConfig cfg = plugin.getFloatingConfig();
        Block lectern = event.getLectern().getBlock();
        ItemStack book = event.getBook();
        if (book == null) {
            // 讲台空了，移除显示
            boolean had = manager.hasDisplay(lectern);
            manager.remove(lectern);
            if (had) sendMsg(event.getPlayer(), cfg.getMsgDisplayRemoved());
        } else if (event.getPlayer().hasPermission(PERMISSION)) {
            // 有权限：创建/更新显示
            boolean existed = manager.hasDisplay(lectern);
            manager.refresh(lectern, book, event.getPlayer().getUniqueId());
            if (!existed) {
                sendMsg(event.getPlayer(), cfg.getMsgDisplayCreated());
            }
        } else if (!manager.hasDisplay(lectern)) {
            // 没权限放新书：提示拒绝（讲台已有显示的情况是路人翻页，静默）
            sendMsg(event.getPlayer(), cfg.getMsgDisplayDenied());
        }
    }

    // 取书兜底
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTakeBook(PlayerTakeLecternBookEvent event) {
        LecternManager manager = plugin.getLecternManager();
        Block lectern = event.getLectern().getBlock();
        boolean had = manager.hasDisplay(lectern);
        manager.remove(lectern);
        if (had) sendMsg(event.getPlayer(), plugin.getFloatingConfig().getMsgDisplayRemoved());
    }

    // 敲掉讲台
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LECTERN) {
            LecternManager manager = plugin.getLecternManager();
            Block block = event.getBlock();
            boolean had = manager.hasDisplay(block);
            manager.remove(block);
            if (had) sendMsg(event.getPlayer(), plugin.getFloatingConfig().getMsgDisplayRemoved());
        }
    }

    // 区块卸载：清掉该区块内的显示实体，防止实体被存进区块存档变成孤儿
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getLecternManager().removeChunk(
                event.getWorld(),
                event.getChunk().getX(),
                event.getChunk().getZ());
    }

    // 扔物品：染料变色 / 骨粉开关闪烁 / 荧光墨囊开关发光 / 钻石开关炫彩
    // 成功触发的操作消耗掉扔出的物品（物品消失），被拒绝或不触发不消耗
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        Material type = event.getItemDrop().getItemStack().getType();
        if (!isDye(type) && type != Material.BONE_MEAL
                && type != Material.GLOW_INK_SAC && type != Material.DIAMOND) return;

        Player player = event.getPlayer();
        int distance = (int) plugin.getFloatingConfig().getRayDistance();
        Block target = player.getTargetBlockExact(distance);
        if (target == null || target.getType() != Material.LECTERN
                || !plugin.getLecternManager().hasDisplay(target)) {
            return; // 没对准讲台或讲台没显示：静默不提示，物品不消耗
        }

        LecternManager manager = plugin.getLecternManager();
        FloatingConfig cfg = plugin.getFloatingConfig();
        UUID uuid = player.getUniqueId();
        boolean consumed = false;

        if (isDye(type)) {
            boolean ok = manager.setColor(target, DyeColorUtil.toHex(dyeOf(type)), uuid);
            sendMsg(player, ok ? cfg.getMsgColorSuccess() : cfg.getMsgColorDenied());
            consumed = ok;
        } else if (type == Material.BONE_MEAL) {
            Boolean on = manager.toggleBlink(target, uuid);
            if (on == null) {
                sendMsg(player, cfg.getMsgDenied());
            } else {
                sendMsg(player, on ? cfg.getMsgBlinkOn() : cfg.getMsgBlinkOff());
                consumed = true;
            }
        } else if (type == Material.GLOW_INK_SAC) {
            Boolean on = manager.toggleGlowing(target, uuid);
            if (on == null) {
                sendMsg(player, cfg.getMsgDenied());
            } else {
                sendMsg(player, on ? cfg.getMsgGlowOn() : cfg.getMsgGlowOff());
                consumed = true;
            }
        } else { // DIAMOND
            Boolean on = manager.toggleRainbow(target, uuid);
            if (on == null) {
                sendMsg(player, cfg.getMsgDenied());
            } else {
                sendMsg(player, on ? cfg.getMsgRainbowOn() : cfg.getMsgRainbowOff());
                consumed = true;
            }
        }

        // 成功触发即消耗：移除掉落的物品实体
        if (consumed) {
            event.getItemDrop().remove();
        }
    }

    // 空字符串不发送
    private void sendMsg(Player player, String msg) {
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg);
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