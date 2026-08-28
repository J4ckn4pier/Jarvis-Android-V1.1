package com.jarvis.brain;

import java.util.List;

/** Grounded menu-price answer: named line items, one explicit subtotal, explicit exclusions. */
public record MenuPriceQuote(List<LineItem> items, double subtotalPerPerson, List<String> exclusions) {
    public record LineItem(String name, double price) {
        public LineItem {
            name = name == null ? "" : name.trim();
            if (name.isBlank()) throw new IllegalArgumentException("item name required");
            if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("valid price required");
        }
    }
    public MenuPriceQuote {
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) throw new IllegalArgumentException("priced items required");
        if (!Double.isFinite(subtotalPerPerson) || subtotalPerPerson < 0) throw new IllegalArgumentException("valid subtotal required");
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
    public static MenuPriceQuote sum(List<LineItem> items, List<String> exclusions) {
        double total = 0; for (LineItem item : items) total += item.price();
        return new MenuPriceQuote(items, total, exclusions);
    }
    public String speak() {
        StringBuilder out = new StringBuilder("The menu lists ");
        for (int i=0;i<items.size();i++) {
            if (i>0) out.append(i==items.size()-1 ? " and " : ", ");
            LineItem item=items.get(i); out.append("the ").append(item.name()).append(" at $").append(format(item.price()));
        }
        out.append(". Overall, your final amount should be about $").append(format(subtotalPerPerson)).append(" per person");
        if (!exclusions.isEmpty()) out.append(", excluding ").append(String.join(" and ", exclusions));
        return out.append('.').toString();
    }
    private static String format(double value) { return value == Math.rint(value) ? Long.toString((long)value) : String.format(java.util.Locale.ROOT,"%.2f",value); }
}
