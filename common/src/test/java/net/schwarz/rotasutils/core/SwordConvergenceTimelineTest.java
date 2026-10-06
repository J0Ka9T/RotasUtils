package net.schwarz.rotasutils.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SwordConvergenceTimelineTest {
 @Test void theStormHitsContinuouslyAtAFixedInterval(){
  assertEquals(30,SwordConvergenceTimeline.DAMAGE_PULSES);
  assertEquals(SwordConvergenceTimeline.RELEASE+6,SwordConvergenceTimeline.damageTick(0));
  assertEquals(SwordConvergenceTimeline.DETONATE,
          SwordConvergenceTimeline.damageTick(SwordConvergenceTimeline.DAMAGE_PULSES-1));
  for(int i=1;i<SwordConvergenceTimeline.DAMAGE_PULSES;i++)
   assertEquals(SwordConvergenceTimeline.DAMAGE_INTERVAL,
           SwordConvergenceTimeline.damageTick(i)-SwordConvergenceTimeline.damageTick(i-1));
  assertTrue(SwordConvergenceTimeline.damageTick(0)>SwordConvergenceTimeline.RELEASE);
 }
 @Test void bladesGatherInFourWavesThenHoldThroughTheHush(){
  assertEquals(8,SwordConvergenceTimeline.count(12));
  assertEquals(32,SwordConvergenceTimeline.count(35));
  assertEquals(128,SwordConvergenceTimeline.count(58));
  assertEquals(384,SwordConvergenceTimeline.count(SwordConvergenceTimeline.SUMMON_END));
  assertEquals(SwordConvergenceTimeline.HUSH,
          SwordConvergenceTimeline.RELEASE-SwordConvergenceTimeline.FREEZE);
  for(int i=0;i<384;i++)for(float t=SwordConvergenceTimeline.FREEZE;t<SwordConvergenceTimeline.RELEASE;t+=.25f)
   assertEquals(SwordConvergenceTimeline.gatherPosition(i,80),SwordConvergenceTimeline.gatherPosition(i,t));
 }
 @Test void everyBladeStartsHighAboveAndOutsideTheTarget(){
  for(int i=0;i<384;i++){
   var p=SwordConvergenceTimeline.gatherPosition(i,(float)SwordConvergenceTimeline.born(i));
   assertTrue(Math.hypot(p.x(),p.z())>=6.9);
   assertTrue(p.y()>5);
  }
 }
 @Test void theDomeSurroundsTheTargetOnEverySide(){
  boolean[] quadrants=new boolean[4];
  for(int i=0;i<384;i++){
   var origin=SwordConvergenceTimeline.gatherPosition(i,SwordConvergenceTimeline.FREEZE);
   quadrants[(origin.x()>0?1:0)+(origin.z()>0?2:0)]=true;
   for(float t=0;t<SwordConvergenceTimeline.FREEZE;t+=.25f){
    var p=SwordConvergenceTimeline.gatherPosition(i,t);
    assertTrue(Double.isFinite(p.x()+p.y()+p.z()));
   }
  }
  for(boolean covered:quadrants)assertTrue(covered);
 }
 @Test void theRainNeverStopsDuringTheStorm(){
  for(float t=SwordConvergenceTimeline.RELEASE;t<SwordConvergenceTimeline.LIFE;t+=1f){
   int diving=0;
   for(int i=0;i<384;i++){
    double phase=SwordConvergenceTimeline.stormPhase(i,t);
    assertTrue(phase>=0&&phase<SwordConvergenceTimeline.CYCLE);
    double alpha=SwordConvergenceTimeline.stormAlpha(i,t);
    assertTrue(alpha>=0&&alpha<=1);
    var p=SwordConvergenceTimeline.stormPosition(i,t);
    assertTrue(Double.isFinite(p.x()+p.y()+p.z()));
    if(phase<SwordConvergenceTimeline.DIVE)diving++;
   }
   assertTrue(diving>50,"some blades are always falling, got "+diving);
  }
 }
 @Test void aBladeFallsThenReformsForTheNextWave(){
  int i=0;
  double phase0=SwordConvergenceTimeline.stormPhase(i,SwordConvergenceTimeline.RELEASE);
  assertEquals(SwordConvergenceTimeline.stormFlight(i,SwordConvergenceTimeline.RELEASE),phase0/SwordConvergenceTimeline.DIVE,1e-9);
  var atSky=SwordConvergenceTimeline.stormPosition(i,SwordConvergenceTimeline.RELEASE);
  var atTarget=SwordConvergenceTimeline.stormPosition(i,SwordConvergenceTimeline.RELEASE+SwordConvergenceTimeline.DIVE);
  assertEquals(0,atTarget.distance(new SwordConvergenceTimeline.Point(0,0,0)),1e-6);
  assertTrue(atSky.distance(new SwordConvergenceTimeline.Point(0,0,0))>5);
  var again=SwordConvergenceTimeline.stormPosition(i,SwordConvergenceTimeline.RELEASE+SwordConvergenceTimeline.CYCLE);
  assertTrue(again.distance(new SwordConvergenceTimeline.Point(0,0,0))>5);
 }
 @Test void rollAndFormationStayInRange(){
  for(int i=0;i<SwordConvergenceTimeline.SWORDS;i++)assertTrue(Double.isFinite(SwordConvergenceTimeline.roll(i)));
  assertNotEquals(SwordConvergenceTimeline.roll(0),SwordConvergenceTimeline.roll(1));
  assertEquals(0,SwordConvergenceTimeline.formation(SwordConvergenceTimeline.FREEZE),1e-9);
  assertEquals(1,SwordConvergenceTimeline.formation(SwordConvergenceTimeline.RELEASE+16),1e-9);
 }
}
