package net.schwarz.rotasutils.client.screen.player;

public final class NpcConversationLayout {
    private final int dialogueX;
    private final int dialogueY;
    private final int dialogueWidth;
    private final int dialogueHeight;
    private final int optionsX;
    private final int optionsY;
    private final int optionsWidth;
    private final int optionsHeight;
    private final int optionRowHeight;

    private NpcConversationLayout(
            int dialogueX,
            int dialogueY,
            int dialogueWidth,
            int dialogueHeight,
            int optionsX,
            int optionsY,
            int optionsWidth,
            int optionsHeight,
            int optionRowHeight) {
        this.dialogueX = dialogueX;
        this.dialogueY = dialogueY;
        this.dialogueWidth = dialogueWidth;
        this.dialogueHeight = dialogueHeight;
        this.optionsX = optionsX;
        this.optionsY = optionsY;
        this.optionsWidth = optionsWidth;
        this.optionsHeight = optionsHeight;
        this.optionRowHeight = optionRowHeight;
    }

    public static NpcConversationLayout compute(int screenWidth, int screenHeight, int optionCount) {
        int marginX = Math.max(12, screenWidth / 48);
        int marginBottom = Math.max(10, screenHeight / 40);
        int dialogueWidth = Math.min(screenWidth - marginX * 2, Math.max(320, screenWidth * 86 / 100));
        int dialogueHeight = Math.max(64, Math.min(92, screenHeight / 4));
        int dialogueX = (screenWidth - dialogueWidth) / 2;
        int dialogueY = screenHeight - marginBottom - dialogueHeight;

        int rows = Math.max(1, optionCount);
        int rowHeight = 24;
        int maxOptionsHeight = Math.max(rowHeight, dialogueY - Math.max(80, screenHeight / 3) - 10);
        int visibleRows = Math.max(1, Math.min(rows, maxOptionsHeight / rowHeight));
        int optionsHeight = visibleRows * rowHeight;
        int optionsWidth = Math.min(screenWidth / 2, Math.max(screenWidth * 34 / 100, 220));
        int optionsX = marginX;
        int optionsY = dialogueY - 10 - optionsHeight;

        return new NpcConversationLayout(
                dialogueX,
                dialogueY,
                dialogueWidth,
                dialogueHeight,
                optionsX,
                optionsY,
                optionsWidth,
                optionsHeight,
                rowHeight);
    }

    public int dialogueX() {
        return dialogueX;
    }

    public int dialogueY() {
        return dialogueY;
    }

    public int dialogueWidth() {
        return dialogueWidth;
    }

    public int dialogueHeight() {
        return dialogueHeight;
    }

    public int optionsX() {
        return optionsX;
    }

    public int optionsY() {
        return optionsY;
    }

    public int optionsWidth() {
        return optionsWidth;
    }

    public int optionsHeight() {
        return optionsHeight;
    }

    public int optionRowHeight() {
        return optionRowHeight;
    }
}
