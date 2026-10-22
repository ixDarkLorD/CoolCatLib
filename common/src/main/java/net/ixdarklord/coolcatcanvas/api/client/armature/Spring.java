package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureMesh;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Secondary motion: a joint that lags behind and swings back, as a tail, ears, antennae, a cape or a hanging lantern
 * would. The joint's tip is a weight on a spring, pulled toward where the animation puts it, so it reacts to the
 * creature walking and turning and to the clips themselves. Chain springs down a tail for a whip.
 * <pre>{@code
 * animator.spring("tail").stiffness(90).damping(7);
 * animator.spring("tail_tip").stiffness(60).damping(5).gravity(4);
 * animator.spring("antenna").stiffness(200).maxAngle(35);
 * }</pre>
 * Colliders keep the weight out of the body: each is a ball round a point of the model (a locator), so a cape drapes
 * over the back and the spikes on it instead of swinging through them. Chain a cape from a few joints, each with the
 * same colliders, and it folds over them.
 * <pre>{@code
 * animator.spring("cape_1").collider("back", 5).collider("spine_spike_2", 2.5);
 * }</pre>
 * Free parts: with no stiffness a joint isn't pulled back to its animated place at all, and hangs or floats where the
 * world puts it: a lantern on a chain swinging as its holder walks, or a balloon on a string held in a hand. Buoyancy
 * lifts it (a balloon, a jellyfish's bell, a kite), a rope lets its string go slack when its holder jerks it back
 * instead of acting as a rod, wind sets it drifting in slow gusts, and twist spins it on its string as it is swung.
 * The animation still moves where it hangs from, so it reacts to every clip: a walk tugs it along behind, a blow
 * whips it round, an idle sets it bobbing.
 * <pre>{@code
 * animator.spring("balloon").stiffness(0).buoyancy(9).damping(1.8).rope(true).wind(1.2).twist(0.6).maxAngle(180);
 * animator.spring("lantern").stiffness(0).gravity(9.8).damping(1.5).maxAngle(150);
 * }</pre>
 * The world: a spring's weight can be kept out of the blocks round it and the entities near it, a ball of
 * {@link #collisionRadius} round it: a tail that rests on the ground and slides along walls, a balloon that bumps a
 * ceiling or is pushed aside by a player walking into it, a cape that lies on the stairs it is dragged up. It bounces
 * off ({@link #bounce}) and drags along what it touches ({@link #friction}).
 * <pre>{@code
 * animator.spring("balloon").stiffness(0).buoyancy(9).collideBlocks(true).collideEntities(true).collisionRadius(2.2);
 * }</pre>
 * Springs that bump: those of one animator marked {@link #bump} keep their weights apart, each a ball of
 * {@link #collisionRadius}, pushing on each other and bouncing off each other: a bunch of balloons jostling on their
 * strings, a row of hanging charms knocking together.
 * <pre>{@code
 * animator.spring("balloon_red").stiffness(0).buoyancy(9).bump(true).collisionRadius(2.2);
 * animator.spring("balloon_blue").stiffness(0).buoyancy(8).bump(true).collisionRadius(2.0);
 * }</pre>
 * Cloth: springs linked to their neighbours ({@link #link}) keep the distance between their weights that the animation
 * gives them, as threads woven between them would, so a cape of a few hanging chains side by side, or a skirt of
 * panels round a waist, moves as one sheet: it ripples across, folds and bunches (it gives a little when pushed
 * together, {@link #linkStiffness}, and hardly at all when pulled apart) instead of parting into strips.
 * <pre>{@code
 * animator.spring("cape_0_1").link("cape_1_1");                  // and cape_1_1 links back to cape_0_1, and so on
 * }</pre>
 * What the cloth is: its {@link #mass} (heavy cloth swings slowly and shrugs off the wind; light cloth answers every
 * tug), the {@link #airDrag} that makes it stream out behind as its wearer moves, the {@link #flutter} that ripples it
 * the faster they go, how far it {@link #stretch}es and springs back, and how stiffly a chain of it resists folding
 * ({@link #bend}: canvas hangs in long curves, silk folds sharp); a steady {@link #windDirection} on top of the drifting
 * wind. A {@link Fabric} sets them all at once.
 * <pre>{@code
 * animator.spring("cape_0_1").fabric(Fabric.WOOL).stiffness(40);
 * animator.spring("veil").fabric(Fabric.SILK).wind(0.8).windDirection(1, 0, 0);
 * }</pre>
 * It is kept light: a frame allocates nothing; it takes at most 8 sub-steps; one that has come to rest with nothing
 * moving it sleeps until something does; the world's blocks are looked up a few times a second (or when it has moved
 * half a block) and its entities every few frames; and farther than {@link #simulationDistance} from the camera it is
 * not worked out at all.
 */
public final class Spring {
    private final String joint;
    private float stiffness = 140.0F;
    private float damping = 12.0F;
    private float gravity;
    private float maxAngle = 70.0F;
    private float weight = 1.0F;
    private float length = -1.0F;
    private boolean enabled = true;
    private boolean rope;
    private float wind;
    private final Vector3f windDirection = new Vector3f();
    private float twist;
    // What the cloth is: how heavy, how the air drags it and ripples it, how far it stretches, how it resists folding.
    private float mass = 1.0F;
    private float airDrag;
    private float flutter;
    private float stretch;
    private float bend;
    // How fast its anchor moved this frame, in blocks per second, and its animated place's velocity.
    private float anchorSpeed;
    private final Vector3f targetVelocity = new Vector3f();
    private float follow;
    private boolean blocks;
    private boolean entities;
    private boolean bump;
    // The springs of its animator that bump (set by the animator), and how big its own ball was last step, in blocks.
    Spring[] peers = new Spring[0];
    // The springs it is woven to, by joint, and (set by the animator) the springs themselves; how firmly.
    private final List<String> links = new ArrayList<>();
    Spring[] linked = new Spring[0];
    // Whether its pull on its length tugs the spring above it (set by the animator: the spring on its joint's parent).
    private boolean pulls;
    @Nullable Spring above;
    private float linkStiffness = 0.6F;
    private float bumpRadius;
    // Room it keeps round its ball from the others that bump, in pixels, and (each step) in blocks.
    private float spacing;
    private float bumpSpacing;
    // How hard spacing eases bumping weights apart at the edge of their ball, blocks per second squared.
    private static final float SPREAD = 4.0F;
    private float collisionRadius = 1.0F;
    private float bounce = 0.3F;
    private float friction = 0.4F;
    private final List<Collider> colliders = new ArrayList<>();
    // Its own clock for the wind, how far it has spun on its string and how fast it spins, and a phase so no two
    // parts drift alike.
    private float clock;
    private float twistAngle;
    private float twistSpeed;
    private final float phase;
    // Kept from frame to frame, so a frame allocates nothing: the points it works with, the turns it builds, the
    // colliders' balls and the world's boxes (and how old those are), and how many frames it has lain still.
    private final Vector3f pivotModel = new Vector3f(), targetModel = new Vector3f(), anchorWorld = new Vector3f(), targetWorld = new Vector3f();
    private final Vector3f lastTarget = new Vector3f(), accel = new Vector3f(), radial = new Vector3f(), across = new Vector3f();
    private final Vector3f normal = new Vector3f(), along = new Vector3f(), from = new Vector3f(), to = new Vector3f(), axis = new Vector3f();
    private final Vector3f scratch = new Vector3f(), worldAt = new Vector3f();
    private final Quaternionf swing = new Quaternionf(), spin = new Quaternionf(), placement = new Quaternionf(), local = new Quaternionf();
    private final Matrix4f around = new Matrix4f();
    private final List<net.minecraft.world.phys.AABB> found = new ArrayList<>();
    private float[] balls = new float[0], blockBox = new float[0], entityBox = new float[0];
    private int blockBoxes, entityBoxes, still;
    private float worldAge = Float.MAX_VALUE, entityAge = Float.MAX_VALUE;
    private float simulationDistance = 64.0F;

    // The weight's place and speed in the world, and the joint it was worked out for.
    private final Vector3f tip = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f lastAnchor = new Vector3f();
    private boolean settled;
    private BakedArmature armature;
    private final Vector3f restTip = new Vector3f();

    Spring(String joint) {
        this.joint = joint;
        this.phase = (joint.hashCode() & 0xFFFF) / (float) 0xFFFF * Mth.TWO_PI;
    }

    public String joint() {
        return this.joint;
    }

    /** How hard it pulls back toward the animated place, per second squared. Higher is snappier; 140 by default. */
    public Spring stiffness(float stiffness) {
        this.stiffness = Math.max(0.0F, stiffness);
        return this;
    }

    /** How quickly the swinging dies down, per second. Low values wobble long; 12 by default. */
    public Spring damping(float damping) {
        this.damping = Math.max(0.0F, damping);
        return this;
    }

    /** How hard it is pulled down, in blocks per second squared: a sagging tail or hanging chain. 0 by default. */
    public Spring gravity(float gravity) {
        this.gravity = gravity;
        return this;
    }

    /**
     * How hard it is lifted, in blocks per second squared: the opposite of {@link #gravity}, for something that floats
     * (a balloon on a string, a kite, a bell of a jellyfish). With no {@link #stiffness} it rises straight up from where
     * it is held and trails behind as its holder moves.
     */
    public Spring buoyancy(float buoyancy) {
        this.gravity = -buoyancy;
        return this;
    }

    /**
     * Whether it hangs on a rope rather than a rod: it can come nearer the joint than its length (the rope goes slack)
     * when the joint is jerked toward it, and only its length holds it from going further. Off by default.
     */
    public Spring rope(boolean rope) {
        this.rope = rope;
        return this;
    }

    /**
     * Whether it pulls on what it hangs from: stretched taut, its weight tugs the weight of the spring on its joint's
     * parent along its length (each giving way by the other's share of their {@link #mass}), as a balloon pulls its
     * string taut toward it, instead of the string standing on its own and the balloon folding over at its top. Down a
     * chain whose every length pulls, the tension runs all the way down: a string of cord lengths curves toward its
     * balloon as its holder swings it. Off by default.
     * <pre>{@code
     * animator.spring("balloon_string_0").fabric(Fabric.CORD).stiffness(0).rope(true).pulls(true);
     * animator.spring("balloon").stiffness(0).buoyancy(9).rope(true).pulls(true);
     * }</pre>
     */
    public Spring pulls(boolean pulls) {
        this.pulls = pulls;
        return this;
    }

    /** How hard the wind pushes it about, in blocks per second squared: a slow drift that now and then gusts. 0 by default. */
    public Spring wind(float wind) {
        this.wind = Math.max(0.0F, wind);
        return this;
    }

    /**
     * A steady breeze the {@link #wind} blows along as well as drifting about, in the world's axes (any length; it is
     * normalized): a banner on a windy hill, a cloak in a gale. None by default (the wind only drifts).
     */
    public Spring windDirection(float x, float y, float z) {
        this.windDirection.set(x, y, z);
        if (this.windDirection.lengthSquared() > 1.0E-8F) this.windDirection.normalize();
        return this;
    }

    /**
     * How heavy it is, 1 by default: everything that moves it (its pull back to place, the wind, the air, a fold) moves
     * it less the heavier it is, so heavy cloth swings slowly and keeps swinging, and light cloth answers every tug.
     * Gravity pulls all alike.
     */
    public Spring mass(float mass) {
        this.mass = Math.max(0.05F, mass);
        return this;
    }

    /**
     * Air resistance, growing with its speed (squared): moving fast, it is held back and streams out behind, then
     * settles as it slows; how cloth flows. 0 by default; a few hundredths is cloth.
     */
    public Spring airDrag(float drag) {
        this.airDrag = Math.max(0.0F, drag);
        return this;
    }

    /** How much it ripples in the air its wearer moves through, more the faster they go: a flag's snap. 0 by default. */
    public Spring flutter(float flutter) {
        this.flutter = Math.max(0.0F, flutter);
        return this;
    }

    /**
     * How far it stretches and springs back, a share of its length (0.05 is five percent): cloth gives a little as it is
     * yanked. 0 by default (it holds its length exactly).
     */
    public Spring stretch(float stretch) {
        this.stretch = Mth.clamp(stretch, 0.0F, 0.5F);
        return this;
    }

    /**
     * How stiffly it holds its shape against folding, per second squared: a pull back to its animated place (which rides
     * on its parent joint's swing, so a chain keeps the curve the animation gives it) that its {@link #mass} does not
     * weaken, as {@link #stiffness} is, damped critically to match so it does not ring. Heavy, stiff cloth (canvas,
     * leather) then hangs heavily yet curves in long arcs instead of sagging and folding sharp at each joint as heavy silk
     * would, and settles instead of whipping. 0 by default. (A long chain of springs whips if it is too lightly damped for
     * its weight: raise {@link #damping} with {@link #mass}.)
     */
    public Spring bend(float bend) {
        this.bend = Math.max(0.0F, bend);
        return this;
    }

    /**
     * How much it moves along with its wearer, 0 to 1: its {@link #damping} works on its speed relative to where the
     * animation carries it rather than its speed in the world, so at 1 it keeps up with every move and only its own
     * swinging is damped (cloth sewn to a body), and at 0 (the default) it is held back as if dragged through the air
     * (a tail, a dangling charm). {@link #airDrag} always drags against the world.
     */
    public Spring follow(float follow) {
        this.follow = Mth.clamp(follow, 0.0F, 1.0F);
        return this;
    }

    /**
     * What it is made of: sets its {@link #mass}, {@link #damping}, {@link #gravity}, {@link #airDrag}, {@link #flutter},
     * {@link #stretch}, {@link #bend} and {@link #linkStiffness} at once. Set any of them afterwards to change just that.
     */
    public Spring fabric(Fabric fabric) {
        this.mass = fabric.mass;
        this.damping = fabric.damping;
        this.gravity = fabric.gravity;
        this.airDrag = fabric.airDrag;
        this.flutter = fabric.flutter;
        this.stretch = fabric.stretch;
        this.bend = fabric.bend;
        this.linkStiffness = fabric.linkStiffness;
        this.follow = fabric.follow;
        return this;
    }

    /**
     * How readily it spins about its own length as it is swung (a balloon turning on its string, a hanging sign), 0 to
     * about 1; the spin dies down by itself. 0 by default.
     */
    public Spring twist(float twist) {
        this.twist = Math.max(0.0F, twist);
        return this;
    }

    /**
     * Whether its weight bumps into the other springs of its animator that bump: each keeps the others' balls of
     * {@link #collisionRadius} out of its own, and they bounce off each other. Off by default.
     */
    public Spring bump(boolean bump) {
        this.bump = bump;
        return this;
    }

    /**
     * Room it keeps from the other springs that {@link #bump}, in pixels past their balls: within it they are eased
     * apart, gently at its edge and harder the nearer they come, before they ever touch. With a gentle
     * {@link #buoyancy}, a bunch of balloons opens out into a spread bouquet instead of crowding into one clump. 0 by
     * default.
     */
    public Spring spacing(float pixels) {
        this.spacing = Math.max(0.0F, pixels);
        return this;
    }

    boolean bumps() {
        return this.bump && this.enabled();
    }

    /**
     * Weaves its weight to the weights of other springs of its animator (by joint), as cloth: they keep the distance the
     * animation puts between them, so side by side they move as one sheet. Link both ways (each to the other).
     */
    public Spring link(String... joints) {
        for (String joint : joints) if (!this.links.contains(joint)) this.links.add(joint);
        return this;
    }

    /**
     * How firmly its links hold when pushed together, 0 (they bunch freely, a soft cloth) to 1 (stiff as card); pulled
     * apart they always hold. 0.6 by default.
     */
    public Spring linkStiffness(float stiffness) {
        this.linkStiffness = Mth.clamp(stiffness, 0.0F, 1.0F);
        return this;
    }

    List<String> links() {
        return this.links;
    }

    /** Whether its weight is kept out of the world's blocks (their collision shapes). Off by default. */
    public Spring collideBlocks(boolean blocks) {
        this.blocks = blocks;
        return this;
    }

    /** Whether its weight is kept out of other entities (their boxes), and pushed aside as they move into it. Off by default. */
    public Spring collideEntities(boolean entities) {
        this.entities = entities;
        return this;
    }

    /** How big its weight is for the world's blocks and entities: a ball this many pixels round; 1 by default. */
    public Spring collisionRadius(float pixels) {
        this.collisionRadius = Math.max(0.0F, pixels);
        return this;
    }

    /** How much of its speed into a block or an entity it keeps, bouncing off, 0 to 1; 0.3 by default. */
    public Spring bounce(float bounce) {
        this.bounce = Mth.clamp(bounce, 0.0F, 1.0F);
        return this;
    }

    /** How much it drags along what it touches, 0 (slides freely) to 1; 0.4 by default. */
    public Spring friction(float friction) {
        this.friction = Mth.clamp(friction, 0.0F, 1.0F);
        return this;
    }

    /**
     * How far from the camera, in blocks, it is still worked out; farther, it holds the swing it had (too small to see)
     * and costs nothing. 64 by default; 0 or less works it out at any distance.
     */
    public Spring simulationDistance(float blocks) {
        this.simulationDistance = blocks;
        return this;
    }

    /** The furthest it swings from the animated place, in degrees, up to 180 for a part that hangs free; 70 by default. */
    public Spring maxAngle(float degrees) {
        this.maxAngle = Math.max(0.0F, degrees);
        return this;
    }

    /** How much of the swing shows, 0 to 1. */
    public Spring weight(float weight) {
        this.weight = Mth.clamp(weight, 0.0F, 1.0F);
        return this;
    }

    /**
     * How long the joint is, in pixels, from its pivot to the weight. By default it reaches to its first child's
     * pivot, or to the middle of its own faces.
     */
    public Spring length(float pixels) {
        this.length = pixels;
        this.armature = null;
        return this;
    }

    /**
     * Keeps the weight out of a ball round a point of the model (a locator, by name), radius in pixels. The point must
     * move with a joint that comes before this one (a parent, or one posed earlier), or it is skipped.
     */
    public Spring collider(String point, float radius) {
        this.colliders.add(new Collider(point, Math.max(0.0F, radius)));
        return this;
    }

    public Spring enabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) this.settled = false;
        return this;
    }

    /** Puts the weight back where the animation has it, with no swing; for a teleport or a respawn. */
    public void reset() {
        this.settled = false;
        this.twistAngle = 0.0F;
        this.twistSpeed = 0.0F;
    }

    boolean enabled() {
        return this.enabled && this.weight > 0.0F;
    }

    // Swings a joint's posed matrix (model pixels) around its pivot. modelToWorld places the model in the world, so
    // the world's motion counts.
    void apply(BakedArmature armature, int index, Matrix4f matrix, Matrix4fc modelToWorld, float deltaTime, Matrix4f[] posed) {
        this.apply(armature, index, matrix, modelToWorld, deltaTime, posed, null);
    }

    void apply(BakedArmature armature, int index, Matrix4f matrix, Matrix4fc modelToWorld, float deltaTime, Matrix4f[] posed,
               ArmatureContext context) {                                // null: no world to collide with
        if (this.armature != armature) {
            this.armature = armature;
            this.findRestTip(armature, index);
            this.settled = false;
        }
        Joint joint = armature.joint(index);
        Vector3f pivot = matrix.transformPosition(this.pivotModel.set(joint.pivot()));
        Vector3f target = matrix.transformPosition(this.targetModel.set(this.restTip));
        Vector3f anchorWorld = modelToWorld.transformPosition(this.anchorWorld.set(pivot));
        Vector3f targetWorld = modelToWorld.transformPosition(this.targetWorld.set(target));
        float reach = targetWorld.distance(anchorWorld);
        if (reach < 1.0E-5F) return;
        float modelReach = target.distance(pivot);
        float toWorld = modelReach > 1.0E-5F ? reach / modelReach : 1.0F / 16.0F;
        if (!this.settled || anchorWorld.distanceSquared(this.lastAnchor) > 64.0F) {
            this.tip.set(targetWorld);
            this.velocity.zero();
            this.twistSpeed = 0.0F;
            this.settled = true;
            this.still = 0;
            this.worldAge = Float.MAX_VALUE;
        }
        // How far the anchor and the animated place moved since last frame: nothing moving and nothing swinging, it
        // sleeps (no stepping at all) until something does.
        float moved = anchorWorld.distanceSquared(this.lastAnchor) + targetWorld.distanceSquared(this.lastTarget);
        this.anchorSpeed = deltaTime > 1.0E-4F ? (float) Math.sqrt(anchorWorld.distanceSquared(this.lastAnchor)) / Math.min(deltaTime, 0.1F) : 0.0F;
        if (this.follow > 0.0F && deltaTime > 1.0E-4F) this.targetVelocity.set(targetWorld).sub(this.lastTarget).div(Math.min(deltaTime, 0.1F));
        else this.targetVelocity.zero();
        this.lastAnchor.set(anchorWorld);
        this.lastTarget.set(targetWorld);
        float dt = Math.min(deltaTime, 0.1F);                                   // a long hitch is not a long fall
        boolean far = this.tooFar(anchorWorld);
        boolean asleep = this.wind <= 0.0F && this.still > 12 && moved < 1.0E-8F;
        if (!far && !asleep && dt > 0.0F) this.step(armature, index, anchorWorld, targetWorld, reach, toWorld, dt, posed, modelToWorld, context, moved);

        if (!this.tip.isFinite() || !this.velocity.isFinite()) {
            this.settled = false;
            return;
        }
        Vector3f from = this.from.set(targetWorld).sub(anchorWorld).normalize();
        Vector3f to = this.to.set(this.tip).sub(anchorWorld).normalize();
        // The angle from a clamped dot product: at rest the two are equal, and rounding must not make it NaN.
        float angle = (float) Math.acos(Mth.clamp(from.dot(to), -1.0F, 1.0F));
        float wanted = Math.min(angle, this.maxAngle * Mth.DEG_TO_RAD) * this.weight;
        Vector3f axis = this.axis.set(from).cross(to);
        boolean swings = wanted >= 1.0E-5F && axis.lengthSquared() >= 1.0E-12F && Float.isFinite(wanted);
        boolean spins = this.twist > 0.0F && Math.abs(this.twistAngle) > 1.0E-4F && Float.isFinite(this.twistAngle);
        if (!swings && !spins) return;
        Quaternionf swing = swings ? this.swing.rotationAxis(wanted, axis.normalize()) : this.swing.identity();
        // The spin about its length, where it hangs now, after the swing that put it there.
        if (spins) swing.premul(this.spin.rotationAxis(this.twistAngle * this.weight, swings ? this.along.set(from).rotate(swing) : from));
        // The swing is in world axes; turn it into the model's.
        Quaternionf placement = modelToWorld.getUnnormalizedRotation(this.placement).normalize();
        Quaternionf local = this.local.set(placement).conjugate().mul(swing).mul(placement);
        matrix.mulLocal(this.around.translation(pivot).rotate(local).translate(-pivot.x, -pivot.y, -pivot.z));
    }

    // Steps the weight through this frame: sub-steps of at most 1/120 s (8 at most), pulled to the animated place,
    // pushed by gravity or buoyancy and the wind, held at its length, out of its colliders and the world.
    private void step(BakedArmature armature, int index, Vector3f anchorWorld, Vector3f targetWorld, float reach, float toWorld, float dt,
                      Matrix4f[] posed, Matrix4fc modelToWorld, ArmatureContext context, float moved) {
        // The colliders, placed in the world where the joints already posed this frame have them.
        int balls = 0;
        if (!this.colliders.isEmpty()) {
            if (this.balls.length < this.colliders.size() * 4) this.balls = new float[this.colliders.size() * 4];
            for (Collider collider : this.colliders) {
                ArmaturePoint point = armature.point(collider.point());
                if (point == null || point.joint() < 0 || point.joint() >= index) continue;
                Vector3f at = modelToWorld.transformPosition(posed[point.joint()].transformPosition(this.scratch.set(point.position())));
                this.balls[balls * 4] = at.x;
                this.balls[balls * 4 + 1] = at.y;
                this.balls[balls * 4 + 2] = at.z;
                this.balls[balls * 4 + 3] = collider.radius() * toWorld;
                balls++;
            }
        }
        float worldRadius = this.collisionRadius * toWorld;
        this.bumpRadius = worldRadius;
        this.bumpSpacing = this.spacing * toWorld;
        if (this.blocks || this.entities) this.lookAround(context, anchorWorld, reach + worldRadius, dt);
        int boxes = this.blockBoxes + this.entityBoxes;

        int steps = Mth.clamp(Mth.ceil(dt * 120.0F), 1, 8);
        float h = dt / steps;
        float inverseMass = 1.0F / this.mass, shortest = reach * (1.0F - this.stretch), longest = reach * (1.0F + this.stretch);
        float spinFade = this.twist > 0.0F ? (float) Math.exp(-1.5F * h) : 1.0F;
        float speedBefore = this.velocity.lengthSquared();
        Vector3f accel = this.accel, radial = this.radial;
        for (int i = 0; i < steps; i++) {
            this.clock += h;
            // Pulled to its place, its swinging damped (relative to how its place moves, as far as it follows its wearer).
            float rx = this.velocity.x - this.targetVelocity.x * this.follow, ry = this.velocity.y - this.targetVelocity.y * this.follow,
                    rz = this.velocity.z - this.targetVelocity.z * this.follow;
            accel.set(targetWorld).sub(this.tip).mul(this.stiffness).sub(rx * this.damping, ry * this.damping, rz * this.damping);
            if (this.wind > 0.0F) {
                // A slow drift, two waves out of step on each axis, and now and then a gust doubling it; and along the
                // breeze, if it has one, steadily, gusting too.
                float t = this.clock + this.phase;
                float gust = Mth.sin(t * 0.31F) > 0.55F ? 2.0F : 1.0F;
                accel.add(this.wind * gust * (Mth.sin(t * 0.83F) * 0.6F + Mth.sin(t * 2.1F + 1.3F) * 0.4F),
                        this.wind * 0.25F * Mth.sin(t * 1.37F + 0.7F),
                        this.wind * gust * (Mth.cos(t * 0.71F + 2.0F) * 0.6F + Mth.sin(t * 1.7F) * 0.4F));
                accel.fma(this.wind * (0.7F + 0.3F * gust), this.windDirection);
            }
            // The air: dragging it back the faster it goes, and rippling it the faster its wearer moves.
            if (this.airDrag > 0.0F) {
                float speed = this.velocity.length();
                accel.fma(-this.airDrag * speed, this.velocity);
            }
            if (this.flutter > 0.0F && this.anchorSpeed > 0.05F) {
                float f = (this.clock + this.phase) * 9.0F, push = this.flutter * 4.0F * this.anchorSpeed;
                accel.add(push * Mth.sin(f * 1.3F), push * 0.5F * Mth.sin(f * 1.7F + 1.0F), push * Mth.cos(f * 1.1F + 2.0F));
            }
            // Stretched or squashed, it springs back toward its length.
            if (this.stretch > 0.0F) {
                radial.set(this.tip).sub(anchorWorld);
                float d = radial.length();
                if (d > 1.0E-6F) accel.fma((reach - d) * 300.0F / d, radial);
            }
            accel.mul(inverseMass);
            // Holding its shape, however heavy it is, and damped to match (critically), so the stiffer it holds the less it
            // rings: a chain of stiff cloth would otherwise whip, each length's swing feeding the next.
            if (this.bend > 0.0F) {
                float settle = 2.0F * (float) Math.sqrt(this.bend);
                accel.add((targetWorld.x - this.tip.x) * this.bend - rx * settle, (targetWorld.y - this.tip.y) * this.bend - ry * settle,
                        (targetWorld.z - this.tip.z) * this.bend - rz * settle);
            }
            accel.y -= this.gravity;
            this.velocity.fma(h, accel);
            this.tip.fma(h, this.velocity);
            // Stay at the joint's length from the pivot (within its stretch; a rope only stops it going further), losing
            // the speed along it.
            radial.set(this.tip).sub(anchorWorld);
            float distance = radial.length();
            if (distance < 1.0E-6F) continue;
            radial.div(distance);
            // Pulling taut on what it hangs from: the weight above gives way along it by this one's share of their mass.
            if (this.pulls && distance > longest && this.above != null && this.above.settled && this.above.tip.isFinite()) {
                float share = this.mass / (this.mass + this.above.mass);
                this.above.tip.fma((distance - longest) * share, radial);
                float outward = this.velocity.dot(radial) - this.above.velocity.dot(radial);
                if (outward > 0.0F) this.above.velocity.fma(outward * share, radial);
                this.above.still = 0;
            }
            if (!this.rope || distance > longest) {
                float hold = this.rope ? longest : Mth.clamp(distance, shortest, longest);
                if (hold != distance) {
                    this.tip.set(anchorWorld).fma(hold, radial);
                    float along = this.velocity.dot(radial);
                    if (this.stretch <= 0.0F ? !this.rope || along > 0.0F : (distance > hold) == (along > 0.0F)) this.velocity.fma(-along, radial);
                } else if (this.stretch <= 0.0F && !this.rope) {
                    float along = this.velocity.dot(radial);
                    this.velocity.fma(-along, radial);
                }
            }
            if (this.twist > 0.0F) {
                // Swung sideways, it turns on its length; the spin dies away.
                Vector3f across = this.across.set(0.0F, 1.0F, 0.0F).cross(radial);
                float sideways = across.lengthSquared() > 1.0E-6F ? this.velocity.dot(across.normalize()) : 0.0F;
                this.twistSpeed = (this.twistSpeed + sideways * this.twist * 6.0F * h) * spinFade;
                this.twistAngle += this.twistSpeed * h;
            }
            boolean pushed = false;
            // Out of the colliders: pushed to the ball's surface, its speed into the ball lost.
            for (int c = 0; c < balls; c++) {
                float dx = this.tip.x - this.balls[c * 4], dy = this.tip.y - this.balls[c * 4 + 1], dz = this.tip.z - this.balls[c * 4 + 2];
                float r = this.balls[c * 4 + 3];
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= r * r || d2 < 1.0E-12F) continue;
                float d = (float) Math.sqrt(d2);
                Vector3f normal = this.normal.set(dx / d, dy / d, dz / d);
                this.tip.fma(r - d, normal);
                float into = this.velocity.dot(normal);
                if (into < 0.0F) this.velocity.fma(-into, normal);
                pushed = true;
            }
            // Out of the world's blocks and entities: the ball round the weight pushed out of each box it is in, its speed
            // into the box turned back (a bounce) and its speed along it dragged.
            for (int b = 0; b < boxes; b++) {
                float[] box = b < this.blockBoxes ? this.blockBox : this.entityBox;
                int o = (b < this.blockBoxes ? b : b - this.blockBoxes) * 6;
                float qx = Mth.clamp(this.tip.x, box[o], box[o + 3]), qy = Mth.clamp(this.tip.y, box[o + 1], box[o + 4]), qz = Mth.clamp(this.tip.z, box[o + 2], box[o + 5]);
                float dx = this.tip.x - qx, dy = this.tip.y - qy, dz = this.tip.z - qz;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= worldRadius * worldRadius) continue;
                Vector3f normal = this.normal;
                float depth;
                if (d2 > 1.0E-10F) {
                    float d = (float) Math.sqrt(d2);
                    normal.set(dx / d, dy / d, dz / d);
                    depth = worldRadius - d;
                } else {
                    // Its middle is inside the box: out the nearest face.
                    float best = this.tip.x - box[o];
                    normal.set(-1, 0, 0);
                    if (box[o + 3] - this.tip.x < best) { best = box[o + 3] - this.tip.x; normal.set(1, 0, 0); }
                    if (this.tip.y - box[o + 1] < best) { best = this.tip.y - box[o + 1]; normal.set(0, -1, 0); }
                    if (box[o + 4] - this.tip.y < best) { best = box[o + 4] - this.tip.y; normal.set(0, 1, 0); }
                    if (this.tip.z - box[o + 2] < best) { best = this.tip.z - box[o + 2]; normal.set(0, 0, -1); }
                    if (box[o + 5] - this.tip.z < best) { best = box[o + 5] - this.tip.z; normal.set(0, 0, 1); }
                    depth = best + worldRadius;
                }
                this.tip.fma(depth, normal);
                float into = this.velocity.dot(normal);
                if (into < 0.0F) {
                    this.velocity.fma(-(1.0F + this.bounce) * into, normal);
                    // Dragged along the face it rubs on.
                    float stay = this.velocity.dot(normal);
                    this.along.set(this.velocity).fma(-stay, normal);
                    this.velocity.fma(-Math.min(1.0F, this.friction * 0.5F), this.along);
                }
                pushed = true;
            }
            // Off the other springs that bump: each pushes itself half the overlap away, and takes half of the speed
            // they meet at back, bounced (they do the same with it, so they part evenly).
            if (this.bump) {
                for (Spring other : this.peers) {
                    if (other == this || !other.settled || !other.tip.isFinite()) continue;
                    float dx = this.tip.x - other.tip.x, dy = this.tip.y - other.tip.y, dz = this.tip.z - other.tip.z;
                    float min = worldRadius + other.bumpRadius, room = min + this.bumpSpacing + other.bumpSpacing;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= room * room || d2 < 1.0E-12F) continue;
                    float d = (float) Math.sqrt(d2);
                    Vector3f normal = this.normal.set(dx / d, dy / d, dz / d);
                    // Within the room they keep: eased apart, the harder the nearer.
                    if (room > min) {
                        this.velocity.fma(Math.min(1.0F, (room - d) / (room - min)) * SPREAD * h, normal);
                        other.still = 0;
                    }
                    if (d >= min) continue;
                    this.tip.fma((min - d) * 0.5F, normal);
                    float closing = (this.velocity.x - other.velocity.x) * normal.x + (this.velocity.y - other.velocity.y) * normal.y
                            + (this.velocity.z - other.velocity.z) * normal.z;
                    if (closing < 0.0F) this.velocity.fma(-closing * 0.5F * (1.0F + this.bounce), normal);
                    other.still = 0;                                        // a knock wakes it
                    pushed = true;
                }
            }
            // Woven to its neighbours: back to the distance the animation puts between them, half the way (they do the
            // same), firmly when pulled apart and as softly as linkStiffness when pushed together; and the speed they
            // part or close at damped, so the sheet moves together instead of jittering.
            for (Spring other : this.linked) {
                if (!other.settled || !other.tip.isFinite()) continue;
                float dx = this.tip.x - other.tip.x, dy = this.tip.y - other.tip.y, dz = this.tip.z - other.tip.z;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 1.0E-12F) continue;
                float d = (float) Math.sqrt(d2);
                float rest = targetWorld.distance(other.lastTarget);
                float off = d - rest;
                float give = off > 0.0F ? 0.5F : 0.5F * this.linkStiffness;
                if (Math.abs(off) * give < 1.0E-6F) continue;
                Vector3f normal = this.normal.set(dx / d, dy / d, dz / d);
                this.tip.fma(-off * give, normal);
                float parting = (this.velocity.x - other.velocity.x) * normal.x + (this.velocity.y - other.velocity.y) * normal.y
                        + (this.velocity.z - other.velocity.z) * normal.z;
                this.velocity.fma(-parting * 0.5F * Math.min(1.0F, h * 30.0F), normal);
                if (Math.abs(off) > 1.0E-3F) other.still = 0;                     // a tug wakes it
                pushed = true;
            }
            if (pushed) {
                radial.set(this.tip).sub(anchorWorld);
                float again = radial.length();
                if (again > 1.0E-6F) this.tip.set(anchorWorld).fma(Mth.clamp(again, shortest, longest) / again, radial);
            }
        }
        // Counting still frames toward sleep: barely moving, and nothing moving it.
        float speed = this.velocity.lengthSquared();
        this.still = speed < 1.0E-6F && speedBefore < 1.0E-6F && moved < 1.0E-8F ? this.still + 1 : 0;
    }

    /**
     * The boxes of the world round the weight, in blocks: the blocks' collision shapes, looked for again a few times a
     * second or when the anchor has moved half a block, and the entities' boxes, every few frames (they move).
     */
    private void lookAround(ArmatureContext context, Vector3f anchorWorld, float reach, float dt) {
        if (context == null || context.level() == null) {
            this.blockBoxes = this.entityBoxes = 0;
            return;
        }
        net.minecraft.world.level.Level level = context.level();
        double grow = reach + 0.25;
        this.worldAge += dt;
        this.entityAge += dt;
        boolean blocksStale = this.worldAge > 0.25F || anchorWorld.distanceSquared(this.worldAt) > 0.25F;
        boolean entitiesStale = this.entityAge > 0.05F;
        if (!blocksStale && !entitiesStale) return;
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(anchorWorld.x - grow, anchorWorld.y - grow, anchorWorld.z - grow,
                anchorWorld.x + grow, anchorWorld.y + grow, anchorWorld.z + grow);
        if (this.blocks && blocksStale) {
            this.found.clear();
            for (net.minecraft.world.phys.shapes.VoxelShape shape : level.getBlockCollisions(null, around)) this.found.addAll(shape.toAabbs());
            this.blockBox = pack(this.found, this.blockBox);
            this.blockBoxes = this.found.size();
            this.worldAge = 0.0F;
            this.worldAt.set(anchorWorld);
        }
        if (this.entities && entitiesStale) {
            this.found.clear();
            net.minecraft.world.entity.Entity self = context.entity();
            for (net.minecraft.world.entity.Entity other : level.getEntities(self, around,
                    e -> !e.isSpectator() && e.isAlive() && (self == null || !e.isPassengerOfSameVehicle(self)))) {
                this.found.add(other.getBoundingBox());
            }
            this.entityBox = pack(this.found, this.entityBox);
            this.entityBoxes = this.found.size();
            this.entityAge = 0.0F;
        }
    }

    private static float[] pack(List<net.minecraft.world.phys.AABB> boxes, float[] into) {
        if (into.length < boxes.size() * 6) into = new float[Math.max(boxes.size() * 6, into.length * 2)];
        for (int i = 0; i < boxes.size(); i++) {
            net.minecraft.world.phys.AABB box = boxes.get(i);
            into[i * 6] = (float) box.minX; into[i * 6 + 1] = (float) box.minY; into[i * 6 + 2] = (float) box.minZ;
            into[i * 6 + 3] = (float) box.maxX; into[i * 6 + 4] = (float) box.maxY; into[i * 6 + 5] = (float) box.maxZ;
        }
        return into;
    }

    /** Whether it is too far from the camera for its swing to be worth working out ({@link #simulationDistance}). */
    private boolean tooFar(Vector3f anchorWorld) {
        if (this.simulationDistance <= 0.0F) return false;
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) return false;
        net.minecraft.world.phys.Vec3 eye = camera.position();
        double dx = eye.x - anchorWorld.x, dy = eye.y - anchorWorld.y, dz = eye.z - anchorWorld.z;
        return dx * dx + dy * dy + dz * dz > (double) this.simulationDistance * this.simulationDistance;
    }

    private record Collider(String point, float radius) {
    }

    private void findRestTip(BakedArmature armature, int index) {
        Joint joint = armature.joint(index);
        Vector3f pivot = new Vector3f(joint.pivot());
        Vector3f tip = null;
        for (int child : joint.children()) {
            Vector3f at = new Vector3f(armature.joint(child).pivot());
            if (at.distanceSquared(pivot) > 1.0E-4F) {
                tip = at;
                break;
            }
        }
        if (tip == null) tip = centreOfFaces(armature, index);
        if (tip == null || tip.distanceSquared(pivot) < 1.0E-4F) tip = new Vector3f(pivot).add(0.0F, -4.0F, 0.0F);
        if (this.length > 0.0F) {
            Vector3f direction = new Vector3f(tip).sub(pivot).normalize();
            tip = new Vector3f(pivot).fma(this.length, direction);
        }
        this.restTip.set(tip);
    }

    private static Vector3f centreOfFaces(BakedArmature armature, int index) {
        Vector3f sum = new Vector3f();
        int count = 0;
        for (ArmatureMesh.Group group : armature.mesh().groups()) {
            float[] faces = group.byJoint()[index];
            if (faces == null) continue;
            for (int at = 0; at < faces.length; at += ArmatureMesh.STRIDE) {
                for (int corner = 0; corner < 4; corner++) {
                    int v = at + corner * 5;
                    sum.add(faces[v], faces[v + 1], faces[v + 2]);
                    count++;
                }
            }
        }
        return count == 0 ? null : sum.div(count);
    }
}
