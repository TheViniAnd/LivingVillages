package ru.livingvillages.paper;

import ru.livingvillages.i18n.Messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

/** Conservative default-font widths, explicit newlines and code-point-safe long-word splitting. */
public final class TooltipText {
    public static final int WIDTH=220;
    private TooltipText(){}
    public static int width(String value){return value.codePoints().map(c->c==' '?4:"il.,:;!'|".indexOf(c)>=0?2:"tfr()[]{}".indexOf(c)>=0?4:c=='W'||c=='M'||c=='Ж'||c=='Ш'||c=='Щ'?7:c>=0x1f000?10:6).sum();}
    public static List<String> wrap(String value,int limit){
        if(limit<12)throw new IllegalArgumentException(Messages.text("Слишком узкий текст"));var out=new ArrayList<String>();
        for(String paragraph:Objects.toString(value,"").replace("\r","").split("\n",-1)){
            String text=paragraph.strip().replaceAll("\\s+"," ");if(text.isEmpty()){out.add("");continue;}String line="";
            for(String word:text.split(" ")){
                if(width(word)>limit){if(!line.isEmpty()){out.add(line);line="";}StringBuilder part=new StringBuilder();
                    for(int cp:word.codePoints().toArray()){String next=new String(Character.toChars(cp));if(width(part+next)>limit&&!part.isEmpty()){out.add(part.toString());part.setLength(0);}part.append(next);}line=part.toString();
                }else if(line.isEmpty())line=word;
                else if(width(line+" "+word)<=limit)line+=" "+word;
                else {out.add(line);line=word;}
            }if(!line.isEmpty())out.add(line);
        }return List.copyOf(out);
    }
    public static List<String> lines(List<String> source){
        var out=new ArrayList<String>();var seen=new HashSet<String>();
        for(String raw:source){String value=Objects.toString(raw,"").strip().replaceAll("[\\t ]+"," ");if(value.isEmpty()){if(!out.isEmpty()&&!out.getLast().isEmpty())out.add("");continue;}if(seen.add(value))out.addAll(wrap(value,WIDTH));}
        while(!out.isEmpty()&&out.getLast().isEmpty())out.removeLast();return List.copyOf(out);
    }
    public static Component title(String text){var chunks=wrap(text,180);return Component.text(chunks.size()>1?chunks.getFirst()+"…":chunks.getFirst());}
    public static void apply(org.bukkit.inventory.meta.ItemMeta meta,String name,NamedTextColor color,List<String> lore){
        var names=wrap(name,WIDTH);meta.itemName(Component.text(names.getFirst(),color));var all=new ArrayList<String>();if(names.size()>1){all.addAll(names.subList(1,names.size()));all.add("");}all.addAll(lore);meta.lore(lines(all).stream().map(t->Component.text(t,NamedTextColor.GRAY)).toList());
    }
}
