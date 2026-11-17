package com.example.xsltservice.controller;

import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.service.ConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversionControllerTest {

    private ConversionService conversionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversionService = Mockito.mock(ConversionService.class);
        DefaultFormattingConversionService formattingConversionService = new DefaultFormattingConversionService();
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversionController(conversionService))
                .setConversionService(formattingConversionService)
                .build();
    }

    @Test
    void returnsBadRequestOnInvalidXmlOrFilter() throws Exception {
        when(conversionService.convertXml(any(ConversionRequestDto.class)))
                .thenThrow(new IllegalArgumentException("Invalid XML content"));

        mockMvc.perform(post("/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xmlContent\":\"<broken>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid XML content"));
    }

    @Test
    void returnsBadGatewayWhenConverterUnavailable() throws Exception {
        when(conversionService.convertXml(any(ConversionRequestDto.class)))
                .thenThrow(new IOException("Connection refused"));

        mockMvc.perform(post("/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xmlContent\":\"<user/>\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Connection refused"));
    }
}
