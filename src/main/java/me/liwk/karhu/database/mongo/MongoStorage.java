package me.liwk.karhu.database.mongo;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.check.api.*;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.database.Storage;
import me.liwk.karhu.manager.ConfigManager;
import me.liwk.karhu.util.MathUtil;
import me.liwk.karhu.util.NetUtil;
import me.liwk.karhu.util.task.Tasker;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoStorage implements Storage {

    private final CodecRegistry pojoCodecRegistry = fromRegistries(com.mongodb.MongoClient.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    private MongoCollection<ViolationX> loggedViolations;
    private MongoCollection<BanX> loggedBans;
    private MongoCollection<AlertsX> loggedStatus;
    private MongoCollection<BanWaveX> loggedBanwavePlayers;
    private final ConcurrentLinkedQueue<ViolationX> violations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BanX> bans = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BanWaveX> banWaveQueue = new ConcurrentLinkedQueue<>();

    public String host, database, username, password;
    public int port;
    public boolean auth;

    public MongoStorage() {
        ConfigManager cfg = Karhu.getInstance().getConfigManager();
        host = cfg.getConfig().getString("mongo.host");
        port = cfg.getConfig().getInt("mongo.port");
        database = cfg.getConfig().getString("mongo.database");
        auth = cfg.getConfig().getBoolean("mongo.authentication.enabled");
        username = cfg.getConfig().getString("mongo.authentication.username");
        password = cfg.getConfig().getString("mongo.authentication.password");
    }

    @Override
    public void init() {
        MongoClient client;

        if(auth) {
            MongoCredential credentials = MongoCredential.createCredential(username, database, password.toCharArray());
            client = new MongoClient(new ServerAddress(host, port), credentials, MongoClientOptions.builder().codecRegistry(pojoCodecRegistry).build());
        } else {
            client = new MongoClient(new ServerAddress(host, port), MongoClientOptions.builder().codecRegistry(pojoCodecRegistry).build());
        }

        MongoDatabase mongodb = client.getDatabase(database);

        loggedViolations = mongodb.getCollection("violations", ViolationX.class);
        loggedBans = mongodb.getCollection("bans", BanX.class);
        loggedStatus = mongodb.getCollection("status", AlertsX.class);
        loggedBanwavePlayers = mongodb.getCollection("banwave", BanWaveX.class);

        new Thread(() -> {
            while (Karhu.getInstance() != null && Karhu.getInstance().isEnabled()) {
                try {
                    NetUtil.sleep(10000);
                    if (violations.isEmpty() && bans.isEmpty() && banWaveQueue.isEmpty()) continue;
                    if (!violations.isEmpty()) {
                        try {
                            loggedViolations.insertMany(new ArrayList<>(violations));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        violations.clear();
                    }
                    if (!bans.isEmpty()) {
                        try {
                            loggedBans.insertMany(new ArrayList<>(bans));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        bans.clear();
                    }
                    if (!banWaveQueue.isEmpty()) {
                        try {
                            loggedBanwavePlayers.insertMany(new ArrayList<>(banWaveQueue));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        banWaveQueue.clear();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "KarhuMongoCommitter").start();
    }

    @Override
    public void addAlert(ViolationX violation) {
        violations.add(violation);
    }

    @Override
    public void addBan(BanX ban) {
        bans.add(ban);
    }

    @Override
    public void setAlerts(String uuid, int status) {
        loggedStatus.replaceOne(eq("player", uuid), new AlertsX(uuid, status));
    }

    @Override
    public boolean getAlerts(String uuid) {
        AlertsX alertsX = loggedStatus.find(eq("player", uuid)).limit(1).first();
        if(alertsX != null) {
            return MathUtil.getIntAsBoolean(alertsX.status);
        } else {
            loggedStatus.replaceOne(eq("player", uuid), new AlertsX(uuid, 1));
        }
        return true;
    }

    @Override
    public void loadActiveViolations(String uuid, KarhuPlayer data) {
        Tasker.taskAsync(() -> {
            List<ViolationX> violations = new ArrayList<>();
            Map<String, Integer> validVls = new HashMap<>();
            loggedViolations.find(eq("player", uuid))
                    .sort(new Document("time", -1))
                    .forEach((Consumer<? super ViolationX>) violations::add);

            for(ViolationX v : violations) {
                if(System.currentTimeMillis() - v.time < 200L * 1000L) {
                    if(!validVls.containsKey(v.type)) {
                        validVls.put(v.type, v.vl);
                    } else {
                        if(v.vl > validVls.get(v.type)) {
                            validVls.replace(v.type, v.vl);
                        }
                    }
                }
            }

            for(Check c : data.getCheckManager().getChecks()) {
                if(validVls.containsKey(c.getCheckInfo().name())) {
                    data.addViolations(c, validVls.get(c.getName()));
                    final int vl = data.getViolations(c, (100L * 1000));
                    data.setCheckVl(vl, c);
                }
            }
        });
    }

    @Override
    public List<ViolationX> getViolations(String uuid, Check type, int page, int limit, long from, long to) {
        List<ViolationX> violations = new ArrayList<>();
        loggedViolations.find(eq("player", uuid))
                .skip(page * limit).limit(limit)
                .sort(new Document("time", -1))
                .forEach((Consumer<? super ViolationX>) violations::add);
        return violations;
    }

    @Override
    public int getViolationAmount(String uuid) {
        AtomicInteger violations = new AtomicInteger();
        loggedViolations.find(eq("player", uuid))
                .sort(new Document("time", -1))
                .forEach((Consumer<? super ViolationX>) v -> violations.incrementAndGet());
        return violations.get();
    }

    @Override
    public List<ViolationX> getAllViolations(String uuid) {
        List<ViolationX> violations = new ArrayList<>();
        loggedViolations.find(eq("player", uuid))
                .sort(new Document("time", -1))
                .forEach((Consumer<? super ViolationX>) violations::add);
        return violations;
    }

    @Override
    public List<String> getBanwaveList() {
        List<String> players = new ArrayList<>();
        loggedBanwavePlayers.find().forEach((Block<? super BanWaveX>) huora -> players.add(huora.player));
        return players;
    }

    @Override
    public int getAllViolationsInStorage() {
        List<ViolationX> violations = new ArrayList<>();
        loggedViolations.find().forEach((Consumer<? super ViolationX>) violations::add);
        return violations.size();
    }

    @Override
    public List<BanX> getRecentBans() {
        List<BanX> bans = new ArrayList<>();
        loggedBans.find().limit(10).forEach((Consumer<? super BanX>) bans::add);
        return bans;
    }

    @Override
    public void purge(String uuid, boolean all) {
        if(all) {
            loggedViolations.drop();
        } else {
            loggedViolations.deleteMany(eq("player", uuid));
        }
    }
    @Override
    public void addToBanWave(BanWaveX bwRequest) {
        if(!this.isInBanwave(bwRequest.player)) {
            banWaveQueue.add(bwRequest);
        }
    }

    @Override
    public boolean isInBanwave(String uuid) {
        BanWaveX bw = loggedBanwavePlayers.find(eq("player", uuid)).first();

        return bw != null;
    }

    @Override
    public void removeFromBanWave(String uuid) {
        loggedBanwavePlayers.findOneAndDelete(eq("player", uuid));
    }

}
