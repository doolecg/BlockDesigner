package io.blockdesigner.assets;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockShapesTest {
    static final BakedModel CUBE = new BakedModel(List.of(), new boolean[6], true, false);

    static float[] px(List<float[]> boxes) {
        float[] b = boxes.getFirst();
        float[] out = new float[6];
        for (int i = 0; i < 6; i++) out[i] = b[i] * 16;
        return out;
    }

    static List<float[]> shape(String state, BakedModel m) {
        return BlockShapes.shape(BlockState.parse(state), m);
    }

    @Test
    void flowersGrassAndSaplingsUseMinecraftsShapes() {
        assertThat(px(shape("minecraft:poppy", CUBE))).containsExactly(5, 0, 5, 11, 10, 11);
        assertThat(px(shape("minecraft:short_grass", CUBE))).containsExactly(2, 0, 2, 14, 13, 14);
        assertThat(px(shape("minecraft:oak_sapling[stage=0]", CUBE))).containsExactly(2, 0, 2, 14, 12, 14);
        assertThat(px(shape("minecraft:sunflower[half=lower]", CUBE))).containsExactly(0, 0, 0, 16, 16, 16);
    }

    @Test
    void cropsGrowAndAmethystFacesTheWayItPoints() {
        assertThat(px(shape("minecraft:wheat[age=0]", CUBE))[4]).isEqualTo(2);
        assertThat(px(shape("minecraft:wheat[age=7]", CUBE))[4]).isEqualTo(16);
        assertThat(px(shape("minecraft:amethyst_cluster[facing=up,waterlogged=false]", CUBE))).containsExactly(3, 0, 3, 13, 7, 13);
        assertThat(px(shape("minecraft:amethyst_cluster[facing=north,waterlogged=false]", CUBE))).containsExactly(3, 3, 9, 13, 13, 16);
        assertThat(px(shape("minecraft:amethyst_cluster[facing=east,waterlogged=false]", CUBE))).containsExactly(0, 3, 3, 7, 13, 13);
    }

    @Test
    void dripstoneDripleafBambooAndCocoa() {
        assertThat(px(shape("minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]", CUBE))).containsExactly(5, 5, 5, 11, 16, 11);
        assertThat(px(shape("minecraft:pointed_dripstone[thickness=base,vertical_direction=up,waterlogged=false]", CUBE))).containsExactly(2, 0, 2, 14, 16, 14);
        // A level dripleaf is its leaf plus its stem; fully tilted, just the stem.
        assertThat(shape("minecraft:big_dripleaf[facing=north,tilt=none,waterlogged=false]", CUBE)).hasSize(2);
        assertThat(shape("minecraft:big_dripleaf[facing=north,tilt=full,waterlogged=false]", CUBE)).hasSize(1);
        assertThat(px(shape("minecraft:bamboo[age=0,leaves=large,stage=0]", CUBE))).containsExactly(3, 0, 3, 13, 16, 13);
        assertThat(px(shape("minecraft:cocoa[age=0,facing=east]", CUBE))).containsExactly(11, 7, 6, 15, 12, 10);
        assertThat(px(shape("minecraft:cocoa[age=2,facing=north]", CUBE))).containsExactly(4, 3, 1, 12, 12, 9);
    }

    @Test
    void signsHeadsBedsAndChests() {
        assertThat(px(shape("minecraft:oak_sign[rotation=0,waterlogged=false]", CUBE))).containsExactly(4, 0, 4, 12, 16, 12);
        assertThat(px(shape("minecraft:oak_wall_sign[facing=north,waterlogged=false]", CUBE))).containsExactly(0, 4.5f, 14, 16, 12.5f, 16);
        assertThat(shape("minecraft:oak_wall_hanging_sign[facing=north,waterlogged=false]", CUBE)).hasSize(2);
        assertThat(px(shape("minecraft:skeleton_skull[powered=false,rotation=0]", CUBE))).containsExactly(4, 0, 4, 12, 8, 12);
        assertThat(px(shape("minecraft:zombie_wall_head[facing=south,powered=false]", CUBE))).containsExactly(4, 4, 0, 12, 12, 8);
        assertThat(px(shape("minecraft:piglin_head[powered=false,rotation=0]", CUBE))).containsExactly(3, 0, 3, 13, 8, 13);
        // The head of a bed facing north has its legs at the north end; the foot at the south end.
        List<float[]> head = shape("minecraft:red_bed[facing=north,occupied=false,part=head]", CUBE);
        assertThat(head).hasSize(3);
        assertThat(head.get(1)[2]).isZero();
        assertThat(shape("minecraft:red_bed[facing=north,occupied=false,part=foot]", CUBE).get(1)[5]).isEqualTo(1f);
        assertThat(px(shape("minecraft:chest[facing=north,type=single,waterlogged=false]", CUBE))).containsExactly(1, 0, 1, 15, 14, 15);
        assertThat(px(shape("minecraft:chest[facing=north,type=left,waterlogged=false]", CUBE))).containsExactly(1, 0, 1, 16, 14, 15);
    }

    @Test
    void bannerPatternsReadBothFormats() {
        var nbt = new io.blockdesigner.core.nbt.CompoundTag().put("patterns", io.blockdesigner.core.nbt.ListTag.of(
                new io.blockdesigner.core.nbt.CompoundTag().putString("pattern", "minecraft:stripe_bottom").putString("color", "red"),
                new io.blockdesigner.core.nbt.CompoundTag().putString("pattern", "somemod:emblem").putString("color", "black")));
        assertThat(EntityModels.patterns(nbt)).containsExactly(
                new EntityModels.Pattern("minecraft:entity/banner/stripe_bottom", "red"),
                new EntityModels.Pattern("somemod:entity/banner/emblem", "black"));
        var old = new io.blockdesigner.core.nbt.CompoundTag().put("Patterns", io.blockdesigner.core.nbt.ListTag.of(
                new io.blockdesigner.core.nbt.CompoundTag().putString("Pattern", "cre").putInt("Color", 15)));
        assertThat(EntityModels.patterns(old)).containsExactly(new EntityModels.Pattern("minecraft:entity/banner/creeper", "black"));
        assertThat(EntityModels.patterns(null)).isEmpty();
    }

    @Test
    void moddedCrossPlantsGetTheGrassShapeAndOtherBlocksKeepTheirModel() {
        // Two diagonal planes, like any cross-shaped plant model.
        float r = 0.7071f;
        BakedQuad a = new BakedQuad(new float[]{0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1}, new float[8], new float[]{r, 0, -r}, Dir.EAST, null, -1, RenderLayer.CUTOUT, true, null);
        BakedQuad b = new BakedQuad(new float[]{0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0, 0}, new float[8], new float[]{r, 0, r}, Dir.EAST, null, -1, RenderLayer.CUTOUT, true, null);
        BakedModel cross = new BakedModel(List.of(a, b), new boolean[6], true, false);
        assertThat(px(shape("somemod:glowing_fern", cross))).containsExactly(2, 0, 2, 14, 13, 14);
        assertThat(shape("minecraft:oak_slab[type=bottom,waterlogged=false]", CUBE)).isNull();
    }
}
