package net.ixdarklord.coolcatcanvas.api.client.utils;

import net.minecraft.resources.Identifier;

public final class NineSliceInfo {

    private NineSliceInfo() {}

    public static final class SliceBounds {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private SliceBounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public static SliceBounds of(int left, int top, int right, int bottom) {
            return new SliceBounds(left, top, right, bottom);
        }

        public static SliceBounds uniform(int size) {
            return new SliceBounds(size, size, size, size);
        }

        public static SliceBounds size(int width, int height) {
            return new SliceBounds(width, height, width, height);
        }

        public int left() { return left; }
        public int top() { return top; }
        public int right() { return right; }
        public int bottom() { return bottom; }
    }

    public static final class TextureRegion {
        private final int uOffset;
        private final int vOffset;
        private final int uWidth;
        private final int vHeight;

        private TextureRegion(int uOffset, int vOffset, int uWidth, int vHeight) {
            this.uOffset = uOffset;
            this.vOffset = vOffset;
            this.uWidth = uWidth;
            this.vHeight = vHeight;
        }

        public static TextureRegion of(int u, int v, int width, int height) {
            return new TextureRegion(u, v, width, height);
        }

        public static TextureRegion UV(int u, int v) {
            return new TextureRegion(u, v, 0, 0);
        }

        public static TextureRegion region(int width, int height) {
            return new TextureRegion(0, 0, width, height);
        }

        public int uOffset() { return uOffset; }
        public int vOffset() { return vOffset; }
        public int uWidth() { return uWidth; }
        public int vHeight() { return vHeight; }
    }

    public static final class TextureInfo {
        private final Identifier texture;
        private final int width;
        private final int height;

        private TextureInfo(Identifier texture, int width, int height) {
            this.texture = texture;
            this.width = width;
            this.height = height;
        }

        public static TextureInfo of(Identifier texture, int width, int height) {
            return new TextureInfo(texture, width, height);
        }

        public static TextureInfo of(Identifier texture) {
            return new TextureInfo(texture, 256, 256);
        }

        public Identifier texture() { return texture; }
        public int width() { return width; }
        public int height() { return height; }
    }
}
