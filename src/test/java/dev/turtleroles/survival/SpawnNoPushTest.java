package dev.turtleroles.survival;
import dev.turtleroles.TurtleRolesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SpawnNoPushTest {
 ServerMock server;
 @BeforeEach void setup(){server=MockBukkit.mock();}
 @AfterEach void close(){MockBukkit.unmock();}
 @Test void spawnDisablesCollisionsAndRestoresOriginalTeamAndFlag(){var p=server.addPlayer();var plugin=mock(TurtleRolesPlugin.class);var area=mock(SpawnArea.class);var board=p.getScoreboard();var team=board.registerNewTeam("original");team.prefix(Component.text("Admin "));team.addEntry(p.getName());p.setCollidable(true);when(area.inside(any())).thenReturn(true);var service=new SpawnNoPush(plugin,area);service.tick();assertFalse(p.isCollidable());assertEquals(Team.OptionStatus.NEVER,board.getEntryTeam(p.getName()).getOption(Team.Option.COLLISION_RULE));assertEquals(team.prefix(),board.getEntryTeam(p.getName()).prefix());when(area.inside(any())).thenReturn(false);service.tick();assertTrue(p.isCollidable());assertEquals(team,board.getEntryTeam(p.getName()));service.close();}
 @Test void preexistingNoCollisionIsNotReenabled(){var p=server.addPlayer();p.setCollidable(false);var area=mock(SpawnArea.class);when(area.inside(any())).thenReturn(true);var service=new SpawnNoPush(mock(TurtleRolesPlugin.class),area);service.tick();when(area.inside(any())).thenReturn(false);service.tick();assertFalse(p.isCollidable());service.close();}
 @Test void clonedEventBoardTemporaryTeamsAreRestored(){
  var p=server.addPlayer();var area=mock(SpawnArea.class);when(area.inside(any())).thenReturn(true);var service=new SpawnNoPush(mock(TurtleRolesPlugin.class),area);
  var old=p.getScoreboard();var original=old.registerNewTeam("rank");original.prefix(Component.text("Rank "));original.addEntry(p.getName());service.tick();
  var next=server.getScoreboardManager().getNewScoreboard();for(Team team:old.getTeams()){var copy=next.registerNewTeam(team.getName());copy.prefix(team.prefix());for(String entry:team.getEntries())copy.addEntry(entry);}
  p.setScoreboard(next);service.tick();when(area.inside(any())).thenReturn(false);service.tick();assertEquals("rank",next.getEntryTeam(p.getName()).getName());assertEquals(Component.text("Rank "),next.getEntryTeam(p.getName()).prefix());service.close();
 }
}
