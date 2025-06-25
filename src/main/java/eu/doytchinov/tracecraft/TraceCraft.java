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
        MinecraftForge.EVENT_BUS.register(eu.doytchinov.tracecraft.commands.TraceCraftCommands.class);

        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);

        LOGGER.info(
                "TraceCraft initialized - this mod collects server-side metrics and optional client metrics for research");
    }

    private void commonSetup(final FMLCommonSetupEvent e) {
        NetworkHandler.init();
        LOGGER.info("Network handler initialized");

        // init server-side components; these work regardless of client mod presence
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER
                || (FMLEnvironment.dist == Dist.CLIENT
                        && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null)) {

            if (INFLUX_DB_HELPER == null) {
                try {
                    LOGGER.info("Initializing InfluxDB connection for server-side metrics collection");
                    INFLUX_DB_HELPER = new InfluxDBHelper();
                    SCHEDULER = Executors.newSingleThreadScheduledExecutor();
                    // schedule regular flushes every 2 seconds, but also check if queue should
                    // flush more frequently
                    SCHEDULER.scheduleAtFixedRate(() -> {
                        if (QUEUE.shouldFlush()) {
                            INFLUX_DB_HELPER.run();
                        }
                    }, 0, 1, TimeUnit.SECONDS);

                    SCHEDULER.scheduleAtFixedRate(INFLUX_DB_HELPER, 2, 2, TimeUnit.SECONDS);
                    LOGGER.info("TraceCraft server-side metrics collection initialized successfully");
                } catch (Exception ex) {
                    LOGGER.error("Failed to initialize InfluxDB connection. Server metrics will not be saved: ", ex);
                }
            }

            MinecraftForge.EVENT_BUS.register(new ServerHooks());
            LOGGER.info("Server-side event handlers registered");
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
