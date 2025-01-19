package cn.ningmo.mcq;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import cn.ningmo.mcq.command.CommandManager;
import cn.ningmo.mcq.util.LogManager;
import cn.ningmo.mcq.filter.MessageFilter;
import cn.ningmo.mcq.monitor.PerformanceMonitor;

public class MCQ extends JavaPlugin {
    private static MCQ instance;
    private BotClient botClient;
    private WhitelistManager whitelistManager;
    private CommandManager commandManager;
    private LogManager logManager;
    private MessageFilter messageFilter;
    private PerformanceMonitor performanceMonitor;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化日志管理器
        logManager = new LogManager(this);
        
        // 初始化命令管理器
        commandManager = new CommandManager(this);
        
        // 初始化白名单管理器
        whitelistManager = new WhitelistManager(this);
        
        // 初始化机器人客户端
        initBotClient();
        
        // 初始化消息过滤器
        messageFilter = new MessageFilter(this);
        
        // 初始化性能监控器
        performanceMonitor = new PerformanceMonitor(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MinecraftEventListener(this), this);
        
        // 注册命令执行器
        getCommand("mcq").setExecutor(new MCQCommand(this));
        
        getLogger().info("MCQ插件已启用！");
    }
    
    @Override
    public void onDisable() {
        if (botClient != null) {
            botClient.disconnect();
        }
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }
        getLogger().info("MCQ插件已禁用！");
    }
    
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (logManager != null) {
            logManager.reloadConfig();
        }
        if (commandManager != null) {
            commandManager.reload();
        }
        if (messageFilter != null) {
            messageFilter.reload();
        }
        if (performanceMonitor != null) {
            performanceMonitor.reload();
        }
    }
    
    private void initBotClient() {
        FileConfiguration config = getConfig();
        String wsUrl = config.getString("bot.ws-url", "ws://localhost:6700");
        botClient = new BotClient(this, wsUrl);
        botClient.connect();
    }
    
    public static MCQ getInstance() {
        return instance;
    }
    
    public BotClient getBotClient() {
        return botClient;
    }
    
    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    public LogManager getLogManager() {
        return logManager;
    }
    
    public MessageFilter getMessageFilter() {
        return messageFilter;
    }
    
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
} 