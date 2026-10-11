package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.gildedledger.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public final class DashboardPage extends Page {
	public DashboardPage(LedgerContext ctx, ServerPlayer p) { super(ctx, p); }

	@Override protected Component title() { return Ui.gold("Gilded Ledger"); }

	@Override
	protected void build() {
		long bal = ctx.store().balance(player.getUUID());
		put(4, Ui.head(player, Ui.gold(player.getName().getString()),
			Ui.gray("Balance"), Ui.green(Money.format(bal)), Ui.gray("Safe on the server - never lost on death.")));

		button(20, Ui.icon(Items.EMERALD, Ui.green("Trade with a player"),
			Ui.gray("Browse a player's inventory"), Ui.gray("and make a purchase offer.")), c -> navigate(new PlayersPage(ctx, player)));

		int in = ctx.trades().incoming(player.getUUID()).size(), out = ctx.trades().outgoing(player.getUUID()).size();
		button(22, Ui.icon(Items.BELL, Ui.gold("Offers"),
			Ui.gray("Incoming: " + in), Ui.gray("Outgoing: " + out), Ui.gray("Accept, reject or cancel.")), c -> navigate(new OffersPage(ctx, player)));

		button(24, Ui.icon(Items.BOOK, Ui.white("Transaction history"),
			Ui.gray("Your last " + ctx.cfg().maxHistoryEntries + " trades.")), c -> navigate(new HistoryPage(ctx, player)));

		put(40, Ui.icon(Items.PAPER, Ui.gold("How it works"),
			Ui.gray("1. Hold the wallet, right-click a player."), Ui.gray("2. Pick an item, set quantity and price."),
			Ui.gray("3. They accept or reject."), Ui.gray("4. Items and money swap instantly.")));
		button(49, Ui.icon(Items.BARRIER, Ui.red("Close")), c -> closeMenu());
	}
}
