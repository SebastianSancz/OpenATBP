package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class PeppermintButler extends UserActor {
    private int timeStopped = 0;
    private boolean qActive = false;
    private boolean stopPassive = false;
    private boolean ultActive = false;
    private boolean wActive = false;
    private boolean interruptW = false;
    private long qStartTime = 0;
    private long ultStartTime = 0;
    private AtomicInteger wRunTime;
    private Point2D passiveLocation = null;
    private static final int ULT_DURATION = 7000;

    private enum Form {
        NORMAL,
        FERAL
    }

    private Form form = Form.NORMAL;

    public PeppermintButler(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("healthRegen") && getState(ActorState.STEALTH))
            return super.getPlayerStat("healthRegen") + (this.maxHealth * 0.02d);
        else if (stat.equalsIgnoreCase("attackSpeed") && this.form == Form.FERAL) {
            double currentAttackSpeed = super.getPlayerStat("attackSpeed");
            double modifier = (this.getStat("attackSpeed") * 0.3d);
            return currentAttackSpeed - modifier < 500 ? 500 : currentAttackSpeed - modifier;
        } else if (stat.equalsIgnoreCase("attackDamage") && this.form == Form.FERAL)
            return super.getPlayerStat("attackDamage") + (this.getStat("attackDamage") * 0.3d);
        return super.getPlayerStat(stat);
    }

    @Override
    public void handleSwapToPoly(int duration) {
        super.handleSwapToPoly(duration);
        if (this.form == Form.FERAL) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "ultHandL");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "ultHandR");
        }
    }

    @Override
    public void handleSwapFromPoly() {
        if (this.form == Form.FERAL) {
            swapAsset(true);
            int timeElapsed = (int) (System.currentTimeMillis() - this.ultStartTime);
            int remainingTime = ULT_DURATION - timeElapsed;
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "marceline_beast_crit_hand",
                    remainingTime,
                    this.id + "ultHandL",
                    true,
                    "Bip001 L Hand",
                    true,
                    false,
                    this.team);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "marceline_beast_crit_hand",
                    remainingTime,
                    this.id + "ultHandR",
                    true,
                    "Bip001 R Hand",
                    true,
                    false,
                    this.team);
        } else {
            swapAsset(false);
        }
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        this.stopPassive = true;
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "pepbut_punch_sparks",
                1000,
                this.id,
                true,
                "",
                true,
                false,
                this.team);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        this.stopPassive = true;
        return super.damaged(a, damage, attackData);
    }

    @Override
    protected boolean canRegenHealth() {
        if (this.isStopped()
                && !qActive
                && !stopPassive
                && this.form != Form.FERAL
                && !isCapturingAltar()
                && !dead) return true;
        return super.canRegenHealth();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultActive) {
            endUlt();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "ultHandL");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "ultHandR");
        }
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultActive) this.addEffect("speed", this.getStat("speed") * 0.4, 150);
        if (this.passiveLocation != null && this.location.distance(this.passiveLocation) > 0.001d) {
            this.stopPassive = true;
            this.timeStopped = 0;
        }
        if (this.ultActive && System.currentTimeMillis() - this.ultStartTime >= ULT_DURATION) {
            endUlt();
        }
        if (this.qActive && System.currentTimeMillis() - this.qStartTime >= 5000) {
            this.qActive = false;
        }
        if (this.isStopped()
                && !qActive
                && !stopPassive
                && this.form != Form.FERAL
                && !isCapturingAltar()
                && !dead) {
            this.passiveLocation = this.location;
            timeStopped += 100;
            if (this.timeStopped >= 1750 && !this.getState(ActorState.STEALTH)) {
                this.setState(ActorState.STEALTH, true);
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "passive", 500, false);
                Runnable delayAnimation =
                        () -> {
                            if (this.timeStopped >= 1750) {
                                ExtensionCommands.actorAnimate(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        "passive_idle",
                                        1000 * 60 * 15,
                                        true);
                                ExtensionCommands.playSound(
                                        this.parentExt,
                                        this.player,
                                        this.id,
                                        "sfx_pepbut_invis_hide",
                                        this.location);
                                this.setState(ActorState.REVEALED, false);
                                this.setState(ActorState.INVISIBLE, true);
                            }
                        };
                parentExt.getTaskScheduler().schedule(delayAnimation, 500, TimeUnit.MILLISECONDS);
                this.updateStatMenu("healthRegen");
            }
        } else {
            this.timeStopped = 0;
            if (this.stopPassive) this.stopPassive = false;
            this.passiveLocation = null;
            if (this.getState(ActorState.STEALTH)) {
                String animation = "idle";
                if (this.location.distance(this.movementLine.getP2()) > 0.1d) animation = "run";
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, animation, 1, false);
                this.setState(ActorState.STEALTH, false);
                this.setState(ActorState.INVISIBLE, false);
                if (!this.getState(ActorState.BRUSH)) this.setState(ActorState.REVEALED, true);
                this.updateStatMenu("healthRegen");
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_pepbut_invis_reveal",
                        this.location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "statusEffect_immunity",
                        2000,
                        id + "_Immunity",
                        true,
                        "displayBar",
                        false,
                        false,
                        team);
                this.addState(ActorState.IMMUNITY, 0d, 2000);
            }
        }
        if (this.qActive && this.currentHealth <= 0) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_qRing");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_aoe");
            this.qActive = false;
        }
        if (this.qActive) {
            for (Actor a :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.location,
                            3f)) {
                if (this.isNonStructure(a)) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell1");
                    double damage = this.getSpellDamage(spellData) / 10d;
                    a.addToDamageQueue(this, damage, spellData, true);
                    a.addState(ActorState.BLINDED, 0d, 500);
                }
            }
        }
        if (this.wActive && this.cancelDashEndAttack()) {
            this.interruptW = true;
        }
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        this.stopPassive = true;
        switch (ability) {
            case 1:
                canCast[0] = false;
                this.qStartTime = System.currentTimeMillis();
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_3",
                        5000,
                        this.id + "_qRing",
                        true,
                        "",
                        true,
                        true,
                        this.team);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "pepbut_aoe",
                        5000,
                        this.id + "_aoe",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_pepbut_aoe", this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                qActive = true;
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new PeppermintAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                canCast[1] = false;
                this.stopMoving();
                this.setCanMove(false);
                this.wActive = true;
                double time = dest.distance(this.location) / 15d;
                this.wRunTime = new AtomicInteger((int) (time * 1000));
                String hohoVoPrefix =
                        (this.avatar.contains("zombie")) ? "pepbut_zombie_" : "pepbut_";
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_" + hohoVoPrefix + "hoho",
                        this.location);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_2.5",
                        this.id + "_wRing",
                        wRunTime.get() + 500,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
                Runnable animationDelay =
                        () -> {
                            if (!hasDashInterrupingCC()) {
                                ExtensionCommands.playSound(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        "sfx_pepbut_dig",
                                        this.location);
                                ExtensionCommands.actorAnimate(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        "spell2b",
                                        wRunTime.get(),
                                        true);
                                ExtensionCommands.createActorFX(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        "pepbut_dig_rocks",
                                        wRunTime.get(),
                                        this.id + "_digRocks",
                                        true,
                                        "",
                                        true,
                                        false,
                                        this.team);
                                this.dash(dest, true, 15d);
                            } else wRunTime.set(0);
                            parentExt
                                    .getTaskScheduler()
                                    .schedule(
                                            new PeppermintAbilityHandler(
                                                    ability, spellData, cooldown, gCooldown, dest),
                                            wRunTime.get(),
                                            TimeUnit.MILLISECONDS);
                        };

                parentExt.getTaskScheduler().schedule(animationDelay, 500, TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                break;
            case 3:
                canCast[2] = false;
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_pepbut_feral", this.location);
                Runnable delay =
                        () -> {
                            this.ultActive = true;
                            this.ultStartTime = System.currentTimeMillis();
                            this.attackCooldown = 0;
                            this.form = Form.FERAL;
                            String[] statsToUpdate = {"speed", "attackSpeed", "attackDamage"};
                            this.updateStatMenu(statsToUpdate);
                            String hissVoPrefix =
                                    (this.avatar.contains("zombie")) ? "pepbut_zombie_" : "pepbut_";
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "vo/vo_" + hissVoPrefix + "feral_hiss",
                                    this.location);
                            swapAsset(true);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "marceline_beast_crit_hand",
                                    ULT_DURATION,
                                    this.id + "ultHandL",
                                    true,
                                    "Bip001 L Hand",
                                    true,
                                    false,
                                    this.team);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "marceline_beast_crit_hand",
                                    ULT_DURATION,
                                    this.id + "ultHandR",
                                    true,
                                    "Bip001 R Hand",
                                    true,
                                    false,
                                    this.team);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "pepbut_feral_explosion",
                                    1000,
                                    this.id + "_ultExplosion",
                                    false,
                                    "",
                                    false,
                                    false,
                                    this.team);
                            this.addState(ActorState.SILENCED, 0d, ULT_DURATION);
                            if (this.qActive) {
                                this.qActive = false;
                                ExtensionCommands.removeFx(
                                        this.parentExt, this.room, this.id + "_qRing");
                                ExtensionCommands.removeFx(
                                        this.parentExt, this.room, this.id + "_aoe");
                            }
                        };
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "e", true, getReducedCooldown(cooldown), gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new PeppermintAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                parentExt.getTaskScheduler().schedule(delay, castDelay, TimeUnit.MILLISECONDS);
                break;
        }
    }

    private void swapAsset(boolean toFeral) {
        String bundle = toFeral ? "pepbut_feral" : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
    }

    private boolean hasDashInterrupingCC() {
        ActorState[] states = {
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.POLYMORPH,
            ActorState.STUNNED,
            ActorState.SILENCED
        };
        for (ActorState state : states) {
            if (this.getState(state)) return true;
        }
        return false;
    }

    private void endUlt() {
        this.form = Form.NORMAL;
        this.ultActive = false;
        if (!this.getState(ActorState.POLYMORPH)) { // poly asset swap handled elsewhere
            swapAsset(false);
        }
        String[] statsToUpdate = {"speed", "attackSpeed", "attackDamage"};
        updateStatMenu(statsToUpdate);
    }

    private boolean isCapturingAltar() {
        Point2D currentAltar = null;
        Point2D[] altarLocations;

        if (!this.room.getGroupId().equalsIgnoreCase("practice")) {
            altarLocations = new Point2D[3];
            altarLocations[0] = new Point2D.Float(MapData.L2_TOP_ALTAR[0], MapData.L2_TOP_ALTAR[1]);
            altarLocations[1] = new Point2D.Float(0f, 0f);
            altarLocations[2] = new Point2D.Float(MapData.L2_BOT_ALTAR[0], MapData.L2_BOT_ALTAR[1]);
        } else {
            altarLocations = new Point2D[2];
            altarLocations[0] = new Point2D.Float(0f, MapData.L1_AALTAR_Z);
            altarLocations[1] = new Point2D.Float(0f, MapData.L1_DALTAR_Z);
        }
        for (Point2D altarLocation : altarLocations) {
            if (this.location.distance(altarLocation) <= 2f) {
                currentAltar = altarLocation;
                break;
            }
        }
        if (currentAltar != null) {
            int altarStatus =
                    this.parentExt.getRoomHandler(this.room.getName()).getAltarStatus(currentAltar);
            return altarStatus < 10;
        }
        return false;
    }

    private class PeppermintAbilityHandler extends AbilityRunnable {

        public PeppermintAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            int W_CAST_DELAY = wRunTime.get() + 500;
            Runnable enableWCasting = () -> canCast[1] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableWCasting,
                            getReducedCooldown(cooldown) - W_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            wActive = false;
            canMove = true;
            if (!interruptW && getHealth() > 0) {
                String beholdVoPrefix = (avatar.contains("zombie")) ? "pepbut_zombie_" : "pepbut_";
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_pepbut_dig_emerge", this.dest);
                ExtensionCommands.playSound(
                        parentExt, room, id, "vo/vo_" + beholdVoPrefix + "behold", location);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell2c", 500, false);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "pepbut_dig_explode",
                        id + "_wExplode",
                        1500,
                        (float) location.getX(),
                        (float) location.getY(),
                        false,
                        team,
                        0f);
                for (Actor a :
                        Champion.getActorsInRadius(
                                parentExt.getRoomHandler(room.getName()), location, 2.5f)) {
                    if (isNonStructure(a)) {
                        a.addToDamageQueue(
                                PeppermintButler.this, getSpellDamage(spellData), spellData, false);
                        a.addState(ActorState.STUNNED, 0d, 1500);
                    }
                }
            } else if (interruptW) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
            }
            interruptW = false;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }
}
