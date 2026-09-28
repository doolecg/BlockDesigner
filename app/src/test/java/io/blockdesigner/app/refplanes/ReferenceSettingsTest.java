package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ReferenceSettingsTest {

    private static ViewInfo view(boolean ortho, ViewInfo.Side side) {
        return new ViewInfo(ortho, Optional.ofNullable(side), new Vec3(0, 0, 50), new Vec3(0, 0, -1), new Vec3(3, 4, 5));
    }

    private static ReferenceSettings image(int w, int h) {
        return ReferenceSettings.DEFAULT.withImage("key", "sketch.png", w, h);
    }

    @Test
    void planeTakesThePicturesAspectRatio() {
        ReferenceSettings wide = image(1920, 1080);
        assertThat(wide.aspect()).isCloseTo(16 / 9.0, within(1e-12));
        Vec3[] c = wide.corners();
        // One block tall, aspect wide, centred: bottom-left, bottom-right, top-right, top-left.
        assertThat(c[0].x()).isCloseTo(-8 / 9.0, within(1e-12));
        assertThat(c[0].y()).isEqualTo(-0.5);
        assertThat(c[2].x()).isCloseTo(8 / 9.0, within(1e-12));
        assertThat(c[2].y()).isEqualTo(0.5);
        assertThat(c[3].x()).isEqualTo(c[0].x());
        assertThat(c[2].x() - c[0].x()).isCloseTo(16 / 9.0, within(1e-12));
        Vec3[] tall = image(300, 600).corners();
        assertThat(tall[1].x() - tall[0].x()).isCloseTo(0.5, within(1e-12));
    }

    @Test
    void sizesAreNeverZero() {
        ReferenceSettings s = image(0, -5);
        assertThat(s.width()).isEqualTo(1);
        assertThat(s.aspect()).isEqualTo(1);
    }

    @Test
    void uvCoversThePictureTopDown() {
        // Bottom corners sample the bottom row (v = 1), top corners the top row (v = 0).
        assertThat(image(10, 10).uv()).containsExactly(0, 1, 1, 1, 1, 0, 0, 0);
    }

    @Test
    void uvOffsetScaleAndFlips() {
        ReferenceSettings s = image(10, 10).withUv(0.25, 0.1, 0.5, 2);
        assertThat(s.uv()).containsExactly(0.25, 2.1, 0.75, 2.1, 0.75, 0.1, 0.25, 0.1);
        assertThat(s.withFlip(true, false).uv()).containsExactly(0.75, 2.1, 0.25, 2.1, 0.25, 0.1, 0.75, 0.1);
        assertThat(s.withFlip(false, true).uv()).containsExactly(0.25, 0.1, 0.75, 0.1, 0.75, 2.1, 0.25, 2.1);
        // A zero scale is kept just above zero.
        assertThat(image(1, 1).withUv(0, 0, 0, 0).uScale()).isPositive();
    }

    @Test
    void showInFiltersViews() {
        ReferenceSettings s = image(4, 3);
        ViewInfo persp = view(false, null), ortho = view(true, null), front = view(true, ViewInfo.Side.FRONT),
                frontPersp = view(false, ViewInfo.Side.FRONT), top = view(true, ViewInfo.Side.TOP);
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.ALL).visibleIn(persp)).isTrue();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.ORTHO).visibleIn(persp)).isFalse();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.ORTHO).visibleIn(ortho)).isTrue();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.ORTHO).visibleIn(top)).isTrue();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.FRONT).visibleIn(front)).isTrue();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.FRONT).visibleIn(frontPersp)).isFalse();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.FRONT).visibleIn(top)).isFalse();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.TOP).visibleIn(top)).isTrue();
        assertThat(s.withShowIn(ReferenceSettings.ShowIn.TOP).visibleIn(ortho)).isFalse();
    }

    @Test
    void newImagesFaceTheViewTheyWereAddedIn() {
        Pose front = ReferenceSettings.placement(view(true, ViewInfo.Side.FRONT));
        assertThat(front.position()).isEqualTo(new Vec3(3, 4, 5));
        assertThat(front.rotation()).isEqualTo(ViewInfo.Side.FRONT.facing());
        assertThat(front.scale().y()).isEqualTo(ReferenceSettings.DEFAULT_SIZE);
        assertThat(ReferenceSettings.placement(view(true, ViewInfo.Side.RIGHT)).rotation()).isEqualTo(new Vec3(0, 90, 0));
        // Only in that view when it is orthographic, like Blender's align-to-view references.
        assertThat(ReferenceSettings.showInFor(view(true, ViewInfo.Side.LEFT))).isEqualTo(ReferenceSettings.ShowIn.LEFT);
        assertThat(ReferenceSettings.showInFor(view(false, ViewInfo.Side.LEFT))).isEqualTo(ReferenceSettings.ShowIn.ALL);
        assertThat(ReferenceSettings.showInFor(view(false, null))).isEqualTo(ReferenceSettings.ShowIn.ALL);
    }

    @Test
    void freeViewsTurnThePlaneTowardsTheCamera() {
        // Looking along +X (from the west): the plane's front must point back along -X.
        Vec3 r = ReferenceSettings.facingYaw(new Vec3(1, -0.3, 0));
        Pose p = Pose.IDENTITY.withRotation(r);
        assertThat(p.axis(2).x()).isCloseTo(-1, within(1e-9));
        assertThat(p.axis(2).z()).isCloseTo(0, within(1e-9));
        // Straight down: no yaw to take.
        assertThat(ReferenceSettings.facingYaw(new Vec3(0, -1, 0))).isEqualTo(new Vec3(0, 0, 0));
    }

    @Test
    void resetAspectMakesXMatchY() {
        assertThat(ReferenceSettings.resetAspect(new Vec3(30, 12, 5))).isEqualTo(new Vec3(12, 12, 5));
    }

    @Test
    void settingsRoundTrip() throws IOException {
        ReferenceSettings s = image(640, 480).withUv(0.1, -0.2, 0.75, 1.5).withOpacity(0.4)
                .withShowIn(ReferenceSettings.ShowIn.BOTTOM).withDepth(Drawing.Depth.IN_FRONT).withFlip(true, true);
        assertThat(ReferenceSettings.decode(s.encode())).isEqualTo(s);
        assertThat(ReferenceSettings.decode(new byte[0])).isEqualTo(ReferenceSettings.DEFAULT);
        assertThat(ReferenceSettings.decode(null)).isEqualTo(ReferenceSettings.DEFAULT);
    }

    @Test
    void newerSavesAreRefused() {
        byte[] data = image(1, 1).encode();
        data[3] = 99; // the format number
        assertThatThrownBy(() -> ReferenceSettings.decode(data)).isInstanceOf(IOException.class);
    }

    @Test
    void picturesAreReadAndShrunkKeepingTheirSize() throws IOException {
        BufferedImage img = new BufferedImage(4600, 2300, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x80FF0000);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        Images.Decoded d = Images.decode(png.toByteArray());
        assertThat(d.width()).isEqualTo(4600);
        assertThat(d.height()).isEqualTo(2300);
        assertThat(d.image().width()).isEqualTo(4096);
        assertThat(d.image().height()).isEqualTo(2048);
        assertThat(d.image().argb()).hasSize(4096 * 2048);
        assertThatThrownBy(() -> Images.decode(new byte[]{1, 2, 3})).isInstanceOf(IOException.class);
    }
}
