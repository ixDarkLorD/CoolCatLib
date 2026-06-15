package net.ixdarklord.coolcatlib.api.network.v1.codec;

@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T var1, O var2);
}
