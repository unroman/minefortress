package org.minefortress.fortress.buildings;

import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.*;

class FortressBuildingBlockData {

    private int blockPointer = 0;
    private final List<PositionedState> referenceState = new ArrayList<>();
    private final Map<BlockPos, BuildingBlockState> actualState = new HashMap<>();

    FortressBuildingBlockData(Map<BlockPos, BlockState> preservedState) {
        for (Map.Entry<BlockPos, BlockState> entry : preservedState.entrySet()) {
            final var pos = entry.getKey();
            final var state = entry.getValue();
            final var block = state.getBlock();
            if(block == Blocks.AIR)
                continue;
            final var positionedState = new PositionedState(pos, block);
            this.referenceState.add(positionedState);
            this.actualState.put(pos, BuildingBlockState.PRESERVED);
        }
    }

    private FortressBuildingBlockData(NbtCompound tag) {
        if(tag.contains("pointer", NbtType.NUMBER))
            blockPointer = tag.getInt("pointer");

        if(tag.contains("referenceState", NbtType.LIST)) {
            final var list = tag.getList("referenceState", NbtType.COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                final var compound = list.getCompound(i);
                final var pos = BlockPos.fromLong(compound.getLong("pos"));
                final var block = Registry.BLOCK.get(compound.getInt("block"));
                final var positionedState = new PositionedState(pos, block);
                referenceState.add(positionedState);
            }
        }

        if(tag.contains("actualState", NbtType.LIST)) {
            final var list = tag.getList("actualState", NbtType.COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                final var compound = list.getCompound(i);
                final var pos = BlockPos.fromLong(compound.getLong("pos"));
                final var block = BuildingBlockState.valueOf(compound.getString("block"));
                actualState.put(pos, block);
            }
        }
    }

    boolean checkTheNextBlocksState(int blocksAmount, ServerWorld world) {
        if(world.getRegistryKey() != World.OVERWORLD)
            throw new IllegalArgumentException("The world must be the overworld");

        boolean stateUpdated = false;
        for (int i = 0; i < blocksAmount; i++) {
            blockPointer = blockPointer % referenceState.size();
            final var state = referenceState.get(blockPointer);
            final var pos = state.pos;
            final var block = state.block;

            final var actualBlock = world.getBlockState(pos).getBlock();

            final var previousState = actualState.getOrDefault(pos, BuildingBlockState.PRESERVED);
            final var newState = Objects.equals(block, actualBlock)? BuildingBlockState.PRESERVED : BuildingBlockState.DESTROYED;

            actualState.put(pos, newState);

            blockPointer++;
            stateUpdated = stateUpdated || previousState != newState;
        }

        return stateUpdated;
    }

    int getHealth() {
        final var preserved = actualState.values().stream().filter(state -> state == BuildingBlockState.PRESERVED).count();
        final var delta = (float) preserved / (float) actualState.size();
        return (int)MathHelper.clampedLerpFromProgress(delta, 0.5f, 1, 0, 100);
    }

    NbtCompound toNbt() {
        final var tag = new NbtCompound();
        final var preservedStateList = new NbtList();
        for (PositionedState state : referenceState) {
            final var compound = new NbtCompound();
            compound.putLong("pos", state.pos.asLong());
            compound.putInt("block", Registry.BLOCK.getRawId(state.block));
            preservedStateList.add(compound);
        }
        tag.put("referenceState", preservedStateList);

        final var actualStateList = new NbtList();
        for (Map.Entry<BlockPos, BuildingBlockState> entry : actualState.entrySet()) {
            final var compound = new NbtCompound();
            compound.putLong("pos", entry.getKey().asLong());
            compound.putString("block", entry.getValue().name());
            actualStateList.add(compound);
        }

        tag.put("actualState", actualStateList);
        tag.putInt("pointer", blockPointer);

        return tag;
    }

    void attack(HostileEntity attacker) {
        if(attacker.getWorld().random.nextFloat() < 0.4f) return;
        for (Map.Entry<BlockPos, BuildingBlockState> entries : actualState.entrySet()) {
            final var pos = entries.getKey();
            final var state = entries.getValue();
            if(state == BuildingBlockState.DESTROYED)
                continue;
            final var world = attacker.getWorld();
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            break;
        }
    }

    static FortressBuildingBlockData fromNbt(NbtCompound compound) {
        return new FortressBuildingBlockData(compound);
    }

    private record PositionedState(BlockPos pos, Block block) {}

    private enum BuildingBlockState {
        DESTROYED,
        PRESERVED,
    }

}
