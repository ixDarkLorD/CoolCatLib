package net.ixdarklord.coolcat_lib.util;

import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

public class RecipeHelper {

    /**
     * Helper class to perform a shapeless recipe match when ingredients that require testing are involved.
     *
     * <p>The problem to solve is a maximum cardinality bipartite matching, for which this implementation uses the augmenting path algorithm.
     * This has good performance in simple cases, and sufficient O(N^3) asymptotic complexity in the worst case.
     */
    public static class Shapeless {
        private final int[] match;
        /**
         * The first {@code size} bits are for the visited array (on the left partition).
         * The remaining {@code size * size} bits are for the adjacency matrix.
         */
        private final BitSet bitSet;

        private Shapeless(int size) {
            match = new int[size];
            bitSet = new BitSet(size * (size+1));
        }

        private boolean augment(int l) {
            if (bitSet.get(l)) return false;
            bitSet.set(l);

            for (int r = 0; r < match.length; ++r) {
                if (bitSet.get(match.length + l * match.length + r)) {
                    if (match[r] == -1 || augment(match[r])) {
                        match[r] = l;
                        return true;
                    }
                }
            }

            return false;
        }

        public static <T extends ItemStack> boolean isMatch(List<T> inputs, List<? extends Predicate<T>> tests) {
            if (inputs.size() != tests.size()) {
                return false;
            }

            Shapeless m = new Shapeless(tests.size());

            // Build stack -> ingredient bipartite graph
            for (int i = 0; i < inputs.size(); ++i) {
                T stack = inputs.get(i);

                for (int j = 0; j < tests.size(); ++j) {
                    if (tests.get(j).test(stack)) {
                        m.bitSet.set((i + 1) * m.match.length + j);
                    }
                }
            }

            // Init matches to -1 (no match)
            Arrays.fill(m.match, -1);

            // Try to find an augmenting path for each stack
            for (int i = 0; i < tests.size(); ++i) {
                if (!m.augment(i)) {
                    return false;
                }

                m.bitSet.set(0, m.match.length, false);
            }

            return true;
        }
    }

    /**
     * Helper class to perform a shaped recipe match when ingredients that require testing are involved.
     */
    public static class Shaped {
        public static <T extends ItemStack> boolean isMatch(List<T> inputs, List<? extends Predicate<T>> tests) {
            return findMatches(inputs, tests) != null;
        }
        public static <T extends ItemStack> int[] findMatches(List<T> inputs, List<? extends Predicate<T>> tests) {
            int elements = inputs.size();
            if (elements != tests.size())
                return null; // There will not be a 1:1 mapping of inputs -> tests

            int[] ret = new int[elements];
            Arrays.fill(ret, -1);

            // [UnusedInputs] [UnusedIngredients] [IngredientMatchMask]...
            BitSet data = new BitSet((elements + 2) * elements);
            for (int x = 0; x < elements; x++)
            {
                int matched = 0;
                int offset = (x + 2) * elements;
                Predicate<T> test = tests.get(x);

                for (int y = 0; y < elements; y++)
                {
                    if (data.get(y))
                        continue;

                    if (test.test(inputs.get(y)))
                    {
                        data.set(offset + y);
                        matched++;
                    }
                }

                if (matched == 0)
                    return null; //We have a test that matched none of the inputs

                if (matched == 1)
                {
                    if (!claim(ret, data, x, elements))
                        return null; //We failed to claim this index, which means it caused something else to go to 0 matches, which makes the whole thing fail
                }
            }

            if (data.nextClearBit(0) >= elements) //All items have been used, which means all tests have a match!
                return ret;

            // We should be in a state where multiple tests are satisfied by multiple inputs. So we need to try a branching recursive test.
            // However, for performance reasons, we should probably make that check a sub-set of the entire graph.
            if (backtrack(data, ret, 0, elements))
                return ret;

            return null; //Backtrack failed, no matches, we cry and go home now :(
        }
        private static boolean claim(int[] ret, BitSet data, int claimed, int elements) {
            Queue<Integer> pending = new LinkedList<Integer>();
            pending.add(claimed);

            while (pending.peek() != null)
            {
                int test = pending.poll();
                int offset = (test + 2) * elements;
                int used = data.nextSetBit(offset) - offset;

                if (used >= elements || used < 0)
                    throw new IllegalStateException("What? We matched something, but it wasn't set in the range of this test! Test: " + test +  " Used: " + used);

                data.set(used);
                data.set(elements + test);
                ret[used] = test;

                for (int x = 0; x < elements; x++)
                {
                    offset = (x + 2) * elements;
                    if (data.get(offset + used) && !data.get(elements + x))
                    {
                        data.clear(offset + used);
                        int count = 0;
                        for (int y = offset; y < offset + elements; y++)
                            if (data.get(y))
                                count++;

                        if (count == 0)
                            return false; //Claiming this caused another test to lose its last match..

                        if (count == 1)
                            pending.add(x);
                    }
                }
            }

            return true;
        }
        private static boolean backtrack(BitSet data, int[] ret, int start, int elements) {
            int test = data.nextClearBit(elements + start) - elements;
            if (test >= elements)
                return true; //Could not find the next unused test.

            if (test < 0)
                throw new IllegalStateException("This should never happen, negative test in backtrack!");

            int offset = (test + 2) * elements;
            for (int x = 0; x < elements; x++)
            {
                if (!data.get(offset + x) || data.get(x))
                    continue;

                data.set(x);

                if (backtrack(data, ret, test + 1, elements))
                {
                    ret[x] = test;
                    return true;
                }

                data.clear(x);
            }

            return false;
        }
    }
}
