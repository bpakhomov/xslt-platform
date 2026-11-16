package com.example.xsltconverter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void convertsRawXmlAtXml2FormatAndWritesLogFile() throws Exception {
        mockMvc.perform(post("/xml2format")
                .contentType(MediaType.APPLICATION_XML)
                .content("<person><name>Bob</name></person>"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.person.name").value("Bob"));

        String logContent = Files.readString(Path.of("logs/xsltconverter.log"));
        assertTrue(logContent.contains("API POST /xml2format -> 200"));
        assertTrue(logContent.contains("<person><name>Bob</name></person>"));
        assertTrue(logContent.contains("{\"person\":{\"name\":\"Bob\"}}"));
    }

    @Test
    void convertsJsonWrappedXmlAtXml2Format() throws Exception {
        mockMvc.perform(post("/xml2format")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xmlContent\":\"<item><code id=\\\"1\\\">OK</code></item>\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.code['@id']").value("1"))
            .andExpect(jsonPath("$.item.code['#text']").value("OK"));
    }

    @Test
    void returnsBadRequestOnInvalidXml() throws Exception {
        mockMvc.perform(post("/xml2format")
                .contentType(MediaType.APPLICATION_XML)
                .content("<broken>"))
            .andExpect(status().isBadRequest());
    }
}
