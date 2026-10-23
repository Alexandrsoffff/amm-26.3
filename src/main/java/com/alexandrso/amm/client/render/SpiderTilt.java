package com.alexandrso.amm.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Belly-to-wall tilt of a spider climbing a wall (port of SpiderWallRenderHandler.compute):
 * builds a rotation that blends the upright frame into a frame lying on the wall plane, and the
 * translation that keeps the lying body centred on the (reshaped) hitbox.
 */
public final class SpiderTilt {
    private SpiderTilt() {
    }

    /** Applies the tilt to a pose stack that is at the entity origin, BEFORE the body yaw is applied. */
    public static void apply(PoseStack poseStack, AmmPoseData d, float bodyRotDegrees, float width, float height) {
        float pr = d.spiderProgress;
        if (pr <= 0.001F || d.spiderWall == null) return;
        double p = pr * pr * (3.0D - 2.0D * pr); // smoothstep

        double yaw = Math.toRadians(bodyRotDegrees);
        double[] u = {0, 1, 0};
        double[] f = {-Math.sin(yaw), 0, Math.cos(yaw)};

        double nx = -d.spiderWall.getStepX();
        double nz = -d.spiderWall.getStepZ();
        double tx = -nz, tz = nx;
        double c = Math.cos(d.spiderTheta), sn = Math.sin(d.spiderTheta);
        double[] fw = {sn * tx, c, sn * tz};

        double[] u2 = normalize(new double[] {u[0] * (1 - p) + nx * p, u[1] * (1 - p), u[2] * (1 - p) + nz * p});
        double[] f2 = {f[0] * (1 - p) + fw[0] * p, f[1] * (1 - p) + fw[1] * p, f[2] * (1 - p) + fw[2] * p};
        double dot = f2[0] * u2[0] + f2[1] * u2[1] + f2[2] * u2[2];
        f2 = new double[] {f2[0] - u2[0] * dot, f2[1] - u2[1] * dot, f2[2] - u2[2] * dot};
        if (len(f2) < 1.0E-4D) f2 = fw;
        f2 = normalize(f2);

        double[] side = cross(u, f);
        double[] side2 = cross(u2, f2);

        double[][] t = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 3; k++) {
                t[r][k] = side2[r] * side[k] + u2[r] * u[k] + f2[r] * f[k];
            }
        }

        double c0 = height / 2.0D;
        double c1 = c0 + (width / 2.0D - c0) * p;
        float[] m = new float[16]; // column-major
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                m[col * 4 + row] = (float) t[row][col];
            }
        }
        m[12] = (float) -(t[0][1] * c0);
        m[13] = (float) (c1 - t[1][1] * c0);
        m[14] = (float) -(t[2][1] * c0);
        m[15] = 1.0F;

        poseStack.mulPose(new Matrix4f().set(m));
    }

    private static double len(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double[] normalize(double[] v) {
        double l = len(v);
        return l < 1.0E-9D ? new double[] {0, 1, 0} : new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
