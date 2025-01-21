package cn.ningmo.mcq;

import cn.ningmo.mcq.command.CustomCommand;
import cn.ningmo.mcq.filter.MessageFilter;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.lang.reflect.Method;

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
        plugin.getLogManager().websocket("成功连接到OneBot服务器！");
        startHeartbeat();
    }
    
    @Override
    public void onMessage(String message) {
        try {
            if (plugin.getLogManager().isWebSocketDebug()) {
                plugin.getLogManager().debug("收到消息: " + message);
            }
            
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            
            // 处理心跳响应
            if (json.has("echo") && "heartbeat".equals(json.get("echo").getAsString())) {
                if (plugin.getLogManager().isWebSocketDebug()) {
                    plugin.getLogManager().debug("收到心跳响应");
                }
                return;
            }
            
            // 处理事件消息
            if (!json.has("post_type")) {
                return;
            }
            
            String postType = json.get("post_type").getAsString();
            switch (postType) {
                case "message":
                    handleMessageEvent(json);
                    break;
                case "notice":
                    handleNoticeEvent(json);
                    break;
                case "request":
                    handleRequestEvent(json);
                    break;
                case "meta_event":
                    handleMetaEvent(json);
                    break;
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理消息时发生错误: " + message, e);
            }
        }
    }
    
    private void handleMessageEvent(JsonObject json) {
        try {
            String messageType = json.get("message_type").getAsString();
            switch (messageType) {
                case "private":
                    handlePrivateMessage(json);
                    break;
                case "group":
                    handleGroupMessage(json);
                    break;
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理消息事件时发生错误", e);
            }
        }
    }
    
    private void handleNoticeEvent(JsonObject json) {
        try {
            String noticeType = json.get("notice_type").getAsString();
            switch (noticeType) {
                case "group_increase":
                    // 处理群成员增加
                    break;
                case "group_decrease":
                    // 处理群成员减少
                    break;
                // 添加其他通知类型处理
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理通知事件时发生错误", e);
            }
        }
    }
    
    private void handleMetaEvent(JsonObject json) {
        try {
            String metaEventType = json.get("meta_event_type").getAsString();
            if ("heartbeat".equals(metaEventType)) {
                if (plugin.getLogManager().isWebSocketDebug()) {
                    plugin.getLogManager().debug("收到心跳事件");
                }
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理元事件时发生错误", e);
            }
        }
    }
    
    private void handleGroupMessage(JsonObject json) {
        try {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().debug("收到群消息: " + json.toString());
            }

            // 检查必要字段
            if (!json.has("message") || !json.has("group_id") || 
                !json.has("sender") || !json.get("sender").isJsonObject()) {
                return;
            }

            // 获取群号
            long groupId = json.get("group_id").getAsLong();
            
            // 检查是否为配置的群
            List<Long> configGroups = plugin.getConfig().getLongList("bot.groups");
            if (!configGroups.contains(groupId)) {
                if (plugin.getLogManager().isDebug()) {
                    plugin.getLogManager().debug("忽略非配置群消息, 群号: " + groupId);
                }
                return;
            }

            // 获取发送者信息
            JsonObject sender = json.get("sender").getAsJsonObject();
            String senderName = sender.get("nickname").getAsString();
            
            // 获取消息内容
            String message;
            if (json.has("message_format") && "array".equals(json.get("message_format").getAsString())) {
                // 处理数组格式消息
                JsonArray messageArray = json.getAsJsonArray("message");
                StringBuilder messageBuilder = new StringBuilder();
                
                for (JsonElement element : messageArray) {
                    JsonObject msgObj = element.getAsJsonObject();
                    String type = msgObj.get("type").getAsString();
                    JsonObject data = msgObj.get("data").getAsJsonObject();
                    
                    switch (type) {
                        case "text":
                            messageBuilder.append(data.get("text").getAsString());
                            break;
                        case "at":
                            messageBuilder.append("@").append(data.get("name").getAsString()).append(" ");
                            break;
                        case "image":
                            messageBuilder.append("[图片] ");
                            break;
                        // 添加其他类型的处理...
                    }
                }
                message = messageBuilder.toString().trim();
            } else {
                // 处理原始消息
                message = json.get("raw_message").getAsString();
            }

            // 过滤消息
            MessageFilter.FilterResult filterResult = plugin.getMessageFilter().filter(message, sender.get("user_id").getAsLong());
            if (!filterResult.isAllowed()) {
                return;
            }
            message = filterResult.getMessage();

            // 转发到服务器
            String format = plugin.getConfig().getString("message-format.qq-to-mc", "§b[QQ] §f{sender}: {message}");
            String finalMessage = format
                .replace("{sender}", senderName)
                .replace("{message}", message);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().broadcastMessage(finalMessage);
            });

        } catch (Exception e) {
            plugin.getLogManager().error("处理群消息时发生错误", e);
        }
    }
    
    private void handleCommand(String message, long senderId, long groupId) {
        plugin.getLogManager().command(String.format(
            "用户 %d 在群 %d 中执行命令: %s", 
            senderId, groupId, message
        ));
        
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
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        callApi("send_group_msg", params, null);
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        plugin.getLogManager().websocket("与OneBot服务器断开连接: " + reason);
        
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
        plugin.getLogManager().error("WebSocket连接错误", ex);
    }
    
    // 添加CQ码处理方法
    private String processCQCodes(String message) {
        try {
            // 替换CQ码中的特殊字符
            message = message.replaceAll("&#91;", "[")
                            .replaceAll("&#93;", "]")
                            .replaceAll("&amp;", "&");
            
            // 处理图片CQ码
            message = message.replaceAll("\\[CQ:image,[^\\]]*\\]", "[图片]");
            
            // 处理表情CQ码
            message = message.replaceAll("\\[CQ:face,id=\\d+\\]", "");
            
            return message;
        } catch (Exception e) {
            plugin.getLogManager().error("处理CQ码时发生错误", e);
            return message;
        }
    }
    
    // 添加心跳检测
    private void startHeartbeat() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (isOpen()) {
                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("action", "get_status");
                heartbeat.addProperty("echo", "heartbeat");
                send(heartbeat.toString());
            }
        }, 20L * 30, 20L * 30); // 每30秒发送一次心跳
    }
    
    // 添加API调用方法
    private void callApi(String action, JsonObject params, String echo) {
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        request.add("params", params);
        if (echo != null) {
            request.addProperty("echo", echo);
        }
        send(request.toString());
    }
    
    private void handleRequestEvent(JsonObject json) {
        try {
            String requestType = json.get("request_type").getAsString();
            switch (requestType) {
                case "friend":
                    handleFriendRequest(json);
                    break;
                case "group":
                    handleGroupRequest(json);
                    break;
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理请求事件时发生错误", e);
            }
        }
    }
    
    private void handleFriendRequest(JsonObject json) {
        // 好友请求处理逻辑
        if (plugin.getLogManager().isDebug()) {
            plugin.getLogManager().debug("收到好友请求: " + json.toString());
        }
    }
    
    private void handleGroupRequest(JsonObject json) {
        // 群请求处理逻辑
        if (plugin.getLogManager().isDebug()) {
            plugin.getLogManager().debug("收到群请求: " + json.toString());
        }
    }
    
    private void handlePrivateMessage(JsonObject json) {
        try {
            // 添加详细的空值检查
            if (!json.has("user_id") || !json.has("message") || !json.has("sender")) {
                plugin.getLogManager().debug("私聊消息缺少必要字段: " + json.toString());
                return;
            }
            
            // 添加类型检查
            if (!json.get("user_id").isJsonPrimitive() || 
                !json.get("message").isJsonPrimitive() || 
                !json.get("sender").isJsonObject()) {
                plugin.getLogManager().debug("私聊消息字段类型错误: " + json.toString());
                return;
            }
            
            long userId = json.get("user_id").getAsLong();
            String message = json.get("message").getAsString();
            
            // 检查是否为管理员
            List<Long> admins = plugin.getConfig().getLongList("bot.admins");
            if (!admins.contains(userId)) {
                if (plugin.getLogManager().isDebug()) {
                    plugin.getLogManager().debug(String.format(
                        "忽略非管理员私聊消息: userId=%d", userId
                    ));
                }
                return;
            }
            
            // 处理管理员私聊命令
            if (message.startsWith("!")) {
                handlePrivateCommand(message, userId);
            }
            
            if (plugin.getLogManager().isMessagesEnabled()) {
                JsonObject sender = json.getAsJsonObject("sender");
                String senderName = sender.has("nickname") ? 
                    sender.get("nickname").getAsString() : 
                    String.valueOf(userId);
                    
                plugin.getLogManager().message(String.format(
                    "收到私聊消息: %s -> %s", 
                    senderName, message
                ));
            }
        } catch (Exception e) {
            if (plugin.getLogManager().isDebug()) {
                plugin.getLogManager().error("处理私聊消息时发生错误", e);
                plugin.getLogManager().debug("原始消息: " + json.toString());
            }
        }
    }
    
    private void handlePrivateCommand(String message, long userId) {
        plugin.getLogManager().command(String.format(
            "管理员 %d 执行私聊命令: %s", 
            userId, message
        ));
        
        String[] args = message.substring(1).split(" ");
        String cmd = args[0].toLowerCase();
        
        Optional<CustomCommand> command = plugin.getCommandManager().getCommand(cmd);
        if (!command.isPresent()) {
            return;
        }
        
        CustomCommand customCmd = command.get();
        if (!customCmd.isAdminOnly()) {
            return;
        }
        
        // 执行管理员命令动作
        executePrivateActions(customCmd.getActions(), args, userId);
    }
    
    private void executePrivateActions(List<String> actions, String[] args, long userId) {
        for (String action : actions) {
            // 替换参数
            String processedAction = processActionPlaceholders(action, args);
            
            // 执行动作
            switch (processedAction.split(" ")[0]) {
                case "reload":
                    plugin.reloadConfig();
                    sendPrivateMessage(userId, "配置文件已重载！");
                    break;
                    
                case "status":
                    sendServerStatusPrivate(userId);
                    break;
                    
                // 添加更多私聊命令动作...
            }
        }
    }
    
    private void sendPrivateMessage(long userId, String message) {
        JsonObject params = new JsonObject();
        params.addProperty("user_id", userId);
        params.addProperty("message", message);
        callApi("send_private_msg", params, null);
    }
    
    private void sendServerStatusPrivate(long userId) {
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
        
        sendPrivateMessage(userId, status.toString());
    }
} 