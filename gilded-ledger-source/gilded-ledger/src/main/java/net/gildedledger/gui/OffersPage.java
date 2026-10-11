package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.gildedledger.Money;
import net.gildedledger.Msg;
import net.gildedledger.Offer;
import net.gildedledger.TradeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Incoming offers first (left-click accept, right-click reject), then your outgoing offers (click to cancel). */
public final class OffersPage extends PagedPage<Offer> {
	public OffersPage(LedgerContext ctx, ServerPlayer p) { super(ctx, p); }

	@Override protected Component title() { return Ui.gold("Offers"); }
	@Override protected String emptyText() { return "No pending offers."; }
	@Override protected Page parent() { return new DashboardPage(ctx, player); }

	@Override
	protected List<Offer> load() {
		List<Offer> l = new ArrayList<>(ctx.trades().incoming(player.getUUID()));
		l.addAll(ctx.trades().outgoing(player.getUUID()));
		return l;
	}

	private boolean incoming(Offer o) { return o.seller.equals(player.getUUID()); }

	@Override
	protected ItemStack icon(Offer o) {
		long secs = Math.max(0, (o.expiresAt - System.currentTimeMillis()) / 1000);
		if (incoming(o)) {
			return Ui.show(o.template, o.quantity, Ui.gray(""),
				Ui.white(o.buyerName + " wants " + o.quantity + "x for " + Money.format(o.price)),
				Ui.gray(Money.perItem(o.price, o.quantity) + " each"),
				Ui.gray("Buyer balance: " + Money.format(ctx.store().balance(o.buyer))),
				Ui.gray("Expires in " + secs + "s"),
				Ui.green("Left-click: ACCEPT"), Ui.red("Right-click: REJECT"));
		}
		return Ui.show(o.template, o.quantity, Ui.gray(""),
			Ui.white("You offered " + Money.format(o.price) + " to " + o.sellerName),
			Ui.gray("Expires in " + secs + "s"), Ui.red("Click: CANCEL"));
	}

	@Override
	protected void onSelect(Offer o, GuiMenu.Click c) {
		TradeManager.Result r;
		if (incoming(o)) r = c.right() ? ctx.trades().reject(player, o.id) : ctx.trades().accept(player, o.id);
		else r = ctx.trades().cancel(player, o.id);
		say(r.ok() ? Msg.ok(r.message()) : Msg.err(r.message()));
		refresh();
	}
}
