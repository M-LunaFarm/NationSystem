package kr.lunaf.nationSystem.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

public class SyncExecutor implements Executor {
    private final Plugin plugin;

    public SyncExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Runnable command) {
        Bukkit.getScheduler().runTask(plugin, command);
    }
}
