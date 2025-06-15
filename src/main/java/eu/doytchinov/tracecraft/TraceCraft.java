package eu.doytchinov.tracecraft;

import com.mojang.logging.LogUtils;
import eu.doytchinov.tracecraft.config.ConfigHandler;
import eu.doytchinov.tracecraft.database.EventQueue;
import eu.doytchinov.tracecraft.influxdb.InfluxDBHelper;
import eu.doytchinov.tracecraft.net.NetworkHandler;
import eu.doytchinov.tracecraft.server.ServerHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static eu.doytchinov.tracecraft.config.ConfigHandler.COMMON_SPEC;

@Mod(TraceCraft.MODID)
public class TraceCraft {
    public static final String MODID = "tracecraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EventQueue QUEUE = new EventQueue();
    private static InfluxDBHelper INFLUX_DB_HELPER;
    private static ScheduledExecutorService SCHEDULER;

    public TraceCraft(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.register(ConfigHandler.class);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent e) {
        NetworkHandler.init();

        // Initialize InfluxDBHelper here, after configs are loaded
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER
                || (FMLEnvironment.dist == Dist.CLIENT
                        && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null)) {
            if (INFLUX_DB_HELPER == null) { // Check if already initialized
                try {
                    LOGGER.info("Attempting to initialize InfluxDBHelper in commonSetup."); // Added log
                    INFLUX_DB_HELPER = new InfluxDBHelper();
                    SCHEDULER = Executors.newSingleThreadScheduledExecutor();
                    SCHEDULER.scheduleAtFixedRate(INFLUX_DB_HELPER, 2, 2, TimeUnit.SECONDS);
                    LOGGER.info("TraceCraft InfluxDB writer initialised on server side via commonSetup");
                } catch (Exception ex) { // Changed variable name from e to ex to avoid conflict
                    LOGGER.error("Failed to initialise InfluxDBHelper in commonSetup: ", ex);
                }
            }
            MinecraftForge.EVENT_BUS.register(new ServerHooks()); // This was inside the old if block, ensure it's correctly placed
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping, shutting down InfluxDB scheduler and connection.");
        if (SCHEDULER != null) {
            SCHEDULER.shutdown();
            try {
                if (!SCHEDULER.awaitTermination(5, TimeUnit.SECONDS)) {
                    SCHEDULER.shutdownNow();
                }
            } catch (InterruptedException e) {
                SCHEDULER.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (INFLUX_DB_HELPER != null) {
            INFLUX_DB_HELPER.close();
        }
        if (INFLUX_DB_HELPER != null && !TraceCraft.QUEUE.isEmpty()) {
            LOGGER.info("Processing remaining " + TraceCraft.QUEUE.size() + " events before full shutdown.");
            INFLUX_DB_HELPER.run();
            INFLUX_DB_HELPER.close();
        }
    }
}
