package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class NumericExpression {
    @FunctionalInterface private interface Node { double value(ToDoubleFunction<String> variables); }
    private final Node root;
    private NumericExpression(Node root) { this.root = root; }

    public static NumericExpression compile(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 1024) {
            throw new IllegalArgumentException("Expression must contain 1-1024 characters");
        }
        Parser parser = new Parser(expression); Node root = parser.expression(0); parser.space();
        if (parser.cursor != expression.length()) { throw parser.error("Unexpected token"); }
        return new NumericExpression(root);
    }

    public double evaluate(ToDoubleFunction<String> variables) { return finite(root.value(variables)); }
    private static double finite(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1.0e15) {
            throw new IllegalArgumentException("Expression result outside finite +/-1e15 range");
        }
        return value;
    }

    private static final class Parser {
        private final String input;
        private int cursor, nodes;
        private Parser(String input) { this.input = input; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at expression offset " + cursor); }
        private void space() { while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) { cursor++; } }
        private boolean take(char symbol) {
            space(); if (cursor < input.length() && input.charAt(cursor) == symbol) { cursor++; return true; } return false;
        }
        private Node bounded(Node node) {
            if (++nodes > 256) { throw error("Expression node budget exceeded"); }
            return variables -> finite(node.value(variables));
        }
        private Node expression(int depth) {
            Node left = product(depth);
            while (true) {
                Node previous = left;
                if (take('+')) { Node right = product(depth); left = bounded(v -> previous.value(v) + right.value(v)); }
                else if (take('-')) { Node right = product(depth); left = bounded(v -> previous.value(v) - right.value(v)); }
                else { return left; }
            }
        }
        private Node product(int depth) {
            Node left = atom(depth);
            while (true) {
                Node previous = left;
                if (take('*')) { Node right = atom(depth); left = bounded(v -> previous.value(v) * right.value(v)); }
                else if (take('/')) { Node right = atom(depth); left = bounded(v -> previous.value(v) / right.value(v)); }
                else if (take('%')) { Node right = atom(depth); left = bounded(v -> previous.value(v) % right.value(v)); }
                else { return left; }
            }
        }
        private Node atom(int depth) {
            if (depth > 32) { throw error("Expression nesting exceeds 32"); }
            if (take('+')) { return atom(depth + 1); }
            if (take('-')) { Node value = atom(depth + 1); return bounded(v -> -value.value(v)); }
            if (take('(')) { Node value = expression(depth + 1); if (!take(')')) { throw error("Missing closing parenthesis"); } return value; }
            space(); int start = cursor;
            if (cursor < input.length() && (Character.isDigit(input.charAt(cursor)) || input.charAt(cursor) == '.')) {
                while (cursor < input.length() && (Character.isDigit(input.charAt(cursor)) || input.charAt(cursor) == '.')) { cursor++; }
                double value;
                try { value = finite(Double.parseDouble(input.substring(start, cursor))); }
                catch (NumberFormatException failure) { throw error("Invalid numeric literal"); }
                return bounded(v -> value);
            }
            while (cursor < input.length() && (Character.isLetterOrDigit(input.charAt(cursor)) || input.charAt(cursor) == '_' || input.charAt(cursor) == '.')) { cursor++; }
            if (start == cursor) { throw error("Expected number, variable or function"); }
            String name = input.substring(start, cursor);
            if (!name.matches("[a-z_][a-z0-9_.]{0,127}")) { throw error("Invalid variable name"); }
            if (!take('(')) { return bounded(v -> v.applyAsDouble(name)); }
            List<Node> args = new ArrayList<>();
            if (!take(')')) {
                do { if (args.size() >= 3) { throw error("Too many function arguments"); } args.add(expression(depth + 1)); } while (take(','));
                if (!take(')')) { throw error("Missing closing function parenthesis"); }
            }
            int arity = switch (name) {
                case "abs", "floor", "ceil", "round", "sqrt" -> 1;
                case "min", "max", "pow" -> 2;
                case "clamp" -> 3;
                default -> throw error("Unknown function " + name);
            };
            if (args.size() != arity) { throw error("Wrong function argument count"); }
            return bounded(v -> {
                double a = args.get(0).value(v), b = arity > 1 ? args.get(1).value(v) : 0;
                return switch (name) {
                    case "abs" -> Math.abs(a); case "floor" -> Math.floor(a); case "ceil" -> Math.ceil(a);
                    case "round" -> Math.round(a); case "sqrt" -> Math.sqrt(a);
                    case "min" -> Math.min(a, b); case "max" -> Math.max(a, b); case "pow" -> Math.pow(a, b);
                    case "clamp" -> { double max = args.get(2).value(v); if (b > max) { throw new IllegalArgumentException("Reversed clamp bounds"); } yield Math.max(b, Math.min(max, a)); }
                    default -> throw new IllegalStateException(name);
                };
            });
        }
    }
}
