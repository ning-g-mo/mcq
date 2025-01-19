package cn.ningmo.mcq.monitor;

import cn.ningmo.mcq.MCQ;
import org.bukkit.scheduler.BukkitTask;

public class PerformanceMonitor {
    private final MCQ plugin;
    private BukkitTask monitorTask;
    private double tpsWarning;
    private int memoryWarning;
    private boolean sendWarnings;
    
    public PerformanceMonitor(MCQ plugin) {
        this.plugin = plugin;
        reload();
    }
    
    public void reload() {
        stop();
        
        if (!plugin.getConfig().getBoolean("performance.enabled", true)) {
            return;
        }
        
        tpsWarning = plugin.getConfig().getDouble("performance.tps-warning", 18.0);
        memoryWarning = plugin.getConfig().getInt("performance.memory-warning", 80);
        sendWarnings = plugin.getConfig().getBoolean("performance.send-warnings", true);
        
        int interval = plugin.getConfig().getInt("performance.interval", 300);
        start(interval);
    }
    
    private void start(int interval) {
        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            checkPerformance();
        }, interval * 20L, interval * 20L);
    }
    
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
    }
    
    private void checkPerformance() {
        // 检查TPS
        double tps;
        try {
            Object serverInstance = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
            double[] recentTps = (double[]) serverInstance.getClass().getField("recentTps").get(serverInstance);
            tps = recentTps[0];
            
            if (tps < tpsWarning) {
                String warning = String.format("服务器TPS过低: %.1f", tps);
                plugin.getLogManager().performance(warning);
                if (sendWarnings) {
                    broadcastWarning(warning);
                }
            }
        } catch (Exception ignored) {}
        
        // 检查内存
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        int memoryUsage = (int) ((usedMemory * 100) / maxMemory);
        if (memoryUsage > memoryWarning) {
            String warning = String.format("服务器内存使用率过高: %d%%", memoryUsage);
            plugin.getLogManager().performance(warning);
            if (sendWarnings) {
                broadcastWarning(warning);
            }
        }
    }
    
    private void broadcastWarning(String warning) {
        for (Long groupId : plugin.getConfig().getLongList("bot.groups")) {
            plugin.getBotClient().sendGroupMessage(groupId, "[警告] " + warning);
        }
    }
} 