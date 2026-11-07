package com.markettrade.devtest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.markettrade.MarketTrade;
import com.markettrade.command.MarketCommand;
import com.markettrade.client.ClientData;
import com.markettrade.client.MarketScreen;
import com.markettrade.client.theme.Theme;
import com.markettrade.client.theme.Themes;
import com.markettrade.market.Expiry;
import com.markettrade.market.Listing;
import com.markettrade.market.MarketData;
import com.markettrade.market.Sale;
import com.markettrade.market.StoredStack;
import com.markettrade.market.WalletData;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Screenshots of the market screens for the project page. Dev only, left out of the jar. Run with
 * {@code gradlew runClient -PmenuShots}: it makes a flat world, fills the market with sample listings and sales, opens each
 * screen and theme, saves the pictures to {@code run-shots/shots/} and quits.
 */
@EventBusSubscriber(modid = MarketTrade.MODID, value = Dist.CLIENT)
public final class MenuStudio {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("markettrade.studio");

    private enum Phase {
        BOOT, WAIT_WORLD, SEED, SHOTS, DONE
    }

    private record Step(String name, Runnable setup, int ticks) {
    }

    private static Phase phase = Phase.BOOT;
    private static int wait;
    private static boolean seeded;
    private static final List<Step> steps = new ArrayList<>();
    private static int index;
    private static boolean setUp;

    private MenuStudio() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ON || phase == Phase.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().clearMessages(true);
        }
        mc.getToasts().clear();
        mc.getTutorial().stop();
        mc.getSoundManager().stop();
        if (phase == Phase.SHOTS) {
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1.0, 1.0);
            if (mc.level != null) {
                mc.level.setDayTime(6000);
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            switch (phase) {
                case BOOT -> boot(mc);
                case WAIT_WORLD -> waitWorld(mc);
                case SEED -> {
                    if (seeded) {
                        planShots(mc);
                        phase = Phase.SHOTS;
                        wait = 40;
                    }
                }
                case SHOTS -> shots(mc);
                default -> {
                }
            }
        } catch (Exception e) {
            LOGGER.error("STUDIO failed", e);
            phase = Phase.DONE;
            mc.stop();
        }
    }

    private static void boot(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen)) {
            return;
        }
        mc.options.guiScale().set(2);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.chatVisibility().set(ChatVisiblity.FULL);
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.onboardAccessibility = false;
        mc.options.hideGui = true;
        mc.resizeDisplay();
        LevelSettings settings = new LevelSettings("market shots", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("market_shots_" + System.currentTimeMillis(), settings,
                new WorldOptions(1L, false, false),
                reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                mc.screen);
        phase = Phase.WAIT_WORLD;
    }

    private static void waitWorld(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        server.execute(() -> {
            seed(server, server.getPlayerList().getPlayers().get(0));
            seeded = true;
        });
        phase = Phase.SEED;
        wait = 60;
    }

    // Sample market

    private static final String[] SELLERS = { "Miner49", "CopperFox", "Stonewell", "Nightshift", "AvaBuilds", "Pebble" };

    private static void seed(MinecraftServer server, ServerPlayer player) {
        GameRules rules = server.overworld().getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        server.overworld().setDayTime(6000);
        MarketData market = MarketData.get(server);
        long now = System.currentTimeMillis();
        Random rnd = new Random(7);
        var enchants = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        List<Object[]> items = new ArrayList<>();
        // stack, price in diamonds, seller index
        items.add(new Object[] { enchanted(enchants, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5), 24L, 0 });
        items.add(new Object[] { enchanted(enchants, Items.DIAMOND_PICKAXE, Enchantments.UNBREAKING, 3), 27L, 1 });
        items.add(new Object[] { enchanted(enchants, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE, 3), 32L, 2 });
        items.add(new Object[] { new ItemStack(Items.DIAMOND_PICKAXE), 34L, 0 });
        ItemStack named = enchanted(enchants, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Reliable"));
        items.add(new Object[] { named, 38L, 3 });
        items.add(new Object[] { new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 120L, 4 });
        items.add(new Object[] { new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 135L, 2 });
        items.add(new Object[] { new ItemStack(Items.NETHERITE_INGOT), 64L, 1 });
        items.add(new Object[] { new ItemStack(Items.NETHERITE_INGOT, 2), 130L, 5 });
        items.add(new Object[] { new ItemStack(Items.ELYTRA), 210L, 3 });
        items.add(new Object[] { new ItemStack(Items.TOTEM_OF_UNDYING), 45L, 4 });
        items.add(new Object[] { book(enchants, Enchantments.MENDING, 1), 30L, 5 });
        items.add(new Object[] { book(enchants, Enchantments.MENDING, 1), 33L, 0 });
        items.add(new Object[] { new ItemStack(Items.OAK_LOG, 64), 2L, 2 });
        items.add(new Object[] { new ItemStack(Items.OAK_LOG, 64), 3L, 4 });
        items.add(new Object[] { new ItemStack(Items.ENDER_PEARL, 16), 6L, 1 });
        items.add(new Object[] { enchanted(enchants, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 5), 30L, 3 });
        items.add(new Object[] { new ItemStack(Items.BEACON), 300L, 5 });
        items.add(new Object[] { new ItemStack(Items.SHULKER_SHELL, 2), 18L, 0 });
        items.add(new Object[] { new ItemStack(Items.TRIDENT), 90L, 2 });
        items.add(new Object[] { new ItemStack(Items.GOLDEN_CARROT, 64), 8L, 4 });
        items.add(new Object[] { new ItemStack(Items.NAME_TAG), 5L, 1 });
        items.add(new Object[] { new ItemStack(Items.SADDLE), 7L, 3 });
        items.add(new Object[] { new ItemStack(Items.EXPERIENCE_BOTTLE, 32), 12L, 5 });
        items.add(new Object[] { new ItemStack(Items.HEART_OF_THE_SEA), 55L, 0 });
        items.add(new Object[] { new ItemStack(Items.MUSIC_DISC_PIGSTEP), 40L, 2 });
        for (Object[] it : items) {
            int s = (int) it[2];
            long expires = Expiry.at(now - rnd.nextInt(5) * 86_400_000L - rnd.nextInt(80_000_000), 7);
            market.addListing(new Listing(market.nextId(), seller(s), SELLERS[s], StoredStack.of((ItemStack) it[0]), (long) it[1],
                    expires));
        }
        // 14 days of pickaxe sales for the chart
        for (int day = 13; day >= 0; day--) {
            int sales = 1 + rnd.nextInt(3);
            for (int k = 0; k < sales; k++) {
                long price = 25 + rnd.nextInt(6) + (13 - day) / 3;
                int s = rnd.nextInt(SELLERS.length);
                market.recordSale(new Sale(now - day * 86_400_000L - k * 3_600_000L, seller(s), SELLERS[s], seller((s + 1) % 6),
                        SELLERS[(s + 1) % 6], "minecraft:diamond_pickaxe", 1, price, price * 5 / 100));
            }
        }
        // the player's own things: two listings, a Returns stack, a wallet and items to sell
        market.addListing(new Listing(market.nextId(), player.getUUID(), player.getGameProfile().getName(),
                StoredStack.of(new ItemStack(Items.GOLDEN_APPLE, 8)), 16L, Expiry.at(now, 7)));
        market.addListing(new Listing(market.nextId(), player.getUUID(), player.getGameProfile().getName(),
                StoredStack.of(new ItemStack(Items.AMETHYST_SHARD, 32)), 9L, Expiry.at(now - 3 * 86_400_000L, 7)));
        market.addReturn(player.getUUID(), StoredStack.of(new ItemStack(Items.IRON_INGOT, 48)));
        WalletData.get(server).set(player.getUUID(), 214);
        var inv = player.getInventory();
        inv.clearContent();
        inv.setItem(0, enchanted(enchants, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 4));
        inv.setItem(1, new ItemStack(Items.GOLD_INGOT, 40));
        inv.setItem(2, new ItemStack(Items.REDSTONE, 64));
        inv.setItem(3, new ItemStack(Items.EMERALD, 12));
        inv.setItem(4, new ItemStack(Items.BREAD, 20));
        inv.setItem(9, new ItemStack(Items.OAK_LOG, 64));
        inv.setItem(10, new ItemStack(Items.COBBLESTONE, 64));
        inv.setItem(11, new ItemStack(Items.ENDER_PEARL, 9));
        inv.setItem(12, book(enchants, Enchantments.LOOTING, 3));
        inv.setItem(18, new ItemStack(Items.DIAMOND, 7));
        inv.setItem(19, new ItemStack(Items.BLAZE_ROD, 14));
        inv.setItem(27, new ItemStack(Items.TORCH, 64));
        player.teleportTo(0.5, -60, 0.5);
    }

    private static UUID seller(int i) {
        return UUID.nameUUIDFromBytes(("studio-seller-" + i).getBytes());
    }

    private static ItemStack enchanted(net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> lookup,
            net.minecraft.world.item.Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        Holder<Enchantment> holder = lookup.getOrThrow(key);
        stack.enchant(holder, level);
        return stack;
    }

    private static ItemStack book(net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> lookup,
            ResourceKey<Enchantment> key, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(lookup.getOrThrow(key), level));
    }

    // Shots

    private static void planShots(Minecraft mc) {
        for (Theme theme : Themes.all()) {
            steps.add(new Step("browse_" + theme.id(), () -> {
                ClientData.setTheme(theme);
                mc.setScreen(new MarketScreen());
            }, 60));
        }
        steps.add(new Step("item_page", () -> {
            ClientData.setTheme(Themes.MARKET);
            MarketScreen screen = new MarketScreen();
            mc.setScreen(screen);
            set(screen, "itemId", "minecraft:diamond_pickaxe");
            set(screen, "itemPage", 0);
            view(screen, "ITEM");
        }, 60));
        steps.add(new Step("item_page_vanilla_plus", () -> {
            ClientData.setTheme(Themes.all().stream().filter(t -> t.id().equals("vanilla_plus")).findFirst().orElseThrow());
            MarketScreen screen = new MarketScreen();
            mc.setScreen(screen);
            set(screen, "itemId", "minecraft:diamond_pickaxe");
            set(screen, "itemPage", 0);
            view(screen, "ITEM");
        }, 60));
        steps.add(new Step("sell", () -> {
            ClientData.setTheme(Themes.MARKET);
            MarketScreen screen = new MarketScreen();
            mc.setScreen(screen);
            view(screen, "SELL");
            set(screen, "selectedSlot", 0);
            set(screen, "selected", mc.player.getInventory().getItem(0).copy());
            EditBox priceBox = (EditBox) get(screen, "price");
            priceBox.setWidth(500);
            priceBox.setValue("2 diamond block 4 diamond");
            priceBox.setCursorPosition(0);
            priceBox.setHighlightPos(0);
        }, 60));
        steps.add(new Step("my_listings", () -> {
            MarketScreen screen = new MarketScreen();
            mc.setScreen(screen);
            view(screen, "MINE");
        }, 50));
        steps.add(new Step("wallet", () -> {
            MarketScreen screen = new MarketScreen();
            mc.setScreen(screen);
            view(screen, "WALLET");
        }, 50));
        steps.add(new Step("chest_screen", () -> {
            mc.setScreen(null);
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) {
                        MarketCommand.openChest(sp);
                    }
                });
            } else if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("market chest");
            }
        }, 60));
    }

    private static void shots(Minecraft mc) {
        if (index >= steps.size()) {
            mc.setScreen(null);
            ClientData.setTheme(Themes.MARKET);
            phase = Phase.DONE;
            LOGGER.info("STUDIO done, {} shots", steps.size());
            mc.stop();
            return;
        }
        Step step = steps.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        if (!setUp) {
            if (server != null) {
                server.execute(() -> {
                    GameRules rules = server.overworld().getGameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    server.overworld().setDayTime(6000);
                });
            }
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1.0, 1.0);
            if (mc.level != null) {
                mc.level.setDayTime(6000);
            }
            step.setup().run();
            setUp = true;
            wait = step.ticks();
            return;
        }
        if (server != null) {
            server.execute(() -> server.overworld().setDayTime(6000));
        }
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1.0, 1.0);
        if (mc.level != null) {
            mc.level.setDayTime(6000);
        }
        Path file = mc.gameDirectory.toPath().resolve("shots").resolve(step.name() + ".png");
        try {
            Files.createDirectories(file.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file);
            }
            LOGGER.info("STUDIO wrote {}", file);
        } catch (IOException e) {
            LOGGER.error("STUDIO could not write {}", file, e);
        }
        index++;
        setUp = false;
    }

    // MarketScreen keeps its state private, which is right for the game. These reach in for the pictures only.

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void set(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object get(Object target, String name) {
        try {
            return field(target, name).get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void view(MarketScreen screen, String name) {
        try {
            Class<?> viewClass = Class.forName(MarketScreen.class.getName() + "$View");
            Object value = Enum.valueOf((Class) viewClass, name);
            Method setView = MarketScreen.class.getDeclaredMethod("setView", viewClass);
            setView.setAccessible(true);
            setView.invoke(screen, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
