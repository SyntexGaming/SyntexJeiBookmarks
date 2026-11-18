package dev.syntex.jeibookmarks;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Such-Lesezeichen fuer JEI: gespeicherte Suchbegriffe wie {@code @powah} als Liste rechts oben
 * neben der JEI-Itemliste. Ein Klick schreibt den Begriff in die JEI-Suchleiste.
 *
 * Reiner Client-Mod: auf einem Dedicated Server wird nichts registriert, die Client-Klassen werden gar nicht erst geladen.
 */
@Mod(SyntexJeiBookmarks.MODID)
public class SyntexJeiBookmarks {

    public static final String MODID = "syntexjeibookmarks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SyntexJeiBookmarks() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BookmarkStore.load();
        modBus.addListener(ClientEvents::registerKeys);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("[SyntexJeiBookmarks] initialisiert, {} Lesezeichen", BookmarkStore.bookmarks().size());
    }
}
