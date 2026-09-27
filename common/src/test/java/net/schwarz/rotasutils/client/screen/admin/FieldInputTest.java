package net.schwarz.rotasutils.client.screen.admin;

public final class FieldInputTest {
    public static void main(String[] args) {
        check(FieldInput.validate(" 42 ", true).isEmpty(), "trimmed integer");
        check(!FieldInput.validate("", true).isEmpty(), "empty draft");
        check(!FieldInput.validate("-", true).isEmpty(), "incomplete negative draft");
        check(!FieldInput.validate("2147483648", true).isEmpty(), "integer overflow");
        check(!FieldInput.validate("1.5", true).isEmpty(), "fractional integer");
        check(FieldInput.validate("1.5", false).isEmpty(), "decimal");
        check(!FieldInput.validate("NaN", false).isEmpty(), "NaN");
        check(!FieldInput.validate("Infinity", false).isEmpty(), "infinity");
        check(!FieldInput.validate("1e999", false).isEmpty(), "decimal overflow");
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
