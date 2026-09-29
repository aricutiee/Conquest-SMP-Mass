package dev.turtleroles.service;

import com.github.retrooper.packetevents.protocol.chat.RemoteChatSession;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketNicknameIdentityTest {
    final UUID real=UUID.randomUUID(),alias=UUID.randomUUID(),viewer=UUID.randomUUID();
    final PacketNicknameIdentity bridge=new PacketNicknameIdentity();
    PlayerInfo info() {
        return new PlayerInfo(new UserProfile(real,"ItzRealMe",List.of(new TextureProperty("textures","value","signature"))),
                true,42,GameMode.SURVIVAL,Component.text("Booster ItzRealMe"),new RemoteChatSession(UUID.randomUUID(),null),7,true);
    }
    @Test void sendsCopiedIdentityButRetainsUnlistedAuthenticChatSession() {
        bridge.set(real,alias);var original=info();var out=bridge.update(viewer,List.of(original),true);
        assertEquals(2,out.size());var authentic=out.get(0);var display=out.get(1);
        assertEquals(real,authentic.getProfileId());assertFalse(authentic.isListed());assertSame(original.getChatSession(),authentic.getChatSession());
        assertEquals(alias,display.getProfileId());assertTrue(display.isListed());assertNull(display.getChatSession());
        assertEquals("ItzRealMe",display.getGameProfile().getName());assertEquals(original.getGameProfile().getTextureProperties(),display.getGameProfile().getTextureProperties());
        assertEquals(42,display.getLatency());assertEquals(7,display.getListOrder());assertTrue(display.isShowHat());
        assertEquals(original.getDisplayName(),display.getDisplayName());assertEquals(GameMode.SURVIVAL,display.getGameMode());
        assertTrue(original.isListed());assertEquals(real,original.getProfileId());assertNotNull(original.getChatSession());
        assertEquals(alias,bridge.spawned(viewer,real));
    }
    @Test void changingAliasDoesNotRewriteQueuedRemovalToNewAlias() {
        bridge.set(real,alias);bridge.update(viewer,List.of(info()),true);UUID next=UUID.randomUUID();bridge.set(real,next);
        assertEquals(alias,bridge.spawned(viewer,real));
        assertEquals(alias,bridge.update(viewer,List.of(info()),false).get(1).getProfileId());
        assertEquals(List.of(real,alias),bridge.remove(viewer,List.of(real)));
        assertEquals(real,bridge.spawned(viewer,real));
        assertEquals(next,bridge.update(viewer,List.of(info()),true).get(1).getProfileId());
        assertEquals(next,bridge.spawned(viewer,real));
    }
    @Test void resetRemovesOldAliasAndNextAddReturnsRealProfile() {
        bridge.set(real,alias);bridge.update(viewer,List.of(info()),true);bridge.set(real,null);
        assertEquals(List.of(real,alias),bridge.remove(viewer,List.of(real)));
        var original=info();assertEquals(List.of(original),bridge.update(viewer,List.of(original),true));
        assertEquals(real,bridge.spawned(viewer,real));
    }
    @Test void selfAndBedrockViewsRemainAuthentic() {
        bridge.set(real,alias);var original=info();assertEquals(List.of(original),bridge.update(real,List.of(original),true));
        assertEquals(real,bridge.spawned(real,real));bridge.viewer(viewer,true);
        assertEquals(List.of(original),bridge.update(viewer,List.of(original),true));assertEquals(real,bridge.spawned(viewer,real));
    }
    @Test void unlistedPlayersStayUnlistedAndUnrelatedEntriesAreUnchanged() {
        bridge.set(real,alias);var original=info();original.setListed(false);UUID other=UUID.randomUUID();var untouched=new PlayerInfo(other);
        var out=bridge.update(viewer,List.of(original,untouched),true);
        assertFalse(out.get(0).isListed());assertFalse(out.get(1).isListed());assertSame(untouched,out.get(2));
        assertEquals(other,bridge.spawned(viewer,other));
    }
    @Test void viewerStatesAreIndependentAndForgottenOnDisconnect() {
        bridge.set(real,alias);bridge.update(viewer,List.of(info()),true);UUID second=UUID.randomUUID();
        assertEquals(real,bridge.spawned(second,real));bridge.update(second,List.of(info()),true);
        bridge.forgetViewer(viewer);assertEquals(real,bridge.spawned(viewer,real));assertEquals(alias,bridge.spawned(second,real));
    }
    @Test void noMappingDoesNotDuplicateEntries() {
        var original=info();assertEquals(List.of(original),bridge.update(viewer,List.of(original),true));
        assertEquals(List.of(real),bridge.remove(viewer,List.of(real)));bridge.set(real,real);
        assertEquals(List.of(original),bridge.update(viewer,List.of(original),true));
    }
}
