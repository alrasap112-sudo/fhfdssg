package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.gildedledger.Money;
import net.gildedledger.Msg;
import net.gildedledger.TradeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Quantity + price composer with a two-click confirmation. Nothing is charged or moved here. */
public final class ComposePage extends Page {
	private final ServerPlayer seller;
	private final ItemStack template;
	private final int available;
	private int qty = 1;
	private long price = 1;
	private boolean armed = false;

	public ComposePage(LedgerContext ctx, ServerPlayer buyer, ServerPlayer seller, ItemStack template, int available) {
		super(ctx, buyer);
		this.seller = seller;
		this.template = template.copyWithCount(1);
		this.available = available;
	}

	@Override protected Component title() { return Ui.gold("Offer to " + seller.getName().getString()); }

	private void changeQty(int d) {
		qty = Math.max(1, Math.min(available, qty + d));
		armed = false;
		refresh();
	}

	private void changePrice(long d) {
		price = Math.max(1, Math.min(ctx.cfg().maxPrice, price + d));
		armed = false;
		refresh();
	}

	@Override
	protected void build() {
		String sName = seller.getName().getString();
		String iName = template.getHoverName().getString();
		long bal = ctx.store().balance(player.getUUID());

		put(13, Ui.show(template, qty, Ui.gray(""), Ui.gray("Seller: " + sName), Ui.gray("Available: " + available)));

		// Quantity row
		button(19, Ui.icon(Items.RED_STAINED_GLASS_PANE, Ui.red("-8")), c -> changeQty(-8));
		button(20, Ui.icon(Items.RED_STAINED_GLASS_PANE, Ui.red("-1")), c -> changeQty(-1));
		put(22, Ui.icon(Items.PAPER, Ui.white("Quantity: " + qty), Ui.gray("Available: " + available)));
		button(24, Ui.icon(Items.LIME_STAINED_GLASS_PANE, Ui.green("+1")), c -> changeQty(1));
		button(25, Ui.icon(Items.LIME_STAINED_GLASS_PANE, Ui.green("+8")), c -> changeQty(8));
		button(26, Ui.icon(Items.LIME_STAINED_GLASS_PANE, Ui.green("Max")), c -> changeQty(available));

		// Price row (shift-click = x10)
		long[] steps = {-1000, -100, -10, -1};
		for (int i = 0; i < 4; i++) {
			long s = steps[i];
			button(27 + i, Ui.icon(Items.RED_STAINED_GLASS_PANE, Ui.red(Money.format(s)),
				Ui.gray("Shift-click: x10")), c -> changePrice(s * (c.shift() ? 10 : 1)));
		}
		put(31, Ui.icon(Items.GOLD_INGOT, Ui.gold("Total price: " + Money.format(price)),
			Ui.gray("Per item: " + Money.perItem(price, qty))));
		long[] up = {1, 10, 100, 1000};
		for (int i = 0; i < 4; i++) {
			long s = up[i];
			button(32 + i, Ui.icon(Items.LIME_STAINED_GLASS_PANE, Ui.green("+" + Money.format(s)),
				Ui.gray("Shift-click: x10")), c -> changePrice(s * (c.shift() ? 10 : 1)));
		}

		// Summary + actions
		boolean afford = bal >= price;
		put(40, Ui.icon(Items.BOOK, Ui.white("Summary"),
			Ui.gray("Buy " + qty + "x " + iName), Ui.gray("From " + sName),
			Ui.gold("Total: " + Money.format(price) + " (" + Money.perItem(price, qty) + " each)"),
			Ui.gray("Your balance: " + Money.format(bal)),
			afford ? Ui.green("You can afford this.") : Ui.red("Insufficient funds.")));
		button(39, Ui.icon(Items.REDSTONE_BLOCK, Ui.red("Cancel")), c -> navigate(new BrowsePage(ctx, player, seller)));
		if (!armed) {
			button(41, Ui.icon(Items.EMERALD_BLOCK, Ui.green("Submit Offer"), Ui.gray("Click, then confirm."),
				Ui.gray("Nothing is charged until " + sName), Ui.gray("accepts.")), c -> {
				if (!afford) { say(Msg.err("Insufficient funds: you have " + Money.format(bal) + ".")); return; }
				armed = true;
				refresh();
			});
		} else {
			button(41, Ui.icon(Items.EMERALD_BLOCK, Ui.gold("CONFIRM"),
				Ui.white("Buy " + qty + "x " + iName + " from " + sName + " for " + Money.format(price) + "?"),
				Ui.green("Click to send.")), c -> submit());
		}
		button(49, Ui.icon(Items.ARROW, Ui.white("Back")), c -> navigate(new BrowsePage(ctx, player, seller)));
	}

	private void submit() {
		armed = false;
		TradeManager.Result r = ctx.trades().submit(player, seller, template, qty, price);
		say(r.ok() ? Msg.ok(r.message()) : Msg.err(r.message()));
		if (r.ok()) closeMenu(); else refresh();
	}
}
