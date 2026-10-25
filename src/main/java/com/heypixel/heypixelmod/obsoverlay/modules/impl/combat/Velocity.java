package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.mixin.O.accessors.ClientboundMoveEntityPacketAccessor;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderSkia;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRespawn;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Stuck;
import com.heypixel.heypixelmod.obsoverlay.ui.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.MoveUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.Rotation;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.skia.Skia;
import com.heypixel.heypixelmod.obsoverlay.utils.skia.font.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.vector.Vector3d;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.DragValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "Velocity", cnName = "反击退", description = "Reduces knockback.", category = Category.COMBAT)
public class Velocity extends Module {

    public static final int mainColor = new Color(150, 45, 45, 255).getRGB();

    private final ModeValue mode = ValueBuilder.create(this, "Mode")
            .setModes("Legit", "Buffer")
            .setDefaultModeIndex(0)
            .setOnUpdate(value -> reset())
            .build()
            .getModeValue();

    private boolean isBufferMode() {
        return this.mode.isCurrentMode("Buffer");
    }

    private boolean isLegitMode() {
        return this.mode.isCurrentMode("Legit");
    }

    private final BooleanValue debug = ValueBuilder.create(this, "Verbose Output")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue jumpReset = ValueBuilder.create(this, "Jump reset")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue rotate = ValueBuilder.create(this, "Rotate")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue movementOverride = ValueBuilder.create(this, "Movement override")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue autoSprint = ValueBuilder.create(this, "Auto sprint")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue mode19Plus = ValueBuilder.create(this, "1.9+ Mode")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue smart = ValueBuilder.create(this, "Redefine Motion")
            .setVisibility(() -> isBufferMode() && !mode19Plus.getCurrentValue())
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final FloatValue attack = ValueBuilder.create(this, "Attack amount")
            .setVisibility(() -> isBufferMode() && !smart.getCurrentValue() && !mode19Plus.getCurrentValue())
            .setDefaultFloatValue(5F)
            .setFloatStep(1F)
            .setMinFloatValue(1F)
            .setMaxFloatValue(6F)
            .build()
            .getFloatValue();

    private final FloatValue targetMotion = ValueBuilder.create(this, "Target Motion")
            .setVisibility(() -> isBufferMode() && (smart.getCurrentValue() || mode19Plus.getCurrentValue()))
            .setDefaultFloatValue(0.10F)
            .setFloatStep(0.05F)
            .setMinFloatValue(0.05F)
            .setMaxFloatValue(0.45F)
            .build()
            .getFloatValue();

    private final BooleanValue ignoreNoSprinting = ValueBuilder.create(this, "No Sprinting")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue ignoreInLava = ValueBuilder.create(this, "In Lava")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue ignoreInWater = ValueBuilder.create(this, "In Water")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue ignoreOnFire = ValueBuilder.create(this, "On Fire")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue ignoreS08 = ValueBuilder.create(this, "S08 Cooldown")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue delayTillGround = ValueBuilder.create(this, "Delay till ground")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue multiTarget = ValueBuilder.create(this, "Multi target")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue renderServerPos = ValueBuilder.create(this, "Render Server Pos")
            .setVisibility(this::isBufferMode)
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final DragValue progressPosition = ValueBuilder.create(this, "Progress Position")
            .setDefaultX(0.0F)
            .setDefaultY(0.0F)
            .build()
            .getDragValue();

    private final SmoothAnimationTimer progressAnimation = new SmoothAnimationTimer(0.0F, 0.0F, 0.45F);
    private final SmoothAnimationTimer progressAlpha = new SmoothAnimationTimer(1.0F, 0.0F, 0.35F);

    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<PendingEntityMove> pendingEntityMoves = new ConcurrentLinkedQueue<>();
    private final Queue<PendingEntityTeleport> pendingEntityTeleports = new ConcurrentLinkedQueue<>();
    private final Map<Entity, Vector3d> targets = new ConcurrentHashMap<>();

    private boolean isSuspending = false;
    private int suspendTicks = 0;
    private ClientboundSetEntityMotionPacket clientboundSetEntityMotionPacket = null;
    private boolean isFlushing = false;
    private boolean shouldFlushMotion = false;
    private volatile boolean pendingScheduledReset = false;
    private Entity attackTarget = null;
    private Entity velocityTarget = null;
    private int attacksRemaining = 0;
    private int totalAttacks = 0;
    private int attackCooldown = 0;
    private int releaseRotateTicks = 0;
    private Rotation velocityRotation = null;
    private boolean jump = false;
    private int stuckCooldown = 0;
    private int s08Cooldown = 0;
    private boolean positionInitialized = false;

    private void log(String message) {
        if (this.debug.getCurrentValue()) {
            ChatUtils.addChatMessage(message);
        }
    }

    private int calculateSmartAttacks() {
        if (clientboundSetEntityMotionPacket == null) return (int) attack.getCurrentValue();
        double kbX = -clientboundSetEntityMotionPacket.getXa() / 8000.0;
        double kbZ = -clientboundSetEntityMotionPacket.getZa() / 8000.0;
        double kbStrength = Math.sqrt(kbX * kbX + kbZ * kbZ);
        if (mode19Plus.getCurrentValue()) {
            return kbStrength > targetMotion.getCurrentValue() ? 1 : 0;
        }
        int knockbackLevel = EnchantmentHelper.getKnockbackBonus(mc.player);
        boolean hasKnockback = knockbackLevel > 0;
        double decay = 0.6D;
        if (hasKnockback) {
            decay = 0.6D - (knockbackLevel * 0.05D);
            decay = Math.max(0.4D, decay);
        }
        float threshold = targetMotion.getCurrentValue();
        int maxAttacks = (int) attack.getCurrentValue();
        int minAttacks = 1;
        if (kbStrength <= threshold) return minAttacks;
        double needed = Math.log(threshold / kbStrength) / Math.log(decay);
        int calculated = (int) Math.ceil(needed);
        if (hasKnockback) {
            calculated += knockbackLevel;
        }
        return Math.max(minAttacks, Math.min(calculated, maxAttacks));
    }

    private void disableRotate() {
        releaseRotateTicks = 0;
        velocityRotation = null;
    }

    private void flushPackets() {
        boolean attacked = totalAttacks > 0;

        isFlushing = true;
        targets.clear();
        releasePacket();
        if (clientboundSetEntityMotionPacket != null && mc.getConnection() != null) {
            clientboundSetEntityMotionPacket.handle(mc.getConnection());
            clientboundSetEntityMotionPacket = null;
        }

        shouldFlushMotion = true;
        isFlushing = false;

        StringBuilder sb = new StringBuilder("Sync, ticks used: ").append(suspendTicks);
        if (attacked) {
            sb.append(" | ")
                    .append(smart.getCurrentValue() ? "calculated" : "current")
                    .append(" attack: ").append(totalAttacks);
        }
        if (jump) {
            sb.append(" | jump: 1");
        }
        if (!attacked && !jump && !mode19Plus.getCurrentValue()) {
            sb.append(" (idle)");
        }
        log(sb.toString());
    }

    private void endSuspending() {
        isSuspending = false;
        suspendTicks = 0;
        attackTarget = null;
        velocityTarget = null;
        attacksRemaining = 0;
        totalAttacks = 0;
        targets.clear();
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.level == null) {
            return true;
        }
        if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.player.getHealth() <= 0) {
            return true;
        }
        if (mc.player.isSpectator() || mc.player.getAbilities().flying) {
            return true;
        }
        if (ignoreInLava.getCurrentValue() && mc.player.isInLava()) {
            return true;
        }
        if (ignoreInWater.getCurrentValue() && mc.player.isInWater()) {
            return true;
        }
        if (ignoreOnFire.getCurrentValue() && mc.player.isOnFire()) {
            return true;
        }
        if (ignoreNoSprinting.getCurrentValue() && !mc.player.isSprinting()) {
            return true;
        }
        if (ignoreS08.getCurrentValue() && s08Cooldown > 0) {
            return true;
        }
        if (mc.player.onClimbable() || mc.player.isSleeping()) {
            return true;
        }
        return mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB);
    }

    private boolean shouldIgnorePacketThread() {
        return mc.player == null
                || mc.getConnection() == null
                || mc.gameMode == null
                || mc.player.isUsingItem()
                || mc.player.tickCount < 20
                || mc.player.isDeadOrDying()
                || !mc.player.isAlive()
                || mc.player.getHealth() <= 0.0F
                || mc.player.isSpectator()
                || mc.player.getAbilities().flying
                || (ignoreNoSprinting.getCurrentValue() && !mc.player.isSprinting())
                || mc.screen instanceof ProgressScreen
                || mc.screen instanceof DeathScreen
                || isLongJumpEnabled();
    }

    private boolean isLongJumpEnabled() {
        LongJump longJump = Naven.getInstance().getModuleManager().getModule(LongJump.class);
        return longJump != null && longJump.isEnabled();
    }

    private void scheduleBufferReset(boolean flushQueued, String reason) {
        if (pendingScheduledReset || mc == null) return;
        pendingScheduledReset = true;
        mc.execute(() -> {
            pendingScheduledReset = false;
            if (flushQueued) {
                flushPackets();
            }
            endSuspending();
            disableRotate();
            clientboundSetEntityMotionPacket = null;
            packetQueue.clear();
            pendingEntityMoves.clear();
            pendingEntityTeleports.clear();
            targets.clear();
            isFlushing = false;
            shouldFlushMotion = false;
            attackCooldown = 0;
            attackTarget = null;
            velocityTarget = null;
            attacksRemaining = 0;
            totalAttacks = 0;
            releaseRotateTicks = 0;
            jump = false;
            if (reason != null) {
                log(reason);
            }
        });
    }

    private void processPendingEntityUpdates() {
        if (mc.level == null) {
            pendingEntityMoves.clear();
            pendingEntityTeleports.clear();
            return;
        }

        PendingEntityMove move;
        while ((move = pendingEntityMoves.poll()) != null) {
            Entity entity = mc.level.getEntity(move.entityId());
            if (entity == null) continue;
            Vector3d currentPos = targets.getOrDefault(entity, new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
            targets.put(entity, new Vector3d(currentPos.getX() + move.dx(), currentPos.getY() + move.dy(), currentPos.getZ() + move.dz()));
        }

        PendingEntityTeleport teleport;
        while ((teleport = pendingEntityTeleports.poll()) != null) {
            Entity entity = mc.level.getEntity(teleport.entityId());
            if (entity != null) {
                targets.put(entity, new Vector3d(teleport.x(), teleport.y(), teleport.z()));
            }
        }
    }

    private Entity getCurrentTarget() {
        Entity combatTarget = getCombatModuleTarget();
        if (combatTarget != null) return combatTarget;
        return getLookTarget();
    }

    private Entity getCombatModuleTarget() {
        KillAura killAura = Naven.getInstance().getModuleManager().getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.target != null && killAura.target.isAlive()) {
            return killAura.target;
        }
        return null;
    }

    private Entity getLookTarget() {
        if (!(mc.hitResult instanceof EntityHitResult hit)) return null;

        Entity entity = hit.getEntity();
        if (entity instanceof LivingEntity && entity != mc.player && entity.isAlive() && !entity.isSpectator()) {
            return entity;
        }
        return null;
    }

    private boolean isValidTarget(Entity target) {
        if (target == null || !target.isAlive()) return false;
        if (target == mc.player) return false;
        if (target instanceof LivingEntity living && (living.isDeadOrDying() || living.getHealth() <= 0)) return false;
        Entity combatTarget = getCombatModuleTarget();
        if (combatTarget != null && combatTarget.equals(target)) return true;
        double distance = getDistanceToEntity(target);
        if (distance <= 3.0) return true;
        KillAura killAura = Naven.getInstance().getModuleManager().getModule(KillAura.class);
        return killAura != null && killAura.isEnabled() && distance <= killAura.aimRange.getCurrentValue();
    }

    private boolean isTargetLost() {
        Entity currentCombatTarget = getCombatModuleTarget();
        if (attackTarget == null) return true;
        if (multiTarget.getCurrentValue()) {
            if (currentCombatTarget != null) {
                return !currentCombatTarget.equals(attackTarget) && !currentCombatTarget.equals(velocityTarget);
            }
            return !isValidTarget(attackTarget);
        }
        if (currentCombatTarget != null && attackTarget != null) {
            return !currentCombatTarget.equals(attackTarget);
        }
        return !isValidTarget(attackTarget);
    }

    private double getDistanceToEntity(Entity entity) {
        if (mc.player == null || entity == null) return Double.MAX_VALUE;

        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        AABB aabb = entity.getBoundingBox();

        double x = Math.max(aabb.minX, Math.min(eyePos.x, aabb.maxX));
        double y = Math.max(aabb.minY, Math.min(eyePos.y, aabb.maxY));
        double z = Math.max(aabb.minZ, Math.min(eyePos.z, aabb.maxZ));

        return eyePos.distanceTo(new Vec3(x, y, z));
    }

    private void doAttack(Entity target) {
        if (target == null || mc.player == null || mc.gameMode == null) return;
        if (ignoreNoSprinting.getCurrentValue() && !mc.player.isSprinting()) {
            log("not sprinting");
            return;
        }
        if (mode19Plus.getCurrentValue()) {
            if (mc.player.getAttackStrengthScale(0.5F) < 1.0F) return;
        }

        boolean wasSprinting = mc.player.isSprinting();

        if (wasSprinting) mc.player.setSprinting(false);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (!mode19Plus.getCurrentValue() && wasSprinting) {
            Vec3 vel = mc.player.getDeltaMovement();
            if (EnchantmentHelper.getKnockbackBonus(mc.player) > 0) {
                mc.player.setDeltaMovement(vel.x, vel.y, vel.z);
            } else {
                mc.player.setDeltaMovement(vel.x * 0.6D, vel.y, vel.z * 0.6D);
            }
        }
    }

    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundDisconnectPacket
                || (packet instanceof ClientboundAnimatePacket animate
                && animate.getId() != mc.player.getId());
    }

    @EventTarget
    public void onPacket(EventPacket e) {
        if (e.getType() != EventType.RECEIVE) return;

        if (isLegitMode()) {
            if (mc.player != null && e.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.getId() == mc.player.getId()) {
                jump = true;
            }
            return;
        }

        if (!isBufferMode()) return;

        if (mc.player == null) {
            boolean hasBufferedState = isSuspending
                    || clientboundSetEntityMotionPacket != null
                    || !packetQueue.isEmpty()
                    || attacksRemaining > 0
                    || attackTarget != null;
            scheduleBufferReset(isSuspending || clientboundSetEntityMotionPacket != null || !packetQueue.isEmpty(),
                    hasBufferedState ? "§4Reset, reason:player invalid" : null);
            return;
        }
        if (shouldIgnorePacketThread()) {
            boolean hasBufferedState = isSuspending
                    || clientboundSetEntityMotionPacket != null
                    || !packetQueue.isEmpty()
                    || attacksRemaining > 0
                    || attackTarget != null;
            scheduleBufferReset(isSuspending || clientboundSetEntityMotionPacket != null || !packetQueue.isEmpty(),
                    hasBufferedState ? "§4Reset, reason:player invalid" : null);
            return;
        }
        if (isFlushing) return;

        Packet<?> packet = e.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (ignoreS08.getCurrentValue() && s08Cooldown > 0) {
                e.setCancelled(true);
                return;
            }

            if (ignoreS08.getCurrentValue()) {
                s08Cooldown = 20;
                log("§cS08 detected");
            }

            if (isSuspending) {
                scheduleBufferReset(true, null);
                return;
            }
            clientboundSetEntityMotionPacket = null;
            packetQueue.clear();
            isFlushing = false;
            shouldFlushMotion = false;
            attackCooldown = 0;
            attackTarget = null;
            velocityTarget = null;
            attacksRemaining = 0;
            totalAttacks = 0;
            releaseRotateTicks = 0;
            return;
        }

        if (isSuspending && packet instanceof ClientboundMoveEntityPacket movePacket) {
            e.setCancelled(true);
            ClientboundMoveEntityPacketAccessor accessor = (ClientboundMoveEntityPacketAccessor) movePacket;
            if (accessor.getHasPos()) {
                pendingEntityMoves.offer(new PendingEntityMove(
                        accessor.getEntityId(),
                        accessor.getXa() / 4096.0D,
                        accessor.getYa() / 4096.0D,
                        accessor.getZa() / 4096.0D
                ));
            }
        }

        if (isSuspending && packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            e.setCancelled(true);
            pendingEntityTeleports.offer(new PendingEntityTeleport(
                    teleportPacket.getId(),
                    teleportPacket.getX(),
                    teleportPacket.getY(),
                    teleportPacket.getZ()
            ));
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == mc.player.getId()) {
            if (attackCooldown > 0 && mode19Plus.getCurrentValue() && attacksRemaining > 0) {
                return;
            }
            e.setCancelled(true);
            double velX = -motion.getXa() / 8000.0;
            double velZ = -motion.getZa() / 8000.0;
            if (Math.abs(velX) <= 0.01 && Math.abs(velZ) <= 0.01) return;

            attackTarget = null;
            velocityTarget = null;
            attacksRemaining = 0;
            totalAttacks = 0;
            attackCooldown = 0;
            clientboundSetEntityMotionPacket = motion;
            suspendTicks = 0;

            if (mode19Plus.getCurrentValue()) {
                double velStrength = Math.sqrt(velX * velX + velZ * velZ);
                if (velStrength <= targetMotion.getCurrentValue()) {
                    attacksRemaining = 0;
                    totalAttacks = 0;
                } else {
                    totalAttacks = 1;
                    attacksRemaining = 1;
                }
            } else {
                totalAttacks = smart.getCurrentValue() ? calculateSmartAttacks() : (int) attack.getCurrentValue();
                attacksRemaining = totalAttacks;
            }

            if (jumpReset.getCurrentValue()) {
                jump = true;
            }

            if (!isValidTarget(attackTarget) || attackTarget == null) {
                Entity target = getCurrentTarget();
                if (isValidTarget(target) && mc.player.isSprinting()) {
                    attackTarget = target;
                    velocityTarget = target;
                }
            }

            if (attackTarget != null && renderServerPos.getCurrentValue()) {
                targets.put(attackTarget, new Vector3d(attackTarget.getX(), attackTarget.getY(), attackTarget.getZ()));
            }

            isSuspending = true;
            return;
        }

        if (isSuspending && !isAllowedPacket(packet)) {
            packetQueue.add(packet);
            e.setCancelled(true);
        }
    }

    @EventTarget
    public void onPreTick(EventRunTicks e) {
        if (!isBufferMode()) {
            if (e.type() == EventType.PRE) {
                this.setSuffix(mode.getCurrentMode());
            }
            return;
        }

        this.setSuffix("Buffer" + (isSuspending ? " " + suspendTicks + "T" : ""));

        if (s08Cooldown > 0) {
            s08Cooldown--;
        }
        processPendingEntityUpdates();
        targets.entrySet().removeIf(entry -> entry.getKey() == null || !entry.getKey().isAlive() || entry.getKey().isRemoved());

        Stuck stuck = Naven.getInstance().getModuleManager().getModule(Stuck.class);
        if (stuck != null && stuck.isEnabled()) {
            if (isSuspending) {
                flushPackets();
                endSuspending();
                disableRotate();
            }
            attackTarget = null;
            velocityTarget = null;
            attacksRemaining = 0;
            totalAttacks = 0;
            attackCooldown = 0;
            stuckCooldown = 5;
            return;
        }
        if (stuckCooldown > 0) {
            stuckCooldown--;
            return;
        }
        if (e.type() != EventType.PRE) return;

        if (rotate.getCurrentValue() && velocityRotation != null && !isLongJumpEnabled()) {
            RotationManager.setRotations(velocityRotation, 180.0);
        }

        if (releaseRotateTicks > 0 && !jump) {
            releaseRotateTicks--;
            if (releaseRotateTicks <= 0) {
                disableRotate();
            }
        }

        if (shouldIgnore()) {
            if (isSuspending) {
                flushPackets();
                endSuspending();
                disableRotate();
                log("§4Reset, reason:player invalid");
            }
            attackTarget = null;
            velocityTarget = null;
            attacksRemaining = 0;
            totalAttacks = 0;
            attackCooldown = 0;
            return;
        }

        if (attackCooldown > 0) attackCooldown--;

        if (isSuspending) {
            suspendTicks++;
            if (attackTarget != null && (!attackTarget.isAlive() || attackTarget.isRemoved())) {
                log("§4Reset, reason:target invalid");
                flushPackets();
                endSuspending();
                disableRotate();
                return;
            }

            if (multiTarget.getCurrentValue()) {
                KillAura killAura = Naven.getInstance().getModuleManager().getModule(KillAura.class);
                boolean canSwitch = killAura != null && killAura.isEnabled() && killAura.target != null;

                if (canSwitch) {
                    Entity newTarget = getCurrentTarget();
                    if (newTarget != null && !newTarget.equals(attackTarget)) {
                        attackTarget = newTarget;
                        if (renderServerPos.getCurrentValue() && !targets.containsKey(attackTarget)) {
                            targets.put(attackTarget, new Vector3d(attackTarget.getX(), attackTarget.getY(), attackTarget.getZ()));
                        }
                    }
                } else {
                    log("KillAura not targeting");
                }
            }

            boolean onGround = mc.player.onGround();
            boolean movingUp = mc.player.getDeltaMovement().y > 0;
            boolean falling = mc.player.getDeltaMovement().y < 0;
            boolean timeout = suspendTicks >= 20;
            boolean canRelease = delayTillGround.getCurrentValue() ? onGround : onGround || movingUp || falling;
            boolean shouldRelease = canRelease && isValidTarget(attackTarget) && mc.player.isSprinting();

            if (onGround) {
                if (!isValidTarget(attackTarget)) {
                    flushPackets();
                    endSuspending();
                    disableRotate();
                    return;
                }
                if (!mc.player.isSprinting()) {
                    flushPackets();
                    endSuspending();
                    disableRotate();
                    return;
                }
            }

            if (isTargetLost() && attacksRemaining < totalAttacks) {
                log("Hit complete");
                flushPackets();
                endSuspending();
                disableRotate();
                return;
            }

            if (timeout) {
                if (attacksRemaining < totalAttacks && attacksRemaining > 0) {
                    log("Hit complete");
                } else {
                    flushPackets();
                    log("§4Reset, reason:timeout");
                }
                endSuspending();
                disableRotate();
                return;
            }

            if (shouldRelease) {
                targets.clear();
                Entity rotateTarget = isValidTarget(velocityTarget) ? velocityTarget : attackTarget;
                if (rotate.getCurrentValue() && onGround && isValidTarget(rotateTarget)) {
                    velocityRotation = RotationUtils.calculate(mc.player.getEyePosition(1.0F), rotateTarget.getBoundingBox().getCenter());
                    releaseRotateTicks = 1;
                }
                flushPackets();
                isSuspending = false;
                suspendTicks = 0;
            }
            return;
        }

        if (!isSuspending && attacksRemaining > 0) {
            if (attackTarget == null || !isValidTarget(attackTarget)) {
                log("Hit complete");
                attackTarget = null;
                velocityTarget = null;
                attacksRemaining = 0;
                totalAttacks = 0;
                attackCooldown = 0;
                disableRotate();
                return;
            }

            if (attackCooldown > 0) {
                return;
            }

            if (autoSprint.getCurrentValue() && !mc.player.isSprinting()) {
                mc.options.keySprint.setDown(true);
                mc.player.setSprinting(true);
            }

            if (mode19Plus.getCurrentValue() && mc.player.getAttackStrengthScale(0.5F) < 1.0F) {
                log("Hit complete");
                attackTarget = null;
                velocityTarget = null;
                attacksRemaining = 0;
                totalAttacks = 0;
                attackCooldown = 0;
                disableRotate();
                return;
            }

            if (isTargetLost()) {
                log("Hit complete");
                attackTarget = null;
                velocityTarget = null;
                attacksRemaining = 0;
                totalAttacks = 0;
                attackCooldown = 0;
                disableRotate();
                return;
            }

            if (mc.player.isUsingItem()) {
                log("Hit complete");
                attackTarget = null;
                velocityTarget = null;
                attacksRemaining = 0;
                totalAttacks = 0;
                attackCooldown = 0;
                disableRotate();
                return;
            }

            if (attackTarget instanceof Player targetPlayer) {
                if (AntiBots.isBot(targetPlayer)) {
                    log("Hit complete");
                    attackTarget = null;
                    velocityTarget = null;
                    attacksRemaining = 0;
                    totalAttacks = 0;
                    attackCooldown = 0;
                    disableRotate();
                    return;
                }
            }

            doAttack(attackTarget);
            attacksRemaining--;
            attackCooldown = mode19Plus.getCurrentValue()
                    ? Math.max(1, (int) (20 / mc.player.getCurrentItemAttackStrengthDelay()))
                    : 1;
            log("Reduce(Info: Attack Reduce)");

            if (attacksRemaining <= 0) {
                log("Hit complete");
                attackTarget = null;
                velocityTarget = null;
                totalAttacks = 0;
                attackCooldown = 0;
                disableRotate();
            }
            return;
        }

        if (shouldFlushMotion) {
            releasePacket();
            shouldFlushMotion = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void releasePacket() {
        while (!packetQueue.isEmpty()) {
            Packet<?> p = packetQueue.poll();
            if (p == null || mc.getConnection() == null) continue;
            try {
                ((Packet<ClientGamePacketListener>) p).handle(mc.getConnection());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (!isBufferMode()) {
            if (jump) {
                if (mc.player != null && mc.player.onGround() && MoveUtils.isMoving()) {
                    event.setJump(true);
                }
                jump = false;
            }
            return;
        }

        if (jump) {
            if (mc.player != null) {
                event.setJump(true);
                ChatUtils.addChatMessage("Reduce(Info: Jump Reset)");
            }
            jump = false;
            if (releaseRotateTicks > 0) {
                releaseRotateTicks = 0;
                disableRotate();
            }
        }

        Entity moveTarget = isValidTarget(velocityTarget) ? velocityTarget : attackTarget;
        boolean hasMoveTarget = isValidTarget(moveTarget);
        if (!isSuspending && attacksRemaining > 0 && hasMoveTarget) {
            if (autoSprint.getCurrentValue() && !mc.player.isSprinting() && MoveUtils.isMoving()) {
                mc.options.keySprint.setDown(true);
                mc.player.setSprinting(true);
            }
        }

        if (movementOverride.getCurrentValue() && hasMoveTarget && (isSuspending || attacksRemaining > 0)) {
            event.setForward(1.0F);
            event.setStrafe(0.0F);
            Rotation moveRotation = RotationUtils.calculate(mc.player.getEyePosition(1.0F), moveTarget.getBoundingBox().getCenter());
            MoveUtils.correctionMovement(event, moveRotation.getYaw());
        }
    }

    @EventTarget
    public void onRender(EventRender event) {
        if (!isBufferMode() || !renderServerPos.getCurrentValue() || targets.isEmpty()) return;
        PoseStack poseStack = event.getPMatrixStack();
        for (Map.Entry<Entity, Vector3d> entry : targets.entrySet()) {
            Entity entity = entry.getKey();
            if (!(entity instanceof Player)) continue;
            Vector3d pos = entry.getValue();
            int color = entity == attackTarget
                    ? new Color(200, 0, 0, 60).getRGB()
                    : new Color(0, 200, 0, 60).getRGB();
            RenderUtils.drawEntitySolidBox(poseStack, pos.getX(), pos.getY(), pos.getZ(),
                    entity.getBbWidth(), entity.getBbHeight(), color);
        }
    }

    @EventTarget
    public void onRenderSkia(EventRenderSkia event) {
        boolean inEditor = mc.screen instanceof HUDEditor;
        boolean shouldRender = inEditor || (this.isEnabled() && isBufferMode());

        this.progressAlpha.target = shouldRender ? 1.0F : 0.0F;
        this.progressAlpha.update(true);
        if (this.progressAlpha.value <= 0.01F) return;

        this.progressAnimation.target = inEditor
                ? 60.0F
                : (isSuspending ? Math.min(100.0F, suspendTicks / 20.0F * 100.0F) : 0.0F);
        this.progressAnimation.update(true);

        float width = 120.0F;
        float height = 32.0F;
        float radius = 10.0F;

        if (!positionInitialized && progressPosition.getX() == 0.0F && progressPosition.getY() == 0.0F) {
            float screenWidth = mc.getWindow().getGuiScaledWidth();
            float screenHeight = mc.getWindow().getGuiScaledHeight();
            progressPosition.setPosition(screenWidth / 2.0F - width / 2.0F, screenHeight - 55.0F);
            positionInitialized = true;
        }

        float x = progressPosition.getX();
        float y = progressPosition.getY();
        float alpha = this.progressAlpha.value;
        int bgAlpha = Math.min(255, Math.max(0, (int) (80.0F * alpha)));

        Skia.drawShadow(x, y, width, height, radius);
        Skia.drawRoundedBlur(x, y, width, height, radius);
        Skia.drawRoundedRect(x, y, width, height, radius, new Color(0, 0, 0, bgAlpha));

        float padding = 3.0F;
        float barHeight = 5.0F;
        float barX = x + padding;
        float barY = y + height - padding - barHeight;
        float barWidth = width - padding * 2.0F;
        float fillWidth = barWidth * (this.progressAnimation.value / 100.0F);
        float barRadius = barHeight / 2.0F;

        Path barPath = new Path();
        barPath.addRRect(RRect.makeXYWH(barX, barY, barWidth, barHeight, barRadius));
        Skia.save();
        Skia.getCanvas().clipPath(barPath, ClipMode.INTERSECT, true);
        Skia.drawRoundedRect(barX, barY, barWidth, barHeight, barRadius, new Color(255, 255, 255, (int) (50.0F * alpha)));
        if (fillWidth > 0.5F) {
            Skia.drawRoundedRect(barX, barY, fillWidth, barHeight, barRadius, new Color(alphaColor(mainColor, alpha), true));
        }
        Skia.restore();
        barPath.close();

        Font font = Fonts.getMiSans(10.0F);
        String title = isSuspending ? "Buffer " + suspendTicks + "T" : "Velocity Buffer";
        float titleCenterX = x + width / 2.0F;
        float titleCenterY = y + 9.0F;
        Skia.drawFullCenteredText(title, titleCenterX, titleCenterY, new Color(255, 255, 255, (int) (220.0F * alpha)), font);
        String percentText = Math.round(this.progressAnimation.value) + "%";
        Skia.drawFullCenteredText(percentText, x + width / 2.0F, y + 18.0F, new Color(255, 255, 255, (int) (230.0F * alpha)), font);

        progressPosition.setWidth(width);
        progressPosition.setHeight(height);
    }

    @EventTarget
    public void onPlayerRespawn(EventRespawn e) {
        reset();
    }

    private int alphaColor(int color, float alpha) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        int baseAlpha = color >>> 24;
        return (color & 0x00FFFFFF) | (Math.round(baseAlpha * clampedAlpha) << 24);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (isSuspending) {
            flushPackets();
        }
        releasePacket();
        reset();
        disableRotate();
    }

    private void reset() {
        if (isSuspending) {
            flushPackets();
        }
        endSuspending();
        disableRotate();
        clientboundSetEntityMotionPacket = null;
        packetQueue.clear();
        pendingEntityMoves.clear();
        pendingEntityTeleports.clear();
        targets.clear();
        isFlushing = false;
        shouldFlushMotion = false;
        pendingScheduledReset = false;
        attackCooldown = 0;
        s08Cooldown = 0;
        attackTarget = null;
        velocityTarget = null;
        attacksRemaining = 0;
        totalAttacks = 0;
        releaseRotateTicks = 0;
        jump = false;
        suspendTicks = 0;
        stuckCooldown = 0;
        progressAnimation.value = 0.0F;
        progressAnimation.target = 0.0F;
        progressAlpha.value = 0.0F;
    }

    private record PendingEntityMove(int entityId, double dx, double dy, double dz) {
    }

    private record PendingEntityTeleport(int entityId, double x, double y, double z) {
    }
}
