package net.gildedledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Server settings (config/gildedledger.json). Missing or malformed files never touch player balances. */
public final class LedgerConfig {
	public int configVersion = 1;
	public long startingBalance = 5000;
	public boolean tradingEnabled = true;
	public boolean requireProximity = true;
	public double maxTradeDistance = 16.0;
	public int offerExpirySeconds = 60;
	public int maxHistoryEntries = 50;
	public boolean giveWalletOnFirstJoin = true;
	public boolean giveWalletOnRespawn = true;
	public boolean useCustomItemModel = true;
	public long maxPrice = 1_000_000_000L;
	public int maxPendingOffersPerPlayer = 5;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static LedgerConfig load(Path path, Logger log) {
		LedgerConfig cfg = new LedgerConfig();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				LedgerConfig parsed = GSON.fromJson(r, LedgerConfig.class);
				if (parsed != null) cfg = parsed;
			} catch (Exception e) {
				Path bad = path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis());
				log.error("Config {} is malformed; using defaults. Original kept as {}", path, bad, e);
				try { Files.move(path, bad, StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
				cfg = new LedgerConfig();
			}
		}
		cfg.validate(log);
		cfg.save(path, log);
		return cfg;
	}

	private void validate(Logger log) {
		startingBalance = clamp(startingBalance, 0, Money.MAX, "startingBalance", log);
		maxPrice = clamp(maxPrice, 1, Money.MAX, "maxPrice", log);
		offerExpirySeconds = (int) clamp(offerExpirySeconds, 5, 3600, "offerExpirySeconds", log);
		maxHistoryEntries = (int) clamp(maxHistoryEntries, 1, 1000, "maxHistoryEntries", log);
		maxPendingOffersPerPlayer = (int) clamp(maxPendingOffersPerPlayer, 1, 50, "maxPendingOffersPerPlayer", log);
		if (!(maxTradeDistance >= 1.0 && maxTradeDistance <= 1000.0)) {
			log.warn("maxTradeDistance invalid; reset to 16");
			maxTradeDistance = 16.0;
		}
		configVersion = 1;
	}

	private static long clamp(long v, long lo, long hi, String name, Logger log) {
		if (v < lo || v > hi) {
			long c = Math.max(lo, Math.min(hi, v));
			log.warn("Config {}={} out of range [{}, {}]; using {}", name, v, lo, hi, c);
			return c;
		}
		return v;
	}

	private void save(Path path, Logger log) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) { GSON.toJson(this, w); }
		} catch (IOException e) {
			log.error("Could not write config {}", path, e);
		}
	}
}
