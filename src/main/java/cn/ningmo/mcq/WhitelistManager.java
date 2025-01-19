package cn.ningmo.mcq;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import cn.ningmo.mcq.event.PlayerBindSuccessEvent;

public class WhitelistManager {
    private final MCQ plugin;
    private final File whitelistFile;
    private FileConfiguration whitelistConfig;
    private final Map<String, VerifyCode> pendingVerifications = new HashMap<>();
    
    public WhitelistManager(MCQ plugin) {
        this.plugin = plugin;
        this.whitelistFile = new File(plugin.getDataFolder(), "whitelist.yml");
        loadWhitelist();
    }
    
    private void loadWhitelist() {
        if (!whitelistFile.exists()) {
            plugin.saveResource("whitelist.yml", false);
        }
        whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
    }
    
    private static class VerifyCode {
        final long qqId;
        final String code;
        final long expireTime;
        
        VerifyCode(long qqId, String code, long expireTime) {
            this.qqId = qqId;
            this.code = code;
            this.expireTime = expireTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    public void handleBindRequest(long qqId, String playerName, long groupId) {
        if (!plugin.getConfig().getBoolean("whitelist.enabled")) {
            plugin.getBotClient().sendGroupMessage(groupId, "白名单系统未启用");
            return;
        }
        
        // 检查绑定模式
        String bindMode = plugin.getConfig().getString("whitelist.bind-mode", "direct");
        if (bindMode.equals("verify")) {
            handleVerifyRequest(qqId, playerName, groupId);
        } else {
            handleDirectBind(qqId, playerName, groupId);
        }
    }
    
    private void handleVerifyRequest(long qqId, String playerName, long groupId) {
        // 生成验证码
        String code = generateVerifyCode();
        int expireMinutes = plugin.getConfig().getInt("whitelist.verify.expire", 5);
        long expireTime = System.currentTimeMillis() + (expireMinutes * 60 * 1000);
        
        // 保存验证信息
        pendingVerifications.put(playerName.toLowerCase(), new VerifyCode(qqId, code, expireTime));
        
        // 发送验证码到游戏内
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().getPlayer(playerName).sendMessage(
                "§b[MCQ] §f您的QQ绑定验证码为: §e" + code + "\n" +
                "§f请在 " + expireMinutes + " 分钟内完成验证"
            );
        });
        
        // 发送提示到QQ群
        String message = plugin.getConfig().getString("whitelist.messages.verify-sent");
        plugin.getBotClient().sendGroupMessage(groupId, message);
    }
    
    private String generateVerifyCode() {
        int length = plugin.getConfig().getInt("whitelist.verify.length", 6);
        String format = plugin.getConfig().getString("whitelist.verify.format", "number");
        
        if (format.equals("number")) {
            return String.format("%0" + length + "d", 
                new Random().nextInt((int) Math.pow(10, length)));
        } else {
            String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            StringBuilder code = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < length; i++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            return code.toString();
        }
    }
    
    public void handleVerifyCommand(String playerName, String code) {
        VerifyCode verifyCode = pendingVerifications.get(playerName.toLowerCase());
        if (verifyCode == null) {
            plugin.getServer().getPlayer(playerName).sendMessage("§c[MCQ] §f请先在QQ群中申请绑定！");
            return;
        }
        
        if (verifyCode.isExpired()) {
            pendingVerifications.remove(playerName.toLowerCase());
            plugin.getServer().getPlayer(playerName).sendMessage("§c[MCQ] §f验证码已过期，请重新申请");
            return;
        }
        
        if (!verifyCode.code.equals(code)) {
            plugin.getServer().getPlayer(playerName).sendMessage("§c[MCQ] §f验证码错误，请重新输入");
            return;
        }
        
        // 验证成功，执行绑定
        whitelistConfig.set("players." + playerName, String.valueOf(verifyCode.qqId));
        try {
            whitelistConfig.save(whitelistFile);
            
            // 通知事件监听器绑定成功
            notifyBindSuccess(playerName);
            
            plugin.getServer().getPlayer(playerName).sendMessage("§a[MCQ] §f绑定成功！");
            
            // 添加到服务器白名单
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "whitelist add " + playerName
            );
            
            // 通知QQ群
            for (Long groupId : plugin.getConfig().getLongList("bot.groups")) {
                plugin.getBotClient().sendGroupMessage(groupId, 
                    "玩家 " + playerName + " 已完成白名单绑定！");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("保存白名单数据时发生错误: " + e.getMessage());
            plugin.getServer().getPlayer(playerName).sendMessage("§c[MCQ] §f绑定失败，请联系管理员");
        }
        
        pendingVerifications.remove(playerName.toLowerCase());
    }
    
    private void handleDirectBind(long qqId, String playerName, long groupId) {
        // 检查玩家名是否已被绑定
        String boundQQ = whitelistConfig.getString("players." + playerName);
        if (boundQQ != null) {
            if (boundQQ.equals(String.valueOf(qqId))) {
                plugin.getBotClient().sendGroupMessage(groupId, "你已经绑定了这个游戏ID");
            } else {
                plugin.getBotClient().sendGroupMessage(groupId, "该游戏ID已被其他QQ号绑定");
            }
            return;
        }
        
        // 添加QQ号绑定限制
        int maxBindings = plugin.getConfig().getInt("whitelist.max-bindings-per-qq", 1);
        int currentBindings = 0;
        
        // 添加空值检查
        if (whitelistConfig.getConfigurationSection("players") != null) {
            for (String key : whitelistConfig.getConfigurationSection("players").getKeys(false)) {
                if (whitelistConfig.getString("players." + key).equals(String.valueOf(qqId))) {
                    currentBindings++;
                }
            }
        }
        
        if (currentBindings >= maxBindings) {
            plugin.getBotClient().sendGroupMessage(groupId, "你已达到最大绑定数量限制！");
            return;
        }
        
        // 保存绑定关系
        whitelistConfig.set("players." + playerName, String.valueOf(qqId));
        try {
            whitelistConfig.save(whitelistFile);
            plugin.getBotClient().sendGroupMessage(groupId, "绑定成功！");
            
            // 添加到服务器白名单
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    "whitelist add " + playerName
                );
            });
        } catch (IOException e) {
            plugin.getLogger().severe("保存白名单数据时发生错误: " + e.getMessage());
            plugin.getBotClient().sendGroupMessage(groupId, "绑定失败，请联系管理员");
        }
    }
    
    public boolean isWhitelisted(String playerName) {
        if (!plugin.getConfig().getBoolean("whitelist.enabled")) {
            return true;
        }
        return whitelistConfig.contains("players." + playerName);
    }
    
    // 添加解绑功能
    public void handleUnbindRequest(long qqId, String playerName, long groupId) {
        String boundQQ = whitelistConfig.getString("players." + playerName);
        
        if (boundQQ == null) {
            plugin.getBotClient().sendGroupMessage(groupId, "该游戏ID未绑定白名单");
            return;
        }
        
        if (!boundQQ.equals(String.valueOf(qqId))) {
            plugin.getBotClient().sendGroupMessage(groupId, "你没有权限解绑该游戏ID");
            return;
        }
        
        whitelistConfig.set("players." + playerName, null);
        try {
            whitelistConfig.save(whitelistFile);
            plugin.getBotClient().sendGroupMessage(groupId, "解绑成功！");
            
            // 从服务器白名单移除
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    "whitelist remove " + playerName
                );
            });
        } catch (IOException e) {
            plugin.getLogger().severe("保存白名单数据时发生错误: " + e.getMessage());
            plugin.getBotClient().sendGroupMessage(groupId, "解绑失败，请联系管理员");
        }
    }

    private void notifyBindSuccess(String playerName) {
        plugin.getServer().getPluginManager().callEvent(new PlayerBindSuccessEvent(playerName));
    }
} 