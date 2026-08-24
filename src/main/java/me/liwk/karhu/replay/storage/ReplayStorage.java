package me.liwk.karhu.replay.storage;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.google.gson.*;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.replay.packet.PacketData;
import me.liwk.karhu.replay.session.ReplaySession;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ReplayStorage {

    private final Karhu plugin;
    private final Path replaysDirectory;
    private final Gson gson;

    public ReplayStorage(Karhu plugin) {
        this.plugin = plugin;
        this.replaysDirectory = this.plugin.getDataFolder().toPath().resolve("replays");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(PacketData.class, new PacketDataAdapter())
                .registerTypeAdapter(Column.class, new ChunkColumnAdapter())
                .create();

        // Create replays directory
        try {
            Files.createDirectories(replaysDirectory);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to create replays directory: " + e.getMessage());
        }
    }

    public void saveReplay(ReplaySession session) throws IOException {
        String fileName = session.getReplayId() + ".json";
        Path filePath = replaysDirectory.resolve(fileName);

        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            gson.toJson(session, writer);
        }
    }

    public ReplaySession loadReplay(String replayId) {
        Path filePath = replaysDirectory.resolve(replayId + ".json");

        if (!Files.exists(filePath)) {
            return null;
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            return gson.fromJson(reader, ReplaySession.class);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load replay " + replayId + ": " + e.getMessage());
            return null;
        }
    }

    public List<ReplayInfo> getAvailableReplays() {
        try {
            return Files.list(replaysDirectory)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(this::createReplayInfo)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp())) // Newest first
                    .collect(Collectors.toList());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to list replays: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void deleteReplay(String replayId) {
        Path filePath = replaysDirectory.resolve(replayId + ".json");
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete replay " + replayId + ": " + e.getMessage());
        }
    }

    private ReplayInfo createReplayInfo(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String replayId = fileName.substring(0, fileName.lastIndexOf('.'));

            // Quick read to get basic info without loading full replay
            try (FileReader reader = new FileReader(filePath.toFile())) {
                Map<String, Object> data = gson.fromJson(reader, Map.class);
                return new ReplayInfo(
                        replayId,
                        (String) data.get("playerName"),
                        ((Double) data.get("startTime")).longValue(),
                        (String) data.get("reason"),
                        Files.size(filePath)
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read replay info from " + filePath + ": " + e.getMessage());
            return null;
        }
    }
}

//TODO
