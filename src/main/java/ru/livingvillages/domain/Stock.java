package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

public final class Stock {
    public Map<String, Integer> items = new TreeMap<>();
    public int get(String item) { return items.getOrDefault(item, 0); }
    public int size() { return items.values().stream().reduce(0, Math::addExact); }
    public void add(String item, int amount, int capacity) {
        if (amount < 0 || Math.addExact(size(), amount) > capacity) throw new IllegalArgumentException(Messages.text("Склад заполнен"));
        items.put(item, Math.addExact(get(item), amount));
    }
    public void take(String item, int amount) {
        if (amount < 0 || get(item) < amount) throw new IllegalArgumentException(Messages.text("Недостаточно: ") + item);
        int left = get(item) - amount;
        if (left == 0) items.remove(item); else items.put(item, left);
    }
    public Map<String,Integer> reserve(Map<String,Integer> cost) {
        cost.forEach((k,v) -> { if(v < 0 || get(k) < v) throw new IllegalArgumentException(Messages.text("Нужно: ") + k); });
        cost.forEach(this::take);
        return new TreeMap<>(cost);
    }
}
