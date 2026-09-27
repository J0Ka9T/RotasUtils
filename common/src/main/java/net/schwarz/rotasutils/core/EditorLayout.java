package net.schwarz.rotasutils.core;

public record EditorLayout(int left, int top, int width, int height, int columnWidth, int contentTop, int contentHeight) {
    public static EditorLayout fit(int screenWidth, int screenHeight) {
        if (screenWidth < 240 || screenHeight < 200) { throw new IllegalArgumentException("Editor needs at least 240x200 GUI pixels"); }
        int width = Math.min(820, screenWidth - 16), height = Math.min(510, screenHeight - 16);
        int left = (screenWidth - width) / 2, top = (screenHeight - height) / 2;
        return new EditorLayout(left, top, width, height, (width - 32) / 3, top + 112, height - 152);
    }
}
