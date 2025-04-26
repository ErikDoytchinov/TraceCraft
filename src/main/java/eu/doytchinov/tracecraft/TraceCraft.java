package eu.doytchinov.tracecraft;

import com.mojang.logging.LogUtils;
import eu.doytchinov.tracecraft.database.DatabaseHelper;
import eu.doytchinov.tracecraft.database.EventQueue;
import eu.doytchinov.tracecraft.net.NetworkHandler;
import eu.doytchinov.tracecraft.server.ServerHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(TraceCraft.MODID)
public class TraceCraft {
    public static final String MODID = "tracecraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EventQueue QUEUE = new EventQueue();
    private static DatabaseHelper DB;

    public TraceCraft(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER
                || (FMLEnvironment.dist == Dist.CLIENT
                && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null)) {
            // this starts the database only on the server side
            if (DB == null) {
                DB = new DatabaseHelper(Path.of("mvetrace.db"));
                Executors.newSingleThreadScheduledExecutor()
                        .scheduleAtFixedRate(DB, 2, 2, TimeUnit.SECONDS);
            }

            MinecraftForge.EVENT_BUS.register(new ServerHooks());
            LOGGER.info("TraceCraft DB writer initialised on server side");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent e) {
        NetworkHandler.init();
    }
}
