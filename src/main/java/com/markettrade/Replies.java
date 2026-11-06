package com.markettrade;

import java.util.List;

import com.markettrade.currency.Currencies;
import com.markettrade.market.AmountParser;
import com.markettrade.market.MarketActions;
import com.markettrade.market.WalletActions;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Turns the result of an action into chat lines. Commands and chest screens share it, so both say the same thing. */
public final class Replies {
    /** {@code ok} only picks the colour when it is shown. Every line is sent. */
    public record Reply(boolean ok, List<Component> lines) {
        static Reply ok(Component... lines) {
            return new Reply(true, List.of(lines));
        }

        static Reply fail(Component line) {
            return new Reply(false, List.of(line));
        }
    }

    private Replies() {
    }

    private static String show(long amount) {
        return Currencies.current().format(amount);
    }

    public static Reply sell(MarketActions.SellResult result, ItemStack listed, long price) {
        return switch (result.status()) {
            case OK -> Reply.ok(Text.translatable("markettrade.sell_ok", listed.getHoverName(), listed.getCount(),
                    show(price), result.id()));
            case EMPTY_HAND -> Reply.fail(Text.translatable("markettrade.sell_empty"));
            case CHANGED -> Reply.fail(Text.translatable("markettrade.sell_changed"));
            case BAD_PRICE -> Reply.fail(Text.translatable("markettrade.sell_bad_price"));
            case LIMIT -> Reply.fail(Text.translatable("markettrade.sell_limit", result.limit()));
            case BLOCKED_TEXT -> Reply.fail(Text.translatable("markettrade.sell_blocked"));
            case BANNED -> Reply.fail(Text.translatable("markettrade.banned"));
        };
    }

    public static Reply buy(MarketActions.BuyResult result) {
        return switch (result.status()) {
            case OK -> result.toReturns() > 0
                    ? Reply.ok(Text.translatable("markettrade.buy_ok", result.item().getHoverName(),
                            result.item().getCount(), show(result.price())),
                            Text.translatable("markettrade.buy_to_returns", result.toReturns()))
                    : Reply.ok(Text.translatable("markettrade.buy_ok", result.item().getHoverName(),
                            result.item().getCount(), show(result.price())));
            case NOT_FOUND -> Reply.fail(Text.translatable("markettrade.buy_not_found"));
            case UNAVAILABLE -> Reply.fail(Text.translatable("markettrade.buy_unavailable"));
            case EXPIRED -> Reply.fail(Text.translatable("markettrade.buy_expired"));
            case PRICE_CHANGED -> Reply.fail(Text.translatable("markettrade.buy_price_changed", show(result.price())));
            case OWN_LISTING -> Reply.fail(Text.translatable("markettrade.buy_own"));
            case CANNOT_PAY -> Reply.fail(Text.translatable("markettrade.buy_cannot_pay", show(result.price())));
            case OVERFLOW -> Reply.fail(Text.translatable("markettrade.buy_overflow"));
            case BANNED -> Reply.fail(Text.translatable("markettrade.banned"));
        };
    }

    public static Reply cancel(MarketActions.CancelResult result, long id) {
        return switch (result.status()) {
            case OK -> Reply.ok(Text.translatable("markettrade.cancel_ok", id));
            case NOT_FOUND -> Reply.fail(Text.translatable("markettrade.cancel_not_found"));
            case NOT_YOURS -> Reply.fail(Text.translatable("markettrade.cancel_not_yours"));
        };
    }

    public static Reply report(MarketActions.ReportResult result) {
        return switch (result.status()) {
            case OK -> Reply.ok(Text.translatable("markettrade.report_ok", result.id()));
            case BANNED -> Reply.fail(Text.translatable("markettrade.banned"));
            case NOT_FOUND -> Reply.fail(Text.translatable("markettrade.report_not_found"));
            case OWN_LISTING -> Reply.fail(Text.translatable("markettrade.report_own"));
            case ALREADY_REPORTED -> Reply.fail(Text.translatable("markettrade.report_again"));
            case COOLDOWN -> Reply.fail(Text.translatable("markettrade.report_cooldown", result.waitSeconds()));
        };
    }

    public static Reply deposit(WalletActions.DepositResult result) {
        return switch (result.status()) {
            case OK -> result.skippedCustom()
                    ? Reply.ok(Text.translatable("markettrade.deposit_ok", show(result.deposited()), show(result.balance())),
                            Text.translatable("markettrade.deposit_custom_kept"))
                    : Reply.ok(Text.translatable("markettrade.deposit_ok", show(result.deposited()), show(result.balance())));
            case UNAVAILABLE -> Reply.fail(Text.translatable("markettrade.currency_unavailable"));
            case NOT_CURRENCY -> Reply.fail(Text.translatable("markettrade.deposit_not_currency"));
            case EMPTY -> Reply.fail(Text.translatable("markettrade.deposit_nothing"));
            case ONLY_CUSTOM -> Reply.fail(Text.translatable("markettrade.deposit_only_custom"));
            case OVERFLOW -> Reply.fail(Text.translatable("markettrade.deposit_overflow"));
        };
    }

    public static Reply withdraw(WalletActions.WithdrawResult result) {
        return switch (result.status()) {
            case OK -> Reply.ok(Text.translatable("markettrade.withdraw_ok", show(result.given()), show(result.balance())));
            case PARTIAL -> Reply.ok(Text.translatable("markettrade.withdraw_partial", show(result.given()),
                    show(result.requested() - result.given()), show(result.balance())));
            case UNAVAILABLE -> Reply.fail(Text.translatable("markettrade.currency_unavailable"));
            case INSUFFICIENT -> Reply.fail(Text.translatable("markettrade.withdraw_insufficient", show(result.balance())));
            case NOTHING_COVERED -> Reply.fail(Text.translatable("markettrade.withdraw_nothing_covered"));
        };
    }

    public static Reply claimAll(MarketActions.ClaimResult result) {
        if (result.stacksLeft() > 0) {
            return Reply.ok(Text.translatable("markettrade.returns_claimed", result.claimed()),
                    Text.translatable("markettrade.returns_left", result.stacksLeft()));
        }
        return Reply.ok(Text.translatable("markettrade.returns_claimed", result.claimed()));
    }

    public static Reply claimOne(MarketActions.ClaimOneResult result) {
        return switch (result.status()) {
            case OK -> Reply.ok(Text.translatable("markettrade.returns_claim_one", result.given(),
                    result.item().getHoverName()));
            case PARTIAL -> Reply.ok(Text.translatable("markettrade.returns_claim_partial", result.given(),
                    result.item().getHoverName(), result.left()));
            case NO_ROOM -> Reply.fail(Text.translatable("markettrade.returns_claim_no_room"));
            case GONE -> Reply.fail(Text.translatable("markettrade.returns_claim_gone"));
            case UNAVAILABLE -> Reply.fail(Text.translatable("markettrade.returns_claim_unavailable"));
        };
    }

    /** What to say about typed text that couldn't be read. */
    public static Component amountProblem(AmountParser.Status status) {
        return switch (status) {
            case EMPTY -> Text.translatable("markettrade.amount_empty");
            case TOO_BIG -> Text.translatable("markettrade.amount_too_big");
            default -> Text.translatable("markettrade.amount_bad");
        };
    }
}
