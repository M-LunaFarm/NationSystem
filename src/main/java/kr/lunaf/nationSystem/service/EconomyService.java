package kr.lunaf.nationSystem.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class EconomyService {
    private final Economy economy;

    public EconomyService(Plugin plugin) {
        Economy provider = null;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                provider = rsp.getProvider();
            }
        }
        this.economy = provider;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public double getBalance(UUID playerUuid) {
        if (economy == null) {
            return 0.0;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.getBalance(player);
    }

    public boolean withdraw(UUID playerUuid, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(UUID playerUuid, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public CompletableFuture<Boolean> withdrawSync(UUID playerUuid, double amount, Executor syncExecutor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        syncExecutor.execute(() -> future.complete(withdraw(playerUuid, amount)));
        return future;
    }

    public CompletableFuture<Boolean> depositSync(UUID playerUuid, double amount, Executor syncExecutor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        syncExecutor.execute(() -> future.complete(deposit(playerUuid, amount)));
        return future;
    }
}
