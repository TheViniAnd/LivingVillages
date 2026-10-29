package ru.livingvillages.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-wide presentation language. Message sources, identifiers and player text stay distinct. */
public final class Messages {
    private Messages(){}
    private static final Map<String,String> EN=load("en"), RU=load("ru");
    private static final Map<String,String> REVERSE=reverse(EN);
    private static final Trie TO_EN=new Trie(EN), TO_RU=new Trie(REVERSE);
    private static volatile String selected="en";
    private static final ThreadLocal<String> PREVIEW=new ThreadLocal<>();
    private static Map<String,String> load(String language){
        try(var in=Messages.class.getResourceAsStream("/lang/"+language+".json")){
            if(in==null)throw new IllegalStateException("Missing language catalogue: "+language);
            Map<String,String> result=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),new TypeToken<LinkedHashMap<String,String>>(){}.getType());
            if(result==null||result.isEmpty()||result.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue().isEmpty()))throw new IllegalStateException("Invalid language catalogue: "+language);
            return Collections.unmodifiableMap(result);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static Map<String,String> reverse(Map<String,String> source){var out=new LinkedHashMap<String,String>();source.forEach((ru,en)->out.putIfAbsent(en,ru));return Collections.unmodifiableMap(out);}
    public static String validate(String value){
        String language=Objects.toString(value,"").strip().toLowerCase(Locale.ROOT);
        if(!Set.of("en","ru").contains(language))throw new IllegalArgumentException("language: expected en or ru / требуется en или ru");
        return language;
    }
    public static void use(String language){selected=validate(language);}
    public static String language(){String local=PREVIEW.get();return local==null?selected:local;}
    /** Validate a prospective configuration without changing the running server's language. */
    public static Scope preview(String language){String before=PREVIEW.get();PREVIEW.set(validate(language));return ()->{if(before==null)PREVIEW.remove();else PREVIEW.set(before);};}
    @FunctionalInterface public interface Scope extends AutoCloseable {@Override void close();}
    /** Exact catalogue lookup. Unknown administrator-authored content is deliberately preserved. */
    public static String text(String source){
        if(source==null)return null;
        if(language().equals("en"))return EN.getOrDefault(source,source);
        return RU.getOrDefault(source,REVERSE.getOrDefault(source,source));
    }
    /** Only for plugin-owned saved reasons/labels; never for player names, chat or inventory hashes. */
    public static String saved(String source){if(source==null)return null;String direct=text(source);if(!direct.equals(source))return direct;return (language().equals("en")?TO_EN:TO_RU).apply(source);}
    /** Legacy project kinds predate language support. Their semantic prefixes are language independent. */
    public static boolean startsWith(String value,String source){return value!=null&&(value.startsWith(source)||value.startsWith(EN.getOrDefault(source,source)));}
    public static Set<String> sources(){return EN.keySet();}
    public static String translation(String source,String language){try(var scope=preview(language)){return text(source);}}
    private static boolean word(char c){return Character.isLetterOrDigit(c)||c=='_';}
    private static final class Node {final Map<Character,Node> next=new HashMap<>();String source,target;}
    private static final class Trie {
        private final Node root=new Node();
        Trie(Map<String,String> values){values.forEach((source,target)->{if(source.equals(target))return;Node n=root;for(char c:source.toCharArray())n=n.next.computeIfAbsent(c,k->new Node());n.source=source;n.target=target;});}
        String apply(String input){
            StringBuilder out=new StringBuilder();
            for(int i=0;i<input.length();){Node n=root,best=null;int end=i;
                for(int j=i;j<input.length()&&(n=n.next.get(input.charAt(j)))!=null;j++)if(n.target!=null&&(!word(n.source.charAt(0))||i==0||!word(input.charAt(i-1)))&&(!word(n.source.charAt(n.source.length()-1))||j+1==input.length()||!word(input.charAt(j+1)))){best=n;end=j+1;}
                if(best==null)out.append(input.charAt(i++));else{out.append(best.target);i=end;}
            }return out.toString();
        }
    }
}
