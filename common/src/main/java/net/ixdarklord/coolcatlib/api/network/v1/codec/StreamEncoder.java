package net.ixdarklord.coolcatlib.api.network.v1.codec;

@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O var1, T var2);
}
