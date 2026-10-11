package net.gildedledger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative trade engine. Every method MUST run on the server thread (commands and menu clicks do), which
 * serializes all trades. An offer is removed from {@code pending} BEFORE it is processed, so a duplicate
 * Accept/Reject/Cancel for the same id can never be applied twice.
 */
public final class TradeManager {
	public record Result(boolean ok, String message) {
		static Result ok(String m) { return new Result(true, m); }
		static Result fail(String m) { return new Result(false, m); }
	}

	private final MinecraftServer server;
	private final EconomyStore store;
	private final LedgerConfig cfg;
	private final Map<String, Offer> pending = new LinkedHashMap<>();
	private final SecureRandom rng = new SecureRandom();

	public TradeManager(MinecraftServer server, EconomyStore store, LedgerConfig cfg) {
		this.server = server;
		this.store = store;
		this.cfg = cfg;
	}

	// ---------------------------------------------------------------- queries

	public Offer get(String id) { return pending.get(id); }

	public List<Offer> incoming(UUID seller) {
		List<Offer> l = new ArrayList<>();
		for (Offer o : pending.values()) if (o.seller.equals(seller)) l.add(o);
		return l;
	}

	public List<Offer> outgoing(UUID buyer) {
		List<Offer> l = new ArrayList<>();
		for (Offer o : pending.values()) if (o.buyer.equals(buyer)) l.add(o);
		return l;
	}

	// ---------------------------------------------------------------- submit

	public Result submit(ServerPlayer buyer, ServerPlayer seller, ItemStack template, int qty, long price) {
		if (!cfg.tradingEnabled) return Result.fail("Trading is disabled on this server.");
		if (buyer.getUUID().equals(seller.getUUID())) return Result.fail("You can't trade with yourself.");
		if (server.getPlayerList().getPlayer(seller.getUUID()) != seller) return Result.fail("That player is no longer online.");
		if (cfg.requireProximity) {
			if (!buyer.level().dimension().equals(seller.level().dimension()))
				return Result.fail("That player is in a different dimension.");
			if (buyer.distanceTo(seller) > cfg.maxTradeDistance)
				return Result.fail("You must be within " + (int) cfg.maxTradeDistance + " blocks of " + name(seller) + ".");
		}
		if (template.isEmpty() || WalletItem.is(template)) return Result.fail("That item can't be traded.");
		if (qty < 1) return Result.fail("Quantity must be at least 1.");
		if (price < 1 || price > cfg.maxPrice) return Result.fail("Price must be between $1 and " + Money.format(cfg.maxPrice) + ".");
		long bal = store.balance(buyer.getUUID());
		if (bal < price) return Result.fail("Insufficient funds: you have " + Money.format(bal) + " but offered " + Money.format(price) + ".");
		if (InventoryOps.count(seller.getInventory(), template) < qty) return Result.fail(name(seller) + " no longer has that many.");
		if (!InventoryOps.canFit(buyer.getInventory(), template, qty)) return Result.fail("You don't have enough inventory space for that.");
		if (store.balance(seller.getUUID()) + price > Money.MAX) return Result.fail("The seller's balance can't hold that much.");
		if (outgoing(buyer.getUUID()).size() >= cfg.maxPendingOffersPerPlayer)
			return Result.fail("You have too many pending offers. Wait for them to resolve or cancel some.");
		for (Offer o : pending.values())
			if (o.buyer.equals(buyer.getUUID()) && o.seller.equals(seller.getUUID()) && o.quantity == qty && o.price == price
				&& ItemStack.isSameItemSameComponents(o.template, template))
				return Result.fail("You already sent that exact offer.");

		Offer o = new Offer(newId(), buyer.getUUID(), name(buyer), seller.getUUID(), name(seller), template, qty, price,
			System.currentTimeMillis() + cfg.offerExpirySeconds * 1000L);
		pending.put(o.id, o);
		notifyIncoming(o, seller);
		return Result.ok("Offer sent to " + name(seller) + ": " + qty + "x " + o.itemName + " for " + Money.format(price)
			+ ". It expires in " + cfg.offerExpirySeconds + "s.");
	}

	private String newId() {
		String id;
		do { id = Long.toHexString(rng.nextLong() & 0xFFFFFFFFL); } while (pending.containsKey(id));
		return id;
	}

	private void notifyIncoming(Offer o, ServerPlayer seller) {
		Component msg = Msg.prefix()
			.append(Component.literal(o.buyerName + " wants to buy " + o.quantity + "x " + o.itemName + " from you for "
				+ Money.format(o.price) + " (" + Money.perItem(o.price, o.quantity) + " each). ").withStyle(ChatFormatting.WHITE))
			.append(Msg.button("Accept", ChatFormatting.GREEN, "/cash offer accept " + o.id, "Accept and complete the trade"))
			.append(Component.literal(" "))
			.append(Msg.button("Reject", ChatFormatting.RED, "/cash offer reject " + o.id, "Reject this offer"))
			.append(Component.literal(" "))
			.append(Msg.button("Details", ChatFormatting.AQUA, "/cash offers", "Open the incoming offers panel"));
		seller.sendSystemMessage(msg);
		seller.displayClientMessage(Component.literal("Trade offer from " + o.buyerName + " - " + Money.format(o.price))
			.withStyle(ChatFormatting.GOLD), true);
		seller.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- resolve

	public Result reject(ServerPlayer seller, String id) {
		Offer o = pending.get(id);
		if (o == null) return Result.fail("That offer is no longer available.");
		if (!o.seller.equals(seller.getUUID())) return Result.fail("That offer isn't addressed to you.");
		pending.remove(id);
		finish(o, Offer.State.REJECTED, "REJECTED");
		return Result.ok("Offer from " + o.buyerName + " rejected.");
	}

	public Result cancel(ServerPlayer buyer, String id) {
		Offer o = pending.get(id);
		if (o == null) return Result.fail("That offer is no longer available.");
		if (!o.buyer.equals(buyer.getUUID())) return Result.fail("That isn't your offer.");
		pending.remove(id);
		finish(o, Offer.State.CANCELLED, "CANCELLED");
		return Result.ok("Offer cancelled.");
	}

	public Result accept(ServerPlayer seller, String id) {
		Offer o = pending.get(id);
		if (o == null) return Result.fail("That offer is no longer available.");
		if (!o.seller.equals(seller.getUUID())) return Result.fail("That offer isn't addressed to you.");
		pending.remove(id); // from here on this offer can never be processed again

		if (System.currentTimeMillis() > o.expiresAt) {
			finish(o, Offer.State.EXPIRED, "EXPIRED");
			return Result.fail("That offer has expired.");
		}
		ServerPlayer buyer = server.getPlayerList().getPlayer(o.buyer);
		if (buyer == null) {
			finish(o, Offer.State.CANCELLED, "CANCELLED");
			return Result.fail(o.buyerName + " went offline; the offer was cancelled.");
		}
		if (!cfg.tradingEnabled) {
			finish(o, Offer.State.FAILED, "FAILED");
			return Result.fail("Trading is disabled on this server.");
		}

		String problem = validate(o, buyer, seller);
		if (problem != null) {
			finish(o, Offer.State.FAILED, "FAILED");
			failNotice(o, buyer, seller, problem);
			return Result.fail(problem);
		}

		// Atomic section: snapshot, mutate, persist; any failure restores everything.
		ItemStack[] buyerSnap = InventoryOps.snapshot(buyer.getInventory());
		ItemStack[] sellerSnap = InventoryOps.snapshot(seller.getInventory());
		long buyerBefore = store.balance(o.buyer), sellerBefore = store.balance(o.seller);

		boolean moved = InventoryOps.remove(seller.getInventory(), o.template, o.quantity)
			&& InventoryOps.add(buyer.getInventory(), o.template, o.quantity);
		boolean paid = moved && store.transfer(o.buyer, o.seller, o.price);
		EconomyStore.TradeRecord rec = record(o, "COMPLETED");
		boolean saved = false;
		if (paid) {
			store.addRecord(o.buyer, rec);
			store.addRecord(o.seller, rec);
			try { store.save(); saved = true; } catch (IOException e) { GildedLedger.LOG.error("Trade {} could not be persisted; rolling back", o.id, e); }
		}
		if (!saved) {
			InventoryOps.restore(buyer.getInventory(), buyerSnap);
			InventoryOps.restore(seller.getInventory(), sellerSnap);
			store.setBalance(o.buyer, buyerBefore);
			store.setBalance(o.seller, sellerBefore);
			store.removeRecord(o.buyer, rec.id);
			store.removeRecord(o.seller, rec.id);
			finish(o, Offer.State.FAILED, "FAILED");
			String m = "The trade could not be completed safely. Nothing was changed.";
			failNotice(o, buyer, seller, m);
			return Result.fail(m);
		}

		o.state = Offer.State.COMPLETED;
		GildedLedger.LOG.info("Trade {} completed: {} -> {} x{} {} for {}", o.id, o.sellerName, o.buyerName, o.quantity, o.itemName, o.price);
		buyer.sendSystemMessage(Msg.ok("Purchase complete: " + o.quantity + "x " + o.itemName + " for " + Money.format(o.price)
			+ ". Balance: " + Money.format(store.balance(o.buyer))));
		seller.sendSystemMessage(Msg.ok("Sale complete: " + o.quantity + "x " + o.itemName + " to " + o.buyerName + " for "
			+ Money.format(o.price) + ". Balance: " + Money.format(store.balance(o.seller))));
		buyer.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		seller.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		return Result.ok("Trade completed.");
	}

	/** Re-checks everything against the live server state. @return a problem description or null. */
	private String validate(Offer o, ServerPlayer buyer, ServerPlayer seller) {
		if (store.balance(o.buyer) < o.price) return o.buyerName + " no longer has enough money (insufficient funds).";
		if (InventoryOps.count(seller.getInventory(), o.template) < o.quantity)
			return "The seller no longer has " + o.quantity + "x " + o.itemName + ". The offer is no longer valid.";
		if (!InventoryOps.canFit(buyer.getInventory(), o.template, o.quantity))
			return o.buyerName + " doesn't have enough inventory space.";
		if (store.balance(o.seller) + o.price > Money.MAX) return "The seller's balance can't hold that much.";
		return null;
	}

	private void failNotice(Offer o, ServerPlayer buyer, ServerPlayer seller, String why) {
		buyer.sendSystemMessage(Msg.err("Your offer for " + o.quantity + "x " + o.itemName + " failed: " + why));
		seller.sendSystemMessage(Msg.err("Offer from " + o.buyerName + " failed: " + why));
		buyer.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- lifecycle

	public void tickExpiry() {
		if (pending.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Offer o : new ArrayList<>(pending.values())) {
			if (now > o.expiresAt) {
				pending.remove(o.id);
				finish(o, Offer.State.EXPIRED, "EXPIRED");
			}
		}
	}

	/** Disconnect policy: all pending offers involving the player are cancelled; nothing moves. */
	public void onDisconnect(UUID player) {
		for (Offer o : new ArrayList<>(pending.values())) {
			if (o.buyer.equals(player) || o.seller.equals(player)) {
				pending.remove(o.id);
				finish(o, Offer.State.CANCELLED, "CANCELLED");
			}
		}
	}

	/** Terminal bookkeeping for non-completed offers: history for both sides + notifications. */
	private void finish(Offer o, Offer.State state, String outcome) {
		o.state = state;
		EconomyStore.TradeRecord rec = record(o, outcome);
		store.addRecord(o.buyer, rec);
		store.addRecord(o.seller, rec);
		store.saveQuietly();
		ServerPlayer b = server.getPlayerList().getPlayer(o.buyer);
		ServerPlayer s = server.getPlayerList().getPlayer(o.seller);
		String what = o.quantity + "x " + o.itemName + " for " + Money.format(o.price);
		switch (state) {
			case REJECTED -> {
				if (b != null) b.sendSystemMessage(Msg.err(o.sellerName + " rejected your offer (" + what + "). No money moved."));
				if (s != null) s.sendSystemMessage(Msg.info("You rejected " + o.buyerName + "'s offer."));
			}
			case EXPIRED -> {
				if (b != null) b.sendSystemMessage(Msg.err("Your offer to " + o.sellerName + " (" + what + ") expired."));
				if (s != null) s.sendSystemMessage(Msg.info("The offer from " + o.buyerName + " (" + what + ") expired."));
			}
			case CANCELLED -> {
				if (b != null) b.sendSystemMessage(Msg.info("Your offer to " + o.sellerName + " was cancelled. No money moved."));
				if (s != null) s.sendSystemMessage(Msg.info("The offer from " + o.buyerName + " was cancelled (player left or withdrew)."));
			}
			default -> { }
		}
	}

	private EconomyStore.TradeRecord record(Offer o, String outcome) {
		EconomyStore.TradeRecord r = new EconomyStore.TradeRecord();
		r.id = o.id;
		r.time = System.currentTimeMillis();
		r.buyerId = o.buyer.toString(); r.buyerName = o.buyerName;
		r.sellerId = o.seller.toString(); r.sellerName = o.sellerName;
		r.itemName = o.itemName; r.quantity = o.quantity; r.price = o.price;
		r.outcome = outcome;
		return r;
	}

	private static String name(ServerPlayer p) { return p.getName().getString(); }
}
