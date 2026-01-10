package kr.lunaf.nationSystem.skript.expr;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import kr.lunaf.nationSystem.api.NationSystemApi;
import kr.lunaf.nationSystem.skript.SkriptBridge;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.Event;

public class ExprNationName extends SimpleExpression<String> {
    private Expression<Player> playerExpr;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected String[] get(Event event) {
        NationSystemApi api = SkriptBridge.api();
        if (api == null) {
            return new String[0];
        }
        Player player = playerExpr.getSingle(event);
        if (player == null) {
            return new String[0];
        }
        return api.getNationName(player.getUniqueId()).map(name -> new String[]{name}).orElse(new String[0]);
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "nation of player";
    }
}
