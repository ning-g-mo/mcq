package cn.ningmo.mcq.filter;

import cn.ningmo.mcq.MCQ;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MessageFilter {
    private final MCQ plugin;
    private final Map<Long, RateLimit> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, String> wordFilter = new HashMap<>();
    private int maxLength;
    private int rateLimit;
    private boolean allowEmpty;
    private boolean allowPureImage;
    private String commandPrefix;
    
    public MessageFilter(MCQ plugin) {
        this.plugin = plugin;
        reload();
    }
    
    public void reload() {
        maxLength = plugin.getConfig().getInt("message-filter.max-length", 500);
        rateLimit = plugin.getConfig().getInt("message-filter.rate-limit", 60);
        allowEmpty = plugin.getConfig().getBoolean("message-filter.allow-empty", false);
        allowPureImage = plugin.getConfig().getBoolean("message-filter.allow-pure-image", true);
        commandPrefix = plugin.getConfig().getString("message-filter.command-prefix", "!");
        
        // 加载敏感词
        wordFilter.clear();
        if (plugin.getConfig().getBoolean("message-filter.word-filter.enabled", true)) {
            String replacement = plugin.getConfig().getString("message-filter.word-filter.replace-with", "*");
            List<String> words = plugin.getConfig().getStringList("message-filter.word-filter.words");
            for (String word : words) {
                wordFilter.put(word, replacement.repeat(word.length()));
            }
        }
    }
    
    public FilterResult filter(String message, long senderId) {
        // 检查消息长度
        if (message.length() > maxLength) {
            return new FilterResult(false, "消息长度超过限制");
        }
        
        // 检查空消息
        if (!allowEmpty && message.trim().isEmpty()) {
            return new FilterResult(false, "不允许发送空消息");
        }
        
        // 检查纯图片消息
        if (!allowPureImage && message.matches("^\\[CQ:image,[^\\]]*\\]$")) {
            return new FilterResult(false, "不允许发送纯图片消息");
        }
        
        // 检查速率限制
        if (!checkRateLimit(senderId)) {
            return new FilterResult(false, "发送消息太快，请稍后再试");
        }
        
        // 过滤敏感词
        String filteredMessage = filterWords(message);
        
        return new FilterResult(true, filteredMessage);
    }
    
    private boolean checkRateLimit(long senderId) {
        RateLimit limit = rateLimits.computeIfAbsent(senderId, k -> new RateLimit(rateLimit));
        return limit.tryAcquire();
    }
    
    private String filterWords(String message) {
        String result = message;
        for (Map.Entry<String, String> entry : wordFilter.entrySet()) {
            result = result.replaceAll(Pattern.quote(entry.getKey()), entry.getValue());
        }
        return result;
    }
    
    private static class RateLimit {
        private final int limit;
        private int count;
        private long resetTime;
        
        RateLimit(int limit) {
            this.limit = limit;
            this.count = 0;
            this.resetTime = System.currentTimeMillis() + 60000;
        }
        
        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now >= resetTime) {
                count = 0;
                resetTime = now + 60000;
            }
            
            if (count >= limit) {
                return false;
            }
            
            count++;
            return true;
        }
    }
    
    public static class FilterResult {
        private final boolean allowed;
        private final String message;
        
        FilterResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getMessage() {
            return message;
        }
    }
} 