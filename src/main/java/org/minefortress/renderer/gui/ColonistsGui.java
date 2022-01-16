package org.minefortress.renderer.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.minefortress.entity.Colonist;

public class ColonistsGui extends FortressGuiScreen{

    private int colonistsCount = 0;
    private boolean hovered;
    protected ColonistsGui(MinecraftClient client, ItemRenderer itemRenderer) {
        super(client, itemRenderer);
    }

    @Override
    void tick() {
        if(client.world != null) {
            int count = 0;
            for(Entity entity: client.world.getEntities()) {
                if(entity instanceof Colonist) {
                    count++;
                }
            }
            colonistsCount = count;
        } else {
            colonistsCount = 0;
        }
    }

    @Override
    void render(MatrixStack p, TextRenderer font, int screenWidth, int screenHeight, double mouseX, double mouseY, float delta) {
        final String colonistsCountString = "x" + colonistsCount;

        final int iconX = screenWidth / 2 - 91;
        final int iconY = screenHeight - 40;
        final float textX = screenWidth / 2f - 91 + 15;
        final int textY = screenHeight - 35;

        final int boundLeftX = iconX;
        final int boundRightX = (int)textX + font.getWidth(colonistsCountString);
        final int boundTopY = iconY;
        final int boundBottomY = iconY + 20;

        this.hovered = mouseX >= boundLeftX && mouseX <= boundRightX && mouseY >= boundTopY && mouseY < boundBottomY;

        RenderSystem.setShaderTexture(0, GUI_ICONS_TEXTURE);

        super.itemRenderer.renderGuiItemIcon(new ItemStack(Items.PLAYER_HEAD), iconX, iconY);

        font.draw(p, colonistsCountString, textX, textY, 0xFFFFFF);

        if(this.isHovered()) {
            super.renderTooltip(p, Text.of("Your Pawns count"), (int)mouseX, (int)mouseY);
        }
    }

    @Override
    boolean isHovered() {
        return this.hovered;
    }
}