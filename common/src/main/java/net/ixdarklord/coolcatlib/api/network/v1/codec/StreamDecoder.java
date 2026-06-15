package net.ixdarklord.coolcatlib.api.network.v1.codec;

@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I var1);
}
