package com.example.xsltconverter.controller;

import com.example.xsltconverter.service.ConversionRateLimiter;
import com.example.xsltconverter.service.ConversionType;
import com.example.xsltconverter.service.XmlConversionDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversionControllerRateLimitTest {

    private XmlConversionDispatcher dispatcher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dispatcher = mock(XmlConversionDispatcher.class);
        ConversionController controller = new ConversionController(dispatcher, new ConversionRateLimiter(1));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    class Xml2FormatRateLimit {

        @Test
        void returnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
            when(dispatcher.convert("<a/>", ConversionType.XML_TO_JSON)).thenReturn("{\"a\":null}");

            mockMvc.perform(post("/xml2format")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<a/>"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/xml2format")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<a/>"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.detail").value("Too many requests to /xml2format"));

            verify(dispatcher).convert("<a/>", ConversionType.XML_TO_JSON);
        }
    }
}
