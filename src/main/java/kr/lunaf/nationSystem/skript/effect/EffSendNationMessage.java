package kr.lunaf.nationSystem.skript.effect;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import kr.lunaf.nationSystem.api.NationSystemApi;
import kr.lunaf.nationSystem.skript.SkriptBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class EffSendNationMessage extends Effect {
    private Expression<String> messageExpr;
    private Expression<Player> playerExpr;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.messageExpr = (Expression<String>) exprs[0];
        this.playerExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        NationSystemApi api = SkriptBridge.api();
        if (api == null) {
            return;
        }
        String message = messageExpr.getSingle(event);
        Player player = playerExpr.getSingle(event);
        if (message == null || player == null) {
            return;
        }
        api.getNationId(player.getUniqueId()).ifPresent(id -> api.sendNationMessage(id, message));
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "send nation message";
    }
}
