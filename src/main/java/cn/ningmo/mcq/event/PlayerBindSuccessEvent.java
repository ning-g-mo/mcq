package cn.ningmo.mcq.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerBindSuccessEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String playerName;

    public PlayerBindSuccessEvent(String playerName) {
        this.playerName = playerName;
    }

    public String getPlayerName() {
        return playerName;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
} 