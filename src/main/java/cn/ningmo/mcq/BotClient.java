package cn.ningmo.mcq;

import cn.ningmo.mcq.command.CustomCommand;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Arrays;

public class BotClient extends WebSocketClient {
    private final MCQ plugin;
    private final Map<Long, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<Long, Long> statusCooldowns = new HashMap<>();
    
    public BotClient(MCQ plugin, String wsUrl) {
        super(URI.create(wsUrl));
        this.plugin = plugin;
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        plugin.getLogger().info("成功连接到OneBot服务器！");
        startHeartbeat();
    }
    
    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            
            // 处理群消息
            if (json.has("post_type") && json.get("post_type").getAsString().equals("message")) {
                if (json.get("message_type").getAsString().equals("group")) {
                    handleGroupMessage(json);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理消息时发生错误: " + e.getMessage());
        }
    }
    
    private void handleGroupMessage(JsonObject json) {
        long groupId = json.get("group_id").getAsLong();
        List<Long> configGroups = plugin.getConfig().getLongList("bot.groups");
        
        if (!configGroups.contains(groupId)) {
            return;
        }
        
        String message = json.get("message").getAsString();
        long senderId = json.get("user_id").getAsLong();
        
        // 添加CQ码处理
        message = processCQCodes(message);
        
        // 处理命令
        if (message.startsWith("!")) {
            handleCommand(message, senderId, groupId);
            return;
        }
        
        // 转发消息到游戏内
        String format = plugin.getConfig().getString("message-format.qq-to-mc");
        String senderName = json.getAsJsonObject("sender").get("nickname").getAsString();
        String gameMessage = format
            .replace("{sender}", senderName)
            .replace("{message}", message);
            
        plugin.getServer().broadcastMessage(gameMessage);
    }
    
    private void handleCommand(String message, long senderId, long groupId) {
        String[] args = message.substring(1).split(" ");
        String cmd = args[0].toLowerCase();
        
        String adminPrefix = plugin.getConfig().getString("permissions.admin-prefix").substring(1);
        List<Long> admins = plugin.getConfig().getLongList("bot.admins");
        boolean isAdmin = admins.contains(senderId);
        
        Optional<CustomCommand> command = plugin.getCommandManager().getCommand(cmd);
        if (!command.isPresent()) {
            return;
        }
        
        CustomCommand customCmd = command.get();
        
        // 检查管理员权限
        if (customCmd.isAdminOnly() && !isAdmin) {
            sendGroupMessage(groupId, "你没有权限执行此命令！");
            return;
        }
        
        // 检查冷却
        if (!checkCooldown(senderId, groupId, customCmd)) {
            return;
        }
        
        // 执行命令动作
        executeActions(customCmd.getActions(), args, senderId, groupId);
    }
    
    private boolean checkCooldown(long senderId, long groupId, CustomCommand cmd) {
        if (cmd.getCooldown() <= 0) return true;
        
        Map<String, Long> userCooldowns = cooldowns.computeIfAbsent(senderId, k -> new HashMap<>());
        long lastUse = userCooldowns.getOrDefault(cmd.getName(), 0L);
        long now = System.currentTimeMillis();
        
        if (now - lastUse < cmd.getCooldown() * 1000) {
            long remainingSeconds = (cmd.getCooldown() * 1000 - (now - lastUse)) / 1000;
            sendGroupMessage(groupId, "命令冷却中，请等待 " + remainingSeconds + " 秒后再试");
            return false;
        }
        
        userCooldowns.put(cmd.getName(), now);
        return true;
    }
    
    private void executeActions(List<String> actions, String[] args, long senderId, long groupId) {
        for (String action : actions) {
            // 替换参数
            String processedAction = processActionPlaceholders(action, args);
            
            // 执行动作
            switch (processedAction.split(" ")[0]) {
                case "status":
                    sendServerStatus(groupId);
                    break;
                    
                case "bind":
                    if (args.length < 2) {
                        sendGroupMessage(groupId, "用法: !bind <游戏ID>");
                        return;
                    }
                    plugin.getWhitelistManager().handleBindRequest(senderId, args[1], groupId);
                    break;
                    
                case "broadcast":
                    String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getServer().broadcastMessage("§c[公告] §f" + message);
                    break;
                    
                case "qq_broadcast":
                    String qqMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    for (Long gid : plugin.getConfig().getLongList("bot.groups")) {
                        sendGroupMessage(gid, "[公告] " + qqMessage);
                    }
                    break;
                    
                // 添加更多动作处理...
            }
        }
    }
    
    private String processActionPlaceholders(String action, String[] args) {
        String result = action;
        
        // 替换单个参数 {arg1}, {arg2}, ...
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{arg" + i + "}", args[i]);
        }
        
        // 替换所有剩余参数 {args}
        if (args.length > 1) {
            result = result.replace("{args}", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }
        
        return result;
    }
    
    private void sendServerStatus(long groupId) {
        // 检查冷却
        long cooldown = plugin.getConfig().getLong("status.cooldown", 30) * 1000; // 转换为毫秒
        long lastUse = statusCooldowns.getOrDefault(groupId, 0L);
        long now = System.currentTimeMillis();
        
        if (now - lastUse < cooldown) {
            long remainingSeconds = (cooldown - (now - lastUse)) / 1000;
            sendGroupMessage(groupId, "命令冷却中，请等待 " + remainingSeconds + " 秒后再试");
            return;
        }
        
        StringBuilder status = new StringBuilder("服务器状态：\n");
        
        // 在线玩家信息
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int maxPlayers = plugin.getServer().getMaxPlayers();
        status.append("在线玩家：").append(onlinePlayers).append("/").append(maxPlayers).append("\n");
        
        // TPS信息
        if (plugin.getConfig().getBoolean("status.show-tps", true)) {
            double tps;
            try {
                Object serverInstance = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
                double[] recentTps = (double[]) serverInstance.getClass().getField("recentTps").get(serverInstance);
                tps = recentTps[0];
            } catch (Exception e) {
                tps = 20.0; // 如果无法获取TPS，默认返回20
            }
            String tpsStr = String.format("%.1f", tps);
            status.append("TPS：").append(tpsStr).append("\n");
        }
        
        // 内存信息
        if (plugin.getConfig().getBoolean("status.show-memory", true)) {
            Runtime runtime = Runtime.getRuntime();
            int maxMemory = (int) (runtime.maxMemory() / 1024 / 1024);
            int totalMemory = (int) (runtime.totalMemory() / 1024 / 1024);
            int freeMemory = (int) (runtime.freeMemory() / 1024 / 1024);
            int usedMemory = totalMemory - freeMemory;
            status.append("内存使用：").append(usedMemory).append("MB/").append(maxMemory).append("MB\n");
        }
        
        // 在线玩家列表
        if (plugin.getConfig().getBoolean("status.show-player-list", true) && onlinePlayers > 0) {
            status.append("\n在线玩家列表：\n");
            plugin.getServer().getOnlinePlayers().forEach(player -> 
                status.append("- ").append(player.getName()).append("\n")
            );
        }
        
        sendGroupMessage(groupId, status.toString());
        statusCooldowns.put(groupId, now);
    }
    
    public void sendGroupMessage(long groupId, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "send_group_msg");
        
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        
        json.add("params", params);
        
        send(json.toString());
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        plugin.getLogger().warning("与OneBot服务器断开连接: " + reason);
        
        // 检查重连设置
        if (!plugin.getConfig().getBoolean("bot.reconnect.enabled", true)) {
            return;
        }
        
        int delay = plugin.getConfig().getInt("bot.reconnect.delay", 30);
        int maxAttempts = plugin.getConfig().getInt("bot.reconnect.max-attempts", 5);
        
        // 使用异步任务进行重连
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (!isOpen() && !isClosing()) {
                    plugin.getLogger().info("正在尝试重新连接到OneBot服务器...");
                    reconnectBlocking();
                }
            } catch (InterruptedException e) {
                plugin.getLogger().severe("重连时发生错误: " + e.getMessage());
            }
        }, delay * 20L);
    }
    
    public void disconnect() {
        try {
            super.close();
        } catch (Exception e) {
            plugin.getLogger().severe("断开连接时发生错误: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(Exception ex) {
        plugin.getLogger().severe("WebSocket发生错误: " + ex.getMessage());
    }
    
    // 添加CQ码处理方法
    private String processCQCodes(String message) {
        // 处理图片
        message = message.replaceAll("\\[CQ:image,[^\\]]*\\]", "[图片]");
        // 处理表情
        message = message.replaceAll("\\[CQ:face,id=\\d+\\]", "[表情]");
        // 处理@
        message = message.replaceAll("\\[CQ:at,qq=\\d+\\]", "@某人");
        return message;
    }
    
    // 添加心跳检测
    private void startHeartbeat() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (isOpen()) {
                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("action", "get_status");
                send(heartbeat.toString());
            }
        }, 20L * 30, 20L * 30); // 每30秒发送一次心跳
    }
} 