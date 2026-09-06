package dev.originsx.skilltree;

import dev.originsx.skilltree.data.SkillTreeLoader;
import dev.originsx.skilltree.item.SkillTreeItems;
import dev.originsx.skilltree.net.SkillTreeNetwork;
import dev.originsx.skilltree.tree.SkillGate;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SkillTreeMod.MOD_ID)
public final class SkillTreeMod {
    public static final String MOD_ID = "originsx_skilltree";
    public static final String NAME = "OriginsX - Skill Tree";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public SkillTreeMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                SkillTreeConfig.SPEC, "originsx-skilltree.toml");

        modBus.addListener(SkillTreeNetwork::register);
        SkillTreeItems.register(modBus);
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            dev.originsx.skilltree.client.SkillTreeClient.init(modBus);
        }

        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onRaceChanged);

        SkillGate.install();
    }

    private void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MOD_ID, "skill_trees"), new SkillTreeLoader());
    }
}
