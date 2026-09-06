package dev.originsx.skilltree;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class SkillTreeConfig {

    public static final ModConfigSpec SPEC;
    public static final SkillTreeConfig INSTANCE;

    static {
        Pair<SkillTreeConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
                .configure(SkillTreeConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.DoubleValue tier1Factor;
    public final ModConfigSpec.DoubleValue tier2Factor;
    public final ModConfigSpec.IntValue maxTier;
    public final ModConfigSpec.IntValue canvasWidth;
    public final ModConfigSpec.IntValue canvasHeight;
    public final ModConfigSpec.IntValue cellSize;
    public final ModConfigSpec.IntValue nodeSize;
    public final ModConfigSpec.IntValue defaultCost1;
    public final ModConfigSpec.IntValue defaultCost2;
    public final ModConfigSpec.IntValue defaultCost3;

    private SkillTreeConfig(ModConfigSpec.Builder builder) {
        builder.push("tiers");
        tier1Factor = builder
                .comment("Power scaling factor for tier 1 (0.0 - 1.0)")
                .defineInRange("tier1_factor", 0.34, 0.01, 1.0);
        tier2Factor = builder
                .comment("Power scaling factor for tier 2 (0.0 - 1.0)")
                .defineInRange("tier2_factor", 0.67, 0.01, 1.0);
        maxTier = builder
                .comment("Maximum tier a node can reach")
                .defineInRange("max_tier", 3, 1, 10);
        builder.pop();

        builder.push("costs");
        defaultCost1 = builder
                .comment("Default shard cost for tier 1 unlock")
                .defineInRange("default_cost_1", 2, 0, 999);
        defaultCost2 = builder
                .comment("Default shard cost for tier 2 unlock")
                .defineInRange("default_cost_2", 3, 0, 999);
        defaultCost3 = builder
                .comment("Default shard cost for tier 3 unlock")
                .defineInRange("default_cost_3", 5, 0, 999);
        builder.pop();

        builder.push("editor");
        canvasWidth = builder
                .comment("Canvas width in pixels for the skill tree editor")
                .defineInRange("canvas_width", 1600, 400, 8000);
        canvasHeight = builder
                .comment("Canvas height in pixels for the skill tree editor")
                .defineInRange("canvas_height", 1200, 400, 8000);
        cellSize = builder
                .comment("Grid cell size in pixels (used for legacy coordinate conversion)")
                .defineInRange("cell_size", 34, 8, 128);
        nodeSize = builder
                .comment("Node button size in pixels")
                .defineInRange("node_size", 26, 8, 128);
        builder.pop();
    }
}
