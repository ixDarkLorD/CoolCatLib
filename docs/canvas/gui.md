---
icon: material/dock-window
description: Windows, panels, pan and zoom, animations
---

# GUI

GUI building blocks: draggable windows, scrolling, stacked panels with pan and zoom, slide animations and drawing helpers. Packages: `net.ixdarklord.coolcatcanvas.api.client.gui.components` (and its `widgets`, `widgets.panel` and `animations` subpackages) and `api.client.utils`.

!!! warning
    `AbstractMultiPanelWidget`, `Panel`, `ScrollPanel` and `ViewportPanel` are marked `@ApiStatus.Experimental`: they may still change.

## Windows

`AbstractDraggableWidget` is a self-contained window with its own children, which the player can optionally drag around. Add it to a screen like any widget.

| Class | Description |
|---|---|
| `AbstractDraggableWidget` | Constructor `(Component title, int x, int y, int width, int height, boolean isMovable)`. Implement `init()`, `renderBackground(...)`, `getDraggingRectangle()` (the area that drags it) and `layoutRectangle()`. Optional hooks: `renderLabels`, `renderContents`, `postInit`, `updateChildren`. Children are added with `addRenderableWidget`, `addWidget`, `removeWidget` and `clearWidgets`. Also has `toggleVisibility()`, `setVisible(boolean)` and `moveTo(x, y)`. |
| `AbstractScrollableWidget` | A draggable window with a scrollable area and scrollbar. Override `renderScrollableContents(...)`, `getContentSize(ScreenAxis)` and `getScrollStep(ScreenAxis)`. Also has `setBorderColor`, `setScrollBgColor`, `setScrollColor` and `enableScrollingHorizontally()`. |
| `AbstractMultiPanelWidget` | A window whose content is a stack of `Panel`s, drawn bottom to top. Input goes to the window's own widgets first, then to panels from the top down. A modal panel blocks the panels under it and dims them. Add panels with `addPanel(panel)` and `removePanel(panel)`; also has `getPanels()`, `getTopPanel()`, `setOverlayColor(argb)` and `setBounds(...)`. |
| `MovableElement` | Anything with `moveTo(x, y)`. |

## Panels

| Class | Description |
|---|---|
| `Panel` | One layer. Implement `extractContents(graphics, mouseX, mouseY, partialTick)`; hooks `onResized()` (lay out children here), `onShown()`, `onHidden()`. `setModal(boolean)`, `setVisible(boolean)`, `addChild(widget)` (children draw over the contents and get the mouse first). |
| `ScrollPanel` | Vertical scrolling: implement `getContentHeight()` and `extractScrolled(graphics, left, top, width, mouseX, mouseY, partialTick)`. `setScrollStep`, `setScrollbarColors`, `getScroll`, `setScroll`. |
| `ViewportPanel` | A 2D world the player pans (drag) and zooms (the mouse wheel, anchored at the cursor; <kbd>+</kbd>/<kbd>-</kbd>; <kbd>0</kbd> or <kbd>Home</kbd> to fit; the arrow keys pan). Implement `getContentBounds()` and `extractWorld(graphics, mouseWorldX, mouseWorldY, mouseInView, partialTick)`; optionally `worldClicked(...)`, `extractViewBackground`, `extractViewForeground`. Setters: `setZoomLimits`, `setZoomStep`, `setVignette`, `setEdgeFade`, `setMargin`. Control: `zoomTo`, `zoomBy`, `centerOn`, `requestFit`; coordinates with `toWorldX/Y` and `toScreenX/Y`. |

```java
class MapWindow extends AbstractMultiPanelWidget {
    MapWindow() { super(Component.literal("Map"), 20, 20, 300, 200, true); }

    @Override protected void init() { this.addPanel(new MapPanel()); }
    @Override protected void renderBackground(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        RenderUtils.fillRect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xC0101010);
    }
    @Override public ScreenRectangle getDraggingRectangle() { return new ScreenRectangle(this.getX(), this.getY(), this.getWidth(), 12); }
    @Override protected ScreenRectangle layoutRectangle() { return new ScreenRectangle(this.getX(), this.getY() + 12, this.getWidth(), this.getHeight() - 12); }
}

class MapPanel extends ViewportPanel {
    @Override protected ScreenRectangle getContentBounds() { return new ScreenRectangle(0, 0, 512, 512); }
    @Override protected void extractWorld(GuiGraphicsExtractor graphics, double mouseWorldX, double mouseWorldY, boolean mouseInView, float partialTick) {
        // Draw in world coordinates; the panel applies the pan and zoom.
    }
}
```

## Animations and anchors

| Class | Description |
|---|---|
| `AnimatedComponent` | A timeline in seconds that pauses with the game: `play(reverse[, AnimMode])`, `update()` every frame, `isPlaying()`, `isFinished()`. Modes: `LOOP`, `PING_PONG`, `HOLD_LAST_FRAME`. |
| `SlideAnimation` | Slides an element in from off-screen, horizontally or vertically, anchored with a `ScreenAnchor`. |
| `ScreenAnchor` | `TOP_LEFT` … `BOTTOM_RIGHT` and `CENTER`: `getX(screenWidth, width, padding)`, `getY(...)`, `next()`, `previous()`. |

## Drawing helpers

| Class | Description |
|---|---|
| `RenderUtils` | Shapes: `fillRect`, `drawHollowRect`, `drawLine`, `drawInsideRect` (optionally clipped). Gradients: `fillVerticalGradient`, `fillHorizontalGradient`, `drawDiagonalGradient`. Text: `drawString`, `drawCenteredString`, `drawScrollingString`, and `textColor(color)`, which makes alpha-less RGB colors opaque, since 26.1 skips text whose alpha is 0. Textures: `blitNineSliced`, `blitRepeating`, `blitWithBorder`, `blitInscribed` (aspect-fit). |
| `NineSliceInfo` | Parameter holders for nine-slice blits: `SliceBounds`, `TextureRegion`, `TextureInfo`. |
| `ColorableImageButton` | An `ImageButton` whose sprite is tinted with `setColor(java.awt.Color)`. |
