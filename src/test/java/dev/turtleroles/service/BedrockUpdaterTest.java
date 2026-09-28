package dev.turtleroles.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class BedrockUpdaterTest {
    @TempDir Path temp;
    @Test void onlyOfficialProjectsAndSafeVersionPathsAreAccepted(){
        assertEquals("download.geysermc.org",BedrockUpdater.downloadUri("geyser","2.11.3",1247).getHost());
        assertThrows(IllegalArgumentException.class,()->BedrockUpdater.downloadUri("other","2.0",1));
        assertThrows(IllegalArgumentException.class,()->BedrockUpdater.downloadUri("floodgate","../../evil",1));
        assertThrows(IllegalArgumentException.class,()->BedrockUpdater.downloadUri("geyser","2.0",0));
    }
    @Test void downloadedBytesAreComparedWithKnownSha256() throws Exception {
        Path file=temp.resolve("test.jar");Files.writeString(file,"abc");assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",BedrockUpdater.sha(file));
        Files.writeString(file,"abd");assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",BedrockUpdater.sha(file));
    }
    @Test void officialRelativeRedirectsWorkButExternalAndUnencryptedRedirectsFail() throws Exception {
        var base=java.net.URI.create("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest");
        assertEquals("/v2/projects/geyser/versions/2.11.3/builds/1247",BedrockUpdater.officialRedirect(base,"/v2/projects/geyser/versions/2.11.3/builds/1247").getPath());
        for(String bad:new String[]{"https://example.org/file","http://download.geysermc.org/file","https://user@download.geysermc.org/file","https://download.geysermc.org:8000/file"})
            assertThrows(java.io.IOException.class,()->BedrockUpdater.officialRedirect(base,bad));
    }
    @Test @SuppressWarnings("unchecked") void latestMetadataFollowsOfficial302AndParsesRelease() throws Exception {
        var client=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> redirect=org.mockito.Mockito.mock(java.net.http.HttpResponse.class), ok=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(redirect.statusCode()).thenReturn(302);
        org.mockito.Mockito.when(redirect.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Location",java.util.List.of("/v2/projects/geyser/versions/2.11.3/builds/1247")),(a,b)->true));
        org.mockito.Mockito.when(ok.statusCode()).thenReturn(200);org.mockito.Mockito.when(ok.body()).thenReturn("{\"version\":\"2.11.3\",\"build\":1247}");
        org.mockito.Mockito.when(client.send(org.mockito.Mockito.any(),org.mockito.Mockito.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(redirect,ok);
        assertEquals(1247,BedrockUpdater.metadata(client,java.net.URI.create("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest")).get("build").getAsInt());
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).send(org.mockito.Mockito.any(),org.mockito.Mockito.any());
    }
}
