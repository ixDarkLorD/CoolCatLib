package net.ixdarklord.coolcatcore.api.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a game test, for {@link GameTests#register(String, Class)}. The method takes the test's
 * {@code GameTestHelper} and returns nothing; it may be static, or belong to an instance of its class made with the
 * class's no-argument constructor. Each test is named {@code <modid>:<class>/<method>}, in snake case
 * ({@code mymod:wand_tests/zaps_a_zombie}), or {@code <modid>:<class>/<name>} with {@link #name}.
 * <pre>{@code
 * public final class WandTests {
 *     @GameTest
 *     public static void zapsAZombie(GameTestHelper helper) {
 *         Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
 *         ...
 *         helper.succeedWhen(() -> helper.assertEntityNotPresent(EntityType.ZOMBIE));
 *     }
 * }
 * }</pre>
 * Each element is one of {@link GameTestOptions}'; see there for what it does.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The test's name instead of the method's (in snake case either way). */
    String name() default "";

    /** The structure the test runs in, as an id; empty for {@link GameTestOptions#PLATFORM}. */
    String structure() default "";

    int maxTicks() default 100;

    int setupTicks() default 0;

    boolean required() default true;

    /** Quarter turns clockwise, 0 to 3. */
    int rotation() default 0;

    boolean manualOnly() default false;

    int maxAttempts() default 1;

    int requiredSuccesses() default 1;

    boolean skyAccess() default false;

    int padding() default 0;
}
