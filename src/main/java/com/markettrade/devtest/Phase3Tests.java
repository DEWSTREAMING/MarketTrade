package com.markettrade.devtest;

import java.util.List;
import java.util.UUID;

import com.markettrade.MarketTrade;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyParser;
import com.markettrade.market.Giving;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfig;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.Sale;
import com.markettrade.market.StoredStack;
import com.markettrade.market.WalletData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MarketTrade.MODID)
@PrefixGameTestTemplate(false)
public class Phase3Tests {
    private static final long NOW = 1_000_000L;

    private static ServerPlayer player(GameTestHelper helper) {
        Currencies.set(CurrencyParser.defaultConfig());
        MarketConfigs.set(MarketConfig.defaults());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        WalletData.get(player.server).set(player.getUUID(), 0);
        return player;
    }

    /** Takes back everything a test player put into the shared market data. */
    private static void clear(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            MarketData market = MarketData.get(player.server);
            for (Listing listing : market.listingsOf(player.getUUID())) {
                market.removeListing(listing.id());
            }
            market.replaceReturns(player.getUUID(), List.of());
            WalletData.get(player.server).set(player.getUUID(), 0);
        }
    }

    private static long wallet(ServerPlayer player) {
        return WalletData.get(player.server).balance(player.getUUID());
    }

    private static ItemStack fancySword(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Blade"));
        helper.getLevel().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(Enchantments.SHARPNESS)).ifPresent(holder -> {
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mutable.set(holder, 4);
                    sword.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                });
        return sword;
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void fillWithDirt(Inventory inventory) {
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static int droppedItems(GameTestHelper helper) {
        return helper.getLevel().getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> true).size();
    }

    private static MarketActions.SellResult sellHeld(ServerPlayer seller, long price) {
        Inventory inventory = seller.getInventory();
        return MarketActions.sell(seller, inventory.selected, inventory.getItem(inventory.selected).copy(), price, NOW);
    }

    /** Lists the held stack for a seller and returns the listing id. */
    private static long list(GameTestHelper helper, ServerPlayer seller, ItemStack stack, long price) {
        Inventory inventory = seller.getInventory();
        inventory.setItem(inventory.selected, stack);
        MarketActions.SellResult result = sellHeld(seller, price);
        helper.assertTrue(result.status() == MarketActions.SellStatus.OK, "Listing should work, got " + result.status());
        return result.id();
    }

    @GameTest(template = "empty")
    public static void listingTakesTheStackWithAllDataAndTheLimitIsEnforced(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        MarketConfigs.set(new MarketConfig(5, 7, 2));
        Inventory inv = seller.getInventory();
        MarketData market = MarketData.get(seller.server);

        ItemStack sword = fancySword(helper);
        long id = list(helper, seller, sword.copy(), 300);
        helper.assertTrue(inv.getItem(inv.selected).isEmpty(), "The hand should be empty after listing");
        Listing listing = market.listing(id);
        helper.assertTrue(listing != null && listing.price() == 300, "Listing is saved with its price");
        helper.assertTrue(ItemStack.matches(listing.item().stack(), sword), "Listing keeps enchants and name");
        helper.assertTrue(listing.expiresAt() == NOW + 7L * 86_400_000L, "Expiry is 7 days on");
        helper.assertTrue(listing.seller().equals(seller.getUUID()), "Seller is recorded");

        inv.setItem(inv.selected, ItemStack.EMPTY);
        helper.assertTrue(sellHeld(seller, 10).status() == MarketActions.SellStatus.EMPTY_HAND,
                "An empty hand is refused");
        inv.setItem(inv.selected, new ItemStack(Items.DIRT, 5));
        helper.assertTrue(sellHeld(seller, 0).status() == MarketActions.SellStatus.BAD_PRICE,
                "A price of 0 is refused");
        helper.assertTrue(inv.getItem(inv.selected).getCount() == 5, "A refused listing keeps the item");

        list(helper, seller, new ItemStack(Items.DIRT, 5), 10);
        inv.setItem(inv.selected, new ItemStack(Items.STONE, 7));
        helper.assertTrue(sellHeld(seller, 10).status() == MarketActions.SellStatus.LIMIT,
                "The third listing is over the limit");
        helper.assertTrue(inv.getItem(inv.selected).getCount() == 7, "Over the limit keeps the item");
        helper.assertTrue(market.countListingsOf(seller.getUUID()) == 2, "Still two listings");

        MarketActions.cancel(seller, id);
        long idAfter = list(helper, seller, new ItemStack(Items.STONE, 1), 1);
        helper.assertTrue(idAfter > id + 1, "Ids keep counting up and are not reused");
        clear(seller);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSaleMovesTheRightMoneyAndTheBuyerGetsTheItemIntact(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        WalletData wallets = WalletData.get(seller.server);
        ItemStack sword = fancySword(helper);
        long id = list(helper, seller, sword.copy(), 40);
        wallets.set(buyer.getUUID(), 100);
        long poolBefore = market.pool();

        MarketActions.BuyResult result = MarketActions.buy(buyer, id, 40, NOW + 1);
        helper.assertTrue(result.status() == MarketActions.BuyStatus.OK, "Buy should work, got " + result.status());
        helper.assertTrue(result.cut() == 2, "5% of 40 is 2, got " + result.cut());
        helper.assertTrue(wallet(buyer) == 60, "Buyer paid 40, wallet " + wallet(buyer));
        helper.assertTrue(wallet(seller) == 38, "Seller got 38, wallet " + wallet(seller));
        helper.assertTrue(market.pool() == poolBefore + 2, "Pool gained the cut");
        helper.assertTrue(market.listing(id) == null, "Listing is gone");

        ItemStack held = buyer.getInventory().getItem(0);
        helper.assertTrue(ItemStack.matches(held, sword), "Buyer got the sword with its enchant and name");
        helper.assertTrue(market.returnsOf(buyer.getUUID()).isEmpty(), "Nothing in Returns");

        List<Sale> sales = market.sales().recent();
        Sale last = sales.get(sales.size() - 1);
        helper.assertTrue(last.price() == 40 && last.cut() == 2 && last.count() == 1
                && last.seller().equals(seller.getUUID()) && last.buyer().equals(buyer.getUUID())
                && last.itemId().equals("minecraft:diamond_sword") && last.time() == NOW + 1, "Sale record is right");
        helper.assertTrue(market.sales().lifetime(seller.getUUID()) == 40, "Lifetime total counts the price");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payingPartlyFromCurrencyItemsGivesTheRightChange(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        WalletData wallets = WalletData.get(seller.server);
        Inventory inv = buyer.getInventory();
        long id = list(helper, seller, new ItemStack(Items.COBBLESTONE, 4), 10);

        wallets.set(buyer.getUUID(), 3);
        inv.setItem(5, new ItemStack(Items.DIAMOND, 2));
        inv.setItem(6, new ItemStack(Items.DIAMOND_BLOCK, 1));
        inv.setItem(7, new ItemStack(Items.DIAMOND_BLOCK, 3));
        MarketActions.BuyResult result = MarketActions.buy(buyer, id, 10, NOW);
        helper.assertTrue(result.status() == MarketActions.BuyStatus.OK, "Buy should work, got " + result.status());
        helper.assertTrue(inv.getItem(5).isEmpty(), "Both diamonds were used");
        helper.assertTrue(inv.getItem(6).isEmpty() && inv.getItem(7).getCount() == 3, "One block was used, the rest stay");
        helper.assertTrue(wallet(buyer) == 4, "3 + 2 + 9 - 10 leaves 4 change, got " + wallet(buyer));
        helper.assertTrue(wallet(seller) == 10, "Seller got the price (cut rounds to 0), got " + wallet(seller));
        helper.assertTrue(count(inv, Items.COBBLESTONE) == 4, "Item arrived");

        long second = list(helper, seller, new ItemStack(Items.STONE, 2), 10);
        wallets.set(buyer.getUUID(), 1);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setItem(5, new ItemStack(Items.DIAMOND, 2));
        MarketActions.BuyResult poor = MarketActions.buy(buyer, second, 10, NOW);
        helper.assertTrue(poor.status() == MarketActions.BuyStatus.CANNOT_PAY, "Not enough money, got " + poor.status());
        helper.assertTrue(wallet(buyer) == 1 && inv.getItem(5).getCount() == 2, "A buyer who can't pay loses nothing");
        helper.assertTrue(market.listing(second) != null, "Listing is still there");
        helper.assertTrue(wallet(seller) == 10, "Seller got nothing more");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refusedBuysChangeNothing(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        WalletData wallets = WalletData.get(seller.server);
        wallets.set(buyer.getUUID(), 1000);
        wallets.set(seller.getUUID(), 1000);
        long poolBefore = market.pool();
        long id = list(helper, seller, new ItemStack(Items.GOLD_INGOT, 3), 50);
        long expiresAt = market.listing(id).expiresAt();

        helper.assertTrue(MarketActions.buy(buyer, id, 49, NOW).status() == MarketActions.BuyStatus.PRICE_CHANGED,
                "A wrong expected price is refused");
        helper.assertTrue(MarketActions.buy(seller, id, 50, NOW).status() == MarketActions.BuyStatus.OWN_LISTING,
                "Buying your own listing is refused");
        helper.assertTrue(MarketActions.buy(buyer, id, 50, expiresAt).status() == MarketActions.BuyStatus.EXPIRED,
                "An expired listing can't be bought even before the check has run");
        helper.assertTrue(MarketActions.buy(buyer, id + 1000, 50, NOW).status() == MarketActions.BuyStatus.NOT_FOUND,
                "A listing that doesn't exist is refused");
        helper.assertTrue(market.listing(id) != null, "The listing is untouched by all of that");
        helper.assertTrue(wallet(buyer) == 1000 && wallet(seller) == 1000 && market.pool() == poolBefore,
                "No money moved");
        helper.assertTrue(count(buyer.getInventory(), Items.GOLD_INGOT) == 0, "Buyer has no item");

        helper.assertTrue(MarketActions.buy(buyer, id, 50, expiresAt - 1).status() == MarketActions.BuyStatus.OK,
                "One tick before the end it can still be bought");
        helper.assertTrue(MarketActions.buy(buyer, id, 50, NOW).status() == MarketActions.BuyStatus.NOT_FOUND,
                "A sold listing can't be bought again");
        helper.assertTrue(wallet(buyer) == 950, "Bought only once, wallet " + wallet(buyer));
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aWalletOrPoolThatWouldOverflowRefusesTheSale(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        WalletData wallets = WalletData.get(seller.server);

        long id = list(helper, seller, new ItemStack(Items.EMERALD, 2), 10);
        wallets.set(buyer.getUUID(), 100);
        wallets.set(seller.getUUID(), Long.MAX_VALUE - 5);
        helper.assertTrue(MarketActions.buy(buyer, id, 10, NOW).status() == MarketActions.BuyStatus.OVERFLOW,
                "Seller wallet would overflow");
        helper.assertTrue(wallet(buyer) == 100 && wallet(seller) == Long.MAX_VALUE - 5 && market.listing(id) != null
                && count(buyer.getInventory(), Items.EMERALD) == 0, "Nothing changed");

        wallets.set(seller.getUUID(), 0);
        market.addToPool(1);
        MarketConfigs.set(new MarketConfig(100, 7, 20));
        long big = list(helper, seller, new ItemStack(Items.EMERALD, 1), Long.MAX_VALUE);
        wallets.set(buyer.getUUID(), Long.MAX_VALUE);
        long poolBefore = market.pool();
        helper.assertTrue(MarketActions.buy(buyer, big, Long.MAX_VALUE, NOW).status() == MarketActions.BuyStatus.OVERFLOW,
                "The pool would overflow");
        helper.assertTrue(wallet(buyer) == Long.MAX_VALUE && wallet(seller) == 0 && market.pool() == poolBefore
                && market.listing(big) != null, "Nothing changed for the pool either");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullInventorySendsTheBoughtItemToReturnsAndClaimingKeepsTheRest(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        Inventory inv = buyer.getInventory();
        int droppedBefore = droppedItems(helper);

        fillWithDirt(inv);
        inv.setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        WalletData.get(seller.server).set(buyer.getUUID(), 100);
        long id = list(helper, seller, new ItemStack(Items.OAK_PLANKS, 16), 20);
        MarketActions.BuyResult result = MarketActions.buy(buyer, id, 20, NOW);
        helper.assertTrue(result.status() == MarketActions.BuyStatus.OK, "Buy works");
        helper.assertTrue(count(inv, Items.OAK_PLANKS) == 64, "Four planks fit into the stack");
        helper.assertTrue(result.toReturns() == 12, "Twelve go to Returns, got " + result.toReturns());
        List<StoredStack> returned = market.returnsOf(buyer.getUUID());
        helper.assertTrue(returned.size() == 1 && returned.get(0).count() == 12, "Returns hold the twelve");

        MarketActions.ClaimResult none = MarketActions.claimReturns(buyer);
        helper.assertTrue(none.claimed() == 0 && none.stacksLeft() == 1, "No room, nothing claimed");
        helper.assertTrue(market.returnsOf(buyer.getUUID()).get(0).count() == 12, "All twelve still waiting");

        market.addReturn(buyer.getUUID(), StoredStack.of(new ItemStack(Items.COBBLESTONE, 70)));
        inv.setItem(35, ItemStack.EMPTY);
        MarketActions.ClaimResult some = MarketActions.claimReturns(buyer);
        helper.assertTrue(some.claimed() == 12, "Oldest first, so the planks take the one free slot, got " + some.claimed());
        List<StoredStack> left = market.returnsOf(buyer.getUUID());
        helper.assertTrue(left.size() == 1 && left.get(0).count() == 70, "The cobblestone has no room and stays whole");

        inv.setItem(34, ItemStack.EMPTY);
        MarketActions.ClaimResult part = MarketActions.claimReturns(buyer);
        helper.assertTrue(part.claimed() == 64, "One more slot takes 64, got " + part.claimed());
        helper.assertTrue(market.returnsOf(buyer.getUUID()).size() == 1
                && market.returnsOf(buyer.getUUID()).get(0).count() == 6, "The 6 that did not fit stay");

        inv.setItem(33, ItemStack.EMPTY);
        MarketActions.claimReturns(buyer);
        helper.assertTrue(market.returnsOf(buyer.getUUID()).isEmpty(), "Everything claimed once there was room");
        helper.assertTrue(count(inv, Items.OAK_PLANKS) == 76 && count(inv, Items.COBBLESTONE) == 70, "Nothing lost");
        helper.assertTrue(droppedItems(helper) == droppedBefore, "Nothing was dropped on the ground");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aStackMergingIntoTheOffhandCountsAsGivenAndAFullInventoryLosesNothing(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        Inventory inv = buyer.getInventory();

        inv.setItem(40, new ItemStack(Items.OAK_PLANKS, 10));
        helper.assertTrue(Giving.give(inv, new ItemStack(Items.OAK_PLANKS, 16)) == 16, "The helper counts the offhand");
        helper.assertTrue(inv.getItem(40).getCount() == 26 || count(inv, Items.OAK_PLANKS) == 26,
                "26 planks in all");
        inv.setItem(40, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, ItemStack.EMPTY);
        }

        inv.setItem(40, new ItemStack(Items.OAK_PLANKS, 10));
        WalletData.get(seller.server).set(buyer.getUUID(), 50);
        long id = list(helper, seller, new ItemStack(Items.OAK_PLANKS, 16), 20);
        MarketActions.BuyResult merged = MarketActions.buy(buyer, id, 20, NOW);
        helper.assertTrue(merged.status() == MarketActions.BuyStatus.OK && merged.toReturns() == 0,
                "Merged items are not sent to Returns as well, toReturns " + merged.toReturns());
        helper.assertTrue(market.returnsOf(buyer.getUUID()).isEmpty(), "Returns stay empty");
        helper.assertTrue(count(inv, Items.OAK_PLANKS) == 26, "Exactly 26 planks, no duplicates");

        for (int i = 0; i < 36; i++) {
            inv.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        inv.setItem(40, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(Giving.give(inv, new ItemStack(Items.STICK, 5)) == 0, "A full inventory takes nothing");
        helper.assertTrue(count(inv, Items.STICK) == 0, "And nothing is left behind");
        long id2 = list(helper, seller, new ItemStack(Items.STICK, 5), 20);
        WalletData.get(seller.server).set(buyer.getUUID(), 50);
        MarketActions.BuyResult full = MarketActions.buy(buyer, id2, 20, NOW);
        helper.assertTrue(full.status() == MarketActions.BuyStatus.OK && full.toReturns() == 5,
                "All five go to Returns, got " + full.toReturns());
        helper.assertTrue(market.returnsOf(buyer.getUUID()).get(0).count() == 5, "And they are in the list");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelAndExpirySendTheItemToReturns(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer other = player(helper);
        MarketData market = MarketData.get(seller.server);

        long id = list(helper, seller, fancySword(helper), 100);
        helper.assertTrue(MarketActions.cancel(other, id).status() == MarketActions.CancelStatus.NOT_YOURS,
                "Only the seller can cancel");
        helper.assertTrue(market.listing(id) != null, "Still listed");
        helper.assertTrue(MarketActions.cancel(seller, id).status() == MarketActions.CancelStatus.OK, "Seller cancels");
        helper.assertTrue(market.listing(id) == null, "Listing gone");
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 1
                && ItemStack.matches(market.returnsOf(seller.getUUID()).get(0).stack(), fancySword(helper)),
                "The sword is in Returns with its data");
        helper.assertTrue(MarketActions.cancel(seller, id).status() == MarketActions.CancelStatus.NOT_FOUND,
                "Can't cancel twice");

        long second = list(helper, seller, new ItemStack(Items.IRON_INGOT, 9), 5);
        long expiresAt = market.listing(second).expiresAt();
        helper.assertTrue(MarketActions.expireDue(seller.server, expiresAt - 1) == 0, "Not due yet");
        helper.assertTrue(market.listing(second) != null, "Still listed before the time");
        helper.assertTrue(MarketActions.expireDue(seller.server, expiresAt) >= 1, "Due at the time");
        helper.assertTrue(market.listing(second) == null, "Listing gone after expiry");
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 2, "Two stacks in Returns now");
        clear(seller, other);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void marketDataSurvivesSaveAndLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        ItemStack sword = fancySword(helper);
        MarketData data = new MarketData();
        long first = data.nextId();
        data.addListing(new Listing(first, seller, "Ann", StoredStack.of(sword), Long.MAX_VALUE - 1, Long.MAX_VALUE));
        data.addReturn(buyer, StoredStack.of(new ItemStack(Items.DIAMOND, 12)));
        data.addToPool(Long.MAX_VALUE - 3);
        data.recordSale(new Sale(77, seller, "Ann", buyer, "Bob", "minecraft:diamond_sword", 1, 500, 25));

        CompoundTag tag = data.save(new CompoundTag(), registries);
        MarketData loaded = MarketData.load(tag, registries);
        Listing listing = loaded.listing(first);
        helper.assertTrue(listing != null && listing.price() == Long.MAX_VALUE - 1, "Large price survives");
        helper.assertTrue(listing.expiresAt() == Long.MAX_VALUE && listing.sellerName().equals("Ann")
                && listing.seller().equals(seller), "Expiry and seller survive");
        helper.assertTrue(ItemStack.matches(listing.item().stack(), sword), "Enchants and name survive");
        helper.assertTrue(loaded.returnsOf(buyer).size() == 1 && loaded.returnsOf(buyer).get(0).count() == 12,
                "Returns survive");
        helper.assertTrue(loaded.pool() == Long.MAX_VALUE - 3, "Pool survives");
        helper.assertTrue(loaded.sales().recent().size() == 1 && loaded.sales().recent().get(0).cut() == 25
                && loaded.sales().lifetime(seller) == 500, "Sales and totals survive");
        helper.assertTrue(loaded.nextId() == first + 1, "The id counter carries on and never reuses an id");
        helper.succeed();
    }

    private static CompoundTag ghostTag() {
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "ghostmod:ghost_item");
        raw.putInt("count", 3);
        CompoundTag extra = new CompoundTag();
        extra.putString("keep", "me");
        raw.put("components", extra);
        return raw;
    }

    @GameTest(template = "empty")
    public static void aStackWhoseItemDoesNotExistKeepsItsRawTagAndCantBeBought(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID seller = UUID.randomUUID();
        CompoundTag raw = ghostTag();

        StoredStack ghost = StoredStack.fromTag(raw, registries);
        helper.assertTrue(!ghost.isAvailable(), "A stack with an unknown item is unavailable");
        helper.assertTrue(ghost.itemId().equals("ghostmod:ghost_item") && ghost.count() == 3, "Id and count are readable");

        CompoundTag halfBroken = new CompoundTag();
        halfBroken.putString("id", "minecraft:diamond_sword");
        halfBroken.putInt("count", 1);
        CompoundTag components = new CompoundTag();
        CompoundTag enchants = new CompoundTag();
        CompoundTag levels = new CompoundTag();
        levels.putInt("ghostmod:ghost_enchant", 1);
        enchants.put("levels", levels);
        components.put("minecraft:enchantments", enchants);
        halfBroken.put("components", components);
        StoredStack half = StoredStack.fromTag(halfBroken, registries);
        helper.assertTrue(!half.isAvailable() && half.raw().equals(halfBroken),
                "A stack that only half loads is unavailable and keeps all of its tag");

        MarketData data = new MarketData();
        long good = data.nextId();
        long bad = data.nextId();
        long half2 = data.nextId();
        data.addListing(new Listing(good, seller, "Ann", StoredStack.of(new ItemStack(Items.APPLE, 2)), 5, 100));
        data.addListing(new Listing(bad, seller, "Ann", ghost, 9, 100));
        data.addListing(new Listing(half2, seller, "Ann", half, 9, 100));
        data.addReturn(seller, ghost);

        MarketData once = MarketData.load(data.save(new CompoundTag(), registries), registries);
        MarketData twice = MarketData.load(once.save(new CompoundTag(), registries), registries);
        helper.assertTrue(twice.listing(good) != null && twice.listing(good).item().isAvailable(),
                "The other listing is fine");
        helper.assertTrue(twice.listing(bad) != null && twice.listing(bad).item().raw().equals(raw),
                "The raw tag comes back unchanged after two saves and loads");
        helper.assertTrue(twice.listing(half2).item().raw().equals(halfBroken), "So does the half loaded one");
        helper.assertTrue(twice.returnsOf(seller).size() == 1 && twice.returnsOf(seller).get(0).raw().equals(raw),
                "An unavailable stack in Returns is kept too");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anUnavailableListingCantBeBoughtNeverExpiresAndCanBeCancelled(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MarketData market = MarketData.get(seller.server);
        var registries = helper.getLevel().registryAccess();
        CompoundTag raw = ghostTag();

        long id = market.nextId();
        market.addListing(new Listing(id, seller.getUUID(), "Ann", StoredStack.fromTag(raw, registries), 10, NOW));
        WalletData.get(seller.server).set(buyer.getUUID(), 100);
        helper.assertTrue(MarketActions.buy(buyer, id, 10, NOW - 1).status() == MarketActions.BuyStatus.UNAVAILABLE,
                "Can't be bought");
        helper.assertTrue(MarketActions.buy(buyer, id, 10, NOW + 5).status() == MarketActions.BuyStatus.UNAVAILABLE,
                "Past its time it is still unavailable, not expired");
        MarketActions.expireDue(seller.server, NOW + 10);
        helper.assertTrue(market.listing(id) != null, "It doesn't expire while unavailable");
        helper.assertTrue(wallet(buyer) == 100, "No money moved");

        helper.assertTrue(MarketActions.cancel(seller, id).status() == MarketActions.CancelStatus.OK,
                "The seller can cancel it");
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 1
                && market.returnsOf(seller.getUUID()).get(0).raw().equals(raw), "It sits in Returns with its raw tag");
        MarketActions.ClaimResult claim = MarketActions.claimReturns(seller);
        helper.assertTrue(claim.claimed() == 0 && claim.unavailableLeft() == 1, "A claim leaves it there");
        helper.assertTrue(market.returnsOf(seller.getUUID()).get(0).raw().equals(raw), "Still unchanged");
        clear(seller, buyer);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFormatVersionNewerThanTheCodeMakesTheLoaderThrow(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag newer = new MarketData().save(new CompoundTag(), registries);
        newer.putInt("version", MarketData.FORMAT_VERSION + 1);
        helper.assertTrue(throwsOnLoad(newer, helper), "Newer version must throw");
        helper.assertTrue(throwsOnLoad(new CompoundTag(), helper), "No version must throw");

        CompoundTag missing = new MarketData().save(new CompoundTag(), registries);
        missing.remove("listings");
        helper.assertTrue(throwsOnLoad(missing, helper), "A missing part must throw");

        CompoundTag oneBad = new MarketData().save(new CompoundTag(), registries);
        ListTag listings = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putLong("id", 5);
        listings.add(bad);
        oneBad.put("listings", listings);
        helper.assertTrue(MarketData.load(oneBad, registries).listingsNewestFirst().isEmpty(),
                "One broken listing entry is skipped and the rest loads");
        helper.succeed();
    }

    private static boolean throwsOnLoad(CompoundTag tag, GameTestHelper helper) {
        try {
            MarketData.load(tag, helper.getLevel().registryAccess());
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @GameTest(template = "empty")
    public static void adminCommandsRefuseAPlayerWithoutPermission(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        MinecraftServer server = seller.server;
        MarketData market = MarketData.get(server);
        var commands = server.getCommands();

        long id = list(helper, seller, new ItemStack(Items.DIAMOND_PICKAXE), 80);
        commands.performPrefixedCommand(seller.createCommandSourceStack().withPermission(0), "market admin remove " + id);
        commands.performPrefixedCommand(seller.createCommandSourceStack().withPermission(0), "market admin pool");
        helper.assertTrue(market.listing(id) != null, "No permission, no removal");

        commands.performPrefixedCommand(seller.createCommandSourceStack().withPermission(2),
                "markettrade admin remove " + id);
        helper.assertTrue(market.listing(id) == null, "Permission level 2 can remove");
        helper.assertTrue(market.returnsOf(seller.getUUID()).size() == 1, "The item went to the seller's Returns");
        commands.performPrefixedCommand(seller.createCommandSourceStack().withPermission(2), "market admin remove " + id);
        commands.performPrefixedCommand(seller.createCommandSourceStack().withPermission(2), "market admin pool");
        clear(seller);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerCommandsListBuyAndClaim(GameTestHelper helper) {
        ServerPlayer seller = player(helper);
        ServerPlayer buyer = player(helper);
        MinecraftServer server = seller.server;
        MarketData market = MarketData.get(server);
        var commands = server.getCommands();

        seller.getInventory().setItem(seller.getInventory().selected, new ItemStack(Items.AMETHYST_SHARD, 8));
        commands.performPrefixedCommand(seller.createCommandSourceStack(), "market sell 30");
        List<Listing> mine = market.listingsOf(seller.getUUID());
        helper.assertTrue(mine.size() == 1 && mine.get(0).price() == 30, "The sell command lists the stack");
        long id = mine.get(0).id();

        commands.performPrefixedCommand(buyer.createCommandSourceStack(), "market listings");
        commands.performPrefixedCommand(buyer.createCommandSourceStack(), "market listings 99");
        commands.performPrefixedCommand(seller.createCommandSourceStack(), "market mine");
        WalletData.get(server).set(buyer.getUUID(), 100);
        commands.performPrefixedCommand(buyer.createCommandSourceStack(), "market buy " + id + " 31");
        helper.assertTrue(market.listing(id) != null, "A wrong price in the command changes nothing");
        commands.performPrefixedCommand(buyer.createCommandSourceStack(), "market buy " + id + " 30");
        helper.assertTrue(market.listing(id) == null && count(buyer.getInventory(), Items.AMETHYST_SHARD) == 8,
                "The buy command buys");
        commands.performPrefixedCommand(seller.createCommandSourceStack(), "market returns");
        commands.performPrefixedCommand(seller.createCommandSourceStack(), "market returns claim");
        commands.performPrefixedCommand(server.createCommandSourceStack(), "market listings");
        commands.performPrefixedCommand(server.createCommandSourceStack(), "market mine");
        clear(seller, buyer);
        helper.succeed();
    }
}
