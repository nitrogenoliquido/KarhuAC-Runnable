package me.liwk.karhu.replay.view;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.nbt.*;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import me.liwk.karhu.replay.data.entity.DestroyEntitiesData;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.replay.data.entity.InitialEntityData;
import me.liwk.karhu.replay.data.entity.SpawnEntityData;
import me.liwk.karhu.replay.data.entity.TileEntityData;
import me.liwk.karhu.replay.data.state.*;
import me.liwk.karhu.replay.data.world.*;
import me.liwk.karhu.replay.packet.PacketData;
import me.liwk.karhu.replay.packet.PacketDirection;
import me.liwk.karhu.replay.packet.PacketType;
import me.liwk.karhu.replay.packet.ReplayPacket;
import me.liwk.karhu.replay.session.ReplaySession;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

// Individual Replay Viewer
public class ReplayViewer {

    private final Karhu plugin;
    private final Player viewer;
    private final ReplaySession replay;
    private final List<ReplayPacket> packets;

    private BukkitTask playbackTask;
    private int currentPacketIndex = 0;
    private boolean isPaused = false;
    private float playbackSpeed = 1.0f;
    private long replayStartTime;
    private long realStartTime;

    // Viewer state backup
    private Location originalLocation;
    private GameMode originalGameMode;
    private boolean originalFlying;

    // Replay player entity representation
    private int replayEntityId = -1;
    private final Set<Integer> spawnedEntities = new HashSet<>();
    private final Set<String> sentChunks = new HashSet<>(); // Track chunks sent to viewer
    private ServerVersion serverVersion = null;

    public ReplayViewer(Karhu plugin, Player viewer, ReplaySession replay) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.replay = replay;
        this.packets = replay.getPackets();
        this.serverVersion = PacketEvents.getAPI().getServerManager().getVersion();
    }

    public boolean start() {
        if (packets.isEmpty()) {
            viewer.sendMessage("§cReplay has no recorded packets!");
            return false;
        }

        // Backup viewer state
        originalLocation = viewer.getLocation().clone();
        originalGameMode = viewer.getGameMode();
        originalFlying = viewer.isFlying();

        // Setup viewer for replay
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.setFlying(true);

        // Create a fake player entity to represent the recorded player
        createReplayPlayerEntity();

        // Load initial world state
        loadReplayChunks();
        processInitialState();

        // Initialize timing
        replayStartTime = packets.get(0).getTimestamp();
        realStartTime = System.currentTimeMillis();

        // Start playback
        startPlayback();

        viewer.sendMessage("§aStarted viewing replay for " + replay.getPlayerName());
        viewer.sendMessage("§eUse /replay pause, /replay speed <speed>, /replay seek <time>");

        return true;
    }

    public void stop() {
        if (playbackTask != null) {
            playbackTask.cancel();
            playbackTask = null;
        }

        // Clean up spawned entities
        cleanupSpawnedEntities();

        // Unload all chunks that were sent during replay
        unloadReplayChunks();

        // Restore viewer state
        viewer.setGameMode(originalGameMode);
        viewer.setFlying(originalFlying);
        viewer.teleport(originalLocation);

        viewer.sendMessage("§aStopped viewing replay - world state restored");
    }

    private void unloadReplayChunks() {
        viewer.sendMessage("§7Cleaning up " + sentChunks.size() + " replay chunks...");

        for (String chunkKey : sentChunks) {
            String[] coords = chunkKey.split(",");
            int x = Integer.parseInt(coords[0]);
            int z = Integer.parseInt(coords[1]);

            try {
                sendChunkUnloadPacket(x, z);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to unload chunk " + chunkKey + ": " + e.getMessage());
            }
        }

        sentChunks.clear();
        viewer.sendMessage("§aReplay chunks cleaned up");
    }

    private void createReplayPlayerEntity() {
        // Generate a unique entity ID for the replay player
        replayEntityId = new Random().nextInt(Integer.MAX_VALUE);

        // Find initial player position
        for (ReplayPacket packet : packets) {
            if (packet.getData() instanceof InitialPlayerStateData) {
                InitialPlayerStateData initialState = (InitialPlayerStateData) packet.getData();
                // Spawn a fake player entity to represent the recorded player
                spawnReplayPlayerEntity(initialState.getX(), initialState.getY(), initialState.getZ());
                break;
            }
        }
    }

    private void spawnReplayPlayerEntity(double x, double y, double z) {
        try {
            // Create a player-type entity spawn packet
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    replayEntityId,
                    UUID.randomUUID(),
                    com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.PLAYER,
                    new com.github.retrooper.packetevents.protocol.world.Location(new Vector3d(x,y,z), 0, 0),
                    0, // Rotation
                    0, // Data
                    new Vector3d(0,0,0) // Velocity
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            spawnedEntities.add(replayEntityId);

            // Send player info packet to show the skin
            sendPlayerInfoPacket();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn replay player entity: " + e.getMessage());
        }
    }

    private void sendPlayerInfoPacket() {
        try {
            // Create player info packet to show the player's skin and name
            UUID playerUUID = replay.getPlayerId();
            String playerName = replay.getPlayerName();

            // This is complex in PacketEvents 2.9.5, so we'll create a basic version
            // In a full implementation, you'd fetch the player's skin data

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player info: " + e.getMessage());
        }
    }

    private void processInitialState() {
        // Process all initial state packets first
        for (ReplayPacket packet : packets) {
            if (packet.getDirection() == PacketDirection.INITIAL_STATE) {
                processReplayPacket(packet);
            }
        }
    }

    private void cleanupSpawnedEntities() {
        if (!spawnedEntities.isEmpty()) {
            // Send destroy entities packet for all spawned entities
            int[] entityIds = spawnedEntities.stream().mapToInt(Integer::intValue).toArray();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            spawnedEntities.clear();
        }
    }

    public void pause() {
        isPaused = !isPaused;
        viewer.sendMessage(isPaused ? "§eReplay paused" : "§eReplay resumed");

        if (!isPaused) {
            // Adjust timing when resuming
            realStartTime = System.currentTimeMillis() - (long)((getCurrentReplayTime() - replayStartTime) / playbackSpeed);
        }
    }

    public void setSpeed(float speed) {
        this.playbackSpeed = Math.max(0.1f, Math.min(5.0f, speed));
        viewer.sendMessage("§ePlayback speed set to " + playbackSpeed + "x");

        // Adjust timing for new speed
        realStartTime = System.currentTimeMillis() - (long)((getCurrentReplayTime() - replayStartTime) / playbackSpeed);
    }

    public void seekTo(long timeOffset) {
        long targetTime = replayStartTime + timeOffset * 1000; // Convert seconds to ms

        // Find the packet closest to target time
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i).getTimestamp() >= targetTime) {
                currentPacketIndex = Math.max(0, i - 1);
                break;
            }
        }

        realStartTime = System.currentTimeMillis() - (long)((targetTime - replayStartTime) / playbackSpeed);
        viewer.sendMessage("§eSeeked to " + timeOffset + " seconds");
    }

    private void startPlayback() {
        playbackTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isPaused) return;

                processPackets();
                updateViewerHUD();

                // Check if replay finished
                if (currentPacketIndex >= packets.size()) {
                    viewer.sendMessage("§eReplay finished");
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    private void processPackets() {
        long currentTime = getCurrentReplayTime();

        // Process all packets that should have happened by now
        while (currentPacketIndex < packets.size()) {
            ReplayPacket packet = packets.get(currentPacketIndex);

            if (packet.getTimestamp() > currentTime) {
                break; // Wait for this packet's time
            }

            // Skip initial state packets during playback (already processed)
            if (packet.getDirection() != PacketDirection.INITIAL_STATE) {
                processReplayPacket(packet);
            }

            currentPacketIndex++;
        }
    }

    private void processReplayPacket(ReplayPacket packet) {
        PacketData data = packet.getData();

        switch (packet.getPacketType()) {
            case INITIAL_PLAYER_STATE:
                if (data instanceof InitialPlayerStateData) {
                    InitialPlayerStateData initialState = (InitialPlayerStateData) data;

                    // Teleport viewer to initial position
                    Location spawnLoc = new Location(viewer.getWorld(),
                            initialState.getX(), initialState.getY(), initialState.getZ(),
                            initialState.getYaw(), initialState.getPitch());
                    viewer.teleport(spawnLoc);

                    // Send health/food updates
                    viewer.setHealth(Math.min(20.0, initialState.getHealth()));
                    viewer.setFoodLevel(initialState.getFoodLevel());

                    viewer.sendMessage("§7[INITIAL] §aPlayer state loaded");
                }
                break;

            case INITIAL_ENTITY:
                if (data instanceof InitialEntityData) {
                    sendEntitySpawnPacket((InitialEntityData) data);
                }
                break;

            case INITIAL_INVENTORY:
                if (data instanceof InitialInventoryData) {
                    InitialInventoryData invData = (InitialInventoryData) data;
                    viewer.sendMessage("§7[INITIAL] §dPlayer inventory loaded (" +
                            invData.getMainInventory().size() + " items)");
                }
                break;

            case PLAYER_POSITION:
                if (data instanceof PlayerPositionData) {
                    PlayerPositionData posData = (PlayerPositionData) data;
                    sendPlayerMovementPacket(posData.getX(), posData.getY(), posData.getZ(), 0, 0);
                }
                break;

            case PLAYER_ROTATION:
                if (data instanceof PlayerRotationData) {
                    PlayerRotationData rotData = (PlayerRotationData) data;
                    Location currentLoc = viewer.getLocation();
                    sendPlayerMovementPacket(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ(),
                            rotData.getYaw(), rotData.getPitch());
                }
                break;

            case PLAYER_POSITION_AND_ROTATION:
                if (data instanceof PlayerPosRotData) {
                    PlayerPosRotData posRotData = (PlayerPosRotData) data;
                    sendPlayerMovementPacket(posRotData.getX(), posRotData.getY(), posRotData.getZ(),
                            posRotData.getYaw(), posRotData.getPitch());
                }
                break;

            case INTERACT_ENTITY:
                if (data instanceof InteractEntityData) {
                    InteractEntityData interact = (InteractEntityData) data;
                    sendInteractionEffect(interact.getEntityId(), interact.getAction());
                    viewer.sendMessage("§7[" + formatTime(packet.getTimestamp() - replayStartTime) + "] §eEntity interaction");
                }
                break;

            case USE_ITEM:
                sendItemUseAnimation();
                viewer.sendMessage("§7[" + formatTime(packet.getTimestamp() - replayStartTime) + "] §eItem used");
                break;

            case PLAYER_DIGGING:
                if (data instanceof PlayerDiggingData) {
                    PlayerDiggingData dig = (PlayerDiggingData) data;
                    sendDiggingEffect(dig.getBlockPosition(), dig.getAction());
                    viewer.sendMessage("§7[" + formatTime(packet.getTimestamp() - replayStartTime) + "] §eBlock " + dig.getAction());
                }
                break;

            case CHUNK_DATA:
                if (data instanceof ChunkData) {
                    sendChunkDataPacket((ChunkData) data);
                }
                break;

            case UNLOAD_CHUNK:
                if (data instanceof UnloadChunkData) {
                    UnloadChunkData unload = (UnloadChunkData) data;
                    sendChunkUnloadPacket(unload.getX(), unload.getZ());
                }
                break;

            case SPAWN_ENTITY:
                if (data instanceof SpawnEntityData) {
                    sendEntitySpawnPacketFromData((SpawnEntityData) data);
                }
                break;

            case DESTROY_ENTITIES:
                if (data instanceof DestroyEntitiesData) {
                    sendEntityDestroyPacket(((DestroyEntitiesData) data).getEntityIds());
                }
                break;

            case BLOCK_CHANGE:
                if (data instanceof BlockChangeData) {
                    sendBlockChangePacket((BlockChangeData) data);
                }
                break;

            case ENTITY_VELOCITY:
                if (data instanceof VelocityData) {
                    sendVelocityPacket(replayEntityId, (VelocityData) data);
                }
                break;

            case ENTITY_TELEPORT:
                if (data instanceof TeleportData) {
                    sendEntityTeleportPacket(replayEntityId, (TeleportData) data);
                }
                break;
        }
    }

    // Packet sending methods
    private void sendPlayerMovementPacket(double x, double y, double z, float yaw, float pitch) {
        // Move the spectator camera to follow the player
        Location newLoc = new Location(viewer.getWorld(), x, y, z, yaw, pitch);
        viewer.teleport(newLoc);

        // Also send entity teleport packet for the replay player entity if it exists
        if (replayEntityId != -1) {
            Vector3d position = new Vector3d(x, y, z);

            WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                    replayEntityId,
                    position,
                    yaw,
                    pitch,
                    false // onGround flag
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
        }
    }

    private void sendEntitySpawnPacket(InitialEntityData entityData) {
        try {
            String rawTypeName = entityData.getEntityType();
            if (rawTypeName == null) {
                plugin.getLogger().warning("Entity type is null for entityId " + entityData.getEntityId());
                return;
            }

            com.github.retrooper.packetevents.protocol.entity.type.EntityType entityType =
                    com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.getByName(rawTypeName.toLowerCase());

            if (entityType == null) {
                plugin.getLogger().warning("Unknown entity type \"" + rawTypeName + "\" for entityId " + entityData.getEntityId());
                return;
            }

            Vector3d position = new Vector3d(entityData.getX(), entityData.getY(), entityData.getZ());
            float pitch = entityData.getPitch();
            float yaw = entityData.getYaw();
            float headYaw = yaw;

            // Optional UUID (if you want deterministic UUIDs, replace with nameUUIDFromBytes or store it)
            Optional<UUID> uuid = Optional.of(UUID.randomUUID());

            // Optional velocity: if you don’t have velocity data, use Optional.empty()
            Optional<Vector3d> velocity = Optional.empty();

            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    entityData.getEntityId(),
                    uuid,
                    entityType,
                    position,
                    pitch,
                    yaw,
                    headYaw,
                    0,        // data
                    velocity
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);

            if (entityData.getCustomName() != null) {
                sendEntityMetadata(entityData.getEntityId(), entityData.getCustomName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn entity \"" + entityData.getEntityType()
                    + "\" for entityId " + entityData.getEntityId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendEntitySpawnPacketFromData(SpawnEntityData spawn) {
        try {
            com.github.retrooper.packetevents.protocol.entity.type.EntityType entityType =
                    com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.getByName(
                            spawn.getEntityType().toLowerCase()
                    );

            if (entityType != null) {
                Vector3d position = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());

                WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                        spawn.getEntityId(),
                        Optional.of(UUID.randomUUID()),  // or use a stable UUID
                        entityType,
                        position,
                        0f,   // pitch
                        0f,   // yaw
                        0f,   // headYaw
                        0,    // data
                        Optional.empty() // velocity
                );

                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            } else {
                plugin.getLogger().warning("Unknown entity type: " + spawn.getEntityType());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn entity: " + spawn.getEntityType() + " (" + e.getMessage() + ")");
            e.printStackTrace();
        }
    }

    private void sendEntityMetadata(int entityId, String customName) {
        // Send entity metadata for custom name
        // This is complex in PacketEvents 2.9.5, so we'll skip for now
        // In a full implementation, you'd create proper metadata packets
    }

    private void sendChunkDataPacket(ChunkData chunkData) {
        if (chunkData.getColumn() != null) {
            WrapperPlayServerChunkData chunkPacket = new WrapperPlayServerChunkData(chunkData.getColumn());
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, chunkPacket);
        }
    }

    private void sendChunkUnloadPacket(int x, int z) {
        WrapperPlayServerUnloadChunk unloadPacket = new WrapperPlayServerUnloadChunk(x, z);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, unloadPacket);
    }

    private void sendEntityDestroyPacket(int[] entityIds) {
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
    }

    private void sendBlockChangePacket(BlockChangeData blockChange) {
        try {
            StateType stateType = StateTypes.getByName(blockChange.getBlockType().toLowerCase());

            if (stateType != null) {
                WrappedBlockState blockState = WrappedBlockState.getDefaultState(stateType);

                WrapperPlayServerBlockChange blockChangePacket = new WrapperPlayServerBlockChange(
                        blockChange.getPosition().getPosition().toVector3i(),  // Position object, usually BlockPosition or #Vector3d
                        blockState
                );

                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, blockChangePacket);
            } else {
                plugin.getLogger().warning("Unknown block type: " + blockChange.getBlockType());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send block change: " + blockChange.getBlockType());
            e.printStackTrace();
        }
    }

    private void sendVelocityPacket(int entityId, VelocityData velocity) {
        try {
            Vector3d velVector = new Vector3d(velocity.getVelX(), velocity.getVelY(), velocity.getVelZ());

            WrapperPlayServerEntityVelocity velocityPacket = new WrapperPlayServerEntityVelocity(
                    entityId,
                    velVector
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, velocityPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send entity velocity for entityId " + entityId);
            e.printStackTrace();
        }
    }

    private void sendEntityTeleportPacket(int entityId, TeleportData teleport) {
        try {
            Vector3d position = new Vector3d(teleport.getX(), teleport.getY(), teleport.getZ());

            WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                    entityId,
                    position,
                    teleport.getYaw(),
                    teleport.getPitch(),
                    false  // onGround
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send entity teleport for entityId " + entityId);
            e.printStackTrace();
        }
    }

    private void sendInteractionEffect(int entityId, String action) {
        // Send particle effect or animation at entity location
        // This would require getting the entity's position and sending particle packets
        // For now, we'll send a simple message
        viewer.sendMessage("§7» §e" + action + " interaction");
    }

    private void sendItemUseAnimation() {
        // Send item use animation packet
        // This would typically be an entity animation packet
        viewer.sendMessage("§7» §eItem used");
    }

    private void sendDiggingEffect(com.github.retrooper.packetevents.protocol.world.Location blockPos, String action) {
        // Send block break animation/particles
        try {
            if ("START_DIGGING".equals(action) || "CANCEL_DIGGING".equals(action)) {
                // Send block break animation packet
                WrapperPlayServerBlockBreakAnimation breakAnim = new WrapperPlayServerBlockBreakAnimation(
                        replayEntityId, blockPos.getPosition().toVector3i(), (byte)(action.equals("START_DIGGING") ? 1 : -1)
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, breakAnim);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send digging effect");
        }
    }

    private Location extractLocationFromPacket(PacketData data) {
        if (data instanceof PlayerPositionData) {
            PlayerPositionData pos = (PlayerPositionData) data;
            return new Location(viewer.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        } else if (data instanceof PlayerPosRotData) {
            PlayerPosRotData posRot = (PlayerPosRotData) data;
            return new Location(viewer.getWorld(), posRot.getX(), posRot.getY(), posRot.getZ(), posRot.getYaw(), posRot.getPitch());
        }
        return null;
    }

    private void loadReplayChunks() {
        // Process initial chunk packets and send them to the viewer
        List<ReplayPacket> initialChunks = packets.stream()
                .filter(p -> p.getPacketType().equals(PacketType.INITIAL_CHUNK))
                .collect(Collectors.toList());

        viewer.sendMessage("§7Sending " + initialChunks.size() + " initial chunks...");

        for (ReplayPacket packet : initialChunks) {
            if (packet.getData() instanceof InitialChunkData) {
                InitialChunkData chunkData = (InitialChunkData) packet.getData();
                sendInitialChunkPacket(chunkData);
            }
        }

        // Also send runtime chunks from CHUNK_DATA packets
        for (Map.Entry<String, Column> entry : replay.getChunks().entrySet()) {
            String[] coords = entry.getKey().split(",");
            int x = Integer.parseInt(coords[0]);
            int z = Integer.parseInt(coords[1]);

            // Send chunk data using PacketEvents
            WrapperPlayServerChunkData chunkPacket = new WrapperPlayServerChunkData(entry.getValue());
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, chunkPacket);
        }

        viewer.sendMessage("§aChunks sent successfully!");
    }

    private void sendInitialChunkPacket(InitialChunkData chunkData) {
        try {
            ChunkDataSnapshot snapshot = chunkData.getChunkData();
            String chunkKey = snapshot.getX() + "," + snapshot.getZ();

            // Send chunk data based on server version
            if (this.serverVersion.isOlderThan(ServerVersion.V_1_13)) {
                sendLegacyChunkPacket(snapshot);
            } else {
                sendModernChunkPacket(snapshot);
            }

            // Track sent chunks for cleanup
            sentChunks.add(chunkKey);

            // Send block changes for precision (version-independent)
            sendChunkBlockChanges(snapshot);

            // Send tile entity data
            sendTileEntityData(snapshot);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send initial chunk packet: " + e.getMessage());
        }
    }

    private void sendLegacyChunkPacket(ChunkDataSnapshot snapshot) {
        try {
            // For 1.8-1.12, create a simplified chunk packet
            // Note: PacketEvents 2.9.5 should handle version compatibility internally
            Column column = createLegacyColumnFromSnapshot(snapshot);

            if (column != null) {
                WrapperPlayServerChunkData chunkPacket = new WrapperPlayServerChunkData(column);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, chunkPacket);
            } else {
                // Fallback: send individual block changes only
                viewer.sendMessage("§7[Legacy] Sending chunk " + snapshot.getX() + "," + snapshot.getZ() + " as blocks");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Legacy chunk packet failed, using block changes: " + e.getMessage());
        }
    }

    private void sendModernChunkPacket(ChunkDataSnapshot snapshot) {
        try {
            // For 1.13+ versions
            Column column = createModernColumnFromSnapshot(snapshot);

            if (column != null) {
                WrapperPlayServerChunkData chunkPacket = new WrapperPlayServerChunkData(column);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, chunkPacket);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Modern chunk packet failed, using block changes: " + e.getMessage());
        }
    }


    private Column createLegacyColumnFromSnapshot(ChunkDataSnapshot snapshot) {
        try {
            BaseChunk[] sections = buildSections(snapshot);
            NBTCompound[] blockEntities = buildBlockEntities(snapshot);

            // Legacy = no biome array, no heightmaps
            return new Column(snapshot.getX(), snapshot.getZ(), true,
                    sections, null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create legacy column: " + e.getMessage());
            return null;
        }
    }

    private Column createModernColumnFromSnapshot(ChunkDataSnapshot snapshot) {
        try {
            BaseChunk[] sections = buildSections(snapshot);

            return new Column(snapshot.getX(), snapshot.getZ(), true,
                    sections, null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create modern column: " + e.getMessage());
            return null;
        }
    }

    private Column createColumnFromSnapshot(ChunkDataSnapshot snapshot) {
        try {
            return createModernColumnFromSnapshot(snapshot);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create column from snapshot: " + e.getMessage());
            return new Column(snapshot.getX(), snapshot.getZ(), true,
                    new BaseChunk[0], null);
        }
    }

    private void sendChunkBlockChanges(ChunkDataSnapshot snapshot) {
        // Send individual block change packets for all non-air blocks
        // This method works across all versions as block change packets are consistent

        int blocksProcessed = 0;
        int maxBlocksPerTick = 100; // Prevent overwhelming the client

        for (Map.Entry<String, String> blockEntry : snapshot.getBlocks().entrySet()) {
            try {
                String[] coords = blockEntry.getKey().split(",");
                int x = Integer.parseInt(coords[0]) + (snapshot.getX() << 4);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]) + (snapshot.getZ() << 4);

                String[] materialData = blockEntry.getValue().split(":");
                String material = materialData[0];

                // Version-compatible block state creation
                if (sendVersionCompatibleBlockChange(x, y, z, material)) {
                    blocksProcessed++;
                }

                // Throttle block changes to prevent client lag
                if (blocksProcessed >= maxBlocksPerTick) {
                    break;
                }

            } catch (Exception e) {
                // Continue processing other blocks if one fails
                continue;
            }
        }

        if (blocksProcessed > 0) {
            viewer.sendMessage("§7Sent " + blocksProcessed + " blocks for chunk " +
                    snapshot.getX() + "," + snapshot.getZ());
        }
    }

    private boolean sendVersionCompatibleBlockChange(int x, int y, int z, String material) {
        try {
            // Convert material name to be compatible across versions
            String compatibleMaterial = getVersionCompatibleMaterial(material);

            StateType stateType = StateTypes.getByName(compatibleMaterial.toLowerCase());

            if (stateType != null) {
                WrappedBlockState blockState =
                        WrappedBlockState.getDefaultState(stateType);

                WrapperPlayServerBlockChange blockChangePacket = new WrapperPlayServerBlockChange(new Vector3i(x, y, z), blockState);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, blockChangePacket);
                return true;
            }
        } catch (Exception e) {
            // Silently fail for incompatible blocks
        }
        return false;
    }

    private String getVersionCompatibleMaterial(String material) {
        // Handle material name changes between versions
        if (this.serverVersion.isOlderThan(ServerVersion.V_1_13)) {
            return convertToLegacyMaterial(material);
        } else {
            return convertToModernMaterial(material);
        }
    }

    private String convertToLegacyMaterial(String material) {
        // Convert modern material names to legacy equivalents
        switch (material.toUpperCase()) {
            case "GRASS_BLOCK": return "GRASS";
            case "OAK_PLANKS": return "WOOD";
            case "OAK_LOG": return "LOG";
            case "COBBLESTONE_STAIRS": return "COBBLESTONE_STAIRS";
            case "WHITE_WOOL": return "WOOL";
            case "GRANITE": return "STONE";
            case "ANDESITE": return "STONE";
            case "DIORITE": return "STONE";
            default: return material;
        }
    }

    private String convertToModernMaterial(String material) {
        // Convert legacy material names to modern equivalents
        switch (material.toUpperCase()) {
            case "GRASS": return "GRASS_BLOCK";
            case "WOOD": return "OAK_PLANKS";
            case "LOG": return "OAK_LOG";
            case "WOOL": return "WHITE_WOOL";
            default: return material;
        }
    }

    private void sendTileEntityData(ChunkDataSnapshot snapshot) {
        // Send tile entity data packets
        for (TileEntityData tileEntity : snapshot.getTileEntities()) {
            try {
                int worldX = tileEntity.getX() + (snapshot.getX() << 4);
                int worldZ = tileEntity.getZ() + (snapshot.getZ() << 4);

                // Send tile entity update packet
                // This would depend on the tile entity type
                sendTileEntityPacket(worldX, tileEntity.getY(), worldZ, tileEntity.getType(), tileEntity.getData());

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send tile entity data: " + e.getMessage());
            }
        }
    }

    private void sendTileEntityPacket(int x, int y, int z, String type, Map<String, Object> data) {
        // Send tile entity packets based on type
        // This is complex and would require specific NBT data for each tile entity type
        // For now, we'll create basic representations

        try {
            com.github.retrooper.packetevents.protocol.world.Location location =
                    new com.github.retrooper.packetevents.protocol.world.Location(new Vector3d(x, y, z),0 ,0);

            // Different handling based on tile entity type
            switch (type.toUpperCase()) {
                case "SIGN":
                case "WALL_SIGN": {
                    // Send sign update packet
                    if (data.containsKey("lines")) {
                        String[] lines = (String[]) data.get("lines");
                        sendSignUpdatePacket(location, lines);
                    }
                    break;
                }
                case "CHEST": {
                    // Send chest inventory if needed
                    viewer.sendMessage("§7Chest at " + x + "," + y + "," + z);
                    break;
                }
                case "FURNACE": {
                    // Send furnace data if needed
                    viewer.sendMessage("§7Furnace at " + x + "," + y + "," + z);
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send tile entity packet: " + e.getMessage());
        }
    }

    private void sendSignUpdatePacket(com.github.retrooper.packetevents.protocol.world.Location location, String[] lines) {
        try {
            // Create sign update packet
            // Note: This is simplified - actual sign packets require NBT data
            viewer.sendMessage("§7Sign at " + location.getX() + "," + location.getY() + "," + location.getZ() +
                    ": " + String.join(" | ", lines));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send sign update packet: " + e.getMessage());
        }
    }

    private long getCurrentReplayTime() {
        if (isPaused) {
            return replayStartTime; // Return current position when paused
        }
        return replayStartTime + (long)((System.currentTimeMillis() - realStartTime) * playbackSpeed);
    }

    private void updateViewerHUD() {
        if (currentPacketIndex >= packets.size()) return;

        long currentTime = getCurrentReplayTime();
        long duration = replay.getDuration();
        long elapsed = currentTime - replayStartTime;

        String progress = formatTime(elapsed) + " / " + formatTime(duration);
        String status = isPaused ? "PAUSED" : "PLAYING";

        //TODO, actionbar maybe
        viewer.sendMessage("§e" + status + " §7| §f" + progress + " §7| §aSpeed: " + playbackSpeed + "x");
    }

    private BaseChunk[] buildSections(ChunkDataSnapshot snapshot) {
        BaseChunk[] sections = new BaseChunk[16];

        for (Map.Entry<String, String> entry : snapshot.getBlocks().entrySet()) {
            String[] key = entry.getKey().split(",");
            int bx = Integer.parseInt(key[0]);
            int by = Integer.parseInt(key[1]);
            int bz = Integer.parseInt(key[2]);

            int secY = by >> 4;
            BaseChunk section = sections[secY];
            if (section == null) {
                section = BaseChunk.create();  // PacketEvents factory method
                sections[secY] = section;
            }

            String[] parts = entry.getValue().split(":");
            String material = parts[0];
            String data = parts.length > 1 ? parts[1] : "";

            int stateId = lookupBlockStateId(material, data);
            section.set(bx, by & 15, bz, stateId);
        }

        return sections;
    }

    private NBTCompound[] buildBlockEntities(ChunkDataSnapshot snapshot) {
        List<NBTCompound> tags = new ArrayList<>();
        for (TileEntityData ted : snapshot.getTileEntities()) {
            NBTCompound tag = new NBTCompound();
            tag.setTag("x", new NBTInt(ted.getX()));
            tag.setTag("y", new NBTInt(ted.getY()));
            tag.setTag("z", new NBTInt(ted.getZ()));
            tag.setTag("id", new NBTString(ted.getType()));

            for (Map.Entry<String, Object> entry : ted.getData().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) {
                    tag.setTag(entry.getKey(), new NBTInt((Integer) value));
                } else if (value instanceof Long) {
                    tag.setTag(entry.getKey(), new NBTLong((Long) value));
                } else if (value instanceof Byte) {
                    tag.setTag(entry.getKey(), new NBTByte((Byte) value));
                } else if (value instanceof Double) {
                    tag.setTag(entry.getKey(), new NBTDouble((Double) value));
                } else if (value instanceof Float) {
                    tag.setTag(entry.getKey(), new NBTFloat((Float) value));
                } else if (value instanceof Boolean) {
                    tag.setTag(entry.getKey(), new NBTByte((byte) ((Boolean) value ? 1 : 0)));
                } else {
                    tag.setTag(entry.getKey(), new NBTString(value.toString()));
                }
            }

            tags.add(tag);
        }
        return tags.toArray(new NBTCompound[0]);
    }
    private int lookupBlockStateId(String material, String data) {
        WrappedBlockState state = WrappedBlockState.getByString(material.toLowerCase());
        return state != null ? state.getGlobalId() : 0;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Getters
    public boolean isPaused() { return isPaused; }
    public float getSpeed() { return playbackSpeed; }
    public int getCurrentPacketIndex() { return currentPacketIndex; }
    public int getTotalPackets() { return packets.size(); }
}
