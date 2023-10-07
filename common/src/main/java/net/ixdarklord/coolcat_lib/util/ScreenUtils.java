package net.ixdarklord.coolcat_lib.util;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.stream.Collectors;

public class ScreenUtils extends GuiComponent {
    public static Component limitComponent(Component component, int maxWidth) {
        String text = component.getString(maxWidth);
        return component.getString().length() >= maxWidth
                ? Component.literal(text.substring(0, text.length() - 3) + "...").withStyle(component.getStyle())
                : component;
    }
    public static List<Component> splitComponent(Component component, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        return font.getSplitter().splitLines(component, maxWidth, component.getStyle()).stream()
                .map(text -> Component.literal(text.getString()).withStyle(component.getStyle()))
                .collect(Collectors.toList());
    }

    public static void renderTooltip(PoseStack poseStack, Component text, int color, int mouseX, int mouseY) {
        renderComponentTooltip(poseStack, List.of(text), color, mouseX, mouseY);
    }
    public static void renderComponentTooltip(PoseStack poseStack, List<Component> tooltips, int color, int mouseX, int mouseY) {
        renderTooltip(poseStack, Lists.transform(tooltips, Component::getVisualOrderText), color, mouseX, mouseY);
    }
    public static void renderTooltip(PoseStack poseStack, List<? extends FormattedCharSequence> tooltips, int color, int mouseX, int mouseY) {
        renderTooltipInternal(poseStack, tooltips.stream().map(TooltipComponent::create).collect(Collectors.toList()),color , mouseX, mouseY);
    }

    public static void renderTooltipInternal(PoseStack poseStack, List<TooltipComponent> tooltipComponents, int color, int mouseX, int mouseY) {
        if (tooltipComponents.isEmpty()) return;

        assert Minecraft.getInstance().screen != null;
        Screen screen = Minecraft.getInstance().screen;
        Font font = Minecraft.getInstance().font;

        int i = 0;
        int j = tooltipComponents.size() == 1 ? -2 : 0;

        for(TooltipComponent tooltipComponent : tooltipComponents) {
            int k = tooltipComponent.getWidth(font);
            if (k > i) {
                i = k;
            }

            j += tooltipComponent.getHeight();
        }

        int j2 = mouseX + 12;
        int k2 = mouseY - 12;

        if (j2 + i > screen.width) {
            j2 -= 28 + i;
        }

        if (k2 + j + 6 > screen.height) {
            k2 = screen.height - j - 6;
        }

        poseStack.pushPose();
        int l = -267386864;
        int i1 = 1347420415;
        int j1 = 1344798847;
        float k1 = 400.0F;
        float f = Minecraft.getInstance().getItemRenderer().blitOffset;
        Minecraft.getInstance().getItemRenderer().blitOffset = k1;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix4f = poseStack.last().pose();
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 - 4, j2 + i + 3, k2 - 3, 400, l, l); // BG Start
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 + j + 3, j2 + i + 3, k2 + j + 4, 400, l, l); // BG End
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 - 3, j2 + i + 3, k2 + j + 3, 400, l, l); // BG Start | BG End
        fillGradient(matrix4f, bufferBuilder, j2 - 4, k2 - 3, j2 - 3, k2 + j + 3, 400, l, l); // BG Start | BG End
        fillGradient(matrix4f, bufferBuilder, j2 + i + 3, k2 - 3, j2 + i + 4, k2 + j + 3, 400, l, l); // BG Start | BG End
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 - 3 + 1, j2 - 3 + 1, k2 + j + 3 - 1, 400, i1, j1); // Border Start | Border End
        fillGradient(matrix4f, bufferBuilder, j2 + i + 2, k2 - 3 + 1, j2 + i + 3, k2 + j + 3 - 1, 400, i1, j1); // Border Start | Border End
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 - 3, j2 + i + 3, k2 - 3 + 1, 400, i1, i1); // Border Start | Border Start
        fillGradient(matrix4f, bufferBuilder, j2 - 3, k2 + j + 2, j2 + i + 3, k2 + j + 3, 400, j1, j1); // Border End | Border End
        RenderSystem.enableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        poseStack.translate(0.0, 0.0, k1);
        int l1 = k2;

        for(int i2 = 0; i2 < tooltipComponents.size(); ++i2) {
            TooltipComponent tooltipComponent1 = tooltipComponents.get(i2);
            tooltipComponent1.renderText(font, j2, l1, color, matrix4f, bufferSource);
            l1 += tooltipComponent1.getHeight() + (i2 == 0 ? 2 : 0);
        }

        bufferSource.endBatch();
        poseStack.popPose();
        l1 = k2;

        for(int l2 = 0; l2 < tooltipComponents.size(); ++l2) {
            TooltipComponent tooltipComponent2 = tooltipComponents.get(l2);
            tooltipComponent2.renderImage(font, j2, l1, poseStack, Minecraft.getInstance().getItemRenderer(), 400);
            l1 += tooltipComponent2.getHeight() + (l2 == 0 ? 2 : 0);
        }

        Minecraft.getInstance().getItemRenderer().blitOffset = f;
    }

    public interface TooltipComponent {
        static TooltipComponent create(FormattedCharSequence text) {
            return new TextTooltip(text);
        }

        int getHeight();

        int getWidth(Font font);

        default void renderText(Font font, int x, int y, int color, Matrix4f matrix4f, MultiBufferSource.BufferSource bufferSource) {
        }

        @SuppressWarnings("unused")
        default void renderImage(Font font, int mouseX, int mouseY, PoseStack poseStack, ItemRenderer itemRenderer, int blitOffset) {
        }
    }
    private static class TextTooltip implements TooltipComponent {
        private final FormattedCharSequence text;

        public TextTooltip(FormattedCharSequence formattedCharSequence) {
            this.text = formattedCharSequence;
        }

        public int getWidth(Font font) {
            return font.width(this.text);
        }

        public int getHeight() {
            return 9;
        }

        public void renderText(Font font, int x, int y, int color, Matrix4f matrix4f, MultiBufferSource.BufferSource bufferSource) {
            font.drawInBatch(this.text, (float)x, (float)y, color, true, matrix4f, bufferSource, false, 0, LightTexture.FULL_BRIGHT);
        }
    }
}
