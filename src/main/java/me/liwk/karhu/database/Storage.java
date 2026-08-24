package me.liwk.karhu.database;

import me.liwk.karhu.check.api.BanWaveX;
import me.liwk.karhu.check.api.BanX;
import me.liwk.karhu.check.api.Check;
import me.liwk.karhu.check.api.ViolationX;
import me.liwk.karhu.data.KarhuPlayer;

import java.util.List;

public interface Storage {
    void init();

    void addAlert(ViolationX violation);

    void addBan(BanX violation);

    List<ViolationX> getViolations(String uuid, Check type, int page, int limit, long from, long to);

    List<ViolationX> getAllViolations(String uuid);

    List<String> getBanwaveList();

    boolean isInBanwave(String uuid);

    void addToBanWave(BanWaveX bwRequest);

    void removeFromBanWave(String uuid);

    int getViolationAmount(String uuid);

    void loadActiveViolations(String uuid, KarhuPlayer data);

    void purge(String uuid, boolean all);

    int getAllViolationsInStorage();

    List<BanX> getRecentBans();


    void setAlerts(String uuid, int status);

    boolean getAlerts(String uuid);

    //Map<Check, Integer> getHighestViolations(UUID uuid, Check type, long from, long to);
}
