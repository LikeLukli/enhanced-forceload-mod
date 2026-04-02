package at.lukli.forceloadmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_RANDOM_TICKS = BUILDER
            .comment("When true, chunks loaded via the /forceload command also receive random block ticks, " +
                     "even when no player is nearby.")
            .define("enableRandomTicksForForcedChunks", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
