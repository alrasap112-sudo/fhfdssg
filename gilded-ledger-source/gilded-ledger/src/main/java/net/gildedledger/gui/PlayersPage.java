package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PlayersPage extends PagedPage<ServerPlayer> {
	public PlayersPage(LedgerContext ctx, ServerPlayer p) { super(ctx, p); }

	@Override protected Component title() { return Ui.gold("Choose a player"); }
	@Override protected String emptyText() { return "No other players are online."; }
	@Override protected Page parent() { return new DashboardPage(ctx, player); }

	@Override
	protected List<ServerPlayer> load() {
		List<ServerPlayer> l = new ArrayList<>();
		for (ServerPlayer o : ctx.server().getPlayerList().getPlayers()) if (o != player) l.add(o);
		l.sort(Comparator.comparing(o -> o.getName().getString().toLowerCase(Locale.ROOT)));
		return l;
	}

	private String reason(ServerPlayer o) {
		if (!ctx.cfg().tradingEnabled) return "Trading is disabled.";
		if (!ctx.cfg().requireProximity) return null;
		if (!o.level().dimension().equals(player.level().dimension())) return "Different dimension.";
		if (o.distanceTo(player) > ctx.cfg().maxTradeDistance) return "Too far (max " + (int) ctx.cfg().maxTradeDistance + " blocks).";
		return null;
	}

	@Override
	protected ItemStack icon(ServerPlayer o) {
		String r = reason(o);
		Component dist = Ui.gray(o.level().dimension().equals(player.level().dimension())
			? "Distance: " + String.format(Locale.US, "%.1f", (double) o.distanceTo(player)) + " blocks" : "In another dimension");
		return r == null
			? Ui.head(o, Ui.green(o.getName().getString()), dist, Ui.gold("Click to trade"))
			: Ui.head(o, Ui.gray(o.getName().getString()), dist, Ui.red(r));
	}

	@Override
	protected void onSelect(ServerPlayer o, GuiMenu.Click c) {
		String r = reason(o);
		if (r != null) { say(net.gildedledger.Msg.err("Can't trade with " + o.getName().getString() + ": " + r)); return; }
		navigate(new BrowsePage(ctx, player, o));
	}
}
