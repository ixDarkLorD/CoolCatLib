---
icon: material/cube-scan
description: Block entity renderers with culling, detail by distance, reused states and layers
---

# Block Entity Renderers

`BlockRenderer` is a base for block entity renderers that does the work every renderer otherwise repeats: culling, detail by distance, turning to the block's facing, and reusing its render states so nothing is allocated per frame. You write what it draws. Package: `net.ixdarklord.coolcatcanvas.api.client.blockentity` (client).

!!! info "Since"
    CoolCatLib **8.2.0**, on 26.1.2 only for now.

A `BlockRenderer` is a plain vanilla `BlockEntityRenderer`, so it's registered like any other, draws in a [scene viewport](gui.md#scene-viewports), and can sit inside another renderer.

| You want | Use |
|---|---|
| Your own renderer | [`BlockRenderer`](#a-renderer): `newState`, `extract` and `render`. |
| Less drawn farther off | [Detail](#detail-by-distance): `detail(Detail.FULL, 16)` bands, and `state.detailed(...)`. |
| Not drawn out of view | [Bounds](#culling): `bounds((be, box) -> box.up(2))`. |
| An item shown on the block | [`ItemLayer`](#layers). |
| Text over the block | [`LabelLayer`](#layers). |
| Another block entity drawn inside it | [`ChildLayer`](#layers). |
| An animated model | [`ArmatureBlockRenderer`](#armatures): a `BlockRenderer` already. |

## A renderer

```java
public final class AltarRenderer extends BlockRenderer<AltarBlockEntity, AltarRenderer.State> {
    public AltarRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.viewDistance(96)
                .detail(Detail.FULL, 24)
                .detail(Detail.REDUCED, 48)
                .facing(Facing.HORIZONTAL)
                .bounds((altar, box) -> box.up(2.0F))
                .layer(ItemLayer.<AltarBlockEntity, State>of(AltarBlockEntity::offering)
                        .at(0.5F, 1.3F, 0.5F).spin(2.0F).bob(0.05F).fullBright());
    }

    @Override
    protected State newState() {
        return new State();
    }

    @Override
    protected void extract(AltarBlockEntity altar, State state, BlockRenderContext context) {
        if (!altar.isLit()) {
            state.skip();               // nothing drawn, layers included
            return;
        }
        state.glow = context.transition(altar.litAt(), 20.0F, true);   // 0 to 1 over a second, eased
    }

    @Override
    protected void render(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // At the block's corner, turned to its facing.
        if (state.detailed(Detail.FULL)) drawRunes(state, poseStack, collector);
        drawFlame(state, poseStack, collector);
    }

    public static final class State extends BlockRenderState {
        float glow;
    }
}

// From ClientModConstructor#onConstructMod():
BlockEntityRendererRegistry.register(MyBlockEntities.ALTAR, AltarRenderer::new);
```

- **`newState`** makes an empty state. The game asks for a new state for every block entity every frame; a `BlockRenderer` hands out reused ones instead, so set every field you read in `extract`, and keep things like an `ItemStackRenderState` in the state rather than making new ones.
- **`extract`** reads the block entity into its state. The base fields are already set when it runs:

    | Field | Holds |
    |---|---|
    | `detail` | The [detail](#detail-by-distance) it's drawn at. |
    | `distanceSq` | Its block's middle's distance from the camera, squared. |
    | `time`, `gameTime`, `partialTicks` | Smooth ticks to animate by (`time(period)` as a precise float). |
    | `facing` | Which way its block faces. |
    | `lookedAt` | Whether the player looks at its block, with `trackLookedAt()`. |
    | `reach` | How many times farther than usual it's drawn (`reach(...)`). |

    The `BlockRenderContext` gives the camera, the item model resolver and font, and `transition(changedAt, ticks, on)`: an eased 0 to 1 (or 1 to 0) since a game time, for a lid opening or a light rising.
- **`render`** draws it. The pose is at the block's corner, turned to its facing if the renderer turns, and is popped afterwards. Its layers are drawn after it.
- **`onFrame()`** runs once a frame, before its first block entity: for work they all share, like one spin angle for all of them.

Set it up in the constructor. A renderer is made again on every resource reload, and its settings are read every frame.

## Detail by distance

```java
this.detail(Detail.FULL, 16).detail(Detail.REDUCED, 40);   // FULL to 16 blocks, REDUCED to 40, MINIMAL beyond
this.reach(pedestal -> pedestal.scale());                    // one shown twice as large: every band twice as far
```

`Detail.FULL` (0) is nearest, then `REDUCED` (1) and `MINIMAL` (2); past the last band set it's the next level down, out to its `viewDistance`. Use more levels of your own (3, 4, ...) the same way. With no bands, everything is `FULL`.

In `render`, `state.detailed(Detail.REDUCED)` is true at `FULL` and `REDUCED`. Each layer has the least detail it's still drawn at (`upTo(Detail.REDUCED)` on the built-in ones), and is skipped past it, extract and all.

## Culling

A `BlockRenderer` is culled before the game makes it a state, on both loaders:

- **By distance.** Farther than `viewDistance` (64 blocks by default, vanilla's), grown by `reach`.
- **By view.** When its `bounds` are out of the camera's view. Vanilla doesn't cull block entities by view; NeoForge does, but makes a new box for each one every frame. A `BlockRenderer` tests its own bounds without making one, the same on Fabric and NeoForge.

```java
this.bounds((pedestal, box) -> box.grow(pedestal.scale(), 0.0F, pedestal.scale()).up(1.0F + pedestal.height()));
```

The box starts as the block itself (0 to 1 on each axis, from its corner); grow it to take in everything drawn: a floating item, a beam, text. Bounds that reach past the block also keep it drawn while its block's chunk section is out of view (vanilla's `shouldRenderOffScreen`); `offScreen(false)` turns that off.

Press **F3 + B** to see each one's bounds, colored by the detail it's drawn at: cyan `FULL`, yellow `REDUCED`, red `MINIMAL`.

## Facing

```java
this.facing(Facing.HORIZONTAL);   // turned about the upright axis to horizontal_facing
this.facing(Facing.ALL);          // turned to facing, up and down included
```

Make the model facing north (its front toward -Z), as Blockbench shows it. Override `direction(blockEntity)` to read the facing some other way. For something that must not turn with it, like text facing the camera, call `BlockRenderer.unturn(state, poseStack)` at its point.

## Layers

Layers are drawn with each block entity after `render`, in the order they're added, in its pose.

```java
// The item on a pedestal: spinning, bobbing, lit.
this.layer(ItemLayer.<PedestalBlockEntity, State>of(PedestalBlockEntity::display)
        .at(0.5F, 1.25F, 0.5F).scale(0.6F).spin(2.0F).bob(0.05F).fullBright().upTo(Detail.REDUCED));

// Its name, while the player looks at it, within 12 blocks.
this.layer(LabelLayer.<PedestalBlockEntity, State>of(pedestal -> pedestal.display().getHoverName())
        .at(0.5F, 1.9F, 0.5F).show(pedestal -> LabelLayer.Show.LOOKED_AT).range(12.0));

// Several lines.
this.layer(LabelLayer.<ShrineBlockEntity, State>of(2, (shrine, lines) -> {
    lines[0] = shrine.title();
    lines[1] = shrine.ownerName();
}));

// The pedestals in a cabinet, each drawn by the pedestal renderer.
PedestalRenderer pedestals = new PedestalRenderer(context);
this.layer(ChildLayer.<CabinetBlockEntity, State, PedestalBlockEntity, PedestalRenderer.State>of(pedestals, cabinet -> cabinet.pedestal(0))
        .place((state, poseStack) -> {
            poseStack.translate(0.1F, 0.125F, 0.3F);
            poseStack.scale(0.34F, 0.34F, 0.34F);
        }));
```

Give `ItemLayer` the stack the block entity holds, not a copy, and `LabelLayer` components you keep (a component lays its text out once).

A layer of your own implements `BlockRenderLayer<T, S, L>`. `L` is the layer's own state type: one is made with each of the renderer's states and reused with it, so a layer is typed through, with no casts or keys.

```java
public final class GlowLayer implements BlockRenderLayer<AltarBlockEntity, AltarRenderer.State, GlowLayer.Glow> {
    public Glow newState() { return new Glow(); }
    public void extract(AltarBlockEntity altar, AltarRenderer.State state, Glow glow, BlockRenderContext context) { glow.strength = altar.power(); }
    public void submit(AltarRenderer.State state, Glow glow, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) { ... }
    public int detail() { return Detail.REDUCED; }   // not drawn at MINIMAL
    public static final class Glow { float strength; }
}
```

## Reused states

States come from a pool per renderer: one handed out in a frame is handed out again two frames later, once what was submitted from it has been drawn. The pool grows to the most of its block entities in view at once.

- A state made outside the pool, for one kept across frames or drawn inside another renderer: `renderer.createUnpooledState()`.
- To turn the pool off: `pooled(false)`.
- A subclass that overrides `createRenderState()` itself still works, without the pool: override `newState()` instead.

## Armatures

[`ArmatureBlockRenderer`](../reference/api.md) is a `BlockRenderer`. It is culled by its armature's bounds, and once `detail` bands are set it animates every other frame at `REDUCED` and every fourth frame at `MINIMAL`, without its attachments. Its setup is unchanged:

```java
BlockEntityRendererRegistry.register(MyBlocks.TURRET_ENTITY, ArmatureBlockRenderer.<TurretEntity>of(Armature.of("mymod", "turret"), renderer -> {
    renderer.animate(animator -> animator.lookAt("barrel").range(12));
    renderer.viewDistance(96).detail(Detail.FULL, 24).detail(Detail.REDUCED, 48);
}));
```

## Keeping it fast

- **Static parts belong in the block model.** It's baked into the chunk once; a renderer draws every frame. Draw only what moves.
- **Share render types.** Everything submitted with the same `RenderType` is drawn together, across every block entity.
- **Use the detail bands.** Skip particles, text and fine parts farther off.
- **Don't allocate in `extract` or `render`.** Keep objects in the state or in the renderer (render runs on the client thread only), and prefer `poseStack.mulPose(quaternion.rotationY(angle))` with a kept `Quaternionf` over `Axis.YP.rotationDegrees`.
