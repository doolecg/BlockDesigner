package io.blockdesigner.core.project;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.transform.Transform;
import io.blockdesigner.core.version.McVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFileTest {
    @TempDir
    Path tmp;

    @Test
    void roundTripKeepsLayersCoordinatesAndFlags() throws Exception {
        Structure s = new Structure();
        s.set(-3, -2, 5, BlockState.of("stone"));
        s.set(4, 7, -1, BlockState.parse("oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"));
        s.setBlockEntity(new BlockPos(4, 7, -1), new CompoundTag().putString("id", "minecraft:test"));
        Layer a = new Layer("House", s);
        a.setOffset(new BlockPos(100, 64, -20));
        a.setTransform(new Transform(3, Transform.Mirror.X));
        a.setGhost(true);
        a.setColor(0x123456);
        Layer b = new Layer("Empty", new Structure());
        b.setVisible(false);
        b.setLocked(true);

        Path file = tmp.resolve("test.bdproj");
        ProjectFile.save(new ProjectFile.Contents("Village", McVersion.byId("1.21.1").orElseThrow(), List.of(a, b), a.id(),
                Map.of("chat.json", "[]".getBytes())), file);
        ProjectFile.Contents c = ProjectFile.load(file);

        assertThat(c.name()).isEqualTo("Village");
        assertThat(c.targetVersion().id()).isEqualTo("1.21.1");
        assertThat(c.activeLayerId()).isEqualTo(a.id());
        assertThat(c.layers()).hasSize(2);
        Layer la = c.layers().getFirst();
        assertThat(la.structure().contentEquals(s)).isTrue();
        assertThat(la.offset()).isEqualTo(a.offset());
        assertThat(la.transform()).isEqualTo(a.transform());
        assertThat(la.ghost()).isTrue();
        assertThat(la.color()).isEqualTo(0x123456);
        Layer lb = c.layers().get(1);
        assertThat(lb.visible()).isFalse();
        assertThat(lb.locked()).isTrue();
        assertThat(lb.structure().blockCount()).isZero();
        assertThat(new String(c.extras().get("chat.json"))).isEqualTo("[]");
    }
}
