package dev.turtleroles.service;
import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SkyWordsTest {
 @TempDir Path folder;
 TurtleRolesPlugin plugin(){var p=mock(TurtleRolesPlugin.class);when(p.getDataFolder()).thenReturn(folder.toFile());when(p.getLogger()).thenReturn(Logger.getAnonymousLogger());return p;}
 @Test void colorsAndSmallCapsAreLiteral(){
  assertNotNull(SkyWords.color("purple"));assertEquals(0xff0000,SkyWords.color("#FF0000").value());assertNull(SkyWords.color("rainbow"));
  var w=new SkyWords.Word("word1",UUID.randomUUID(),0,80,0,"purple","Crates <red>",4);
  assertEquals("ᴄʀᴀᴛᴇꜱ <ʀᴇᴅ>",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(SkyWords.text(w)));
 }
 @Test void savedWordsReloadAndRemovalPersists(){try(var b=mockStatic(Bukkit.class)){
  var p=plugin();var s=new SkyWords(p);var w=new SkyWords.Word("word1",UUID.randomUUID(),-12.5,120,45,"red","Hello world",4);
  assertTrue(s.change(w.id(),w));assertEquals(w,new SkyWords(p).words.get("word1"));
  assertTrue(s.change(w.id(),null));assertTrue(new SkyWords(p).words.isEmpty());
 }}
 @Test void sizeValidationRejectsNanAndLeavesSavedValue(){try(var b=mockStatic(Bukkit.class)){
  var p=plugin();var s=new SkyWords(p);var w=new SkyWords.Word("word1",UUID.randomUUID(),0,80,0,"purple","Crates",4);s.change(w.id(),w);
  var sender=mock(CommandSender.class);when(sender.hasPermission("conquest.setword.admin")).thenReturn(true);
  s.onCommand(sender,null,"setword",new String[]{"size","word1","NaN"});assertEquals(w,s.words.get("word1"));
  s.onCommand(sender,null,"setword",new String[]{"size","word1","8"});assertEquals(8,s.words.get("word1").scale());assertEquals(8,new SkyWords(p).words.get("word1").scale());
 }}
 @Test void unauthorizedSenderCannotEdit(){
  var s=new SkyWords(plugin());var sender=mock(CommandSender.class);s.onCommand(sender,null,"setword",new String[]{"purple","Crates"});
  assertTrue(s.words.isEmpty());verify(sender).sendMessage("Only administrators can manage floating words.");
 }
 @Test void fullTextReferenceSupportsSpacesAndSizeFifty(){try(var b=mockStatic(Bukkit.class)){
  var p=plugin();var s=new SkyWords(p);s.change("word1",new SkyWords.Word("word1",UUID.randomUUID(),0,80,0,"purple","Welcome Home",4));
  var sender=mock(CommandSender.class);when(sender.hasPermission("conquest.setword.admin")).thenReturn(true);
  s.onCommand(sender,null,"setword",new String[]{"size","Welcome","Home","50"});assertEquals(50,s.words.get("word1").scale());
  s.onCommand(sender,null,"setword",new String[]{"edit","Welcome","Home","red","The","Arena"});assertEquals("The Arena",s.words.get("word1").text());assertEquals("red",s.words.get("word1").color());
  assertTrue(s.onTabComplete(sender,null,"setword",new String[]{"edit","The"}).contains("The Arena"));
  s.onCommand(sender,null,"setword",new String[]{"remove","The","Arena"});assertTrue(s.words.isEmpty());
 }}
}
