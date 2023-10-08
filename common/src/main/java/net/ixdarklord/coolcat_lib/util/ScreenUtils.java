package net.ixdarklord.coolcat_lib.util;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ScreenUtils extends GuiGraphics {
    public static ScreenUtils INSTANCE = new ScreenUtils();

    public ScreenUtils() {
        super(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
    }

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

    public void renderTooltip(Font font, Component text, int color, int mouseX, int mouseY) {
        this.renderTooltip(font, List.of(text.getVisualOrderText()), color, mouseX, mouseY);
    }

    public void renderTooltip(Font font, List<? extends FormattedCharSequence> tooltipLines, int color, int mouseX, int mouseY) {
        this.renderTooltipInternal(font, tooltipLines.stream().map(TooltipComponent::create).collect(Collectors.toList()), color, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
    }

    public void renderComponentTooltip(Font font, List<Component> tooltipLines, int color, int mouseX, int mouseY) {
        this.renderTooltip(font, Lists.transform(tooltipLines, Component::getVisualOrderText), color, mouseX, mouseY);
    }
    public void renderTooltip(Font font, List<FormattedCharSequence> tooltipLines, int color, ClientTooltipPositioner tooltipPositioner, int mouseX, int mouseY) {
        this.renderTooltipInternal(font, tooltipLines.stream().map(TooltipComponent::create).collect(Collectors.toList()), color, mouseX, mouseY, tooltipPositioner);
    }

    public void renderTooltipInternal(Font font, List<TooltipComponent> components, int color, int mouseX, int mouseY, ClientTooltipPositioner tooltipPositioner) {
        if (!components.isEmpty()) {
            int i = 0;
            int j = components.size() == 1 ? -2 : 0;

            TooltipComponent tooltipComponent;
            for(Iterator<TooltipComponent> var8 = components.iterator(); var8.hasNext(); j += tooltipComponent.getHeight()) {
                tooltipComponent = var8.next();
                int k = tooltipComponent.getWidth(font);
                if (k > i) {
                    i = k;
                }
            }

            Vector2ic vector2ic = tooltipPositioner.positionTooltip(this.guiWidth(), this.guiHeight(), mouseX, mouseY, i, j);
            int n = vector2ic.x();
            int o = vector2ic.y();
            this.pose().pushPose();
            int finalI = i;
            int finalJ = j;
            this.drawManaged(() -> {
                TooltipRenderUtil.renderTooltipBackground(this, n, o, finalI, finalJ, 400);
            });
            this.pose().translate(0.0F, 0.0F, 400.0F);
            int q = o;

            int r;
            TooltipComponent tooltipComponent1;
            for(r = 0; r < components.size(); ++r) {
                tooltipComponent1 = components.get(r);
                tooltipComponent1.renderText(font, n, q, color, this.pose().last().pose(), this.bufferSource());
                q += tooltipComponent1.getHeight() + (r == 0 ? 2 : 0);
            }

            q = o;

            for(r = 0; r < components.size(); ++r) {
                tooltipComponent1 = components.get(r);
                tooltipComponent1.renderImage(font, n, q, this);
                q += tooltipComponent1.getHeight() + (r == 0 ? 2 : 0);
            }

            this.pose().popPose();
        }
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
        default void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        }
    }
    public static class TextTooltip implements TooltipComponent {
        private final FormattedCharSequence text;

        public TextTooltip(FormattedCharSequence formattedCharSequence) {
            this.text = formattedCharSequence;
        }

        public int getWidth(Font font) {
            return font.width(this.text);
        }

        public int getHeight() {
            return 10;
        }

        public void renderText(Font font, int mouseX, int mouseY, int color, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
            font.drawInBatch(this.text, (float)mouseX, (float)mouseY, color, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
        }
    }
}
