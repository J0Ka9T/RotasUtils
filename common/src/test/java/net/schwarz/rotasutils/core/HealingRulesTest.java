package net.schwarz.rotasutils.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HealingRulesTest {
 @Test void transferCannotKillDonorOrOverhealRecipient() {
  assertEquals(0, HealingRules.transfer(1, 0, 20, 8));
  assertEquals(2, HealingRules.transfer(10, 18, 20, 8));
  assertEquals(3, HealingRules.transfer(4, 0, 20, 8));
 }
 @Test void invalidNumbersNeverReachHealth() {
  assertEquals(0, HealingRules.transfer(Float.NaN, 0, 20, 8));
  assertEquals(0, HealingRules.power(Float.POSITIVE_INFINITY));
  assertEquals(80, HealingRules.power(1000));
 }
 @Test void emergencyScalesWithMissingHealthAndNeverExceedsBudget() {
  assertEquals(0, HealingRules.emergency(20, 20, 8));
  assertEquals(8, HealingRules.emergency(2, 20, 8));
  assertEquals(4, HealingRules.emergency(16, 20, 8));
 }
}
