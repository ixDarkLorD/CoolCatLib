package net.ixdarklord.coolcat_lib.util;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.NoSuchElementException;

public class Divisor implements IntIterator {
    private final int denominator;
    private final int quotient;
    private final int mod;
    private int returnedParts;
    private int remainder;

    public Divisor(int i, int j) {
        this.denominator = j;
        if (j > 0) {
            this.quotient = i / j;
            this.mod = i % j;
        } else {
            this.quotient = 0;
            this.mod = 0;
        }

    }

    public boolean hasNext() {
        return this.returnedParts < this.denominator;
    }

    public int nextInt() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        } else {
            int i = this.quotient;
            this.remainder += this.mod;
            if (this.remainder >= this.denominator) {
                this.remainder -= this.denominator;
                ++i;
            }

            ++this.returnedParts;
            return i;
        }
    }

    @VisibleForTesting
    public static Iterable<Integer> asIterable(int numerator, int denominator) {
        return () -> new Divisor(numerator, denominator);
    }
}
