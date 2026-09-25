package io.blockdesigner.assets;

import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Bundled (jar-in-jar) mods such as Create Aeronautics keep their assets in nested jars. */
class NestedJarIT {
    @Test
    void loadsBlocksFromNestedJars() throws Exception {
        var scan = new McInstallLocator().scan();
        var inst = scan.instances().stream().filter(i -> i.name().contains("Aeronautics")).findFirst();
        Assumptions.assumeTrue(inst.isPresent());
        var jar = scan.jarFor(inst.get().mcVersion());
        Assumptions.assumeTrue(jar.isPresent());
        var mods = inst.get().mods().stream().filter(p -> p.getFileName().toString().contains("aeronautics-bundled")).toList();
        Assumptions.assumeTrue(!mods.isEmpty() && Files.exists(mods.getFirst()));
        try (BlockAssets a = BlockAssets.open(jar.get().jar(), mods, List.of(), null)) {
            assertThat(a.registry().contains("aeronautics:white_envelope")).isTrue();
            assertThat(a.model(BlockState.of("aeronautics:white_envelope")).missing()).isFalse();
        }
    }
}
