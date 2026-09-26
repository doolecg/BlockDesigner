package io.blockdesigner.plugin;

import io.blockdesigner.plugin.ToolEvent.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PoseTest {

    private static void near(Vec3 v, double x, double y, double z) {
        assertThat(v.x()).isCloseTo(x, within(1e-9));
        assertThat(v.y()).isCloseTo(y, within(1e-9));
        assertThat(v.z()).isCloseTo(z, within(1e-9));
    }

    @Test
    void identityLeavesPointsAlone() {
        near(Pose.IDENTITY.toWorld(1, 2, 3), 1, 2, 3);
        near(Pose.at(10, 0, -4).toWorld(1, 2, 3), 11, 2, -1);
    }

    @Test
    void scaleThenRotateThenMove() {
        // 90° about Y turns +X to -Z (right-handed), after scaling X by 2.
        Pose p = new Pose(new Vec3(5, 0, 0), new Vec3(0, 90, 0), new Vec3(2, 1, 1));
        near(p.toWorld(1, 0, 0), 5, 0, -2);
        near(p.toWorld(0, 1, 0), 5, 1, 0);
        near(p.toLocal(p.toWorld(0.3, -0.7, 0.25)), 0.3, -0.7, 0.25);
    }

    @Test
    void eulerOrderIsXThenYThenZ() {
        Pose p = Pose.IDENTITY.withRotation(new Vec3(90, 0, 90));
        // X first: +Y goes to +Z; then Z (about world Z) leaves +Z alone.
        near(p.toWorld(0, 1, 0), 0, 0, 1);
        // +X: unchanged by X, then Z turns it to +Y.
        near(p.toWorld(1, 0, 0), 0, 1, 0);
    }

    @Test
    void rotatingAboutAPivotSwingsThePosition() {
        Pose p = Pose.at(2, 0, 0).rotatedAbout(1, 90, new Vec3(0, 0, 0));
        near(p.position(), 0, 0, -2);
        near(p.rotation(), 0, 90, 0);
        // Turning back comes home exactly.
        Pose back = p.rotatedAbout(1, -90, new Vec3(0, 0, 0));
        near(back.position(), 2, 0, 0);
        near(back.rotation(), 0, 0, 0);
    }

    @Test
    void rotatingComposesWithTheExistingRotation() {
        Pose p = Pose.IDENTITY.withRotation(new Vec3(30, 0, 0)).rotatedAbout(2, 45, new Vec3(0, 0, 0));
        near(p.rotation(), 30, 0, 45);
        // A world-X turn of an object already turned about Z isn't a plain X change; the result still maps points right.
        Pose q = Pose.IDENTITY.withRotation(new Vec3(0, 0, 90)).rotatedAbout(0, 90, new Vec3(0, 0, 0));
        near(q.toWorld(1, 0, 0), 0, 0, 1);
    }

    @Test
    void scaledByMultipliesInOwnAxes() {
        Pose p = new Pose(new Vec3(1, 2, 3), new Vec3(0, 45, 0), new Vec3(2, 3, 1)).scaledBy(1.5, 2, 1);
        near(p.scale(), 3, 6, 1);
        near(p.position(), 1, 2, 3);
    }

    @Test
    void axisGivesTurnedDirections() {
        Pose p = Pose.IDENTITY.withRotation(ViewInfo.Side.RIGHT.facing());
        near(p.axis(2), 1, 0, 0);
        near(p.axis(0), 0, 0, -1);
    }

    @Test
    void sideFacingTurnsThePlaneTowardsTheCamera() {
        // A plane's normal (+Z) must point back at the camera, and its up (+Y) must be screen up.
        near(Pose.IDENTITY.withRotation(ViewInfo.Side.FRONT.facing()).axis(2), 0, 0, 1);
        near(Pose.IDENTITY.withRotation(ViewInfo.Side.BACK.facing()).axis(2), 0, 0, -1);
        near(Pose.IDENTITY.withRotation(ViewInfo.Side.LEFT.facing()).axis(2), -1, 0, 0);
        Pose top = Pose.IDENTITY.withRotation(ViewInfo.Side.TOP.facing());
        near(top.axis(2), 0, 1, 0);
        near(top.axis(1), 0, 0, -1);
        Pose bottom = Pose.IDENTITY.withRotation(ViewInfo.Side.BOTTOM.facing());
        near(bottom.axis(2), 0, -1, 0);
        near(bottom.axis(1), 0, 0, 1);
    }

    @Test
    void gimbalLockStillRoundTrips() {
        Pose p = Pose.IDENTITY.withRotation(new Vec3(20, 90, 0)).rotatedAbout(1, 0, new Vec3(0, 0, 0));
        Pose q = Pose.IDENTITY.withRotation(new Vec3(20, 90, 0));
        near(p.toWorld(1, 2, 3), q.toWorld(1, 2, 3).x(), q.toWorld(1, 2, 3).y(), q.toWorld(1, 2, 3).z());
    }
}
