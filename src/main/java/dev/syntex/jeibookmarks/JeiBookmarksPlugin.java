package dev.syntex.jeibookmarks;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

/**
 * JEI-Anbindung: liefert die Laufzeit und meldet das Panel als Sperrflaeche, damit JEI die Item-Plaetze
 * und die Seiten-Navigation darunter von selbst freilaesst.
 */
@JeiPlugin
public class JeiBookmarksPlugin implements IModPlugin {

    private static volatile IJeiRuntime runtime;

    public static IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(SyntexJeiBookmarks.MODID, "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                BookmarkPanel.Layout layout = BookmarkPanel.layoutFor(Minecraft.getInstance().screen);
                return layout == null ? List.of() : List.of(new Rect2i(layout.x(), layout.y(), layout.width(), layout.height()));
            }
        });
    }
}
