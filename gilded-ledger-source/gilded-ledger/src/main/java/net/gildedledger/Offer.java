package net.gildedledger;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** One purchase request. Terminal states are entered exactly once (the offer is removed from the pending map first). */
public final class Offer {
	public enum State { DRAFT, PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED, FAILED, COMPLETED }

	public final String id;
	public final UUID buyer, seller;
	public final String buyerName, sellerName;
	public final ItemStack template; // count 1, taken from the server-side inventory, never from the client
	public final String itemName;
	public final int quantity;
	public final long price;
	public final long createdAt, expiresAt;
	public State state = State.PENDING;

	Offer(String id, UUID buyer, String buyerName, UUID seller, String sellerName, ItemStack template, int quantity,
		  long price, long expiresAt) {
		this.id = id;
		this.buyer = buyer; this.buyerName = buyerName;
		this.seller = seller; this.sellerName = sellerName;
		this.template = template.copyWithCount(1);
		this.itemName = template.getHoverName().getString();
		this.quantity = quantity;
		this.price = price;
		this.createdAt = System.currentTimeMillis();
		this.expiresAt = expiresAt;
	}
}
