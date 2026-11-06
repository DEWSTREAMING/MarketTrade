package com.markettrade.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.markettrade.currency.CurrencyConfig;
import com.markettrade.currency.CurrencyItem;

/**
 * Reads typed amounts: a whole number in the smallest unit, or the displayed form with the config's names, like
 * {@code 3 gold 2 silver}. No Minecraft classes.
 */
public final class AmountParser {
    public enum Status {
        OK, EMPTY, BAD, TOO_BIG
    }

    public record Result(Status status, long amount) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private AmountParser() {
    }

    public static Result parse(String text, CurrencyConfig currency) {
        if (text == null || text.isBlank()) {
            return new Result(Status.EMPTY, 0);
        }
        List<String> tokens = tokens(text.trim().toLowerCase(Locale.ROOT));
        if (tokens.size() == 1) {
            return plain(tokens.get(0));
        }

        long total = 0;
        int i = 0;
        while (i < tokens.size()) {
            String number = tokens.get(i);
            if (!isDigits(number)) {
                return new Result(Status.BAD, 0);
            }
            i++;
            StringBuilder name = new StringBuilder();
            while (i < tokens.size() && !isDigits(tokens.get(i))) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(tokens.get(i));
                i++;
            }
            Long value = unitValue(name.toString(), currency);
            if (value == null) {
                return new Result(Status.BAD, 0);
            }
            try {
                long count = Long.parseLong(number);
                total = Math.addExact(total, Math.multiplyExact(count, value));
            } catch (ArithmeticException | NumberFormatException e) {
                return new Result(Status.TOO_BIG, 0);
            }
        }
        return new Result(Status.OK, total);
    }

    private static Result plain(String token) {
        if (!isDigits(token)) {
            return new Result(Status.BAD, 0);
        }
        try {
            return new Result(Status.OK, Long.parseLong(token));
        } catch (NumberFormatException e) {
            return new Result(Status.TOO_BIG, 0);
        }
    }

    private static Long unitValue(String name, CurrencyConfig currency) {
        if (name.isEmpty()) {
            return null;
        }
        for (CurrencyItem item : currency.items()) {
            if (item.name().toLowerCase(Locale.ROOT).equals(name)) {
                return item.value();
            }
        }
        if (currency.name().toLowerCase(Locale.ROOT).equals(name)) {
            return 1L;
        }
        return null;
    }

    /** Splits on spaces and between a digit and a letter, so {@code 3gold} reads as {@code 3 gold}. */
    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                flush(out, current);
                continue;
            }
            if (current.length() > 0) {
                boolean wasDigit = isDigit(current.charAt(current.length() - 1));
                if (wasDigit != isDigit(c)) {
                    flush(out, current);
                }
            }
            current.append(c);
        }
        flush(out, current);
        return out;
    }

    private static void flush(List<String> out, StringBuilder current) {
        if (current.length() > 0) {
            out.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isDigits(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
