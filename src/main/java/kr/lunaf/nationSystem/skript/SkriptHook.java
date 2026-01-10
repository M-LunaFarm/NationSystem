package kr.lunaf.nationSystem.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import kr.lunaf.nationSystem.skript.expr.ExprNationId;
import kr.lunaf.nationSystem.skript.expr.ExprNationLevel;
import kr.lunaf.nationSystem.skript.expr.ExprNationName;
import kr.lunaf.nationSystem.skript.cond.CondInNation;
import kr.lunaf.nationSystem.skript.cond.CondInWar;
import kr.lunaf.nationSystem.skript.effect.EffSendNationMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SkriptHook {
    private final JavaPlugin plugin;

    public SkriptHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            return;
        }
        SkriptAddon addon = Skript.registerAddon(plugin);
        try {
            addon.loadClasses("kr.lunaf.nationSystem.skript");
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to load Skript addon classes: " + e.getMessage());
            return;
        }
        Skript.registerExpression(ExprNationName.class, String.class, ch.njol.skript.lang.ExpressionType.PROPERTY,
            "nation of %player%");
        Skript.registerExpression(ExprNationId.class, Long.class, ch.njol.skript.lang.ExpressionType.PROPERTY,
            "nation id of %player%");
        Skript.registerExpression(ExprNationLevel.class, Integer.class, ch.njol.skript.lang.ExpressionType.PROPERTY,
            "nation level of %player%");
        Skript.registerCondition(CondInNation.class, "%player% is in nation");
        Skript.registerCondition(CondInWar.class, "%player% is in war");
        Skript.registerEffect(EffSendNationMessage.class, "send nation message %string% to %player%");
    }
}
