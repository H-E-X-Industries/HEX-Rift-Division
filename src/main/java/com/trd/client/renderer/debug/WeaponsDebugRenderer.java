package com.trd.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trd.block.entity.weapons.MissileTurretBlockEntity;
import com.trd.entity.weapons.missiles.MissileLightEntity;
import com.trd.explosion.logic.ExplosionFire;
import com.trd.explosion.logic.ExplosionHydrogen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Дебаг-визуализация оружия (F3): тромбон и фронт взрывных волн.
 * <p>
 * Ракета помечается фиолетовым кубом, как просили; траектория рисуется интегрированием
 * её текущей скорости с затуханием. У взрывов показывается активная фаза, курсор
 * сканирования ( фронт волны) и что именно волна уже задела: огонь / заменённые
 * блоки / снесённые, а у водорода ещё и вырезанный кратер с тинтовыми кольцами.
 */
public final class WeaponsDebugRenderer {

    // ракета и её траектория
    private static final float MISSILE_R = 0.75F, MISSILE_G = 0.25F, MISSILE_B = 0.95F;
    private static final float TRAJ_R = 0.75F, TRAJ_G = 0.25F, TRAJ_B = 0.95F;

    // цель тромбона
    private static final float TARGET_R = 1.0F, TARGET_G = 0.15F, TARGET_B = 0.15F;
    private static final float SALVO_R = 1.0F, SALVO_G = 0.85F, SALVO_B = 0.1F;

    // зажигательная волна
    private static final float FIRE_WAVE_R = 1.0F, FIRE_WAVE_G = 0.45F, FIRE_WAVE_B = 0.05F;
    private static final float FIRE_BLOCK_R = 1.0F, FIRE_BLOCK_G = 0.25F, FIRE_BLOCK_B = 0.0F;
    private static final float REPLACE_R = 1.0F, REPLACE_G = 0.9F, REPLACE_B = 0.2F;
    private static final float DESTROY_R = 0.95F, DESTROY_G = 0.1F, DESTROY_B = 0.1F;
    private static final float CURSOR_R = 0.1F, CURSOR_G = 1.0F, CURSOR_B = 0.3F;

    // водородный взрыв
    private static final float H2_ZONE1_R = 1.0F, H2_ZONE1_G = 0.3F, H2_ZONE1_B = 0.1F;
    private static final float H2_ZONE2_R = 0.6F, H2_ZONE2_G = 0.2F, H2_ZONE2_B = 1.0F;
    private static final float H2_CARVE_R = 1.0F, H2_CARVE_G = 0.45F, H2_CARVE_B = 0.0F;

    private WeaponsDebugRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffer, Level level) {
        RenderType renderType = RenderType.debugLineStrip(2.0);
        VertexConsumer v = buffer.getBuffer(renderType);

        poseStack.pushPose();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();

        renderFireWaves(m, v);
        renderHydrogenWaves(m, v);
        renderTrombones(m, v, level, cam);
        renderMissiles(m, v, level, cam);

        poseStack.popPose();
        buffer.endBatch(renderType);
    }

    // ================= ТРОМБОН =================

    private static void renderTrombones(Matrix4f m, VertexConsumer v, Level level, Vec3 cam) {
        // Обхода BlockEntity по области в Level нет, поэтому идём по чанкам:
        // на клиенте и сервере API одинаковый.
        int minCX = (int) Math.floor((cam.x - 96) / 16.0);
        int maxCX = (int) Math.floor((cam.x + 96) / 16.0);
        int minCZ = (int) Math.floor((cam.z - 96) / 16.0);
        int maxCZ = (int) Math.floor((cam.z + 96) / 16.0);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof MissileTurretBlockEntity t)) continue;
                    Vec3 from = new Vec3(t.getBlockPos().getX() + 0.5,
                            t.getBlockPos().getY() + 2.0, t.getBlockPos().getZ() + 0.5);

                    boolean salvo = t.isDebugSalvoActive();
                    LivingEntity target = t.getDebugTarget();

                    // Ракетница видна всегда, чтобы было видно, где искать
                    DebugShapes.cross(m, v, from, 0.6,
                            salvo ? SALVO_R : 0.5F, salvo ? SALVO_G : 0.5F, salvo ? SALVO_B : 0.5F, 0.7F);

                    if (target == null || !target.isAlive()) continue;
                    Vec3 tp = target.getBoundingBox().getCenter();

                    // Линия полёта: ракетница -> цель, по ней ракета и летит.
                    // Ровно то, что просят видеть, без догадок о траектории.
                    DebugShapes.line(m, v, from, tp, TARGET_R, TARGET_G, TARGET_B, 0.9F);
                    // куб на цели
                    DebugShapes.box(m, v, tp, 0.5, TARGET_R, TARGET_G, TARGET_B, 1.0F);
                    // тип ракеты залпа — вдоль линии, у самого ствола
                    DebugShapes.sphere(m, v, from, 0.35, 10,
                            SALVO_R, SALVO_G, SALVO_B, salvo ? 0.9F : 0.35F);
                }
            }
        }
    }

    private static void renderMissiles(Matrix4f m, VertexConsumer v, Level level, Vec3 cam) {
        AABB box = new AABB(cam.x - 96, cam.y - 48, cam.z - 96, cam.x + 96, cam.y + 48, cam.z + 96);
        for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof MissileLightEntity)) {
            MissileLightEntity mis = (MissileLightEntity) e;
            Vec3 p = mis.position();

            // фиолетовый куб на текущей позиции ракеты
            DebugShapes.box(m, v, p, 0.45, MISSILE_R, MISSILE_G, MISSILE_B, 1.0F);

            // Короткий вектор скорости: показывает, куда ракета реально идёт сейчас.
            // Раньше здесь рисовалась псевдо-траектория (интегрирование скорости с
            // затуханием) — она не совпадала с полётом и только путала.
            Vec3 vel = mis.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-6) {
                DebugShapes.line(m, v, p, p.add(vel.scale(12.0)),
                        TRAJ_R, TRAJ_G, TRAJ_B, 0.85F);
            }

            // Линия «ракета → её цель»: ракета летит именно сюда, и это читается
            // сразу, в отличие от угадывания траектории по скорости.
            LivingEntity target = mis.getTarget();
            if (target != null && target.isAlive()) {
                Vec3 tp = target.getBoundingBox().getCenter();
                DebugShapes.line(m, v, p, tp, TRAJ_R, TRAJ_G, TRAJ_B, 0.35F);
            }
        }
    }

    // ================= ЗАЖИГАТЕЛЬНАЯ ВОЛНА =================

    private static void renderFireWaves(Matrix4f m, VertexConsumer v) {
        for (ExplosionFire.DebugSnapshot s : ExplosionFire.debugActiveExplosions()) {
            // Показывается ровно один взрыв, поэтому гасить нечего: иначе
            // достреленный последний взрыв остался бы тусклым, а его как раз
            // и нужно разглядывать.
            float f = 1.0F;

            DebugShapes.sphere(m, v, s.center(), s.waveRadius(), 28,
                    FIRE_WAVE_R * f, FIRE_WAVE_G * f, FIRE_WAVE_B * f, 0.55F * f);
            DebugShapes.sphere(m, v, s.center(), s.coreRadius(), 20,
                    FIRE_WAVE_R * f, FIRE_WAVE_G * f, FIRE_WAVE_B * f, 0.8F * f);

            // фронт: курсор сканирования
            BlockPos cur = s.scanCursor();
            DebugShapes.box(m, v, new Vec3(cur.getX() + 0.5, cur.getY() + 0.5, cur.getZ() + 0.5), 1.0,
                    CURSOR_R, CURSOR_G, CURSOR_B, 1.0F);

            drawBlocks(m, v, s.fire(), FIRE_BLOCK_R, FIRE_BLOCK_G, FIRE_BLOCK_B, 0.5F * f);
            drawBlocks(m, v, s.replace(), REPLACE_R, REPLACE_G, REPLACE_B, 0.4F * f);
            drawBlocks(m, v, s.destroy(), DESTROY_R, DESTROY_G, DESTROY_B, 0.5F * f);
        }
    }

    // ================= ВОДОРОДНАЯ ВОЛНА =================

    private static void renderHydrogenWaves(Matrix4f m, VertexConsumer v) {
        for (ExplosionHydrogen.DebugSnapshot s : ExplosionHydrogen.debugActiveExplosions()) {
            // один взрыв — гасить нечего (см. ExplosionFire)
            float f = 1.0F;

            DebugShapes.sphere(m, v, s.center(), s.zone1Radius(), 28,
                    H2_ZONE1_R * f, H2_ZONE1_G * f, H2_ZONE1_B * f, 0.5F * f);
            DebugShapes.sphere(m, v, s.center(), s.zone2Radius(), 28,
                    H2_ZONE2_R * f, H2_ZONE2_G * f, H2_ZONE2_B * f, 0.45F * f);

            BlockPos cur = s.scanCursor();
            DebugShapes.box(m, v, new Vec3(cur.getX() + 0.5, cur.getY() + 0.5, cur.getZ() + 0.5), 1.0,
                    CURSOR_R, CURSOR_G, CURSOR_B, 1.0F);

            drawBlocks(m, v, s.carve(), H2_CARVE_R, H2_CARVE_G, H2_CARVE_B, 0.45F * f);
            drawBlocks(m, v, s.replace(), REPLACE_R, REPLACE_G, REPLACE_B, 0.35F * f);
            drawBlocks(m, v, s.destroy(), DESTROY_R, DESTROY_G, DESTROY_B, 0.45F * f);

            // тинтовые кольца идут парами (позиция, ступень затемнения 0..7)
            long[] tint = s.tint();
            for (int i = 0; i + 1 < tint.length; i += 2) {
                BlockPos p = BlockPos.of(tint[i]);
                int step = (int) tint[i + 1];
                // чем темнее блок, тем холоднее цвет
                float k = 1.0F - (step / 7.0F) * 0.6F;
                DebugShapes.blockBox(m, v, p, 0.1F * k, 0.5F * k, 1.0F * k, 0.30F * f);
            }
        }
    }

    // ================= ОБЩЕЕ =================

    private static void drawBlocks(Matrix4f m, VertexConsumer v, long[] positions,
                                   float r, float g, float b, float a) {
        if (positions == null) return;
        // очень большие списки забивают буфер — рисуем с шагом, фронт всё равно читается
        int stride = positions.length > 4000 ? (positions.length / 4000) + 1 : 1;
        for (int i = 0; i < positions.length; i += stride) {
            DebugShapes.blockBox(m, v, BlockPos.of(positions[i]), r, g, b, a);
        }
    }
}
