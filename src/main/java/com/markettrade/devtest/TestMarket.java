package com.markettrade.devtest;

import java.util.List;

import com.markettrade.market.Listing;
import com.markettrade.market.MarketData;
import com.markettrade.market.StoredStack;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Shared setup for GameTests that need a market with real listings. The market is shared by the whole test server. */
final class TestMarket {
    static final long DAY = 86_400_000L;
    /** Added to every listing time so "7d 0h" is exact however long the test takes to draw it. */
    private static final long HALF_HOUR = 1_800_000L;

    private TestMarket() {
    }

    static void clearAll(MinecraftServer server) {
        MarketData market = MarketData.get(server);
        for (Listing listing : market.listingsNewestFirst()) {
            market.removeListing(listing.id());
        }
    }

    /** Adds a listing straight to the market, owned by {@code seller}, running out in {@code days} days. */
    static long add(ServerPlayer seller, ItemStack stack, long price, long days) {
        MarketData market = MarketData.get(seller.server);
        long id = market.nextId();
        market.addListing(new Listing(id, seller.getUUID(), seller.getGameProfile().getName(), StoredStack.of(stack), price,
                System.currentTimeMillis() + days * DAY + HALF_HOUR));
        return id;
    }

    static long add(ServerPlayer seller, ItemStack stack, long price) {
        return add(seller, stack, price, 7);
    }

    /** Clears the market and lists the 60 sample items for {@code owner}, so the first sample is the newest. */
    static void seedSamples(ServerPlayer owner) {
        clearAll(owner.server);
        List<TestSamples.Sample> samples = TestSamples.getAll(owner.registryAccess());
        for (int i = samples.size() - 1; i >= 0; i--) {
            TestSamples.Sample sample = samples.get(i);
            add(owner, sample.item().copy(), sample.price());
        }
    }

    /** The slot number in the 6 row chest menu for an index in the player's inventory. */
    static int menuSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 81 + inventoryIndex : 54 + (inventoryIndex - 9);
    }

    static void click(AbstractContainerMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        menu.clicked(slot, button, type, player);
    }

    static void click(AbstractContainerMenu menu, ServerPlayer player, int slot) {
        menu.clicked(slot, 0, ClickType.PICKUP, player);
    }

    static void clearInventory(Inventory inventory) {
        inventory.clearContent();
    }
}
