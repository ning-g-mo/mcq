package cn.ningmo.mcq;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import cn.ningmo.mcq.event.PlayerBindSuccessEvent;

public class MinecraftEventListener implements Listener {
    private final MCQ plugin;
    private final Map<String, BukkitTask> kickTasks = new HashMap<>();
    private final Map<String, BukkitTask> remindTasks = new HashMap<>();
    
    public MinecraftEventListener(MCQ plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String format = plugin.getConfig().getString("message-format.mc-to-qq");
        String message = format
            .replace("{player}", event.getPlayer().getName())
            .replace("{message}", event.getMessage());
            
        for (Long groupId : plugin.getConfig().getLongList("bot.groups")) {
            plugin.getBotClient().sendGroupMessage(groupId, message);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        // 检查是否启用强制绑定
        if (!plugin.getConfig().getBoolean("whitelist.force-bind.enabled", true)) {
            return;
        }
        
        // 检查玩家是否已绑定
        if (plugin.getWhitelistManager().isWhitelisted(playerName)) {
            return;
        }
        
        // 如果不允许未绑定玩家进入，直接踢出
        if (!plugin.getConfig().getBoolean("whitelist.force-bind.allow-join", true)) {
            String kickMessage = plugin.getConfig().getString("whitelist.force-bind.kick-message");
            player.kickPlayer(kickMessage);
            return;
        }
        
        // 获取踢出延迟时间
        int kickDelay = plugin.getConfig().getInt("whitelist.force-bind.kick-delay", 300);
        if (kickDelay > 0) {
            // 发送提示消息
            String joinMessage = plugin.getConfig().getString("whitelist.force-bind.join-message")
                .replace("{time}", String.valueOf(kickDelay));
            player.sendMessage(joinMessage);
            
            // 设置定时提醒
            int remindInterval = plugin.getConfig().getInt("whitelist.force-bind.remind-interval", 60);
            if (remindInterval > 0) {
                BukkitTask remindTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                    if (player.isOnline() && !plugin.getWhitelistManager().isWhitelisted(playerName)) {
                        player.sendMessage("§c[MCQ] §f请尽快完成QQ白名单绑定！");
                    } else {
                        BukkitTask task = remindTasks.remove(playerName);
                        if (task != null) {
                            task.cancel();
                        }
                    }
                }, remindInterval * 20L, remindInterval * 20L);
                remindTasks.put(playerName, remindTask);
            }
            
            // 设置延迟踢出
            BukkitTask kickTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !plugin.getWhitelistManager().isWhitelisted(playerName)) {
                    String kickMessage = plugin.getConfig().getString("whitelist.force-bind.kick-message");
                    player.kickPlayer(kickMessage);
                }
                kickTasks.remove(playerName);
                BukkitTask remind = remindTasks.remove(playerName);
                if (remind != null) {
                    remind.cancel();
                }
            }, kickDelay * 20L);
            kickTasks.put(playerName, kickTask);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        
        // 取消踢出任务
        BukkitTask kickTask = kickTasks.remove(playerName);
        if (kickTask != null) {
            kickTask.cancel();
        }
        
        // 取消提醒任务
        BukkitTask remindTask = remindTasks.remove(playerName);
        if (remindTask != null) {
            remindTask.cancel();
        }
        
        String message = "§c- §f" + playerName + " 离开了服务器";
        for (Long groupId : plugin.getConfig().getLongList("bot.groups")) {
            plugin.getBotClient().sendGroupMessage(groupId, message);
        }
    }
    
    @EventHandler
    public void onPlayerBindSuccess(PlayerBindSuccessEvent event) {
        String playerName = event.getPlayerName();
        
        // 取消踢出任务
        BukkitTask kickTask = kickTasks.remove(playerName);
        if (kickTask != null) {
            kickTask.cancel();
        }
        
        // 取消提醒任务
        BukkitTask remindTask = remindTasks.remove(playerName);
        if (remindTask != null) {
            remindTask.cancel();
        }
        
        // 发送成功消息
        Player player = plugin.getServer().getPlayer(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage("§a[MCQ] §f白名单绑定成功！");
        }
    }
} 