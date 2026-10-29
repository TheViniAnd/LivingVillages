package ru.livingvillages.paper;

import java.util.*;
import ru.livingvillages.i18n.Messages;

final class Russian {
    private Russian(){}
    private static final Map<String,String> ITEMS=Map.ofEntries(
        Map.entry("NETHERITE_INGOT","Незеритовый слиток"),Map.entry("NETHERITE_SWORD","Незеритовый меч"),Map.entry("DIAMOND_SWORD","Алмазный меч"),Map.entry("DIAMOND","Алмаз"),Map.entry("GOLD_INGOT","Золотой слиток"),Map.entry("TRIDENT","Трезубец"),Map.entry("HEART_OF_THE_SEA","Сердце моря"),Map.entry("PRISMARINE_SHARD","Осколок призмарина"),Map.entry("BLAZE_ROD","Стержень ифрита"),Map.entry("SLIME_BALL","Сгусток слизи"),Map.entry("OBSIDIAN","Обсидиан"),
        Map.entry("BAKED_POTATO","Печёный картофель"),Map.entry("BEETROOT","Свёкла"),Map.entry("BEETROOT_SEEDS","Семена свёклы"),Map.entry("CHICKEN","Курятина"),Map.entry("COOKED_COD","Жареная треска"),Map.entry("POTATO","Картофель"),Map.entry("RABBIT","Крольчатина"),Map.entry("RABBIT_HIDE","Кроличья шкурка"),Map.entry("COBBLESTONE_SLAB","Плита из булыжника"),Map.entry("COBBLESTONE_STAIRS","Ступени из булыжника"),Map.entry("COBBLESTONE_WALL","Ограда из булыжника"),Map.entry("CUT_SANDSTONE_SLAB","Плита из резного песчаника"),Map.entry("DEEPSLATE_BRICK_SLAB","Плита из глубинносланцевого кирпича"),Map.entry("DEEPSLATE_BRICK_STAIRS","Ступени из глубинносланцевого кирпича"),Map.entry("SANDSTONE_STAIRS","Ступени из песчаника"),Map.entry("SANDSTONE_WALL","Ограда из песчаника"),Map.entry("STONE_BRICK_STAIRS","Ступени из каменного кирпича"),Map.entry("STONE_SHOVEL","Каменная лопата"),Map.entry("STONE_SLAB","Каменная плита"),
        Map.entry("REDSTONE","Красная пыль"),Map.entry("PAPER","Бумага"),
        Map.entry("WHITE_BED","Белая кровать"),Map.entry("IRON_SWORD","Железный меч"),Map.entry("BOW","Лук"),Map.entry("ARROW","Стрела"),Map.entry("FLINT","Кремень"),Map.entry("FEATHER","Перо"),Map.entry("STRING","Нить"),Map.entry("CHISELED_STONE_BRICKS","Резные каменные кирпичи"),Map.entry("TARGET","Мишень"),Map.entry("FLETCHING_TABLE","Стол лучника"),Map.entry("IRON_BARS","Железная решётка"),
        Map.entry("CACTUS","Кактус"),Map.entry("BEEF","Говядина"),Map.entry("PORKCHOP","Свинина"),Map.entry("COOKED_BEEF","Стейк"),Map.entry("HONEY_BOTTLE","Бутылочка мёда"),Map.entry("HONEYCOMB","Пчелиные соты"),Map.entry("GLASS_BOTTLE","Бутылочка"),Map.entry("SHEARS","Ножницы"),Map.entry("DANDELION","Одуванчик"),Map.entry("WHEAT","Пшеница"),Map.entry("WHEAT_SEEDS","Семена пшеницы"),Map.entry("RAW_IRON","Рудное железо"),Map.entry("STONE_AXE","Каменный топор"),Map.entry("STONE_PICKAXE","Каменная кирка"),Map.entry("FISHING_ROD","Удочка"),Map.entry("SMITHING_TABLE","Стол кузнеца"),Map.entry("COBBLESTONE","Булыжник"),Map.entry("MOSSY_COBBLESTONE","Замшелый булыжник"),
        Map.entry("BREAD","Хлеб"),Map.entry("CARROT","Морковь"),Map.entry("COD","Сырая треска"),Map.entry("COAL","Уголь"),Map.entry("CHARCOAL","Древесный уголь"),
        Map.entry("WHITE_WOOL","Белая шерсть"),Map.entry("COPPER_INGOT","Медный слиток"),Map.entry("IRON_INGOT","Железный слиток"),Map.entry("SAND","Песок"),Map.entry("GLASS","Стекло"),Map.entry("GLASS_PANE","Стеклянная панель"),
        Map.entry("CLAY_BALL","Глина"),Map.entry("BRICK","Кирпич"),Map.entry("SUGAR_CANE","Тростник"),Map.entry("SWEET_BERRIES","Сладкие ягоды"),Map.entry("LEATHER","Кожа"),Map.entry("STONE_HOE","Каменная мотыга"),
        Map.entry("KELP","Ламинария"),Map.entry("MANGROVE_PROPAGULE","Мангровый отросток"),Map.entry("DIRT","Земля"),Map.entry("WATER_BUCKET","Ведро воды"),Map.entry("BLUE_BED","Синяя кровать"),Map.entry("LANTERN","Фонарь"),
        Map.entry("BARREL","Бочка"),Map.entry("CRAFTING_TABLE","Верстак"),Map.entry("HAY_BLOCK","Сноп сена"),Map.entry("COMPOSTER","Компостер"),Map.entry("STONECUTTER","Камнерез"),Map.entry("FURNACE","Печь"),Map.entry("GRAVEL","Гравий"),
        Map.entry("STONE_BRICKS","Каменные кирпичи"),Map.entry("DEEPSLATE_BRICKS","Глубинносланцевые кирпичи"),Map.entry("MUD_BRICKS","Саманные кирпичи"),Map.entry("SMOOTH_SANDSTONE","Гладкий песчаник"),Map.entry("CUT_SANDSTONE","Резной песчаник"),Map.entry("SANDSTONE_SLAB","Песчаниковая плита"),Map.entry("STONE_BRICK_SLAB","Плита из каменных кирпичей")
    );
    private static String russianItem(String id){
        if(id.equals("STICK"))return "Палки";if(id.equals("STONE"))return "Камень";if(id.equals("GRINDSTONE"))return "Точило";if(id.equals("BUCKET"))return "Ведро";if(id.equals("IRON_NUGGET"))return "Железный самородок";if(id.equals("TORCH"))return "Факел";if(id.equals("CHEST"))return "Сундук";if(id.equals("SANDSTONE"))return "Песчаник";
        if(id.startsWith("WOODEN_") || id.startsWith("IRON_")){String tool=switch(id.substring(id.indexOf('_')+1)){case "HOE"->"мотыга";case "AXE"->"топор";case "PICKAXE"->"кирка";case "SHOVEL"->"лопата";default->null;};if(tool!=null)return (id.startsWith("WOODEN_")?"Деревянный инструмент: ":"Железный инструмент: ")+tool;}
        String direct=ITEMS.get(id);if(direct!=null)return direct;
        if(id.endsWith("_WOOL")){String colour=switch(id.substring(0,id.length()-5)){case "ORANGE"->"Оранжевая";case "MAGENTA"->"Пурпурная";case "LIGHT_BLUE"->"Голубая";case "YELLOW"->"Жёлтая";case "LIME"->"Лаймовая";case "PINK"->"Розовая";case "GRAY"->"Серая";case "LIGHT_GRAY"->"Светло-серая";case "CYAN"->"Бирюзовая";case "PURPLE"->"Фиолетовая";case "BLUE"->"Синяя";case "BROWN"->"Коричневая";case "GREEN"->"Зелёная";case "RED"->"Красная";case "BLACK"->"Чёрная";default->"Белая";};return colour+" шерсть";}
        Map<String,String> woods=new LinkedHashMap<>();woods.put("PALE_OAK","Бледный дуб");woods.put("CHERRY","Вишня");woods.put("JUNGLE","Тропическая древесина");woods.put("DARK_OAK","Тёмный дуб");woods.put("MANGROVE","Мангр");woods.put("SPRUCE","Ель");woods.put("ACACIA","Акация");woods.put("BIRCH","Берёза");woods.put("OAK","Дуб");
        boolean stripped=id.startsWith("STRIPPED_");String name=stripped?id.substring(9):id;
        for(var e:woods.entrySet())if(name.startsWith(e.getKey()+"_")){String suffix=name.substring(e.getKey().length()+1);String type=switch(suffix){case "LOG"->"бревно";case "WOOD"->"древесина";case "PLANKS"->"доски";case "STAIRS"->"ступени";case "SLAB"->"плита";case "FENCE"->"забор";case "FENCE_GATE"->"калитка";case "SIGN","WALL_SIGN"->"табличка";case "DOOR"->"дверь";case "SAPLING"->"саженец";case "BOAT"->"лодка";case "TRAPDOOR"->"люк";default->"материал";};return e.getValue()+" — "+(stripped?"обтёсанная ":"")+type;}
        return "Материал «"+id+"»";
    }
    static String item(String id){
        if(Messages.language().equals("ru"))return russianItem(id);
        if(ITEMS.containsKey(id))return Messages.text(ITEMS.get(id));
        // Vanilla material ids remain the authoritative identifiers, including future materials.
        return Arrays.stream(id.toLowerCase(Locale.ROOT).split("_")).map(word->word.isEmpty()?word:Character.toUpperCase(word.charAt(0))+word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
    }
    static String animal(String kind){return Messages.text(switch(kind){case "COW"->"Корова";case "PIG"->"Свинья";case "SHEEP"->"Овца";case "BEE"->"Пчела";default->kind;});}
    static String state(String state){return Messages.text(switch(state){case "SITE_BLOCKED"->"Участок заблокирован";case "SURVEY"->"Разведка месторождения";case "COLLECTING"->"Сбор материалов";case "QUEUED"->"В очереди";case "BUILDING"->"Строится";case "PAUSED"->"Приостановлено";case "COMPLETE"->"Готово";case "CANCELLED"->"Отменено";default->state;});}
}
