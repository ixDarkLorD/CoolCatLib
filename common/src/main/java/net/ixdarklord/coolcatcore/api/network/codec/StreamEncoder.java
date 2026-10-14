package net.ixdarklord.coolcatcore.api.network.codec;

@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O var1, T var2);
}
