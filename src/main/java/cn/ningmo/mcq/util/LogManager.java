package cn.ningmo.mcq.util;

import cn.ningmo.mcq.MCQ;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogManager {
    private final MCQ plugin;
    private final Logger logger;
    private boolean detailedErrors;
    private boolean debug;
    private boolean websocket;
    private boolean commands;
    private boolean messages;
    private boolean performance;
    private boolean api;
    private boolean whitelist;
    private List<String> ignoredExceptions;
    private int dedupTime;
    private final Map<String, Long> errorHistory = new HashMap<>();
    
    public LogManager(MCQ plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reloadConfig();
    }
    
    public void reloadConfig() {
        String level = plugin.getConfig().getString("logging.level", "INFO");
        try {
            logger.setLevel(Level.parse(level));
        } catch (IllegalArgumentException e) {
            logger.warning("无效的日志级别: " + level + "，使用默认级别 INFO");
            logger.setLevel(Level.INFO);
        }
        
        detailedErrors = plugin.getConfig().getBoolean("logging.detailed-errors", true);
        debug = plugin.getConfig().getBoolean("logging.debug", false);
        websocket = plugin.getConfig().getBoolean("logging.websocket", false);
        commands = plugin.getConfig().getBoolean("logging.commands", true);
        messages = plugin.getConfig().getBoolean("logging.messages", true);
        performance = plugin.getConfig().getBoolean("logging.performance", false);
        api = plugin.getConfig().getBoolean("logging.api", false);
        whitelist = plugin.getConfig().getBoolean("logging.whitelist", true);
        
        ignoredExceptions = plugin.getConfig().getStringList("logging.error-filter.ignored-exceptions");
        dedupTime = plugin.getConfig().getInt("logging.error-filter.dedup-time", 60);
    }
    
    public void debug(String message) {
        if (debug) {
            logger.info("[DEBUG] " + message);
        }
    }
    
    public void websocket(String message) {
        if (websocket) {
            logger.info("[WebSocket] " + message);
        }
    }
    
    public void command(String message) {
        if (commands) {
            logger.info("[Command] " + message);
        }
    }
    
    public void message(String message) {
        if (messages) {
            logger.info("[Message] " + message);
        }
    }
    
    public void performance(String message) {
        if (performance) {
            logger.info("[Performance] " + message);
        }
    }
    
    public void api(String message) {
        if (api) {
            logger.fine("[API] " + message);
        }
    }
    
    public void whitelist(String message) {
        if (whitelist) {
            logger.info("[Whitelist] " + message);
        }
    }
    
    public void error(String message, Throwable error) {
        // 检查是否为忽略的异常类型
        if (ignoredExceptions.contains(error.getClass().getName())) {
            if (debug) {
                logger.fine("[DEBUG] 已忽略异常: " + error.getClass().getName() + " - " + error.getMessage());
            }
            return;
        }
        
        // 错误消息去重
        String errorKey = error.getClass().getName() + ": " + error.getMessage();
        long now = System.currentTimeMillis();
        Long lastTime = errorHistory.get(errorKey);
        
        if (lastTime != null && now - lastTime < dedupTime * 1000) {
            if (debug) {
                logger.fine("[DEBUG] 忽略重复错误: " + errorKey);
            }
            return;
        }
        
        errorHistory.put(errorKey, now);
        
        // 记录错误
        if (detailedErrors) {
            logger.severe("[ERROR] " + message + "\n原因: " + error.getMessage() + "\n" + getStackTrace(error));
        } else {
            logger.severe("[ERROR] " + message + ": " + error.getMessage());
        }
    }
    
    private String getStackTrace(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) {
            if (element.getClassName().startsWith("cn.ningmo.mcq")) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
        }
        return sb.toString();
    }
    
    // Getters
    public boolean isDebug() { return debug; }
    public boolean isWebSocketDebug() { return websocket; }
    public boolean isCommandsEnabled() { return commands; }
    public boolean isMessagesEnabled() { return messages; }
    public boolean isPerformanceEnabled() { return performance; }
    public boolean isApiEnabled() { return api; }
    public boolean isWhitelistEnabled() { return whitelist; }
    public boolean isDetailedErrors() { return detailedErrors; }
} 