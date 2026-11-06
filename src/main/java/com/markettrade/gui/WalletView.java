package com.markettrade.gui;

import java.util.List;

import com.markettrade.Replies;
import com.markettrade.Text;
import com.markettrade.currency.Currencies;
import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;
import com.markettrade.market.AmountParser;
import com.markettrade.market.WalletActions;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The balance, Deposit All, one button per currency item and an Amount button. Everything goes through WalletActions. */
final class WalletView implements MarketView {
    private static final int BALANCE = 4;
    private static final int DEPOSIT_ALL = 38;
    private static final int AMOUNT = 42;
    /** Two rows of seven. */
    private static final int[] ITEM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final BrowseState state;

    WalletView(BrowseState state) {
        this.state = state;
    }

    @Override
    public void draw(MarketChestMenu menu) {
        ServerPlayer player = menu.player();
        CurrencyConfig currency = Currencies.current();
        long balance = WalletActions.balance(player.server, player.getUUID());
        menu.put(BALANCE, Buttons.of(Items.GOLD_INGOT, Buttons.name("markettrade.balance", ListingIcons.showWithTotal(balance))));
        menu.put(MarketChestMenu.BACK_SLOT, Buttons.of(Items.ARROW, Buttons.name("markettrade.back")));
        if (!currency.hasItems()) {
            return;
        }

        List<CurrencyItem> items = currency.items();
        for (int i = 0; i < items.size() && i < ITEM_SLOTS.length; i++) {
            CurrencyItem entry = items.get(i);
            Item item = itemOf(entry.item());
            if (item == null) {
                continue;
            }
            menu.put(ITEM_SLOTS[i], Buttons.of(item,
                    Buttons.plain(net.minecraft.network.chat.Component.literal(entry.name()), ChatFormatting.WHITE),
                    Buttons.line("markettrade.wallet_value", ListingIcons.show(entry.value())),
                    Buttons.line("markettrade.wallet_take_one"), Buttons.line("markettrade.wallet_take_stack")),
                    MarketChestMenu.Drawn.currency(entry.item()));
        }
        menu.put(DEPOSIT_ALL, Buttons.of(Items.HOPPER, Buttons.name("markettrade.deposit_all_button")));
        menu.put(AMOUNT, Buttons.of(Items.NAME_TAG, Buttons.name("markettrade.withdraw_amount_button"),
                Buttons.line("markettrade.withdraw_amount_hint")));
    }

    @Override
    public void click(MarketChestMenu menu, ServerPlayer player, int slot, int button, ClickType type) {
        MarketChestMenu.Drawn drawn = menu.drawn(slot);
        if (drawn != null && drawn.text() != null) {
            WalletActions.WithdrawResult result = type == ClickType.QUICK_MOVE
                    ? WalletActions.withdrawStack(player, drawn.text())
                    : WalletActions.withdrawItem(player, drawn.text(), 1);
            Buttons.say(player, Replies.withdraw(result));
            menu.show(this);
        } else if (slot == MarketChestMenu.BACK_SLOT) {
            menu.show(new BrowseView(state));
        } else if (slot == DEPOSIT_ALL && Currencies.current().hasItems()) {
            Buttons.say(player, Replies.deposit(WalletActions.depositAll(player)));
            menu.show(this);
        } else if (slot == AMOUNT && Currencies.current().hasItems()) {
            askAmount(menu, player);
        }
    }

    private void askAmount(MarketChestMenu menu, ServerPlayer player) {
        menu.lock();
        TextEntryMenu.ask(player, menu, Text.translatable("markettrade.enter_withdraw"), "",
                Text.translatable("markettrade.text_withdraw_hint"), new TextEntryMenu.Handler() {
                    @Override
                    public Runnable submit(ServerPlayer p, String text) {
                        AmountParser.Result result = AmountParser.parse(text, Currencies.current());
                        if (!result.ok()) {
                            p.sendSystemMessage(Replies.amountProblem(result.status()).copy().withStyle(ChatFormatting.RED));
                            return null;
                        }
                        if (result.amount() < 1) {
                            p.sendSystemMessage(Text.translatable("markettrade.amount_empty").withStyle(ChatFormatting.RED));
                            return null;
                        }
                        Buttons.say(p, Replies.withdraw(WalletActions.withdraw(p, result.amount())));
                        return () -> MarketChestMenu.open(p, state, new WalletView(state));
                    }

                    @Override
                    public Runnable cancel(ServerPlayer p) {
                        return () -> MarketChestMenu.open(p, state, new WalletView(state));
                    }
                });
    }

    private static Item itemOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }
}
