package cn.ningmo.mcq.command;

import cn.ningmo.mcq.MCQ;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommandManager {
    private final MCQ plugin;
    private final Map<String, CustomCommand> commands = new HashMap<>();
    private final Map<String, String> aliasMap = new HashMap<>();
    
    public CommandManager(MCQ plugin) {
        this.plugin = plugin;
        loadCommands();
    }
    
    private void loadCommands() {
        commands.clear();
        aliasMap.clear();
        
        FileConfiguration config = plugin.getConfig();  // 直接使用getConfig()而不是触发reload
        ConfigurationSection cmdSection = config.getConfigurationSection("commands");
        if (cmdSection == null) return;
        
        for (String key : cmdSection.getKeys(false)) {
            ConfigurationSection section = cmdSection.getConfigurationSection(key);
            if (section == null) continue;
            
            String name = section.getString("name", key);
            List<String> aliases = section.getStringList("aliases");
            String permission = section.getString("permission", "");
            int cooldown = section.getInt("cooldown", 0);
            boolean adminOnly = section.getBoolean("admin-only", false);
            String usage = section.getString("usage", "");
            String description = section.getString("description", "");
            List<String> actions = section.getStringList("actions");
            
            CustomCommand cmd = new CustomCommand(
                name,
                aliases,
                permission,
                cooldown,
                adminOnly,
                usage,
                description,
                actions
            );
            
            commands.put(name.toLowerCase(), cmd);
            for (String alias : aliases) {
                aliasMap.put(alias.toLowerCase(), name.toLowerCase());
            }
        }
    }
    
    public Optional<CustomCommand> getCommand(String input) {
        String cmdName = aliasMap.getOrDefault(input.toLowerCase(), input.toLowerCase());
        return Optional.ofNullable(commands.get(cmdName));
    }
    
    public void reload() {
        commands.clear();
        aliasMap.clear();
        loadCommands();
    }
} 