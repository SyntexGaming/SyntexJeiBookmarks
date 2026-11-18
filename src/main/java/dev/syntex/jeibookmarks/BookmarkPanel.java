package dev.syntex.jeibookmarks;

import dev.syntex.jeibookmarks.BookmarkStore.Bookmark;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das Panel rechts oben: Kopfzeile mit Titel, "+" und Einklappen, darunter eine Zeile je Lesezeichen.
 * Alle Masse sind GUI-Koordinaten und richten sich nach dem Platz rechts vom Container, passen sich also
 * an GUI-Skalierung und Fenstergroesse an. Es belegt hoechstens die halbe Breite des Bereichs, damit fuer
 * die JEI-Liste immer Platz bleibt.
 */
public final class BookmarkPanel {

    private static final int MARGIN = BookmarkStore.DEFAULT_MARGIN;
    /** Naeher als das am Rand: das Panel rastet auf den normalen Randabstand ein. */
    private static final int SNAP = 6;
    /** Breite des Griffs links in der Kopfzeile (zum Ziehen). */
    private static final int GRIP = 8;
    private static final int HEADER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 14;
    private static final int BUTTON = 10;
    private static final int ICON = 12;
    private static final int PAD = 2;
    private static final int SCROLLBAR = 2;
    private static final int MAX_WIDTH = 110;
    /** Unterhalb davon wird das Panel gar nicht gezeigt. */
    private static final int MIN_AVAILABLE_WIDTH = 60;
    /** Unten bleibt Platz fuer JEIs eigene Leiste: rechts die Suchleiste (20) + Rand (6), links Lesezeichen-/Konfig-Knopf. */
    private static final int BOTTOM_RESERVED = 30;
    /** Hoechstens dieser Bruchteil (1/3) der nutzbaren Hoehe belegt das Panel in dem Streifen, in dem JEIs Lesezeichen-Liste steht. */
    private static final int JEI_LIST_SHARE = 3;
    /** Das Panel klebt am oberen Rand: Tooltips brauchen hier etwas Abstand, sonst werden sie oben abgeschnitten. */
    private static final int TOOLTIP_MIN_Y = 24;

    private static final int COLOR_BACKGROUND = 0xE0141420;
    private static final int COLOR_BORDER = 0xFF3C3C5A;
    private static final int COLOR_HEADER = 0xFF23233A;
    private static final int COLOR_HOVER = 0x30FFFFFF;
    private static final int COLOR_ACTIVE = 0x603C8CDC;
    private static final int COLOR_ACCENT = 0xFF50A0F0;
    private static final int COLOR_TEXT = 0xFFE8E8F0;
    private static final int COLOR_MUTED = 0xFF8C8CA0;
    private static final int COLOR_ADD = 0xFF70D070;

    private static int scroll;
    /** Panel wird gerade mit der Maus gezogen; {@code grab*} = Abstand des Griffpunkts zur linken oberen Ecke. */
    private static boolean dragging;
    private static double grabX;
    private static double grabY;

    private BookmarkPanel() {}

    /**
     * Berechnete Position fuer den aktuellen Bildschirm. {@code rows} = sichtbare Zeilen ab {@code first},
     * {@code left} = sitzt am linken Rand, {@code screenWidth/screenHeight} = GUI-Groesse (fuer das Ziehen).
     */
    public record Layout(int x, int y, int width, int height, int first, int rows, boolean collapsed,
                         boolean left, int screenWidth, int screenHeight) {

        public int right() {
            return x + width;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }

        public int collapseX() {
            return right() - BUTTON - 1;
        }

        public int addX() {
            return collapseX() - BUTTON - 1;
        }

        public int dockX() {
            return addX() - BUTTON - 1;
        }

        public int rowY(int visibleIndex) {
            return y + HEADER_HEIGHT + visibleIndex * ROW_HEIGHT;
        }

        private boolean inHeader(double my) {
            return my >= y && my < y + HEADER_HEIGHT;
        }

        boolean overCollapse(double mx, double my) {
            return inHeader(my) && mx >= collapseX() && mx < collapseX() + BUTTON;
        }

        boolean overAdd(double mx, double my) {
            return !collapsed && inHeader(my) && mx >= addX() && mx < addX() + BUTTON;
        }

        boolean overDock(double mx, double my) {
            return !collapsed && inHeader(my) && mx >= dockX() && mx < dockX() + BUTTON;
        }

        /** Alles in der Kopfzeile links von den Knoepfen: Griff und Titel, hier wird gezogen. */
        boolean overGrip(double mx, double my) {
            return inHeader(my) && mx >= x && mx < (collapsed ? collapseX() : dockX());
        }

        /** @return Index in der Lesezeichenliste oder -1 */
        int rowAt(double mx, double my) {
            if (collapsed || !contains(mx, my) || my < y + HEADER_HEIGHT) {
                return -1;
            }
            int visible = (int) (my - y - HEADER_HEIGHT) / ROW_HEIGHT;
            return visible < rows ? first + visible : -1;
        }
    }

    public static Layout layoutFor(Screen screen) {
        IJeiRuntime runtime = JeiBookmarksPlugin.runtime();
        if (runtime == null || screen == null) {
            return null;
        }
        Optional<IGuiProperties> properties = runtime.getScreenHelper().getGuiProperties(screen);
        if (properties.isEmpty()) {
            return null;
        }
        int screenWidth = properties.get().screenWidth();
        int screenHeight = properties.get().screenHeight();
        int available = screenWidth - properties.get().guiRight();
        // In der Grundstellung (rechts oben) braucht das Panel Platz neben dem Container. Wer es selbst
        // verschoben hat, hat die Lage bewusst gewaehlt: dann wird es immer gezeigt, nur im Bild gehalten.
        boolean custom = !BookmarkStore.isDefaultPosition();
        if (!custom && available < MIN_AVAILABLE_WIDTH) {
            return null;
        }
        boolean left = BookmarkStore.isLeft();
        int reserved = BOTTOM_RESERVED;

        if (BookmarkStore.isCollapsed()) {
            int width = GRIP + BUTTON + 3;
            return place(width, HEADER_HEIGHT, 0, 0, true, left, false, screenWidth, screenHeight, reserved);
        }

        Font font = Minecraft.getInstance().font;
        List<Bookmark> bookmarks = BookmarkStore.bookmarks();
        int wanted = font.width(title()) + GRIP + PAD * 2 + BUTTON * 3 + 3;
        for (Bookmark bookmark : bookmarks) {
            wanted = Math.max(wanted, ICON + PAD * 3 + font.width(ModResolver.display(bookmark).label()));
        }
        int y = clampY(BookmarkStore.offsetY(), screenHeight, reserved, false);
        int space = screenHeight - y - HEADER_HEIGHT - reserved;
        int rows = Math.clamp(space / ROW_HEIGHT, 0, bookmarks.size());
        // JEIs eigene Lesezeichen-Liste steht links neben dem Container und weicht unserer Sperrflaeche aus.
        // Damit sie nicht zu einer schmalen Spalte gequetscht oder ganz ausgeblendet wird, klebt unser Panel
        // in diesem Streifen am linken Rand und bekommt hoechstens einen Teil der Hoehe, der Rest bleibt JEI.
        boolean jeiStrip = left && BookmarkStore.offsetX() < properties.get().guiLeft() && JeiBookmarkArea.wanted(runtime);
        if (jeiStrip) {
            int maxRows = Math.max(1, ((screenHeight - reserved) / JEI_LIST_SHARE - HEADER_HEIGHT) / ROW_HEIGHT);
            rows = Math.min(rows, maxRows);
        }
        if (rows < bookmarks.size()) {
            wanted += SCROLLBAR + 1;
        }
        int widthLimit = custom ? Math.min(MAX_WIDTH, screenWidth / 3) : Math.min(MAX_WIDTH, available / 2);
        int width = Math.min(wanted, widthLimit);
        scroll = Math.clamp(scroll, 0, bookmarks.size() - rows);
        int height = HEADER_HEIGHT + rows * ROW_HEIGHT + (rows > 0 ? 1 : 0);
        return place(width, height, scroll, rows, false, left, jeiStrip, screenWidth, screenHeight, reserved);
    }

    /** Setzt Breite/Hoehe an die gespeicherte Lage (Randabstand zur gewaehlten Seite), im Bild gehalten. */
    private static Layout place(int width, int height, int first, int rows, boolean collapsed, boolean left, boolean toEdge,
                                int screenWidth, int screenHeight, int reserved) {
        int x = left ? (toEdge ? MARGIN : BookmarkStore.offsetX()) : screenWidth - BookmarkStore.offsetX() - width;
        x = Math.clamp(x, 0, Math.max(0, screenWidth - width));
        int y = clampY(BookmarkStore.offsetY(), screenHeight, reserved, collapsed);
        return new Layout(x, y, width, height, first, rows, collapsed, left, screenWidth, screenHeight);
    }

    /** Hoechste erlaubte Oberkante: Kopfzeile plus (ausgeklappt) mindestens eine Zeile muessen noch ins Bild passen. */
    private static int clampY(int y, int screenHeight, int reserved, boolean collapsed) {
        int max = screenHeight - reserved - HEADER_HEIGHT - (collapsed ? 0 : ROW_HEIGHT + 1);
        return Math.clamp(y, 0, Math.max(0, max));
    }

    /** JEI ist an, wenn die Liste zu sehen ist. Eine Suche ohne Treffer blendet die Liste aus, nicht aber das Panel. */
    private static boolean visible(IJeiRuntime runtime) {
        if (runtime.getIngredientListOverlay().isListDisplayed()) {
            return true;
        }
        try {
            return mezz.jei.common.Internal.getClientToggleState().isOverlayEnabled();
        } catch (LinkageError | RuntimeException e) {
            // Nicht Teil der offiziellen API: Faellt der Aufruf weg, zaehlt nur eine aktive Suche.
            return !runtime.getIngredientFilter().getFilterText().isEmpty();
        }
    }

    private static Layout activeLayout(Screen screen) {
        IJeiRuntime runtime = JeiBookmarksPlugin.runtime();
        return runtime != null && visible(runtime) ? layoutFor(screen) : null;
    }

    private static Component title() {
        return Component.translatable("syntexjeibookmarks.title");
    }

    // ---------------------------------------------------------------- Zeichnen

    static void render(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        Layout layout = activeLayout(screen);
        if (layout == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        IIngredientFilter filter = JeiBookmarksPlugin.runtime().getIngredientFilter();
        String current = filter.getFilterText().trim();

        g.nextStratum();
        g.fill(layout.x(), layout.y(), layout.right(), layout.y() + layout.height(), COLOR_BACKGROUND);
        g.fill(layout.x(), layout.y(), layout.right(), layout.y() + HEADER_HEIGHT, COLOR_HEADER);
        g.outline(layout.x(), layout.y(), layout.width(), layout.height(), COLOR_BORDER);

        drawGrip(g, layout.x() + 3, layout.y() + 2, dragging || layout.overGrip(mx, my) ? COLOR_TEXT : COLOR_MUTED);
        // Der Pfeil zeigt zum naechsten Rand: dorthin klappt das Panel ein bzw. von dort aus.
        String collapseGlyph = layout.collapsed() == layout.left() ? ">" : "<";
        drawButton(g, font, layout.collapseX(), layout.y() + 1, collapseGlyph, COLOR_TEXT, layout.overCollapse(mx, my));
        if (layout.collapsed()) {
            if (layout.overCollapse(mx, my)) {
                g.setTooltipForNextFrame(font, Component.translatable("syntexjeibookmarks.expand"), mx, Math.max(my, TOOLTIP_MIN_Y));
            } else if (!dragging && layout.overGrip(mx, my)) {
                g.setTooltipForNextFrame(font, Component.translatable("syntexjeibookmarks.move"), mx, Math.max(my, TOOLTIP_MIN_Y));
            }
            return;
        }

        boolean canAdd = !current.isEmpty() && !BookmarkStore.contains(current);
        drawButton(g, font, layout.addX(), layout.y() + 1, "+", canAdd ? COLOR_ADD : COLOR_MUTED, layout.overAdd(mx, my));
        // Der Pfeil zeigt, auf welche Seite ein Klick das Panel setzt.
        drawButton(g, font, layout.dockX(), layout.y() + 1, layout.left() ? "»" : "«", COLOR_TEXT, layout.overDock(mx, my));
        int titleX = layout.x() + GRIP + 1;
        int titleWidth = layout.dockX() - titleX - 1;
        if (font.width(title()) <= titleWidth) {
            g.text(font, title(), titleX, layout.y() + 2, COLOR_MUTED, false);
        }

        List<Bookmark> bookmarks = BookmarkStore.bookmarks();
        boolean scrollbar = layout.rows() < bookmarks.size();
        int rowRight = layout.right() - 1 - (scrollbar ? SCROLLBAR + 1 : 0);
        for (int i = 0; i < layout.rows(); i++) {
            Bookmark bookmark = bookmarks.get(layout.first() + i);
            ModResolver.Display display = ModResolver.display(bookmark);
            int rowY = layout.rowY(i);
            boolean active = bookmark.query().equalsIgnoreCase(current);
            if (active) {
                g.fill(layout.x() + 1, rowY, rowRight, rowY + ROW_HEIGHT, COLOR_ACTIVE);
                g.fill(layout.x() + 1, rowY, layout.x() + 2, rowY + ROW_HEIGHT, COLOR_ACCENT);
            }
            if (layout.rowAt(mx, my) == layout.first() + i) {
                g.fill(layout.x() + 1, rowY, rowRight, rowY + ROW_HEIGHT, COLOR_HOVER);
            }
            int textX = layout.x() + PAD + 1;
            if (!display.icon().isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(textX, rowY + 1);
                g.pose().scale(ICON / 16f);
                g.item(display.icon(), 0, 0);
                g.pose().popMatrix();
                textX += ICON + 1;
            }
            g.text(font, fit(font, display.label(), rowRight - textX - 1), textX, rowY + 3, COLOR_TEXT, false);
        }
        if (scrollbar) {
            int top = layout.rowY(0);
            int track = layout.rows() * ROW_HEIGHT;
            int thumb = Math.max(6, track * layout.rows() / bookmarks.size());
            int thumbY = top + (track - thumb) * layout.first() / Math.max(1, bookmarks.size() - layout.rows());
            g.fill(layout.right() - 1 - SCROLLBAR, thumbY, layout.right() - 1, thumbY + thumb, COLOR_MUTED);
        }

        renderTooltip(g, font, layout, current, mx, my);
    }

    private static void renderTooltip(GuiGraphicsExtractor g, Font font, Layout layout, String current, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        if (dragging) {
            return;
        }
        if (layout.overCollapse(mx, my)) {
            lines.add(Component.translatable("syntexjeibookmarks.collapse"));
        } else if (layout.overGrip(mx, my)) {
            lines.add(Component.translatable("syntexjeibookmarks.move"));
            lines.add(Component.translatable("syntexjeibookmarks.move.hint").withStyle(ChatFormatting.GRAY));
        } else if (layout.overDock(mx, my)) {
            lines.add(Component.translatable(layout.left() ? "syntexjeibookmarks.dock.right" : "syntexjeibookmarks.dock.left"));
            lines.add(Component.translatable("syntexjeibookmarks.dock.reset").withStyle(ChatFormatting.GRAY));
        } else if (layout.overAdd(mx, my)) {
            lines.add(Component.translatable("syntexjeibookmarks.add"));
            if (current.isEmpty()) {
                lines.add(Component.translatable("syntexjeibookmarks.add.empty").withStyle(ChatFormatting.GRAY));
            } else {
                String key = BookmarkStore.contains(current) ? "syntexjeibookmarks.add.duplicate" : "syntexjeibookmarks.add.query";
                lines.add(Component.translatable(key, current).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("syntexjeibookmarks.add.mod_hint", ClientEvents.ADD_MOD.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            int row = layout.rowAt(mx, my);
            if (row >= 0) {
                Bookmark bookmark = BookmarkStore.bookmarks().get(row);
                lines.add(Component.literal(ModResolver.display(bookmark).label()));
                lines.add(Component.literal(bookmark.query()).withStyle(ChatFormatting.AQUA));
                boolean active = bookmark.query().equalsIgnoreCase(current);
                lines.add(Component.translatable(active ? "syntexjeibookmarks.row.clear" : "syntexjeibookmarks.row.search").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("syntexjeibookmarks.row.remove").withStyle(ChatFormatting.GRAY));
            }
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, Math.max(my, TOOLTIP_MIN_Y));
        }
    }

    /** Zwei Spalten mal drei Punkte: das uebliche Zeichen fuer "hier anfassen". */
    private static void drawGrip(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int column = 0; column < 2; column++) {
            for (int row = 0; row < 3; row++) {
                g.fill(x + column * 2, y + row * 3, x + column * 2 + 1, y + row * 3 + 1, color);
            }
        }
    }

    private static void drawButton(GuiGraphicsExtractor g, Font font, int x, int y, String text, int color, boolean hovered) {
        if (hovered) {
            g.fill(x, y, x + BUTTON, y + BUTTON, COLOR_HOVER);
        }
        g.centeredText(font, text, x + BUTTON / 2, y + 1, color);
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    // ---------------------------------------------------------------- Eingabe

    /** @return true, wenn der Klick im Panel lag - er darf dann weder JEI noch den Container erreichen */
    static boolean mouseClicked(Screen screen, double mx, double my, int button) {
        dragging = false;
        Layout layout = activeLayout(screen);
        if (layout == null || !layout.contains(mx, my)) {
            return false;
        }
        IIngredientFilter filter = JeiBookmarksPlugin.runtime().getIngredientFilter();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.overCollapse(mx, my)) {
            BookmarkStore.setCollapsed(!layout.collapsed());
            click();
        } else if (layout.overDock(mx, my) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Auf die andere Seite, ganz an den Rand; die Hoehe bleibt.
            BookmarkStore.setPosition(!layout.left(), MARGIN, layout.y());
            BookmarkStore.savePosition();
            click();
        } else if (layout.overDock(mx, my) && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            BookmarkStore.resetPosition();
            click();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.overGrip(mx, my)) {
            dragging = true;
            grabX = mx - layout.x();
            grabY = my - layout.y();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.overAdd(mx, my)) {
            if (BookmarkStore.add(filter.getFilterText())) {
                scroll = Integer.MAX_VALUE;
                click();
            }
        } else {
            int row = layout.rowAt(mx, my);
            if (row >= 0) {
                Bookmark bookmark = BookmarkStore.bookmarks().get(row);
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    boolean active = bookmark.query().equalsIgnoreCase(filter.getFilterText().trim());
                    filter.setFilterText(active ? "" : bookmark.query());
                    click();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    BookmarkStore.remove(bookmark);
                    click();
                }
            }
        }
        return true;
    }

    /** Folgt der Maus, solange am Griff gezogen wird. Die Lage wird erst beim Loslassen gespeichert. */
    static void mouseDragged(Screen screen, double mx, double my) {
        if (!dragging) {
            return;
        }
        Layout layout = activeLayout(screen);
        if (layout == null) {
            dragging = false;
            return;
        }
        int reserved = BOTTOM_RESERVED;
        int x = Math.clamp((int) Math.round(mx - grabX), 0, Math.max(0, layout.screenWidth() - layout.width()));
        int y = clampY((int) Math.round(my - grabY), layout.screenHeight(), reserved, layout.collapsed());
        // Die Seite richtet sich nach der Haelfte, in der die Panelmitte liegt; gemessen wird vom nahen Rand.
        boolean toLeft = x + layout.width() / 2.0 < layout.screenWidth() / 2.0;
        int offsetX = toLeft ? x : layout.screenWidth() - layout.width() - x;
        if (offsetX <= SNAP) {
            offsetX = MARGIN;
        }
        BookmarkStore.setPosition(toLeft, offsetX, y <= SNAP ? MARGIN : y);
    }

    /** @return true, wenn gerade ein Ziehen beendet wurde (die Lage ist dann gespeichert) */
    static boolean mouseReleased() {
        if (!dragging) {
            return false;
        }
        dragging = false;
        BookmarkStore.savePosition();
        return true;
    }

    static boolean mouseScrolled(Screen screen, double mx, double my, double delta) {
        Layout layout = activeLayout(screen);
        if (layout == null || !layout.contains(mx, my)) {
            return false;
        }
        // Wird beim naechsten layoutFor auf den gueltigen Bereich begrenzt.
        scroll = Math.max(0, layout.first() - (int) Math.signum(delta));
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
