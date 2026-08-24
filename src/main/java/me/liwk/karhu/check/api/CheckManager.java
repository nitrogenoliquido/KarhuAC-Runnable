package me.liwk.karhu.check.api;

import lombok.Getter;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.check.impl.combat.aimassist.*;
import me.liwk.karhu.check.impl.combat.aimassist.analysis.*;
import me.liwk.karhu.check.impl.combat.autoclicker.*;
import me.liwk.karhu.check.impl.combat.hitbox.HitboxA;
import me.liwk.karhu.check.impl.combat.killaura.*;
import me.liwk.karhu.check.impl.combat.reach.LagAbuse;
import me.liwk.karhu.check.impl.combat.reach.ReachA;
import me.liwk.karhu.check.impl.combat.velocity.VelocityA;
import me.liwk.karhu.check.impl.combat.velocity.VelocityB;
import me.liwk.karhu.check.impl.mouse.Mouse;
import me.liwk.karhu.check.impl.mouse.Sensitivity;
import me.liwk.karhu.check.impl.movement.elytra.ElytraA;
import me.liwk.karhu.check.impl.movement.fly.*;
import me.liwk.karhu.check.impl.movement.inventory.InventoryA;
import me.liwk.karhu.check.impl.movement.inventory.InventoryB;
import me.liwk.karhu.check.impl.movement.inventory.Refill;
import me.liwk.karhu.check.impl.movement.motion.MotionA;
import me.liwk.karhu.check.impl.movement.motion.MotionB;
import me.liwk.karhu.check.impl.movement.motion.MotionI;
import me.liwk.karhu.check.impl.movement.motion.MotionJ;
import me.liwk.karhu.check.impl.movement.omnisprint.OmniSprintA;
import me.liwk.karhu.check.impl.movement.speed.SpeedC;
import me.liwk.karhu.check.impl.movement.step.StepA;
import me.liwk.karhu.check.impl.movement.vehicle.VehicleFly;
import me.liwk.karhu.check.impl.movement.water.JesusA;
import me.liwk.karhu.check.impl.movement.water.JesusB;
import me.liwk.karhu.check.impl.packet.badpackets.*;
import me.liwk.karhu.check.impl.packet.timer.TimerA;
import me.liwk.karhu.check.impl.packet.timer.TimerB;
import me.liwk.karhu.check.impl.packet.timer.TimerC;
import me.liwk.karhu.check.impl.world.block.*;
import me.liwk.karhu.check.impl.world.ground.GroundA;
import me.liwk.karhu.check.impl.world.ground.GroundB;
import me.liwk.karhu.check.impl.world.ground.GroundC;
import me.liwk.karhu.check.impl.world.scaffold.*;
import me.liwk.karhu.check.type.PacketCheck;
import me.liwk.karhu.check.type.PositionCheck;
import me.liwk.karhu.check.type.RotationCheck;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.util.APICaller;
import me.liwk.karhu.util.benchmark.BenchmarkType;
import me.liwk.karhu.util.benchmark.KarhuBenchmarker;
import me.liwk.karhu.util.update.MovementUpdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CheckManager {

    private final Check[] checks;
    private final KarhuPlayer kp;

    @Getter
    private final List<RotationCheck> rotationChecks;
    @Getter
    private final List<PositionCheck> positionChecks;
    @Getter
    private final List<PacketCheck> packetChecks;

    // Filtered lists for fast runtime access
    private List<RotationCheck> activeRotationChecks;
    private List<PositionCheck> activePositionChecks;
    private List<PacketCheck> activePacketChecks;

    public CheckManager(final KarhuPlayer karhuPlayer, final Karhu karhu) {

        kp = karhuPlayer;

        final List<Check> c = Arrays.asList(new Check[]{

                /*
                --- Combat ---
                 */
                new AutoClickerA(karhuPlayer, karhu),
                new AutoClickerB(karhuPlayer, karhu),
                new AutoClickerC(karhuPlayer, karhu),
                new AutoClickerD(karhuPlayer, karhu),
                new AutoClickerE(karhuPlayer, karhu),
                new AutoClickerF(karhuPlayer, karhu),
                new AutoClickerG(karhuPlayer, karhu),
                new AutoClickerH(karhuPlayer, karhu),
                new AutoClickerI(karhuPlayer, karhu),
                new AutoClickerJ(karhuPlayer, karhu),
                new AutoClickerK(karhuPlayer, karhu),
                new AutoClickerL(karhuPlayer, karhu),
                new AutoClickerM(karhuPlayer, karhu),
                new AutoClickerO(karhuPlayer, karhu),
                new AutoClickerP(karhuPlayer, karhu),
                new AutoClickerQ(karhuPlayer, karhu),
                new AutoClickerR(karhuPlayer, karhu),
                new AutoClickerU(karhuPlayer, karhu),
                new AutoClickerW(karhuPlayer, karhu),

                //new ClickSniffer(karhuPlayer, karhu),

                new VelocityA(karhuPlayer, karhu),
                new VelocityB(karhuPlayer, karhu),

                new ReachA(karhuPlayer, karhu),
                new HitboxA(karhuPlayer, karhu),
                new LagAbuse(karhuPlayer, karhu),

                new AimAssistA(karhuPlayer, karhu),
                new AimAssistC(karhuPlayer, karhu),
                new AimAssistE(karhuPlayer, karhu),
                new AimAssistF(karhuPlayer, karhu),
                new AimAssistG(karhuPlayer, karhu),
                new AimAssistH(karhuPlayer, karhu),
                new AimAssistI(karhuPlayer, karhu),
                new AimAssistJ(karhuPlayer, karhu),
                //new AimAssistK(karhuPlayer, karhu),
                new AimAssistM(karhuPlayer, karhu),
                new AimAssistN(karhuPlayer, karhu),

                new AnalysisA(karhuPlayer, karhu),
                new AnalysisB(karhuPlayer, karhu),
                new AnalysisC(karhuPlayer, karhu),
                new AnalysisD(karhuPlayer, karhu),
                new AnalysisE(karhuPlayer, karhu),
                new AnalysisF(karhuPlayer, karhu),
                //new AnalysisG(karhuPlayer, karhu),
                //new AnalysisH(karhuPlayer, karhu),

                new KillauraA(karhuPlayer, karhu),
                new KillauraB(karhuPlayer, karhu),
                new KillauraC(karhuPlayer, karhu),
                new KillauraE(karhuPlayer, karhu),
                new KillauraF(karhuPlayer, karhu),
                new KillauraG(karhuPlayer, karhu),
                new KillauraH(karhuPlayer, karhu),
                new KillauraI(karhuPlayer, karhu),
                new KillauraJ(karhuPlayer, karhu),
                new KillauraK(karhuPlayer, karhu),
                new KillauraL(karhuPlayer, karhu),
                new KillauraM(karhuPlayer, karhu),
                new KillauraN(karhuPlayer, karhu),
                new KillauraO(karhuPlayer, karhu),


                /*
                --- Movement ---
                 */
                new FlyA(karhuPlayer, karhu),
                new FlyB(karhuPlayer, karhu),
                new FlyC(karhuPlayer, karhu),
                new FlyD(karhuPlayer, karhu),
                new FlyE(karhuPlayer, karhu),
                new FlyF(karhuPlayer, karhu),

                new ElytraA(karhuPlayer, karhu),

                new VehicleFly(karhuPlayer, karhu),

                new MotionA(karhuPlayer, karhu),
                new MotionB(karhuPlayer, karhu),
                new MotionI(karhuPlayer, karhu),
                new MotionJ(karhuPlayer, karhu),

                new StepA(karhuPlayer, karhu),

                new SpeedC(karhuPlayer, karhu),

                new OmniSprintA(karhuPlayer, karhu),

                new JesusA(karhuPlayer, karhu),
                new JesusB(karhuPlayer, karhu),

                new InventoryA(karhuPlayer, karhu),
                new InventoryB(karhuPlayer, karhu),

                new Refill(karhuPlayer, karhu),

                /*
                --- Packet ---
                 */
                new BadPacketsA(karhuPlayer, karhu),
                new BadPacketsB(karhuPlayer, karhu),
                new BadPacketsC(karhuPlayer, karhu),
                new BadPacketsD(karhuPlayer, karhu),
                new BadPacketsE(karhuPlayer, karhu),
                new BadPacketsF(karhuPlayer, karhu),
                new BadPacketsG(karhuPlayer, karhu),
                new BadPacketsH(karhuPlayer, karhu),
                new BadPacketsJ(karhuPlayer, karhu),
                new BadPacketsK(karhuPlayer, karhu),
                new BadPacketsM(karhuPlayer, karhu),
                new BadPacketsN(karhuPlayer, karhu),
                new BadPacketsO(karhuPlayer, karhu),
                new BadPacketsQ(karhuPlayer, karhu),
                new BadPacketsR(karhuPlayer, karhu),
//
                new TimerA(karhuPlayer, karhu),
                new TimerB(karhuPlayer, karhu),
                new TimerC(karhuPlayer, karhu),

                /*
                --- World ---
                 */

                new ScaffoldA(karhuPlayer, karhu),
                new ScaffoldB(karhuPlayer, karhu),
                new ScaffoldC(karhuPlayer, karhu),
                new ScaffoldF(karhuPlayer, karhu),
                new ScaffoldG(karhuPlayer, karhu),
                new ScaffoldH(karhuPlayer, karhu),
                new ScaffoldI(karhuPlayer, karhu),
                new ScaffoldJ(karhuPlayer, karhu),
                new ScaffoldK(karhuPlayer, karhu),
                new ScaffoldL(karhuPlayer, karhu),
                new ScaffoldM(karhuPlayer, karhu),
                new ScaffoldN(karhuPlayer, karhu),
                new ScaffoldO(karhuPlayer, karhu),
                new ScaffoldP(karhuPlayer, karhu),
                new ScaffoldQ(karhuPlayer, karhu),
                new ScaffoldR(karhuPlayer, karhu),
                new ScaffoldS(karhuPlayer, karhu),
                new ScaffoldT(karhuPlayer, karhu),
                new FastBreakA(karhuPlayer, karhu),
                new FastBreakB(karhuPlayer, karhu),
                new FastBreakC(karhuPlayer, karhu),

                //new GhostBreak(karhuPlayer, karhu),
                new BlockReach(karhuPlayer, karhu),
                new NoLookBreak(karhuPlayer, karhu),

                new GroundA(karhuPlayer, karhu),
                new GroundB(karhuPlayer, karhu),
                new GroundC(karhuPlayer, karhu),

                /*
                --- Other ---
                 */
                new Sensitivity(karhuPlayer, karhu),
                new Mouse(karhuPlayer, karhu),
        });

        this.checks = c.toArray(new Check[]{});

        packetChecks = getAllOfType(PacketCheck.class);
        positionChecks = getAllOfType(PositionCheck.class);
        rotationChecks = getAllOfType(RotationCheck.class);

        // Initialize active check lists
        rebuildActiveCheckLists();
    }

    /**
     * Rebuilds the active check lists based on current enabled state
     * Call this when check states are updated
     */
    public void rebuildActiveCheckLists() {
        activePacketChecks = new ArrayList<>();
        activePositionChecks = new ArrayList<>();
        activeRotationChecks = new ArrayList<>();

        for (PacketCheck check : packetChecks) {
            if (check.isSilent() || Karhu.getInstance().getCheckState().isEnabled(check.getName())) {
                activePacketChecks.add(check);
            }
        }

        for (PositionCheck check : positionChecks) {
            Check<MovementUpdate> c = check;
            if (c.isSilent() || Karhu.getInstance().getCheckState().isEnabled(c.getName())) {
                activePositionChecks.add(check);
            }
        }

        for (RotationCheck check : rotationChecks) {
            Check<MovementUpdate> c = check;
            if (c.isSilent() || Karhu.getInstance().getCheckState().isEnabled(c.getName())) {
                activeRotationChecks.add(check);
            }
        }
    }

    /**
     * Get the active filtered list based on check type
     */
    public List<?> getActiveChecksForType(List<?> originalList) {
        if (originalList == packetChecks) {
            return activePacketChecks;
        } else if (originalList == positionChecks) {
            return activePositionChecks;
        } else if (originalList == rotationChecks) {
            return activeRotationChecks;
        }
        return originalList;
    }

    public void runChecks(List<?> paskat, Object e, Object packet) {

        boolean apiAvailable = Karhu.isAPIAvailable();

        // Use the pre-filtered active check list
        List<?> activeChecks = getActiveChecksForType(paskat);

        long start = System.nanoTime();
        for (final Check c : (List<Check<?>>) activeChecks) {
            //Fire pre check events
            if (apiAvailable) {
                //kp.checkExempt(c.getSubCategory());
                if (APICaller.callPreCheck(c.getCheckInfo(), c, kp.getBukkitPlayer(), packet)) {
                    c.setDidFail(false);

                    c.handle(e);

                    //Fire post check events
                    APICaller.callPostCheck(kp.getBukkitPlayer(), c.getCheckInfo(), c, packet);
                }
            } else {
                c.setDidFail(false);
                c.handle(e);
            }
        }
        KarhuBenchmarker.getProfileData(BenchmarkType.CHECKS)
                .insertResult(start, System.nanoTime());

    }

    public Check[] getChecks() {
        return this.checks;
    }

    public int checkAmount() {
        return this.checks.length;
    }

    public <T> T getCheck(Class<T> clazz) {
        return (T) Arrays.stream(checks)
                .filter(check -> check.getClass() == clazz)
                .findFirst()
                .orElse(null);
    }

    private <T> List<T> getAllOfType(final Class<T> clazz) {
        return (List<T>) Arrays.stream(checks).filter(clazz::isInstance).collect(Collectors.toList());
    }
}
