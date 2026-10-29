package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

public final class Money {
    public static final long MAX = 1_000_000_000_000L;
    public static final int[] DENOMINATIONS = {4096, 64, 1};
    private Money() { }
    public static long checked(long value) {
        if(value < 0 || value > MAX) throw new IllegalArgumentException(Messages.text("Сумма вне допустимого диапазона"));
        return value;
    }
    public static long add(long a, long b) { return checked(Math.addExact(checked(a),checked(b))); }
    public static long cost(long price, int count) {
        if(count <= 0 || count > 4096) throw new IllegalArgumentException(Messages.text("Неверный размер партии"));
        return checked(Math.multiplyExact(checked(price), count));
    }
    public static Map<Integer,Integer> split(long value) {
        checked(value);
        Map<Integer,Integer> result = new LinkedHashMap<>();
        for(int d: DENOMINATIONS) { result.put(d, Math.toIntExact(value/d)); value %= d; }
        return result;
    }
    public static String describe(long value) {
        var split = split(value);
        return split.get(4096)+Messages.text(" з. ")+split.get(64)+Messages.text(" с. ")+split.get(1)+Messages.text(" б.");
    }
}
