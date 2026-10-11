package net.gildedledger.gui;

import net.gildedledger.InventoryOps;
import net.gildedledger.LedgerContext;
import net.gildedledger.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Read-only view of the seller's items, aggregated by item type. Selection is by server-held list index only. */
public final class BrowsePage extends PagedPage<InventoryOps.Entry> {
	private final ServerPlayer seller;

	public BrowsePage(LedgerContext ctx, ServerPlayer buyer, ServerPlayer seller) {
		super(ctx, buyer);
		this.seller = seller;
	}

	@Override protected Component title() { return Ui.gold("Trading with " + seller.getName().getString()); }
	@Override protected String emptyText() { return "This player has nothing to sell."; }
	@Override protected Page parent() { return new PlayersPage(ctx, player); }

	@Override
	protected List<InventoryOps.Entry> load() {
		if (ctx.server().getPlayerList().getPlayer(seller.getUUID()) != seller) return List.of();
		return InventoryOps.aggregate(seller.getInventory());
	}

	@Override
	protected ItemStack icon(InventoryOps.Entry e) {
		return Ui.show(e.template, e.total, Ui.gray(""), Ui.green("Available: " + e.total), Ui.gold("Click to make an offer"));
	}

	@Override
	protected void onSelect(InventoryOps.Entry e, GuiMenu.Click c) {
		if (ctx.server().getPlayerList().getPlayer(seller.getUUID()) != seller) {
			say(Msg.err("That player is no longer online."));
			return;
		}
		int have = InventoryOps.count(seller.getInventory(), e.template);
		if (have < 1) { say(Msg.err("That item is gone. Refreshing.")); refresh(); return; }
		navigate(new ComposePage(ctx, player, seller, e.template, have));
	}
}
