package com.example.s3service.controller;

import com.example.s3service.service.MinioStorageService;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class S3ControllerTest {

    private MinioStorageService storageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storageService = mock(MinioStorageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new S3Controller(storageService)).build();
    }

    @Test
    void uploadsAndDownloadsFile() throws Exception {
        byte[] payload = "<root/>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "sample.xml", MediaType.APPLICATION_XML_VALUE, payload);
        when(storageService.upload(eq("sample.xml"), any(), eq((long) payload.length), eq(MediaType.APPLICATION_XML_VALUE)))
                .thenReturn("sample.xml");
        when(storageService.download("sample.xml")).thenReturn(payload);

        mockMvc.perform(multipart("/s3").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().string("sample.xml"));

        mockMvc.perform(get("/s3/sample.xml"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(payload));
    }

    @Test
    void returnsNotFoundWhenObjectMissing() throws Exception {
        ErrorResponseException notFound = mock(ErrorResponseException.class);
        ErrorResponse error = mock(ErrorResponse.class);
        when(error.code()).thenReturn("NoSuchKey");
        when(notFound.errorResponse()).thenReturn(error);
        when(notFound.getMessage()).thenReturn("The specified key does not exist.");
        when(storageService.download("missing.xml")).thenThrow(notFound);

        mockMvc.perform(get("/s3/missing.xml"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The specified key does not exist."));
    }

    @Test
    void listsAndDeletesFiles() throws Exception {
        when(storageService.list()).thenReturn(List.of("doc.xml"));

        mockMvc.perform(get("/s3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("doc.xml"));

        mockMvc.perform(delete("/s3/doc.xml"))
                .andExpect(status().isNoContent());
        verify(storageService).delete("doc.xml");
    }
}
