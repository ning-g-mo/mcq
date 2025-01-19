package cn.ningmo.mcq;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MCQCommand implements CommandExecutor {
    private final MCQ plugin;
    
    public MCQCommand(MCQ plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§b[MCQ] §f命令帮助：");
            sender.sendMessage("§f/mcq reload - 重载配置文件");
            sender.sendMessage("§f/mcq verify <验证码> - 完成QQ绑定验证");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mcq.admin")) {
                sender.sendMessage("§c你没有权限执行此命令！");
                return true;
            }
            
            plugin.reloadConfig();
            sender.sendMessage("§a配置文件已重载！");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("verify")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c此命令只能由玩家执行！");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage("§c用法: /mcq verify <验证码>");
                return true;
            }
            
            Player player = (Player) sender;
            plugin.getWhitelistManager().handleVerifyCommand(player.getName(), args[1]);
            return true;
        }
        
        return false;
    }
} 