package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.util.*;

/** Logical half of the same PREPARED/inventory/COMMITTED protocol as ordinary trading. */
public final class CommunalStore {
    private CommunalStore(){}
    private static void amount(long n){if(n<=0)throw new IllegalArgumentException(Messages.text("Укажите положительное количество"));}
    
    
    public static void gift(Village v,UUID actor,String item,int count,Rules rules){TrustAccess.requireFriend(v,actor,rules);amount(count);v.stock.add(item,count,v.capacity);}
    
    
}
