package dev.syntex.jeibookmarks;

import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Liest Zustand und Flaeche von JEIs eigener Item-Lesezeichen-Liste (Taste A, steht links neben dem
 * Container). Die JEI-API nennt beides nicht, deshalb per Reflection auf {@code BookmarkOverlay}. Faellt das
 * aus (andere JEI-Version), wird einmal gewarnt und danach nichts mehr geliefert: das Panel verhaelt sich
 * dann wie ohne diese Funktion, JEI weicht der gemeldeten Sperrflaeche trotzdem selbst aus.
 */
public final class JeiBookmarkArea {

    private static boolean broken;
    private static boolean ready;
    private static Field contents;
    private static Field toggleState;
    private static Method listDisplayed;
    private static Method bookmarkList;
    private static Method listIsEmpty;
    private static Method overlayEnabled;
    private static Method backgroundArea;
    private static Method getX;
    private static Method getY;
    private static Method getWidth;
    private static Method getHeight;

    private JeiBookmarkArea() {}

    /**
     * Will JEI seine Lesezeichen-Liste zeigen? Ja, wenn die Anzeige eingeschaltet ist und Eintraege da sind,
     * auch wenn sie gerade keinen Platz hat und deshalb ausgeblendet ist. Sonst (und bei Lesefehlern) nein.
     */
    public static boolean wanted(IJeiRuntime runtime) {
        Object overlay = overlay(runtime);
        if (overlay == null) {
            return false;
        }
        try {
            boolean enabled = (Boolean) overlayEnabled.invoke(toggleState.get(overlay));
            return enabled && !(Boolean) listIsEmpty.invoke(bookmarkList.invoke(overlay));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** @return belegte Flaeche der JEI-Lesezeichen-Liste, {@code null} wenn sie nicht gezeigt wird oder nicht lesbar ist */
    public static Rect2i current(IJeiRuntime runtime) {
        Object overlay = overlay(runtime);
        if (overlay == null) {
            return null;
        }
        try {
            if (!(Boolean) listDisplayed.invoke(overlay)) {
                return null;
            }
            Object rect = backgroundArea.invoke(contents.get(overlay));
            int width = (Integer) getWidth.invoke(rect);
            int height = (Integer) getHeight.invoke(rect);
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new Rect2i((Integer) getX.invoke(rect), (Integer) getY.invoke(rect), width, height);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return null;
        }
    }

    private static Object overlay(IJeiRuntime runtime) {
        if (broken || runtime == null) {
            return null;
        }
        try {
            Object overlay = runtime.getBookmarkOverlay();
            if (overlay != null && !ready) {
                init(overlay.getClass());
            }
            return overlay;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return null;
        }
    }

    private static void init(Class<?> overlayType) throws ReflectiveOperationException {
        Field contentsField = overlayType.getDeclaredField("contents");
        Field toggleField = overlayType.getDeclaredField("toggleState");
        contentsField.setAccessible(true);
        toggleField.setAccessible(true);
        Method background = contentsField.getType().getMethod("getBackgroundArea");
        Class<?> rectType = background.getReturnType();
        Method list = overlayType.getMethod("getBookmarkList");

        getX = rectType.getMethod("getX");
        getY = rectType.getMethod("getY");
        getWidth = rectType.getMethod("getWidth");
        getHeight = rectType.getMethod("getHeight");
        listDisplayed = overlayType.getMethod("isListDisplayed");
        bookmarkList = list;
        listIsEmpty = list.getReturnType().getMethod("isEmpty");
        overlayEnabled = toggleField.getType().getMethod("isBookmarkOverlayEnabled");
        backgroundArea = background;
        contents = contentsField;
        toggleState = toggleField;
        ready = true;
    }

    private static void fail(Throwable e) {
        broken = true;
        SyntexJeiBookmarks.LOGGER.warn("[SyntexJeiBookmarks] JEI-Lesezeichen-Liste nicht lesbar, Panel nimmt keine Ruecksicht darauf: {}", e.toString());
    }
}
