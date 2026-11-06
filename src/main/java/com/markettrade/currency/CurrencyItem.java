package com.markettrade.currency;

/** One physical item that backs the currency. The name is owner text and is shown as it is. */
public record CurrencyItem(String item, long value, String name) {
}
