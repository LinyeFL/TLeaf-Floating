package me.linyefl.tleaf.floating;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class TLeafCommand implements CommandExecutor {

    private static final String RELOAD_PERMISSION = "tleaf-floating.reload";

    private final TLeafFloating plugin;

    public TLeafCommand(TLeafFloating plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sendMsg(sender, "reload-denied");
                return true;
            }
            plugin.getFloatingConfig().reload();
            plugin.reloadMessages();
            sendMsg(sender, "reload-success");
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6TLeaf-Floating 帮助");
        sender.sendMessage("§7/tfl help §f- 查看帮助");
        sender.sendMessage("§7/tfl reload §f- 重载配置（需权限）");
        sender.sendMessage("§7把书放到讲台显示书名，扔染料/骨粉/荧光墨囊/钻石切换效果");
    }

    private void sendMsg(CommandSender sender, String key) {
        String msg = plugin.getMessage(key);
        if (msg != null && !msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }
}