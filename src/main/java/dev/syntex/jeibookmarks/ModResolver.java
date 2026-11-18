package dev.syntex.jeibookmarks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Macht aus einem Suchbegriff Beschriftung und Icon: {@code @powah} wird zu "Powah!" mit dem ersten Item
 * des Mods. Ist das {@code @}-Wort nicht eindeutig einem Mod zuzuordnen, bleibt der Suchbegriff die
 * Beschriftung und es gibt kein Icon.
 */
public final class ModResolver {

    /** @param icon erstes Item des Mods, sonst {@link ItemStack#EMPTY} */
    public record Display(String label, ItemStack icon) {}

    private static final Map<String, Display> CACHE = new HashMap<>();
    private static Map<String, Item> firstItems;

    private ModResolver() {}

    public static Display display(BookmarkStore.Bookmark bookmark) {
        return CACHE.computeIfAbsent(bookmark.query(), q -> resolve(bookmark));
    }

    private static Display resolve(BookmarkStore.Bookmark bookmark) {
        String query = bookmark.query();
        String[] tokens = query.trim().split("\\s+");
        String modToken = null;
        for (String token : tokens) {
            if (token.length() > 1 && token.startsWith("@")) {
                modToken = token.substring(1);
                break;
            }
        }
        IModInfo mod = modToken == null ? null : findMod(modToken);
        String label = bookmark.label() != null ? bookmark.label()
                : mod != null && tokens.length == 1 ? mod.getDisplayName() : query;
        return new Display(label, mod == null ? ItemStack.EMPTY : firstItem(mod.getModId()));
    }

    private static IModInfo findMod(String token) {
        String needle = normalize(token);
        List<IModInfo> mods = ModList.get().getMods();
        for (IModInfo mod : mods) {
            if (mod.getModId().equalsIgnoreCase(token)) {
                return mod;
            }
        }
        for (IModInfo mod : mods) {
            if (normalize(mod.getDisplayName()).equals(needle)) {
                return mod;
            }
        }
        // Teilwort nur, wenn es eindeutig ist - sonst waere die Beschriftung geraten.
        List<IModInfo> partial = mods.stream()
                .filter(m -> m.getModId().toLowerCase(Locale.ROOT).contains(needle)
                        || normalize(m.getDisplayName()).contains(needle))
                .sorted(Comparator.comparing(IModInfo::getModId))
                .toList();
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static ItemStack firstItem(String modId) {
        if (firstItems == null) {
            firstItems = new HashMap<>();
            // Registrier-Reihenfolge: das zuerst angelegte Item ist meist das typische des Mods.
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    firstItems.putIfAbsent(BuiltInRegistries.ITEM.getKey(item).getNamespace(), item);
                }
            }
        }
        Item item = firstItems.get(modId);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }
}
