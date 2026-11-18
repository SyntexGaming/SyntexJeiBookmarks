package dev.syntex.jeibookmarks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lesezeichen-Liste, gespeichert in {@code config/syntexjeibookmarks.json}. Jede Aenderung wird sofort
 * geschrieben. Eine kaputte Datei wird als {@code .bad} beiseitegelegt, damit sie nicht verloren geht.
 */
public final class BookmarkStore {

    /** @param label eigene Beschriftung aus der Datei, sonst {@code null} (dann Mod-Name bzw. Suchbegriff) */
    public record Bookmark(String query, String label) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Abstand zum Bildschirmrand in der Grundstellung (rechts oben). */
    public static final int DEFAULT_MARGIN = 2;

    private static final List<Bookmark> BOOKMARKS = new ArrayList<>();
    private static boolean collapsed;
    /** Panel sitzt am linken statt am rechten Rand. */
    private static boolean left;
    /** Abstand zum Rand der gewaehlten Seite (GUI-Pixel), so bleibt die Lage bei anderer Fenstergroesse sinnvoll. */
    private static int offsetX = DEFAULT_MARGIN;
    private static int offsetY = DEFAULT_MARGIN;
    /** Zaehlt jede Aenderung an Liste/Lage/Einklappen: das Panel merkt sich sein Layout, solange sich nichts aendert. */
    private static int version;

    private BookmarkStore() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("syntexjeibookmarks.json");
    }

    public static int version() {
        return version;
    }

    public static List<Bookmark> bookmarks() {
        return Collections.unmodifiableList(BOOKMARKS);
    }

    public static boolean isCollapsed() {
        return collapsed;
    }

    public static void setCollapsed(boolean value) {
        version++;
        collapsed = value;
        save();
    }

    public static boolean isLeft() {
        return left;
    }

    public static int offsetX() {
        return offsetX;
    }

    public static int offsetY() {
        return offsetY;
    }

    /** Grundstellung: rechts oben, wie vor dem Verschieben. */
    public static boolean isDefaultPosition() {
        return !left && offsetX == DEFAULT_MARGIN && offsetY == DEFAULT_MARGIN;
    }

    /** Setzt die Lage nur im Speicher (waehrend des Ziehens), geschrieben wird erst mit {@link #savePosition()}. */
    public static void setPosition(boolean toLeft, int x, int y) {
        version++;
        left = toLeft;
        offsetX = Math.max(0, x);
        offsetY = Math.max(0, y);
    }

    public static void savePosition() {
        save();
    }

    public static void resetPosition() {
        setPosition(false, DEFAULT_MARGIN, DEFAULT_MARGIN);
        save();
    }

    public static boolean contains(String query) {
        return BOOKMARKS.stream().anyMatch(b -> b.query().equalsIgnoreCase(query.trim()));
    }

    /** @return false, wenn der Begriff leer oder schon gespeichert ist */
    public static boolean add(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty() || contains(trimmed)) {
            return false;
        }
        BOOKMARKS.add(new Bookmark(trimmed, null));
        version++;
        save();
        return true;
    }

    public static void remove(Bookmark bookmark) {
        if (BOOKMARKS.remove(bookmark)) {
            version++;
            save();
        }
    }

    static void load() {
        version++;
        BOOKMARKS.clear();
        collapsed = false;
        setPosition(false, DEFAULT_MARGIN, DEFAULT_MARGIN);
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            collapsed = root.has("collapsed") && root.get("collapsed").getAsBoolean();
            // Aeltere Dateien (1.0.0) kennen die Lage nicht: dann bleibt es bei rechts oben.
            setPosition(root.has("side") && "left".equals(root.get("side").getAsString()),
                    root.has("offsetX") ? root.get("offsetX").getAsInt() : DEFAULT_MARGIN,
                    root.has("offsetY") ? root.get("offsetY").getAsInt() : DEFAULT_MARGIN);
            for (JsonElement element : root.getAsJsonArray("bookmarks")) {
                JsonObject entry = element.getAsJsonObject();
                String query = entry.get("query").getAsString().trim();
                String label = entry.has("label") ? entry.get("label").getAsString() : null;
                if (!query.isEmpty() && !contains(query)) {
                    BOOKMARKS.add(new Bookmark(query, label));
                }
            }
        } catch (IOException | RuntimeException e) {
            SyntexJeiBookmarks.LOGGER.error("[SyntexJeiBookmarks] {} ist kaputt, wird als .bad beiseitegelegt", file, e);
            BOOKMARKS.clear();
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                SyntexJeiBookmarks.LOGGER.error("[SyntexJeiBookmarks] konnte {} nicht verschieben", file, moveError);
            }
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("collapsed", collapsed);
        root.addProperty("side", left ? "left" : "right");
        root.addProperty("offsetX", offsetX);
        root.addProperty("offsetY", offsetY);
        JsonArray list = new JsonArray();
        for (Bookmark bookmark : BOOKMARKS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("query", bookmark.query());
            if (bookmark.label() != null) {
                entry.addProperty("label", bookmark.label());
            }
            list.add(entry);
        }
        root.add("bookmarks", list);

        Path file = file();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SyntexJeiBookmarks.LOGGER.error("[SyntexJeiBookmarks] konnte {} nicht speichern", file, e);
        }
    }
}
