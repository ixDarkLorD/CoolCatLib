package net.ixdarklord.coolcatcore.api.network.codec;

@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I var1);
}
