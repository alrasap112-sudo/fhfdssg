package net.gildedledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyStoreTest {
	private static EconomyStore open(Path dir) {
		return EconomyStore.load(dir.resolve("economy.json"), 5000, 50, LoggerFactory.getLogger("test"));
	}

	@Test void startingBalanceGrantedOnce(@TempDir Path dir) throws Exception {
		EconomyStore s = open(dir); UUID a = UUID.randomUUID();
		assertTrue(s.ensureAccount(a, "A"));
		assertEquals(5000, s.balance(a));
		s.transfer(a, UUID.randomUUID(), 1); // unknown target: refused
		assertFalse(s.ensureAccount(a, "Renamed"));
		assertEquals(5000, s.balance(a));
	}

	@Test void transferConservesMoneyAndPersists(@TempDir Path dir) throws Exception {
		EconomyStore s = open(dir); UUID b = UUID.randomUUID(), c = UUID.randomUUID();
		s.ensureAccount(b, "B"); s.ensureAccount(c, "C"); s.setBalance(c, 2000);
		assertTrue(s.transfer(b, c, 20));
		assertEquals(4980, s.balance(b)); assertEquals(2020, s.balance(c));
		s.save();
		EconomyStore r = open(dir);
		assertEquals(4980, r.balance(b)); assertEquals(2020, r.balance(c));
		assertFalse(r.ensureAccount(b, "B")); // reconnect never re-grants
	}

	@Test void rejectsOverdraftNegativeAndOverflow(@TempDir Path dir) {
		EconomyStore s = open(dir); UUID b = UUID.randomUUID(), c = UUID.randomUUID();
		s.ensureAccount(b, "B"); s.ensureAccount(c, "C");
		assertFalse(s.transfer(b, c, 5001));
		assertFalse(s.transfer(b, c, -5));
		assertFalse(s.transfer(b, c, 0));
		assertFalse(s.transfer(b, b, 5));
		s.setBalance(c, Money.MAX);
		assertFalse(s.transfer(b, c, 1));
		assertEquals(5000, s.balance(b));
	}

	@Test void corruptFileWithoutBackupRefusesToStart(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("economy.json"), "{not json");
		assertThrows(IllegalStateException.class, () -> open(dir));
	}

	@Test void corruptFileRecoversFromBackup(@TempDir Path dir) throws Exception {
		EconomyStore s = open(dir); UUID a = UUID.randomUUID(); s.ensureAccount(a, "A"); s.save(); s.setBalance(a, 4500); s.save();
		Files.writeString(dir.resolve("economy.json"), "garbage");
		assertEquals(5000, open(dir).balance(a)); // last good backup
	}

	@Test void historyIsBoundedNewestFirst(@TempDir Path dir) {
		EconomyStore s = EconomyStore.load(dir.resolve("e.json"), 5000, 3, LoggerFactory.getLogger("t"));
		UUID a = UUID.randomUUID();
		for (int i = 0; i < 5; i++) { var r = new EconomyStore.TradeRecord(); r.id = "" + i; s.addRecord(a, r); }
		assertEquals(3, s.history(a).size());
		assertEquals("4", s.history(a).get(0).id);
	}

	@Test void moneyFormat() {
		assertEquals("$5,000", Money.format(5000));
		assertEquals("$1,250,000", Money.format(1_250_000));
		assertEquals("$5", Money.perItem(20, 4));
		assertEquals("$6.67", Money.perItem(20, 3));
	}
}
