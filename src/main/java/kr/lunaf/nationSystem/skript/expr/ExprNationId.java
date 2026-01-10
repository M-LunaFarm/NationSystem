package kr.lunaf.nationSystem.skript.expr;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import kr.lunaf.nationSystem.api.NationSystemApi;
import kr.lunaf.nationSystem.skript.SkriptBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

public class ExprNationId extends SimpleExpression<Long> {
    private Expression<Player> playerExpr;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Long[] get(Event event) {
        NationSystemApi api = SkriptBridge.api();
        if (api == null) {
            return new Long[0];
        }
        Player player = playerExpr.getSingle(event);
        if (player == null) {
            return new Long[0];
        }
        return api.getNationId(player.getUniqueId()).map(id -> new Long[]{id}).orElse(new Long[0]);
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Long> getReturnType() {
        return Long.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "nation id of player";
    }
}
