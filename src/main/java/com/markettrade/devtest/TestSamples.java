package com.markettrade.devtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The sixty items the chest screen tests list. Not shipped. */
final class TestSamples {
    record Sample(ItemStack item, String seller, long price) {
    }

    private TestSamples() {
    }

    static List<Sample> getAll(RegistryAccess registryAccess) {
        return buildSampleListings(registryAccess);
    }

    private static List<Sample> buildSampleListings(RegistryAccess registryAccess) {
        List<Sample> list = new ArrayList<>(60);

        // 1. Renamed item
        ItemStack renamedSword = new ItemStack(Items.DIAMOND_SWORD);
        renamedSword.set(DataComponents.CUSTOM_NAME, Component.literal("Dragon Slayer"));
        list.add(new Sample(renamedSword, "Dewey", 500L));

        // 2. Enchanted item
        ItemStack enchantedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        if (registryAccess != null) {
            registryAccess.lookup(Registries.ENCHANTMENT).ifPresent(lookup -> {
                lookup.get(Enchantments.UNBREAKING).ifPresent(holder -> {
                    ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mut.set(holder, 3);
                    enchantedPickaxe.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
                });
            });
        }
        list.add(new Sample(enchantedPickaxe, "Miner49er", 350L));

        // 3-60. 58 other vanilla items
        list.add(new Sample(new ItemStack(Items.NETHERITE_INGOT), "Alex", 1000L));
        list.add(new Sample(new ItemStack(Items.GOLDEN_APPLE), "Steve", 50L));
        list.add(new Sample(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Notch", 5000L));
        list.add(new Sample(new ItemStack(Items.ELYTRA), "Icarus", 7500L));
        list.add(new Sample(new ItemStack(Items.TOTEM_OF_UNDYING), "Immortal", 2000L));
        list.add(new Sample(new ItemStack(Items.TRIDENT), "Poseidon", 1200L));
        list.add(new Sample(new ItemStack(Items.DIAMOND, 8), "DiamondDave", 80L));
        list.add(new Sample(new ItemStack(Items.EMERALD, 16), "Villager", 32L));
        list.add(new Sample(new ItemStack(Items.NETHER_STAR), "WitherBuster", 10000L));
        list.add(new Sample(new ItemStack(Items.ANCIENT_DEBRIS, 4), "DeepDigger", 600L));
        list.add(new Sample(new ItemStack(Items.OBSIDIAN, 64), "PortalMaker", 64L));
        list.add(new Sample(new ItemStack(Items.CRYING_OBSIDIAN, 16), "RespawnPoint", 48L));
        list.add(new Sample(new ItemStack(Items.ENDER_PEARL, 16), "Enderman", 80L));
        list.add(new Sample(new ItemStack(Items.BLAZE_ROD, 16), "FireBender", 64L));
        list.add(new Sample(new ItemStack(Items.GHAST_TEAR, 8), "NetherWalker", 120L));
        list.add(new Sample(new ItemStack(Items.SPYGLASS), "Lookout", 15L));
        list.add(new Sample(new ItemStack(Items.COMPASS), "Explorer", 10L));
        list.add(new Sample(new ItemStack(Items.CLOCK), "TimeKeeper", 12L));
        list.add(new Sample(new ItemStack(Items.SADDLE), "Horseman", 25L));
        list.add(new Sample(new ItemStack(Items.NAME_TAG, 3), "PetLover", 30L));
        list.add(new Sample(new ItemStack(Items.LEAD, 2), "Rancher", 8L));
        list.add(new Sample(new ItemStack(Items.HONEY_BOTTLE, 4), "BeeKeeper", 16L));
        list.add(new Sample(new ItemStack(Items.SLIME_BALL, 32), "Bounce", 32L));
        list.add(new Sample(new ItemStack(Items.HEART_OF_THE_SEA), "Sailor", 1500L));
        list.add(new Sample(new ItemStack(Items.NAUTILUS_SHELL, 8), "Fisherman", 240L));
        list.add(new Sample(new ItemStack(Items.SHULKER_SHELL, 2), "BoxMaker", 800L));
        list.add(new Sample(new ItemStack(Items.DRAGON_BREATH, 4), "Alchemist", 200L));
        list.add(new Sample(new ItemStack(Items.AMETHYST_SHARD, 32), "GeodeHunter", 32L));
        list.add(new Sample(new ItemStack(Items.COPPER_INGOT, 64), "Smith", 16L));
        list.add(new Sample(new ItemStack(Items.IRON_INGOT, 64), "IronGolem", 32L));
        list.add(new Sample(new ItemStack(Items.GOLD_INGOT, 32), "Piglin", 64L));
        list.add(new Sample(new ItemStack(Items.REDSTONE, 64), "Engineer", 32L));
        list.add(new Sample(new ItemStack(Items.LAPIS_LAZULI, 64), "Enchanter", 32L));
        list.add(new Sample(new ItemStack(Items.GLOWSTONE_DUST, 64), "Lumin", 32L));
        list.add(new Sample(new ItemStack(Items.QUARTZ, 64), "Builder", 32L));
        list.add(new Sample(new ItemStack(Items.COAL, 64), "Miner", 16L));
        list.add(new Sample(new ItemStack(Items.CHARCOAL, 64), "Lumberjack", 16L));
        list.add(new Sample(new ItemStack(Items.COOKED_BEEF, 64), "Chef", 20L));
        list.add(new Sample(new ItemStack(Items.GOLDEN_CARROT, 64), "Farmer", 64L));
        list.add(new Sample(new ItemStack(Items.CAKE), "Baker", 15L));
        list.add(new Sample(new ItemStack(Items.PUMPKIN_PIE, 16), "Baker", 24L));
        list.add(new Sample(new ItemStack(Items.BREAD, 64), "Miller", 16L));
        list.add(new Sample(new ItemStack(Items.ARROW, 64), "Archer", 16L));
        list.add(new Sample(new ItemStack(Items.BOW), "Fletcher", 20L));
        list.add(new Sample(new ItemStack(Items.CROSSBOW), "Pillager", 25L));
        list.add(new Sample(new ItemStack(Items.SHIELD), "Defender", 15L));
        list.add(new Sample(new ItemStack(Items.OAK_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.BIRCH_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.SPRUCE_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.DARK_OAK_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.MANGROVE_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.CHERRY_LOG, 64), "Woodsman", 16L));
        list.add(new Sample(new ItemStack(Items.COBBLESTONE, 64), "Quarry", 5L));
        list.add(new Sample(new ItemStack(Items.SMOOTH_STONE, 64), "Mason", 10L));
        list.add(new Sample(new ItemStack(Items.PRISMARINE, 64), "OceanTemple", 40L));
        list.add(new Sample(new ItemStack(Items.SEA_LANTERN, 16), "Diver", 64L));
        list.add(new Sample(new ItemStack(Items.SPONGE), "Dryer", 250L));
        list.add(new Sample(new ItemStack(Items.MUSIC_DISC_PIGSTEP), "DJ", 3000L));

        return Collections.unmodifiableList(list);
    }
}
