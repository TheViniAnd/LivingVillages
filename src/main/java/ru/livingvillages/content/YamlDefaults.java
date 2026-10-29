package ru.livingvillages.content;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.*;

/** Merge YAML trees before interpreting dots as Bukkit paths; blueprint symbols are literal keys. */
public final class YamlDefaults {
    private static final char LITERAL_SEPARATOR='\u001f';
    private YamlDefaults(){}
    public static YamlConfiguration load(Reader current,Reader bundled) throws IOException,InvalidConfigurationException {
        YamlConfiguration values=literal(current),defaults=literal(bundled);
        merge(values,defaults);
        // Changing the separator leaves literal stored keys intact; callers can still use normal dotted paths.
        values.options().pathSeparator('.');return values;
    }
    private static YamlConfiguration literal(Reader reader) throws IOException,InvalidConfigurationException {
        YamlConfiguration config=new YamlConfiguration();config.options().pathSeparator(LITERAL_SEPARATOR);config.load(reader);return config;
    }
    private static void merge(ConfigurationSection values,ConfigurationSection defaults){
        for(var entry:defaults.getValues(false).entrySet()){
            String key=entry.getKey();Object fallback=entry.getValue(),existing=values.get(key);
            if(fallback instanceof ConfigurationSection section){
                if(existing==null)merge(values.createSection(key),section);
                else if(existing instanceof ConfigurationSection target)merge(target,section);
            }else if(existing==null)values.set(key,fallback);
        }
    }
}
