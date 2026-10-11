package net.gildedledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent accounts + history in ONE file (economy.json) so balances and the ledger are always replaced together
 * by an atomic move. A .bak copy of the previous good state is kept. Contains no Minecraft types (unit-testable).
 */
public final class EconomyStore {
	public static final class TradeRecord {
		public String id;
		public long time;
		public String buyerId, buyerName, sellerId, sellerName;
		public String itemName;
		public int quantity;
		public long price;
		public String outcome; // COMPLETED, REJECTED, EXPIRED, CANCELLED, FAILED
	}

	private static final class Data {
		int version = 1;
		Map<String, Long> balances = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, List<TradeRecord>> history = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Path backup;
	private final long startingBalance;
	private final int historyLimit;
	private final Logger log;
	private Data data;

	private EconomyStore(Path file, long startingBalance, int historyLimit, Logger log, Data data) {
		this.file = file;
		this.backup = file.resolveSibling(file.getFileName() + ".bak");
		this.startingBalance = startingBalance;
		this.historyLimit = historyLimit;
		this.log = log;
		this.data = data;
	}

	/** @throws IllegalStateException if existing data is unreadable (we refuse to silently reset balances). */
	public static EconomyStore load(Path file, long startingBalance, int historyLimit, Logger log) {
		Path bak = file.resolveSibling(file.getFileName() + ".bak");
		Data d = null;
		if (Files.exists(file)) {
			try { d = read(file); } catch (Exception e) {
				log.error("economy.json unreadable, trying backup", e);
				try { d = read(bak); log.warn("Recovered economy from backup {}", bak); } catch (Exception e2) {
					throw new IllegalStateException("Economy data is corrupt and no valid backup exists. Fix or restore "
						+ file + " manually. Refusing to start with reset balances.", e2);
				}
			}
		} else if (Files.exists(bak)) {
			try { d = read(bak); } catch (Exception e) { throw new IllegalStateException("Backup unreadable: " + bak, e); }
		}
		if (d == null) d = new Data();
		if (d.balances == null) d.balances = new LinkedHashMap<>();
		if (d.names == null) d.names = new LinkedHashMap<>();
		if (d.history == null) d.history = new LinkedHashMap<>();
		return new EconomyStore(file, startingBalance, historyLimit, log, d);
	}

	private static Data read(Path p) throws IOException {
		try (Reader r = Files.newBufferedReader(p)) {
			Data d = GSON.fromJson(r, Data.class);
			if (d == null) throw new IOException("empty file");
			return d;
		}
	}

	/** Creates the account with the starting balance exactly once. @return true if newly created. */
	public synchronized boolean ensureAccount(UUID id, String name) {
		String k = id.toString();
		data.names.put(k, name);
		if (data.balances.containsKey(k)) return false;
		data.balances.put(k, startingBalance);
		return true;
	}

	public synchronized long balance(UUID id) {
		return data.balances.getOrDefault(id.toString(), 0L);
	}

	public synchronized boolean hasAccount(UUID id) { return data.balances.containsKey(id.toString()); }

	/** In-memory transfer; false (nothing changed) on insufficient funds, bad amount, or overflow past MAX. */
	public synchronized boolean transfer(UUID from, UUID to, long amount) {
		if (amount <= 0 || amount > Money.MAX || from.equals(to)) return false;
		long fb = balance(from), tb = balance(to);
		if (!hasAccount(from) || !hasAccount(to) || fb < amount) return false;
		long nt;
		try { nt = Math.addExact(tb, amount); } catch (ArithmeticException e) { return false; }
		if (nt > Money.MAX) return false;
		data.balances.put(from.toString(), fb - amount);
		data.balances.put(to.toString(), nt);
		return true;
	}

	public synchronized boolean setBalance(UUID id, long value) {
		if (value < 0 || value > Money.MAX || !hasAccount(id)) return false;
		data.balances.put(id.toString(), value);
		return true;
	}

	public synchronized void addRecord(UUID owner, TradeRecord r) {
		List<TradeRecord> l = data.history.computeIfAbsent(owner.toString(), k -> new ArrayList<>());
		l.add(r);
		while (l.size() > historyLimit) l.remove(0);
	}

	public synchronized void removeRecord(UUID owner, String recordId) {
		List<TradeRecord> l = data.history.get(owner.toString());
		if (l != null) l.removeIf(r -> recordId.equals(r.id));
	}

	/** Newest first; copy, safe to iterate. */
	public synchronized List<TradeRecord> history(UUID owner) {
		List<TradeRecord> l = new ArrayList<>(data.history.getOrDefault(owner.toString(), List.of()));
		java.util.Collections.reverse(l);
		return l;
	}

	public synchronized void save() throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(data, w); }
		if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public void saveQuietly() {
		try { save(); } catch (IOException e) { log.error("Failed to save economy data", e); }
	}
}
