package eu.doytchinov.tracecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    /**
     * Holds all COMMON config values under the TOML [general] and [metrics]
     * sections.
     */
    public static class Common {
        public final ForgeConfigSpec.ConfigValue<String> influxdbUrl;
        public final ForgeConfigSpec.BooleanValue metricsEnabled;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("=== TraceCraft General Settings ===").push("general");
            influxdbUrl = builder
                    .comment("URL of your InfluxDB instance")
                    .define("influxdbUrl", "http://localhost:8086");
            builder.pop();

            builder.comment("=== TraceCraft Metrics Settings ===").push("metrics");
            metricsEnabled = builder
                    .comment("Enable or disable metrics collection")
                    .define("enabled", true);
            builder.pop();
        }
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    /**
     * Triggered when the config is first loaded or changed via file.
     * You can add reload logic here if needed.
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            // e.g., log or validate values
            LOGGER.info("Loading TraceCraft common config");
            LOGGER.info("InfluxDB URL from config: {}", COMMON.influxdbUrl.get());
        }
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            // handle live reload if desired
        }
    }

    /**
     * Helper to get the InfluxDB URL from config
     */
    public static String getInfluxdbUrl() {
        return COMMON.influxdbUrl.get();
    }

    /**
     * Helper to check if metrics are enabled
     */
    public static boolean isMetricsEnabled() {
        return COMMON.metricsEnabled.get();
    }
}
