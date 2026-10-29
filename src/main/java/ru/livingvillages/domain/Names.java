package ru.livingvillages.domain;

import ru.livingvillages.i18n.Messages;

import java.text.Normalizer;
import java.util.*;

/** Shared main-thread naming registry. Saved strings and UUIDs remain the source of truth. */
public final class Names {
    private Names(){}
    public static String key(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    public static String checked(String text){
        if(text==null)throw new IllegalArgumentException(Messages.text("Введите название"));
        String value=Normalizer.normalize(text.strip(),Normalizer.Form.NFC);
        int size=value.codePointCount(0,value.length());
        if(size<3||size>32||!value.matches("[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} '\u2019\\-]{1,30}[\\p{L}\\p{M}\\p{N}]"))throw new IllegalArgumentException(Messages.text("Название: 3–32 символа, буквы, цифры, пробел, дефис или апостроф; без команд и оформления."));
        if(value.contains("  "))throw new IllegalArgumentException(Messages.text("В названии не должно быть двойных пробелов"));return value;
    }
    
    public static String village(Village v,Rules rules){
        var pool=rules.villageNames.isEmpty()?List.of("Alderwick"):rules.villageNames;int start=Math.floorMod(v.id.hashCode(),pool.size());
        for(int cycle=0;;cycle++)for(int i=0;i<pool.size();i++){String name=pool.get((start+i)%pool.size())+(cycle==0?"":" "+roman(cycle+1));if(!rules.villageNameOwners.containsKey(key(name))){rules.villageNameOwners.put(key(name),v.id);return name;}}
    }
    public static String resident(Village v,Rules rules,UUID id){
        var first=rules.residentNames.isEmpty()?List.of("Adrian","Elise","Felix","Clara","Hugo"):rules.residentNames;var last=rules.surnames.isEmpty()?List.of("Ashford","Beaumont"):rules.surnames;int total=Math.multiplyExact(first.size(),last.size()),start=Math.floorMod(id.hashCode(),total);
        for(int cycle=0;;cycle++)for(int i=0;i<total;i++){int n=(start+i)%total;String name=first.get(n%first.size())+" "+last.get(n/first.size())+(cycle==0?"":" "+roman(cycle+1));String key=key(name);if(!rules.residentNameOwners.containsKey(key)&&v.residents.stream().noneMatch(person->person.name!=null&&key(person.name).equals(key))){rules.residentNameOwners.put(key,id);return name;}}
    }
    public static boolean generatedLegacy(Village v){return v.name!=null&&v.name.endsWith(" · "+v.id.toString().substring(0,6));}
    private static boolean legacy(String name,Rules r){if(name==null||name.isBlank())return true;return r.legacyResidentNames.stream().anyMatch(old->name.equals(old)||name.matches(java.util.regex.Pattern.quote(old)+" [0-9]+"));}
    public static void assign(Village v,Rules rules){
        if(v.name==null||v.name.isBlank()||generatedLegacy(v))v.name=village(v,rules);else rules.villageNameOwners.put(key(v.name),v.id);
        for(var person:v.residents){UUID owner=person.name==null?null:rules.residentNameOwners.get(key(person.name));if(legacy(person.name,rules)||owner!=null&&!owner.equals(person.id))person.name=resident(v,rules,person.id);rules.residentNameOwners.put(key(person.name),person.id);}
    }
    public static void seed(Collection<Village> all,Rules rules){for(var v:all){if(v.name!=null&&!generatedLegacy(v))rules.villageNameOwners.put(key(v.name),v.id);for(var person:v.residents)if(!legacy(person.name,rules))rules.residentNameOwners.putIfAbsent(key(person.name),person.id);}}
    private static String roman(int n){StringBuilder s=new StringBuilder();int[] numbers={1000,900,500,400,100,90,50,40,10,9,5,4,1};String[] letters={"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};for(int i=0;i<numbers.length;i++)while(n>=numbers[i]){s.append(letters[i]);n-=numbers[i];}return s.toString();}
}
