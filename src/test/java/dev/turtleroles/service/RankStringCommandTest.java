package dev.turtleroles.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RankStringCommandTest {
 @Test void rankChangesRecomputeCooldownFromOriginalUse(){
  assertEquals(50000,RankStringCommand.remaining(160000,60000,60,110000));
  assertEquals(5000,RankStringCommand.remaining(160000,60000,15,110000));
  assertEquals(0,RankStringCommand.remaining(160000,60000,15,120000));
  assertEquals(40000,RankStringCommand.remaining(115000,15000,60,120000));
  assertEquals(0,RankStringCommand.remaining(160000,60000,0,110000));
  assertEquals(0,RankStringCommand.remaining(0,60000,60,110000));
 }
}
