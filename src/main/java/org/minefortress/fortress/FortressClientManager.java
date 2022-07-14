package org.minefortress.fortress;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.minefortress.MineFortressMod;
import org.minefortress.entity.Colonist;
import org.minefortress.fight.ClientFightManager;
import org.minefortress.fight.ClientFightSelectionManager;
import org.minefortress.fortress.resources.client.ClientResourceManager;
import org.minefortress.fortress.resources.client.ClientResourceManagerImpl;
import org.minefortress.interfaces.FortressMinecraftClient;
import org.minefortress.network.ServerboundFortressCenterSetPacket;
import org.minefortress.network.ServerboundSetGamemodePacket;
import org.minefortress.network.helpers.FortressChannelNames;
import org.minefortress.network.helpers.FortressClientNetworkHelper;
import org.minefortress.professions.ClientProfessionManager;
import org.minefortress.utils.BuildingHelper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class FortressClientManager extends AbstractFortressManager {

    private final ClientProfessionManager professionManager;
    private final ClientResourceManager resourceManager = new ClientResourceManagerImpl();
    private final ClientFightManager fightManager;

    private UUID id;

    private boolean initialized = false;

    private BlockPos fortressCenter = null;
    private int colonistsCount = 0;

    private FortressToast setCenterToast;

    private BlockPos posAppropriateForCenter;
    private BlockPos oldPosAppropriateForCenter;

    private Colonist selectedColonist;
    private Vec3d selectedColonistDelta;

    private List<EssentialBuildingInfo> buildings = new ArrayList<>();
    private Map<Block, List<BlockPos>> specialBlocks = new HashMap<>();
    private Map<Block, List<BlockPos>> blueprintsSpecialBlocks = new HashMap<>();

    private FortressGamemode gamemode;

    private boolean isInCombat;

    private int maxColonistsCount;

    public FortressClientManager() {
        professionManager = new ClientProfessionManager(() -> ((FortressMinecraftClient) MinecraftClient.getInstance()).getFortressClientManager());
        fightManager = new ClientFightManager(() -> this);
    }

    public void select(Colonist colonist) {
        if(isInCombat) {
            final var mouse = MinecraftClient.getInstance().mouse;
            final var selectionManager = fightManager.getSelectionManager();
            selectionManager.startSelection(mouse.getX(), mouse.getY(), colonist.getPos());
            selectionManager.updateSelection(mouse.getX(), mouse.getY(), colonist.getPos());
            selectionManager.endSelection();

            selectedColonist = null;
            return;
        }
        this.selectedColonist = colonist;
        final Vec3d entityPos = colonist.getPos();
        final Vec3d playerPos = MinecraftClient.getInstance().player.getPos();

        selectedColonistDelta = entityPos.subtract(playerPos);
    }

    public void updateBuildings(List<EssentialBuildingInfo> buildings) {
        this.buildings = buildings;
    }

    public void setSpecialBlocks(Map<Block, List<BlockPos>> specialBlocks, Map<Block, List<BlockPos>> blueprintSpecialBlocks) {
        this.specialBlocks = specialBlocks;
        this.blueprintsSpecialBlocks = blueprintSpecialBlocks;
    }

    public boolean isSelectingColonist() {
        return selectedColonist != null && !isInCombat;
    }

    public Colonist getSelectedColonist() {
        return selectedColonist;
    }

    public void stopSelectingColonist() {
        this.selectedColonist = null;
        this.selectedColonistDelta = null;
    }

    public Vec3d getProperCameraPosition() {
        if(!isSelectingColonist()) throw new IllegalStateException("No colonist selected");
        return this.selectedColonist.getPos().subtract(selectedColonistDelta);
    }

    public int getColonistsCount() {
        return colonistsCount;
    }

    public void sync(int colonistsCount, BlockPos fortressCenter, FortressGamemode gamemode, UUID fortressId, int maxColonistsCount) {
        this.colonistsCount = colonistsCount;
        this.fortressCenter = fortressCenter;
        this.gamemode = gamemode;
        this.id = fortressId;
        this.maxColonistsCount = maxColonistsCount;
        initialized = true;
    }

    public UUID getId() {
        return id;
    }

    public void tick(FortressMinecraftClient fortressClient) {
        if(isSelectingColonist() && selectedColonist.isDead()) stopSelectingColonist();

        final MinecraftClient client = (MinecraftClient) fortressClient;
        if(
                client.world == null ||
                client.interactionManager == null ||
                client.interactionManager.getCurrentGameMode() != MineFortressMod.FORTRESS
        ) {
            if(setCenterToast != null) {
                setCenterToast.hide();
                setCenterToast = null;
            }

            posAppropriateForCenter = null;
            return;
        }
        if(!initialized) return;
        if(isFortressInitializationNeeded()) {
            if(setCenterToast == null) {
                this.setCenterToast = new FortressToast("Set up your Fortress", "Right-click to place", Items.CAMPFIRE);
                client.getToastManager().add(setCenterToast);
            }

            final BlockPos hoveredBlockPos = fortressClient.getHoveredBlockPos();
            if(hoveredBlockPos!=null && !hoveredBlockPos.equals(BlockPos.ORIGIN)) {
                if(hoveredBlockPos.equals(oldPosAppropriateForCenter)) return;

                BlockPos cursor = hoveredBlockPos;
                while (!BuildingHelper.canPlaceBlock(client.world, cursor))
                    cursor = cursor.up();

                while (BuildingHelper.canPlaceBlock(client.world, cursor.down()))
                    cursor = cursor.down();

                posAppropriateForCenter = cursor.toImmutable();
            }
        }
    }


    public BlockPos getPosAppropriateForCenter() {
        return posAppropriateForCenter;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isFortressInitializationNeeded() {
        return initialized && fortressCenter == null && this.gamemode != FortressGamemode.NONE;
    }

    public void setupFortressCenter() {
        if(fortressCenter!=null) throw new IllegalStateException("Fortress center already set");
        this.setCenterToast.hide();
        this.setCenterToast = null;
        fortressCenter = posAppropriateForCenter;
        posAppropriateForCenter = null;
        final ServerboundFortressCenterSetPacket serverboundFortressCenterSetPacket = new ServerboundFortressCenterSetPacket(fortressCenter);
        FortressClientNetworkHelper.send(FortressChannelNames.FORTRESS_SET_CENTER, serverboundFortressCenterSetPacket);

        final MinecraftClient client = MinecraftClient.getInstance();
        final WorldRenderer worldRenderer = client.worldRenderer;


        if(worldRenderer!=null) {
            worldRenderer.scheduleBlockRenders(fortressCenter.getX(), fortressCenter.getY(), fortressCenter.getZ());
            worldRenderer.scheduleTerrainUpdate();
        }
    }

    public void updateRenderer(WorldRenderer worldRenderer) {
        if(oldPosAppropriateForCenter == posAppropriateForCenter) return;
        final BlockPos posAppropriateForCenter = this.getPosAppropriateForCenter();
        if(posAppropriateForCenter != null) {
            oldPosAppropriateForCenter = posAppropriateForCenter;
            final BlockPos start = posAppropriateForCenter.add(-2, -2, -2);
            final BlockPos end = posAppropriateForCenter.add(2, 2, 2);
            worldRenderer.scheduleBlockRenders(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());
            worldRenderer.scheduleTerrainUpdate();
        }
    }

    public List<BlockPos> getBuildingSelection(BlockPos pos) {
        for(EssentialBuildingInfo building : buildings){
            final BlockPos start = building.getStart();
            final BlockPos end = building.getEnd();
            if(isPosBetween(pos, start, end)){
                return StreamSupport
                        .stream(BlockPos.iterate(start, end).spliterator(), false)
                        .map(BlockPos::toImmutable)
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    public ClientProfessionManager getProfessionManager() {
        return professionManager;
    }

    @Override
    public boolean hasRequiredBuilding(String requirementId, int minCount) {
        if(requirementId.startsWith("miner") || requirementId.startsWith("lumberjack") || requirementId.startsWith("warrior")) {
            return buildings.stream()
                    .filter(b -> b.getRequirementId().equals(requirementId))
                    .mapToInt(EssentialBuildingInfo::getBedsCount)
                    .sum() > minCount;
        }
        if(requirementId.equals("shooting_gallery"))
            minCount = 0;
        return buildings.stream().filter(b -> b.getRequirementId().equals(requirementId)).count() > minCount;
    }

    @Override
    public boolean hasRequiredBlock(Block block, boolean blueprint, int minCount) {
        if(blueprint)
            return this.blueprintsSpecialBlocks.getOrDefault(block, Collections.emptyList()).size() > minCount;
        else
            return this.specialBlocks.getOrDefault(block, Collections.emptyList()).size() > minCount;
    }

    @Override
    public int getTotalColonistsCount() {
        return colonistsCount;
    }

    @Override
    public void setGamemode(FortressGamemode gamemode) {
        if(gamemode == null) throw new IllegalArgumentException("Gamemode cannot be null");
        if(gamemode == FortressGamemode.NONE) throw new IllegalArgumentException("Gamemode cannot be NONE");
        final ServerboundSetGamemodePacket serverboundSetGamemodePacket = new ServerboundSetGamemodePacket(gamemode);
        FortressClientNetworkHelper.send(FortressChannelNames.FORTRESS_SET_GAMEMODE, serverboundSetGamemodePacket);
    }

    public boolean gamemodeNeedsInitialization() {
        return this.initialized && this.gamemode == FortressGamemode.NONE;
    }

    public boolean isCreative() {
        return this.gamemode == FortressGamemode.CREATIVE;
    }

    public boolean isSurvival() {
        return this.gamemode != null && this.gamemode == FortressGamemode.SURVIVAL;
    }

    public ClientResourceManager getResourceManager() {
        return resourceManager;
    }

    private boolean isPosBetween(BlockPos pos, BlockPos start, BlockPos end) {
        return pos.getX() >= start.getX() && pos.getX() <= end.getX() &&
                pos.getY() >= start.getY() && pos.getY() <= end.getY() &&
                pos.getZ() >= start.getZ() && pos.getZ() <= end.getZ();
    }

    public boolean isInCombat() {
        return isInCombat;
    }

    public void setInCombat(boolean inCombat) {
        isInCombat = inCombat;
    }

    public ClientFightManager getFightManager() {
        return fightManager;
    }

    public int getMaxColonistsCount() {
        return maxColonistsCount;
    }
}
