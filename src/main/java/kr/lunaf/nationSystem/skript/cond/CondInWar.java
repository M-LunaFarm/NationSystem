package kr.lunaf.nationSystem.skript.cond;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import kr.lunaf.nationSystem.api.NationSystemApi;
import kr.lunaf.nationSystem.skript.SkriptBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class CondInWar extends Condition {
    private Expression<Player> playerExpr;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    public boolean check(Event event) {
        NationSystemApi api = SkriptBridge.api();
        if (api == null) {
            return false;
        }
        Player player = playerExpr.getSingle(event);
        if (player == null) {
            return false;
        }
        return api.getNationId(player.getUniqueId()).map(api::isInWar).orElse(false);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "player is in war";
    }
}
