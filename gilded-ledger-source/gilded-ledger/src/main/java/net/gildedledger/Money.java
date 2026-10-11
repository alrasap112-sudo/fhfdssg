package net.gildedledger;

import java.util.Locale;

/** Currency helpers. All amounts are whole dollars stored as long; no floating point in transactions. */
public final class Money {
	public static final long MAX = 1_000_000_000_000L;

	private Money() {}

	public static String format(long v) {
		return (v < 0 ? "-$" : "$") + String.format(Locale.US, "%,d", Math.abs(v));
	}

	/** Display-only price per item. */
	public static String perItem(long total, int qty) {
		if (qty <= 0) return format(0);
		if (total % qty == 0) return format(total / qty);
		return "$" + String.format(Locale.US, "%,.2f", (double) total / qty);
	}
}
