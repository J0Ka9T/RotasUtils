package net.schwarz.rotasutils.client.screen.player;

public final class NpcActionPresentation {
    public enum Kind { DIALOGUE, QUEST, SHOP, SERVICE, GIFT, NAVIGATION }

    private NpcActionPresentation() {
    }

    public static Kind kind(String response) {
        if (response == null || response.startsWith("@")) return Kind.NAVIGATION;
        if (response.startsWith("choice:")) return Kind.DIALOGUE;
        if (response.startsWith("quest:") || response.equals("board")) return Kind.QUEST;
        if (response.equals("shop")) return Kind.SHOP;
        if (response.startsWith("gift:")) return Kind.GIFT;
        return Kind.SERVICE;
    }

    public static String glyph(String response) {
        return switch (kind(response)) {
            case DIALOGUE -> "?";
            case QUEST -> response != null && response.equals("board") ? "#" : "!";
            case SHOP -> "$";
            case SERVICE -> "+";
            case GIFT -> "*";
            case NAVIGATION -> "<";
        };
    }
}
