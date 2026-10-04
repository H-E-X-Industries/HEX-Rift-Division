package com.trd.client.renderer.debug;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Примитивы отрисовки для дебаг-рендера оружия (F3).
 * <p>
 * В 1.21.1 у {@code VertexConsumer} цепочка называется {@code addVertex(...).setColor(...).setNormal(...)},
 * а завершающего {@code endVertex()} больше нет — вершину закрывает сам {@code addVertex}.
 * <p>
 * Всё дальше {@link #MAX_RENDER_DISTANCE} от камеры не рисуется. Ограничение только на обход
 * сущностей мало: линия «турель → цель» и траектория растягиваются на сотни блоков, и вершины
 * уходят далеко за пределы видимости, хотя толку от них на экране нет.
 */
public final class DebugShapes {

    /** Предел отрисовки от камеры. 0 — не ограничивать (вне кадра / без камеры). */
    private static double maxDistanceSq = 0.0;
    private static Vec3 viewCenter = null;

    private DebugShapes() {
    }

    /** Задать камеру и предел дистанции на время одного кадра. */
    public static void beginFrame(Vec3 viewCenter, double maxDistance) {
        DebugShapes.viewCenter = viewCenter;
        DebugShapes.maxDistanceSq = maxDistance > 0.0 ? maxDistance * maxDistance : 0.0;
    }

    public static void endFrame() {
        DebugShapes.viewCenter = null;
        DebugShapes.maxDistanceSq = 0.0;
    }

    /** Вписывается ли точка в предел отрисовки. */
    public static boolean inRange(Vec3 p) {
        Vec3 c = viewCenter;
        if (c == null || maxDistanceSq <= 0.0) {
            return true;
        }
        return c.distanceToSqr(p) <= maxDistanceSq;
    }

    /** Отрезок. */
    public static void line(Matrix4f m, VertexConsumer v, Vec3 a, Vec3 b,
                            float r, float g, float bl, float a2) {
        if (!inRange(a) || !inRange(b)) {
            return;
        }
        v.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, a2).setNormal(0, 1, 0);
        v.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, a2).setNormal(0, 1, 0);
    }

    /** Каркас куба со стороной {@code size}, центр — {@code center}. */
    public static void box(Matrix4f m, VertexConsumer v, Vec3 center, double size,
                           float r, float g, float b, float a) {
        if (!inRange(center)) {
            return;
        }
        float h = (float) (size / 2.0);
        float x1 = (float) (center.x - h), y1 = (float) (center.y - h), z1 = (float) (center.z - h);
        float x2 = (float) (center.x + h), y2 = (float) (center.y + h), z2 = (float) (center.z + h);

        // нижняя грань
        line(m, v, new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), r, g, b, a);
        line(m, v, new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), r, g, b, a);
        line(m, v, new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), r, g, b, a);
        line(m, v, new Vec3(x1, y1, z2), new Vec3(x1, y1, z1), r, g, b, a);
        // верхняя грань
        line(m, v, new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), r, g, b, a);
        line(m, v, new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), r, g, b, a);
        line(m, v, new Vec3(x2, y2, z2), new Vec3(x1, y2, z2), r, g, b, a);
        line(m, v, new Vec3(x1, y2, z2), new Vec3(x1, y2, z1), r, g, b, a);
        // в��ртикали
        line(m, v, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), r, g, b, a);
        line(m, v, new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), r, g, b, a);
        line(m, v, new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), r, g, b, a);
        line(m, v, new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), r, g, b, a);
    }

    /** Каркас куба по координатам блока (угол в pos, размер 1). */
    public static void blockBox(Matrix4f m, VertexConsumer v, net.minecraft.core.BlockPos pos,
                                float r, float g, float b, float a) {
        box(m, v, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), 1.02, r, g, b, a);
    }

    /**
     * Каркас сферы: три окружности (XY, XZ, YZ) плюс «экватор» по XZ.
     * Дёшево, но на глаз читается как шар, чего для отладки фронта волны достаточно.
     */
    public static void sphere(Matrix4f m, VertexConsumer v, Vec3 center, double radius,
                              int segments,
                              float r, float g, float b, float a) {
        if (segments < 6) segments = 6;
        double rad = Math.max(radius, 0.05);
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2.0 * i / segments;
                double a1 = Math.PI * 2.0 * (i + 1) / segments;
                Vec3 p0 = circlePoint(center, rad, a0, axis);
                Vec3 p1 = circlePoint(center, rad, a1, axis);
                line(m, v, p0, p1, r, g, b, a);
            }
        }
    }

    private static Vec3 circlePoint(Vec3 c, double rad, double angle, int axis) {
        double u = Math.cos(angle) * rad;
        double w = Math.sin(angle) * rad;
        return switch (axis) {
            case 0 -> new Vec3(c.x, c.y + u, c.z + w);   // XY
            case 1 -> new Vec3(c.x + u, c.y, c.z + w);   // XZ
            default -> new Vec3(c.x + u, c.y + w, c.z);  // YZ
        };
    }

    /** Крестик на месте — удобный маркер без объёма. */
    public static void cross(Matrix4f m, VertexConsumer v, Vec3 p, double size,
                             float r, float g, float b, float a) {
        double s = size;
        line(m, v, p.add(-s, 0, 0), p.add(s, 0, 0), r, g, b, a);
        line(m, v, p.add(0, -s, 0), p.add(0, s, 0), r, g, b, a);
        line(m, v, p.add(0, 0, -s), p.add(0, 0, s), r, g, b, a);
    }
}
