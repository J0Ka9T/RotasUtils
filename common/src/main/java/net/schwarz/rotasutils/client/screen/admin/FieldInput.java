package net.schwarz.rotasutils.client.screen.admin;

public final class FieldInput {
    private FieldInput() { }

    public static String validate(String value, boolean integer) {
        try {
            if (integer) Integer.parseInt(value.trim());
            else if (!Double.isFinite(Double.parseDouble(value.trim()))) return "Enter a finite number";
            return "";
        } catch (NumberFormatException exception) {
            return integer ? "Enter a whole number" : "Enter a decimal number";
        }
    }
}
