package com.shengyu.music.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.shengyu.music.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CatalogAdminServiceTest {
    @Test void htmlDisguisedAsMp3IsRejectedBeforeStorage() throws Exception {
        Path fake = Files.createTempFile("fake-song-", ".mp3");
        try {
            Files.writeString(fake, "<!DOCTYPE html><html><body>download error</body></html>");
            var storage = mock(MediaStorage.class);
            var previews = mock(PreviewGenerator.class);
            var service = new CatalogAdminService(mock(JdbcTemplate.class), mock(NamedParameterJdbcTemplate.class),
                storage, previews, mock(CatalogService.class));
            var error = assertThrows(ApiException.class,
                () -> service.add(fake, null, "测试歌曲", "测试歌手", "", "cn", "未分类", false, null, List.of()));
            assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            verifyNoInteractions(storage, previews);
        } finally {
            Files.deleteIfExists(fake);
        }
    }
}
