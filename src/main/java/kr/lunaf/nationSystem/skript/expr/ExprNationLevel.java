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

public class ExprNationLevel extends SimpleExpression<Integer> {
    private Expression<Player> playerExpr;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Integer[] get(Event event) {
        NationSystemApi api = SkriptBridge.api();
        if (api == null) {
            return new Integer[0];
        }
        Player player = playerExpr.getSingle(event);
        if (player == null) {
            return new Integer[0];
        }
        return api.getMembership(player.getUniqueId())
            .map(member -> new Integer[]{member.level()})
            .orElse(new Integer[0]);
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Integer> getReturnType() {
        return Integer.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "nation level of player";
    }
}
