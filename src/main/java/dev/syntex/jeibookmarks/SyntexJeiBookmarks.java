package dev.syntex.jeibookmarks;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Such-Lesezeichen fuer JEI: gespeicherte Suchbegriffe wie {@code @powah} als Liste rechts oben
 * neben der JEI-Itemliste. Ein Klick schreibt den Begriff in die JEI-Suchleiste.
 *
 * Reiner Client-Mod: {@code dist = Dist.CLIENT} - auf einem Dedicated Server wird die Klasse gar nicht
 * erst geladen.
 */
@Mod(value = SyntexJeiBookmarks.MODID, dist = Dist.CLIENT)
public class SyntexJeiBookmarks {

    public static final String MODID = "syntexjeibookmarks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SyntexJeiBookmarks(IEventBus modBus, ModContainer container) {
        BookmarkStore.load();
        modBus.addListener(ClientEvents::registerKeys);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("[SyntexJeiBookmarks] initialisiert, {} Lesezeichen", BookmarkStore.bookmarks().size());
    }
}
