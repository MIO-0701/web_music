package com.shengyu.music.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import com.shengyu.music.common.ApiException;

class BatchSongImportServiceTest {
    private static MockMultipartFile zip(String field, String filename, String entry) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entry)); output.write(new byte[] {1, 2, 3}); output.closeEntry();
        }
        return new MockMultipartFile(field, filename, "application/zip", bytes.toByteArray());
    }
    private static MockMultipartFile csv(String text) {
        return new MockMultipartFile("csv", "songs.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }
    private static final String HEADER = "title,artist,album,audio_file,cover_file,language,genre,vip\n";

    @Test void importsQuotedCsvAndMatchedArchivePaths() throws Exception {
        var admin = mock(CatalogAdminService.class); var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        when(admin.add(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), anyList())).thenReturn(42L);
        var service = new BatchSongImportService(admin, jdbc);
        Map<String, Object> result = service.upload(zip("audioZip", "audio.zip", "cn/song.mp3"), zip("coverZip", "covers.zip", "cn/cover.jpg"),
            csv(HEADER + "\"Song, One\",Artist,Album,cn/song.mp3,cn/cover.jpg,cn,pop,true\n"));
        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(result.get("ids")).isEqualTo(java.util.List.of(42L));
        verify(admin).add(any(), any(), eq("Song, One"), eq("Artist"), eq("Album"), eq("cn"), eq("pop"), eq(true), startsWith("batch/sha256/"), anyList());
    }

    @Test void rejectsZipTraversalBeforeImport() throws Exception {
        var admin = mock(CatalogAdminService.class); var service = new BatchSongImportService(admin, mock(JdbcTemplate.class));
        var error = assertThrows(ApiException.class, () -> service.upload(zip("audioZip", "audio.zip", "../bad.mp3"),
            zip("coverZip", "covers.zip", "cover.jpg"), csv(HEADER + "Song,Artist,,bad.mp3,cover.jpg,cn,pop,false\n")));
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }

    @Test void rejectsUnknownGenreBeforeImport() throws Exception {
        var admin = mock(CatalogAdminService.class); var service = new BatchSongImportService(admin, mock(JdbcTemplate.class));
        var error = assertThrows(ApiException.class, () -> service.upload(zip("audioZip", "audio.zip", "song.mp3"),
            zip("coverZip", "covers.zip", "cover.jpg"), csv(HEADER + "Song,Artist,,song.mp3,cover.jpg,cn,unknown,false\n")));
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }
}
