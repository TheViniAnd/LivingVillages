package ru.livingvillages.persistence;

import com.google.gson.*;
import java.util.*;
import ru.livingvillages.domain.Edition;
import ru.livingvillages.i18n.Messages;

/** Inspect unsupported saved data BEFORE Gson can discard fields unknown to the Free model. */
public final class FreeSaveGuard {
    private FreeSaveGuard(){}
    private static boolean populated(JsonElement e){return e!=null&&!e.isJsonNull()&&(!e.isJsonArray()||!e.getAsJsonArray().isEmpty())&&(!e.isJsonObject()||!e.getAsJsonObject().isEmpty());}
    private static void reject(){throw new IllegalArgumentException(Messages.text("Это сохранения Premium. Бесплатная версия их не изменяет. Используйте Premium или отдельную чистую папку LivingVillages."));}
    public static void check(String json){
        var data=JsonParser.parseString(json).getAsJsonObject();
        for(String key:List.of("leader","overlord","siege","assistants","diplomacy","animals","rareWorks"))if(populated(data.get(key)))reject();
        if(data.has("culture")&&!data.get("culture").isJsonNull()&&!Edition.FREE.culture(data.get("culture").getAsString()))reject();
        if(data.has("level")&&data.get("level").getAsInt()>3||data.has("radius")&&data.get("radius").getAsInt()>48)reject();
        for(String key:List.of("buildings","projects","residents"))if(data.has(key)&&data.get(key).isJsonArray())for(var row:data.getAsJsonArray(key)){
            var object=row.getAsJsonObject();if(populated(object.get("owner"))||populated(object.get("replacement")))reject();
            for(String field:List.of("laborFee","laborPaid","laborWorked"))if(object.has(field)&&!object.get(field).isJsonNull()&&object.get(field).getAsLong()!=0)reject();
            if(key.equals("buildings")&&(object.has("level")&&object.get("level").getAsInt()>1||object.has("kind")&&!Edition.FREE.building(object.get("kind").getAsString())))reject();
            if(key.equals("residents"))for(String field:List.of("militaryKind","campaignTarget"))if(populated(object.get(field)))reject();
        }
    }
}
