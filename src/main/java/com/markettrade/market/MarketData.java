package com.markettrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Listings, Returns, the pool and the sale records. Only {@link MarketActions} should change it. */
public final class MarketData extends SavedData {
    public static final String NAME = "markettrade_market";
    /** Raise this and add an upgrade step in {@link #upgrade} (with a test) whenever the saved layout changes. */
    public static final int FORMAT_VERSION = 3;
    /** Closed reports kept besides every open one. */
    public static final int KEEP_CLOSED_REPORTS = 500;

    private static final Logger LOGGER = LogUtils.getLogger();

    private long nextId = 1;
    private long pool;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<StoredStack>> returns = new LinkedHashMap<>();
    private final SalesLog sales = new SalesLog();
    private long nextReportId = 1;
    private final Map<Long, Report> reports = new LinkedHashMap<>();
    private final Map<UUID, ShopBan> bans = new LinkedHashMap<>();
    private final PoolLedger ledger = new PoolLedger();
    /** Not saved: when each player last filed a report. */
    private final Map<UUID, Long> lastReport = new java.util.HashMap<>();

    public MarketData() {
    }

    public static SavedData.Factory<MarketData> factory() {
        return new SavedData.Factory<>(MarketData::new, MarketData::load, null);
    }

    public static MarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Throws on a newer version or broken data. Vanilla swallows that, and the caller at server start deals with it. */
    public static MarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("version", Tag.TAG_INT)) {
            throw new IllegalStateException("Market data has no format version");
        }
        int version = tag.getInt("version");
        if (version > FORMAT_VERSION) {
            throw new IllegalStateException("Market data is format " + version + " but this mod only knows up to "
                    + FORMAT_VERSION + ". Use a newer Market Trade.");
        }
        if (version < 1) {
            throw new IllegalStateException("Market data has an invalid format version " + version);
        }
        tag = upgrade(tag, version);
        if (!tag.contains("nextId", Tag.TAG_LONG) || !tag.contains("pool", Tag.TAG_LONG)
                || !tag.contains("listings", Tag.TAG_LIST) || !tag.contains("returns", Tag.TAG_LIST)
                || !tag.contains("sales", Tag.TAG_LIST) || !tag.contains("lifetime", Tag.TAG_LIST)
                || !tag.contains("reports", Tag.TAG_LIST) || !tag.contains("bans", Tag.TAG_LIST)
                || !tag.contains("nextReportId", Tag.TAG_LONG) || !tag.contains("ledger", Tag.TAG_LIST)) {
            throw new IllegalStateException("Market data is missing a part");
        }
        MarketData data = new MarketData();
        data.nextId = Math.max(1, tag.getLong("nextId"));
        data.pool = Math.max(0, tag.getLong("pool"));

        ListTag listingTags = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingTags.size(); i++) {
            CompoundTag entry = listingTags.getCompound(i);
            try {
                if (!entry.contains("id", Tag.TAG_LONG) || !entry.contains("price", Tag.TAG_LONG)
                        || !entry.contains("expiresAt", Tag.TAG_LONG) || !entry.contains("item", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("a part is missing");
                }
                long id = entry.getLong("id");
                Listing listing = new Listing(id, UUID.fromString(entry.getString("seller")),
                        entry.getString("sellerName"), StoredStack.fromTag(entry.getCompound("item"), registries),
                        entry.getLong("price"), entry.getLong("expiresAt"));
                if (data.listings.containsKey(id)) {
                    throw new IllegalArgumentException("duplicate id " + id);
                }
                data.listings.put(id, listing);
                data.nextId = Math.max(data.nextId, id + 1);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping listing entry {} ({}): {}", i, entry, e.getMessage());
            }
        }

        ListTag returnTags = tag.getList("returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < returnTags.size(); i++) {
            CompoundTag entry = returnTags.getCompound(i);
            try {
                UUID player = UUID.fromString(entry.getString("player"));
                ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
                List<StoredStack> stacks = data.returns.computeIfAbsent(player, k -> new ArrayList<>());
                for (int j = 0; j < items.size(); j++) {
                    stacks.add(StoredStack.fromTag(items.getCompound(j), registries));
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping returns entry {} ({}): {}", i, entry, e.getMessage());
            }
        }

        ListTag saleTags = tag.getList("sales", Tag.TAG_COMPOUND);
        for (int i = 0; i < saleTags.size(); i++) {
            CompoundTag entry = saleTags.getCompound(i);
            try {
                data.sales.addRecord(new Sale(entry.getLong("time"), UUID.fromString(entry.getString("seller")),
                        entry.getString("sellerName"), UUID.fromString(entry.getString("buyer")),
                        entry.getString("buyerName"), entry.getString("item"), entry.getInt("count"),
                        entry.getLong("price"), entry.getLong("cut")));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping sale record {} ({}): {}", i, entry, e.getMessage());
            }
        }

        ListTag lifetimeTags = tag.getList("lifetime", Tag.TAG_COMPOUND);
        for (int i = 0; i < lifetimeTags.size(); i++) {
            CompoundTag entry = lifetimeTags.getCompound(i);
            try {
                data.sales.addLifetime(UUID.fromString(entry.getString("player")), entry.getLong("total"));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping lifetime entry {} ({}): {}", i, entry, e.getMessage());
            }
        }

        data.nextReportId = Math.max(1, tag.getLong("nextReportId"));
        ListTag reportTags = tag.getList("reports", Tag.TAG_COMPOUND);
        for (int i = 0; i < reportTags.size(); i++) {
            CompoundTag entry = reportTags.getCompound(i);
            try {
                if (!entry.contains("id", Tag.TAG_LONG) || !entry.contains("item", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("a part is missing");
                }
                Report report = new Report(entry.getLong("id"), entry.getLong("time"),
                        UUID.fromString(entry.getString("reporter")), entry.getString("reporterName"),
                        UUID.fromString(entry.getString("seller")), entry.getString("sellerName"),
                        entry.getLong("listingId"), StoredStack.fromTag(entry.getCompound("item"), registries),
                        entry.getString("reason"), entry.getBoolean("open"));
                data.reports.put(report.id(), report);
                data.nextReportId = Math.max(data.nextReportId, report.id() + 1);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping report entry {} ({}): {}", i, entry, e.getMessage());
            }
        }

        ListTag banTags = tag.getList("bans", Tag.TAG_COMPOUND);
        for (int i = 0; i < banTags.size(); i++) {
            CompoundTag entry = banTags.getCompound(i);
            try {
                UUID by = entry.contains("bannedBy", Tag.TAG_STRING) ? UUID.fromString(entry.getString("bannedBy")) : null;
                ShopBan ban = new ShopBan(UUID.fromString(entry.getString("player")), entry.getString("name"), by,
                        entry.getString("bannedByName"), entry.getString("reason"), entry.getLong("time"));
                data.bans.put(ban.player(), ban);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping ban entry {} ({}): {}", i, entry, e.getMessage());
            }
        }

        ListTag ledgerTags = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < ledgerTags.size(); i++) {
            CompoundTag entry = ledgerTags.getCompound(i);
            try {
                PoolEntry.Kind kind = PoolEntry.Kind.valueOf(entry.getString("kind"));
                UUID actor = entry.contains("actor", Tag.TAG_STRING) ? UUID.fromString(entry.getString("actor")) : null;
                UUID target = entry.contains("target", Tag.TAG_STRING) ? UUID.fromString(entry.getString("target")) : null;
                data.ledger.add(new PoolEntry(entry.getLong("time"), kind, entry.getLong("amount"),
                        entry.getLong("poolAfter"), actor, entry.getString("actorName"), target,
                        entry.getString("targetName"), entry.getString("note")));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping pool ledger entry {} ({}): {}", i, entry, e.getMessage());
            }
        }
        return data;
    }

    /** Brings older data up to {@link #FORMAT_VERSION}. Version 2 added reports and shop bans, version 3 the pool ledger. */
    private static CompoundTag upgrade(CompoundTag tag, int fromVersion) {
        if (fromVersion < 2) {
            tag = tag.copy();
            tag.putLong("nextReportId", 1);
            tag.put("reports", new ListTag());
            tag.put("bans", new ListTag());
        }
        if (fromVersion < 3) {
            tag = tag.copy();
            tag.put("ledger", new ListTag());
        }
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", FORMAT_VERSION);
        tag.putLong("nextId", nextId);
        tag.putLong("pool", pool);

        ListTag listingTags = new ListTag();
        for (Listing listing : listings.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", listing.id());
            entry.putString("seller", listing.seller().toString());
            entry.putString("sellerName", listing.sellerName());
            entry.put("item", listing.item().toTag(registries));
            entry.putLong("price", listing.price());
            entry.putLong("expiresAt", listing.expiresAt());
            listingTags.add(entry);
        }
        tag.put("listings", listingTags);

        ListTag returnTags = new ListTag();
        for (Map.Entry<UUID, List<StoredStack>> returned : returns.entrySet()) {
            if (returned.getValue().isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("player", returned.getKey().toString());
            ListTag items = new ListTag();
            for (StoredStack stack : returned.getValue()) {
                items.add(stack.toTag(registries));
            }
            entry.put("items", items);
            returnTags.add(entry);
        }
        tag.put("returns", returnTags);

        ListTag saleTags = new ListTag();
        for (Sale sale : sales.recent()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("time", sale.time());
            entry.putString("seller", sale.seller().toString());
            entry.putString("sellerName", sale.sellerName());
            entry.putString("buyer", sale.buyer().toString());
            entry.putString("buyerName", sale.buyerName());
            entry.putString("item", sale.itemId());
            entry.putInt("count", sale.count());
            entry.putLong("price", sale.price());
            entry.putLong("cut", sale.cut());
            saleTags.add(entry);
        }
        tag.put("sales", saleTags);

        ListTag lifetimeTags = new ListTag();
        for (Map.Entry<UUID, Long> total : sales.lifetimeTotals().entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("player", total.getKey().toString());
            entry.putLong("total", total.getValue());
            lifetimeTags.add(entry);
        }
        tag.put("lifetime", lifetimeTags);

        tag.putLong("nextReportId", nextReportId);
        ListTag reportTags = new ListTag();
        for (Report report : reports.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", report.id());
            entry.putLong("time", report.time());
            entry.putString("reporter", report.reporter().toString());
            entry.putString("reporterName", report.reporterName());
            entry.putString("seller", report.seller().toString());
            entry.putString("sellerName", report.sellerName());
            entry.putLong("listingId", report.listingId());
            entry.put("item", report.item().toTag(registries));
            entry.putString("reason", report.reason());
            entry.putBoolean("open", report.open());
            reportTags.add(entry);
        }
        tag.put("reports", reportTags);

        ListTag banTags = new ListTag();
        for (ShopBan ban : bans.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("player", ban.player().toString());
            entry.putString("name", ban.name());
            if (ban.bannedBy() != null) {
                entry.putString("bannedBy", ban.bannedBy().toString());
            }
            entry.putString("bannedByName", ban.bannedByName());
            entry.putString("reason", ban.reason());
            entry.putLong("time", ban.time());
            banTags.add(entry);
        }
        tag.put("bans", banTags);

        ListTag ledgerTags = new ListTag();
        for (PoolEntry pool : ledger.oldestFirst()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("time", pool.time());
            entry.putString("kind", pool.kind().name());
            entry.putLong("amount", pool.amount());
            entry.putLong("poolAfter", pool.poolAfter());
            if (pool.actor() != null) {
                entry.putString("actor", pool.actor().toString());
            }
            entry.putString("actorName", pool.actorName() == null ? "" : pool.actorName());
            if (pool.target() != null) {
                entry.putString("target", pool.target().toString());
            }
            entry.putString("targetName", pool.targetName() == null ? "" : pool.targetName());
            entry.putString("note", pool.note() == null ? "" : pool.note());
            ledgerTags.add(entry);
        }
        tag.put("ledger", ledgerTags);
        return tag;
    }

    // Listings

    /** Ids come from a saved counter and are never reused. */
    public long nextId() {
        long id = nextId++;
        setDirty();
        return id;
    }

    public void addListing(Listing listing) {
        listings.put(listing.id(), listing);
        setDirty();
    }

    public Listing listing(long id) {
        return listings.get(id);
    }

    public Listing removeListing(long id) {
        Listing removed = listings.remove(id);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Newest first. */
    public List<Listing> listingsNewestFirst() {
        List<Listing> all = new ArrayList<>(listings.values());
        all.sort(Comparator.comparingLong(Listing::id).reversed());
        return all;
    }

    public List<Listing> listingsOf(UUID seller) {
        List<Listing> mine = new ArrayList<>();
        for (Listing listing : listingsNewestFirst()) {
            if (listing.seller().equals(seller)) {
                mine.add(listing);
            }
        }
        return mine;
    }

    public int countListingsOf(UUID seller) {
        int count = 0;
        for (Listing listing : listings.values()) {
            if (listing.seller().equals(seller)) {
                count++;
            }
        }
        return count;
    }

    // Returns

    public void addReturn(UUID player, StoredStack stack) {
        returns.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
        setDirty();
    }

    /** Oldest first. */
    public List<StoredStack> returnsOf(UUID player) {
        return List.copyOf(returns.getOrDefault(player, List.of()));
    }

    public void replaceReturns(UUID player, List<StoredStack> stacks) {
        if (stacks.isEmpty()) {
            returns.remove(player);
        } else {
            returns.put(player, new ArrayList<>(stacks));
        }
        setDirty();
    }

    // Pool and sales

    public long pool() {
        return pool;
    }

    public boolean canAddToPool(long amount) {
        return amount >= 0 && amount <= Long.MAX_VALUE - pool;
    }

    public boolean addToPool(long amount) {
        if (!canAddToPool(amount)) {
            return false;
        }
        pool += amount;
        setDirty();
        return true;
    }

    /** Takes from the pool. False, and nothing changes, if it holds less. */
    public boolean takeFromPool(long amount) {
        if (amount < 0 || amount > pool) {
            return false;
        }
        pool -= amount;
        setDirty();
        return true;
    }

    public void setPool(long amount) {
        pool = Math.max(0, amount);
        setDirty();
    }

    public void addLedger(PoolEntry entry) {
        ledger.add(entry);
        setDirty();
    }

    /** Newest first. */
    public List<PoolEntry> ledgerNewestFirst() {
        return ledger.newestFirst();
    }

    public void recordSale(Sale sale) {
        sales.record(sale);
        setDirty();
    }

    public SalesLog sales() {
        return sales;
    }

    // Reports

    public long nextReportId() {
        long id = nextReportId++;
        setDirty();
        return id;
    }

    public void addReport(Report report) {
        reports.put(report.id(), report);
        setDirty();
    }

    public Report report(long id) {
        return reports.get(id);
    }

    /** Newest first, open and closed. */
    public List<Report> reportsNewestFirst() {
        List<Report> all = new ArrayList<>(reports.values());
        all.sort(Comparator.comparingLong(Report::id).reversed());
        return all;
    }

    public List<Report> openReportsNewestFirst() {
        return reportsNewestFirst().stream().filter(Report::open).toList();
    }

    /** True if this player already reported this listing, open or closed. */
    public boolean hasReported(UUID reporter, long listingId) {
        for (Report report : reports.values()) {
            if (report.reporter().equals(reporter) && report.listingId() == listingId) {
                return true;
            }
        }
        return false;
    }

    /** Closes the report, then drops the oldest closed ones past {@link #KEEP_CLOSED_REPORTS}. Open ones are never dropped. */
    public Report closeReport(long id) {
        Report report = reports.get(id);
        if (report == null || !report.open()) {
            return null;
        }
        Report closed = report.closed();
        reports.put(id, closed);
        List<Report> closedOnes = reportsNewestFirst().stream().filter(r -> !r.open()).toList();
        for (int i = KEEP_CLOSED_REPORTS; i < closedOnes.size(); i++) {
            reports.remove(closedOnes.get(i).id());
        }
        setDirty();
        return closed;
    }

    /** When this player last filed a report, or Long.MIN_VALUE. */
    public long lastReportTime(UUID player) {
        return lastReport.getOrDefault(player, Long.MIN_VALUE);
    }

    public void setLastReportTime(UUID player, long time) {
        lastReport.put(player, time);
    }

    // Shop bans

    public boolean isBanned(UUID player) {
        return bans.containsKey(player);
    }

    public ShopBan ban(UUID player) {
        return bans.get(player);
    }

    public void addBan(ShopBan ban) {
        bans.put(ban.player(), ban);
        setDirty();
    }

    public ShopBan removeBan(UUID player) {
        ShopBan removed = bans.remove(player);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Newest first. */
    public List<ShopBan> bansNewestFirst() {
        List<ShopBan> all = new ArrayList<>(bans.values());
        all.sort(Comparator.comparingLong(ShopBan::time).reversed());
        return all;
    }
}
