package org.minefortress.blueprints;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.minefortress.interfaces.FortressClientWorld;
import org.minefortress.network.ServerboundBlueprintTaskPacket;
import org.minefortress.network.helpers.FortressChannelNames;
import org.minefortress.network.helpers.FortressClientNetworkHelper;
import org.minefortress.tasks.BuildingManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BlueprintManager {

    private static final Vec3f WRONG_PLACEMENT_COLOR = new Vec3f(1.0F, 0.5F, 0.5F);

    private final MinecraftClient client;

    private BlueprintMetadata selectedStructure;
    private final Map<String, BlueprintRenderInfo> blueprintInfos = new HashMap<>();
    private BlockPos blueprintBuildPos = null;

    public BlueprintManager(MinecraftClient client) {
        this.client = client;
    }

    public void buildStructure(ChunkBuilder chunkBuilder) {
        final String selectedStructureId = selectedStructure.getId();
        final String file = selectedStructure.getFile();
        if(!blueprintInfos.containsKey(selectedStructureId)) {
            final BlockRotation rotation = selectedStructure.getRotation();
            final BlueprintRenderInfo blueprintRenderInfo = BlueprintRenderInfo.create(file, client.world, chunkBuilder, rotation);
            blueprintInfos.put(selectedStructureId, blueprintRenderInfo);
        }

        if(client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            final BlockPos blockPos = blockHitResult.getBlockPos();
            if(blockPos != null)
                this.blueprintInfos.get(selectedStructureId).rebuild(blockPos);
        }
    }

    public void tick() {
        if(!hasSelectedBlueprint()) return;
        blueprintBuildPos = getSelectedPos();
    }

    @Nullable
    private BlockPos getSelectedPos() {
        if(client.crosshairTarget instanceof BlockHitResult) {
            final BlockPos originalPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
            if(originalPos != null) return moveToStructureSize(originalPos);
        }
        return null;
    }

    private BlockPos moveToStructureSize(BlockPos pos) {
        final boolean posSolid = !BuildingManager.doesNotHaveCollisions(client.world, pos);
        final BlueprintRenderInfo selectedInfo = blueprintInfos.get(selectedStructure.getId());
        final Vec3i size = selectedInfo.getSize();
        final Vec3i halfSize = new Vec3i(size.getX() / 2, 0, size.getZ() / 2);
        BlockPos movedPos = pos.subtract(halfSize);
        movedPos = selectedInfo.getChunkRendererRegion().isStandsOnGround() ? movedPos.down() : movedPos;
        movedPos = posSolid? movedPos.up():movedPos;
        return movedPos;
    }

    public void renderLayer(RenderLayer renderLayer, MatrixStack matrices, double d, double e, double f, Matrix4f matrix4f) {
        int k;
        RenderSystem.assertThread(RenderSystem::isOnRenderThread);
        renderLayer.startDrawing();
        this.client.getProfiler().push("filterempty");
        this.client.getProfiler().swap(() -> "render_" + renderLayer);
        VertexFormat h = renderLayer.getVertexFormat();
        Shader shader = RenderSystem.getShader();
        BufferRenderer.unbindAll();
        for (int i = 0; i < 12; ++i) {
            k = RenderSystem.getShaderTexture(i);
            shader.addSampler("Sampler" + i, k);
        }
        if (shader.modelViewMat != null) {
            shader.modelViewMat.set(matrices.peek().getModel());
        }
        if (shader.projectionMat != null) {
            shader.projectionMat.set(matrix4f);
        }
        if (shader.colorModulator != null) {
            shader.colorModulator.set(WRONG_PLACEMENT_COLOR);
        }
        if (shader.fogStart != null) {
            shader.fogStart.set(RenderSystem.getShaderFogStart());
        }
        if (shader.fogEnd != null) {
            shader.fogEnd.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.fogColor != null) {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }
        if (shader.textureMat != null) {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }
        if (shader.gameTime != null) {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }
        RenderSystem.setupShaderLights(shader);
        shader.bind();
        GlUniform i = shader.chunkOffset;
        k = 0;

        final ChunkBuilder.BuiltChunk chunk = getBuiltChunk();
        if(chunk != null && !chunk.getData().isEmpty(renderLayer) && blueprintBuildPos != null) {
            VertexBuffer vertexBuffer = chunk.getBuffer(renderLayer);
            if (i != null) {
                i.set((float)((double)blueprintBuildPos.getX() - d), (float)((double)blueprintBuildPos.getY() - e), (float)((double)blueprintBuildPos.getZ() - f));
                i.upload();
            }
            vertexBuffer.drawVertices();
            k = 1;
        }
        if (i != null) {
            i.set(Vec3f.ZERO);
        }
        shader.unbind();
        if (k != 0) {
            h.endDrawing();
        }
        VertexBuffer.unbind();
        VertexBuffer.unbindVertexArray();
        this.client.getProfiler().pop();
        renderLayer.endDrawing();
    }

    @Nullable
    private ChunkBuilder.BuiltChunk getBuiltChunk() {
        return this.blueprintInfos.get(this.selectedStructure.getId()).getBuiltChunk();
    }


    public boolean hasSelectedBlueprint() {
        return selectedStructure != null;
    }

    public void selectStructure(BlueprintMetadata blueprintMetadata) {
        this.selectedStructure = blueprintMetadata;
    }

    public void buildCurrentStructure(BlockPos clickPos) {
        if(selectedStructure == null) throw new IllegalStateException("No blueprint selected");
        if(blueprintBuildPos == null) throw new IllegalStateException("No blueprint build position");

        UUID taskId = UUID.randomUUID();
        final FortressClientWorld world = (FortressClientWorld) client.world;
        if(world != null) {
            final Map<BlockPos, BlockState> structureData = blueprintInfos.get(selectedStructure.getId()).getChunkRendererRegion().getStructureData();
            final List<BlockPos> blocks = structureData
                    .entrySet()
                    .stream()
                    .filter(ent -> ent.getValue().getBlock() != Blocks.AIR)
                    .map(Map.Entry::getKey)
                    .map(it -> it.add(blueprintBuildPos))
                    .collect(Collectors.toList());
            world.getClientTasksHolder().addTask(taskId, blocks);
        }
        final ServerboundBlueprintTaskPacket serverboundBlueprintTaskPacket = new ServerboundBlueprintTaskPacket(taskId, selectedStructure.getId(), selectedStructure.getFile(), blueprintBuildPos, selectedStructure.getRotation());
        FortressClientNetworkHelper.send(FortressChannelNames.NEW_BLUEPRINT_TASK, serverboundBlueprintTaskPacket);
        
        clearStructure();
    }

    public void clearStructure() {
        this.selectedStructure = null;
    }

    public String getSelectedStructureName() {
        return this.selectedStructure != null ? this.selectedStructure.getName() : "";
    }

    public void rotateSelectedStructureClockwise() {
        if(selectedStructure == null) throw new IllegalStateException("No blueprint selected");
        this.selectedStructure.rotateRight();
    }

    public void rotateSelectedStructureCounterClockwise() {
        if(selectedStructure == null) throw new IllegalStateException("No blueprint selected");
        this.selectedStructure.rotateLeft();
    }

}
