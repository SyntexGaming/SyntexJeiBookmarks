package dev.syntex.jeibookmarks;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;

import java.util.Optional;

/** Bindet das Panel an die Screen-Events. Hoechste Prioritaet, damit Klicks im Panel JEI gar nicht erst erreichen. */
public final class ClientEvents {

    private static final String CATEGORY = "key.category.syntexjeibookmarks.main";

    public static final KeyMapping ADD_MOD = new KeyMapping(
            "key.syntexjeibookmarks.add_mod",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_M,
            CATEGORY
    );

    /** Maustaste, deren Druck im Panel lag: das zugehoerige Loslassen/Ziehen gehoert uns auch. */
    private static int heldButton = -1;

    private ClientEvents() {}

    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ADD_MOD);
    }

    /** Post: JEI und Tooltips sind schon gezeichnet, die Hoehe hebt das Panel ueber deren Items. */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        BookmarkPanel.render(event.getScreen(), g, event.getMouseX(), event.getMouseY());
        g.pose().popPose();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (BookmarkPanel.mouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            heldButton = event.getButton();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == heldButton) {
            BookmarkPanel.mouseReleased();
            heldButton = -1;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (heldButton != -1) {
            BookmarkPanel.mouseDragged(event.getScreen(), event.getMouseX(), event.getMouseY());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (BookmarkPanel.mouseScrolled(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!ADD_MOD.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        IJeiRuntime runtime = JeiBookmarksPlugin.runtime();
        Screen screen = event.getScreen();
        // Beim Tippen ist "M" ein Buchstabe und keine Taste.
        if (runtime == null || runtime.getIngredientListOverlay().hasKeyboardFocus() || screen.getFocused() instanceof EditBox) {
            return;
        }
        Optional<String> modId = modUnderMouse(runtime, screen);
        if (modId.isPresent()) {
            BookmarkStore.add("@" + modId.get());
            event.setCanceled(true);
        }
    }

    private static Optional<String> modUnderMouse(IJeiRuntime runtime, Screen screen) {
        Optional<ITypedIngredient<?>> ingredient = runtime.getIngredientListOverlay().getIngredientUnderMouse()
                .or(() -> runtime.getBookmarkOverlay().getIngredientUnderMouse());
        // Im Kreativ-Suchreiter tippt man ohne fokussiertes Feld, dort zaehlen nur die JEI-Listen.
        if (ingredient.isEmpty() && !(screen instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen())) {
            Minecraft minecraft = Minecraft.getInstance();
            double mx = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            ingredient = runtime.getScreenHelper().getClickableIngredientUnderMouse(screen, mx, my)
                    .<ITypedIngredient<?>>map(clickable -> clickable.getTypedIngredient())
                    .findFirst();
        }
        return ingredient.map(typed -> displayModId(runtime.getIngredientManager(), typed));
    }

    private static <T> String displayModId(IIngredientManager manager, ITypedIngredient<T> typed) {
        return manager.getIngredientHelper(typed.getType()).getDisplayModId(typed.getIngredient());
    }
}
