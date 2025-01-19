package cn.ningmo.mcq.command;

import java.util.List;

public class CustomCommand {
    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final int cooldown;
    private final boolean adminOnly;
    private final String usage;
    private final String description;
    private final List<String> actions;
    
    public CustomCommand(String name, List<String> aliases, String permission, 
                        int cooldown, boolean adminOnly, String usage, 
                        String description, List<String> actions) {
        this.name = name;
        this.aliases = aliases;
        this.permission = permission;
        this.cooldown = cooldown;
        this.adminOnly = adminOnly;
        this.usage = usage;
        this.description = description;
        this.actions = actions;
    }
    
    // Getters
    public String getName() { return name; }
    public List<String> getAliases() { return aliases; }
    public String getPermission() { return permission; }
    public int getCooldown() { return cooldown; }
    public boolean isAdminOnly() { return adminOnly; }
    public String getUsage() { return usage; }
    public String getDescription() { return description; }
    public List<String> getActions() { return actions; }
} 