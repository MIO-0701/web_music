package com.shengyu.music.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.shengyu.music.auth.AuthUser;
import com.shengyu.music.common.ApiException;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CatalogControllerTest {
    private final CatalogService catalog = mock(CatalogService.class);
    private final MediaStorage storage = mock(MediaStorage.class);
    private final CatalogController controller = new CatalogController(catalog, storage);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void user(LocalDateTime expiry) {
        var principal = new AuthUser(1, "listener", "听众", "USER", expiry);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test void vipNonMemberOnlyReceivesPreviewBytesEvenWithRange() {
        user(null);
        when(catalog.raw(7)).thenReturn(Map.of("published", true, "vip", true, "audio_key", "audio/full.mp3", "preview_key", "previews/clip.mp3"));
        when(storage.size("previews/clip.mp3")).thenReturn(100L);
        when(storage.read("previews/clip.mp3", 10, 11)).thenReturn(new ByteArrayInputStream(new byte[11]));
        var response = controller.audio(7, "bytes=10-20");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 10-20/100");
        verify(storage, never()).read(eq("audio/full.mp3"), anyLong(), anyLong());
    }

    @Test void activeMemberReceivesFullAudio() {
        user(LocalDateTime.now().plusDays(1));
        when(catalog.raw(7)).thenReturn(Map.of("published", true, "vip", true, "audio_key", "audio/full.mp3", "preview_key", "previews/clip.mp3"));
        when(storage.size("audio/full.mp3")).thenReturn(100L);
        when(storage.read("audio/full.mp3", 0, 100)).thenReturn(new ByteArrayInputStream(new byte[100]));
        var response = controller.audio(7, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().containsKey("Content-Range")).isFalse();
        verify(storage).read("audio/full.mp3", 0, 100);
    }

    @Test void unpublishedSongCannotBeStreamed() {
        user(null);
        when(catalog.raw(7)).thenReturn(Map.of("published", false, "vip", false));
        assertThrows(ApiException.class, () -> controller.audio(7, null));
        verifyNoInteractions(storage);
    }
}
