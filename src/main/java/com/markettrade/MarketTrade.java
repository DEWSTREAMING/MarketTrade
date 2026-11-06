package com.markettrade;

import com.markettrade.command.MarketCommand;
import com.markettrade.currency.Currencies;
import com.markettrade.market.MarketActions;
import com.markettrade.market.MarketConfigs;
import com.markettrade.market.MarketData;
import com.markettrade.market.SavedDataGuard;
import com.markettrade.market.WalletData;
import com.markettrade.net.ClientTracker;
import com.markettrade.net.ModNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(MarketTrade.MODID)
public class MarketTrade {
    public static final String MODID = "markettrade";
    private static final int TICKS_PER_MINUTE = 20 * 60;

    public MarketTrade(IEventBus modBus, ModContainer container) {
        modBus.addListener(ModNetworking::register);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(ClientTracker::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> com.markettrade.net.MarketRequests.forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            Currencies.load();
            MarketConfigs.load();
        });
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            SavedDataGuard.guardAtStart(event.getServer(), WalletData.factory(), WalletData.NAME);
            SavedDataGuard.guardAtStart(event.getServer(), MarketData.factory(), MarketData.NAME);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> MarketActions.expireDue(event.getServer(),
                System.currentTimeMillis()));
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onAnvilRepair);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.markettrade.client.ClientSetup.init(modBus);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % TICKS_PER_MINUTE == 0) {
            MarketActions.expireDue(event.getServer(), System.currentTimeMillis());
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && MarketActions.returnsCount(player.server, player.getUUID()) > 0) {
            player.sendSystemMessage(Text.translatable("markettrade.returns_waiting"));
        }
        if (event.getEntity() instanceof ServerPlayer player && player.hasPermissions(2)) {
            int open = MarketData.get(player.server).openReportsNewestFirst().size();
            if (open > 0) {
                player.sendSystemMessage(Text.translatable("markettrade.reports_waiting", open));
            }
        }
    }

    private void onAnvilRepair(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketActions.logRename(player, event.getLeft(), event.getOutput());
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        MarketCommand.register(event.getDispatcher());
    }
}
