package eu.doytchinov.tracecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    /**
     * Holds all COMMON config values under the TOML [general], [metrics], [events],
     * and [performance]
     * sections.
     */
    public static class Common {
        public final ForgeConfigSpec.ConfigValue<String> influxdbUrl;

        // event category toggles
        public final ForgeConfigSpec.BooleanValue blockEventsEnabled;
        public final ForgeConfigSpec.BooleanValue tickMetricsEnabled;
        public final ForgeConfigSpec.BooleanValue playerBehaviorEnabled;
        public final ForgeConfigSpec.BooleanValue combatEventsEnabled;
        public final ForgeConfigSpec.BooleanValue worldEventsEnabled;
        public final ForgeConfigSpec.BooleanValue networkMetricsEnabled;
        public final ForgeConfigSpec.BooleanValue systemMetricsEnabled;

        // performance configuration
        public final ForgeConfigSpec.IntValue queueSize;
        public final ForgeConfigSpec.IntValue flushThreshold;
        public final ForgeConfigSpec.BooleanValue dryRunMode;

        // sampling configuration
        public final ForgeConfigSpec.IntValue playerLocationSampleInterval;
        public final ForgeConfigSpec.IntValue proximityCheckInterval;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("=== TraceCraft General Settings ===").push("general");
            influxdbUrl = builder
                    .comment("URL of your InfluxDB instance")
                    .define("influxdbUrl", "http://localhost:8086");
            builder.pop();

            builder.comment("=== TraceCraft Metrics Settings ===").push("metrics");
            dryRunMode = builder
                    .comment("Enable dry-run mode (collect but don't export metrics)")
                    .define("dryRunMode", false);
            builder.pop();

            builder.comment("=== Event Category Toggles ===").push("events");
            blockEventsEnabled = builder
                    .comment("Enable tracking of block placement and breaking events")
                    .define("blockEvents", true);
            tickMetricsEnabled = builder
                    .comment("Enable server tick performance metrics")
                    .define("tickMetrics", true);
            playerBehaviorEnabled = builder
                    .comment("Enable player behavior tracking (movement, interactions, etc.)")
                    .define("playerBehavior", true);
            combatEventsEnabled = builder
                    .comment("Enable combat and death event tracking")
                    .define("combatEvents", true);
            worldEventsEnabled = builder
                    .comment("Enable world events (chunk loading, physics, etc.)")
                    .define("worldEvents", true);
            networkMetricsEnabled = builder
                    .comment("Enable network performance metrics")
                    .define("networkMetrics", true);
            systemMetricsEnabled = builder
                    .comment("Enable system resource metrics (memory, CPU, etc.)")
                    .define("systemMetrics", true);
            builder.pop();

            builder.comment("=== Performance Configuration ===").push("performance");
            queueSize = builder
                    .comment("Maximum size of the event queue before forcing a flush")
                    .defineInRange("queueSize", 1000, 100, 10000);
            flushThreshold = builder
                    .comment("Number of events to accumulate before flushing to database")
                    .defineInRange("flushThreshold", 100, 10, 1000);
            builder.pop();

            builder.comment("=== Sampling Intervals ===").push("sampling");
            playerLocationSampleInterval = builder
                    .comment("Interval in milliseconds for sampling player locations")
                    .defineInRange("playerLocationInterval", 1000, 500, 30000);
            proximityCheckInterval = builder
                    .comment("Interval in milliseconds for checking player proximity")
                    .defineInRange("proximityCheckInterval", 1000, 500, 10000);
            builder.pop();
        }
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            LOGGER.info("Loading TraceCraft common config");
            LOGGER.info("InfluxDB URL from config: {}", COMMON.influxdbUrl.get());
            LOGGER.info("Dry-run mode: {}", COMMON.dryRunMode.get());

            LOGGER.info(
                    "Event Categories - Block: {}, Tick: {}, Player: {}, Combat: {}, World: {}, Network: {}, System: {}",
                    COMMON.blockEventsEnabled.get(), COMMON.tickMetricsEnabled.get(),
                    COMMON.playerBehaviorEnabled.get(), COMMON.combatEventsEnabled.get(),
                    COMMON.worldEventsEnabled.get(), COMMON.networkMetricsEnabled.get(),
                    COMMON.systemMetricsEnabled.get());

            LOGGER.info("Performance - Queue Size: {}, Flush Threshold: {}",
                    COMMON.queueSize.get(), COMMON.flushThreshold.get());
        }
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            LOGGER.info("TraceCraft configuration reloaded");
            LOGGER.info("Updated settings - Dry-run: {}",
                    COMMON.dryRunMode.get());

            LOGGER.info("Performance settings updated - Queue Size: {}, Flush Threshold: {}",
                    COMMON.queueSize.get(), COMMON.flushThreshold.get());
        }
    }

    public static String getInfluxdbUrl() {
        return COMMON.influxdbUrl.get();
    }

    public static boolean isDryRunMode() {
        return COMMON.dryRunMode.get();
    }

    // event category helpers
    public static boolean areBlockEventsEnabled() {
        return COMMON.blockEventsEnabled.get();
    }

    public static boolean areTickMetricsEnabled() {
        return COMMON.tickMetricsEnabled.get();
    }

    public static boolean isPlayerBehaviorEnabled() {
        return COMMON.playerBehaviorEnabled.get();
    }

    public static boolean areCombatEventsEnabled() {
        return COMMON.combatEventsEnabled.get();
    }

    public static boolean areWorldEventsEnabled() {
        return COMMON.worldEventsEnabled.get();
    }

    public static boolean areNetworkMetricsEnabled() {
        return COMMON.networkMetricsEnabled.get();
    }

    public static boolean areSystemMetricsEnabled() {
        return COMMON.systemMetricsEnabled.get();
    }

    // performance configuration helpers
    public static int getQueueSize() {
        return COMMON.queueSize.get();
    }

    public static int getFlushThreshold() {
        return COMMON.flushThreshold.get();
    }

    // sampling interval helpers
    public static long getPlayerLocationSampleInterval() {
        return COMMON.playerLocationSampleInterval.get();
    }

    public static long getProximityCheckInterval() {
        return COMMON.proximityCheckInterval.get();
    }
}
