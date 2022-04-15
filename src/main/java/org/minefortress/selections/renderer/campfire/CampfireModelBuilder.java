package org.minefortress.selections.renderer.campfire;

import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;

public class CampfireModelBuilder {

    private final BlockBufferBuilderStorage blockBufferBuilders;

    private BuiltCampfire builtCampfire;

    public CampfireModelBuilder(BlockBufferBuilderStorage blockBuffersBuilders) {
        this.blockBufferBuilders = blockBuffersBuilders;
    }

    public void build() {
        if(this.builtCampfire == null) {
            this.builtCampfire = new BuiltCampfire();
            this.builtCampfire.build(blockBufferBuilders);
        }
    }

    public BuiltCampfire getOrBuildCampfire() {
        if(builtCampfire == null) {
            build();
        }
        return this.builtCampfire;
    }

    public void close() {
        if(this.builtCampfire != null) {
            this.builtCampfire.close();
        }
    }

}
