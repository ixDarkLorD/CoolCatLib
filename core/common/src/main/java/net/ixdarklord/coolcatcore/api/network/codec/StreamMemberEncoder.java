package net.ixdarklord.coolcatcore.api.network.codec;

@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T var1, O var2);
}
