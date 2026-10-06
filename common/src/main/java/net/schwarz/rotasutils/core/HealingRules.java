package net.schwarz.rotasutils.core;
public final class HealingRules {
 private HealingRules() {}
 public static float power(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(80, value)) : 0; }
 public static float emergency(float health, float maximum, float budget) {
  if (!Float.isFinite(health) || !Float.isFinite(maximum)) return 0;
  return Math.max(0, Math.min(maximum - health, power(budget)));
 }
 public static float transfer(float donor, float health, float maximum, float budget) {
  if (!Float.isFinite(donor)) return 0;
  return Math.min(Math.max(0, donor - 1), emergency(health, maximum, budget));
 }
}
