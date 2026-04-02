package at.lukli.forceloadmod;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ForcedChunksSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(EnhancedForceloadMod.MOD_ID)
public class EnhancedForceloadMod {
    public static final String MOD_ID = "enhancedforceloadmod";

    public EnhancedForceloadMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!Config.ENABLE_RANDOM_TICKS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        int randomTickSpeed = serverLevel.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (randomTickSpeed <= 0) return;

        // Only fetch forced-chunk data when chunks are actually registered
        ForcedChunksSavedData forcedData = serverLevel.getDataStorage().get(
                ForcedChunksSavedData.factory(), "chunks");
        if (forcedData == null) return;

        if (forcedData.getChunks().isEmpty()) return;

        // Vanilla already random-ticks chunks within simulation distance of a player.
        // We only tick forced chunks that are *outside* that range to avoid double-ticking.
        int simDistance = serverLevel.getServer().getPlayerList().getSimulationDistance();

        // Pre-compute player chunk positions once per tick to avoid O(forced * players) repeated calls.
        List<ChunkPos> playerChunkPositions = serverLevel.players().stream()
                .map(ServerPlayer::chunkPosition)
                .toList();

        for (long packedPos : forcedData.getChunks()) {
            ChunkPos chunkPos = new ChunkPos(packedPos);

            boolean alreadyTicked = false;
            for (ChunkPos playerChunk : playerChunkPositions) {
                if (chunkPos.getChessboardDistance(playerChunk) <= simDistance) {
                    alreadyTicked = true;
                    break;
                }
            }
            if (alreadyTicked) continue;

            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) continue;

            tickChunkRandomly(serverLevel, chunk, chunkPos, randomTickSpeed);
        }
    }

    private static void tickChunkRandomly(ServerLevel level, LevelChunk chunk, ChunkPos chunkPos, int randomTickSpeed) {
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.isRandomlyTicking()) continue;

            int minBlockY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));

            for (int i = 0; i < randomTickSpeed; i++) {
                int localX = level.random.nextInt(16);
                int localY = level.random.nextInt(16);
                int localZ = level.random.nextInt(16);

                mutablePos.set(
                        chunkPos.getMinBlockX() + localX,
                        minBlockY + localY,
                        chunkPos.getMinBlockZ() + localZ);

                BlockState blockState = section.getBlockState(localX, localY, localZ);
                if (blockState.isRandomlyTicking()) {
                    blockState.randomTick(level, mutablePos, level.random);
                }

                FluidState fluidState = section.getFluidState(localX, localY, localZ);
                if (fluidState.isRandomlyTicking()) {
                    fluidState.randomTick(level, mutablePos, level.random);
                }
            }
        }
    }
}
