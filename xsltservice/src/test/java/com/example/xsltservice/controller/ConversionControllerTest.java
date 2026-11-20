package com.example.xsltservice.controller;

import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.service.ConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
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
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void returnsBadRequestOnInvalidXmlOrFilter() throws Exception {
        when(conversionService.convertXml(any(ConversionRequestDto.class), any()))
                .thenThrow(new IllegalArgumentException("Invalid XML content"));

        mockMvc.perform(post("/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xmlContent\":\"<broken>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid XML content"));
    }

    @Test
    void returnsBadGatewayWhenConverterUnavailable() throws Exception {
        when(conversionService.convertXml(any(ConversionRequestDto.class), any()))
                .thenThrow(new IOException("Connection refused"));

        mockMvc.perform(post("/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xmlContent\":\"<user/>\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Connection refused"));
    }

    @Test
    void returnsConversionByIdAndNotFoundWhenMissing() throws Exception {
        ConversionResponseDto dto = new ConversionResponseDto(
                1L,
                ConversionType.XML_TO_XML,
                3.5,
                LocalDateTime.of(2026, 11, 18, 14, 0),
                "1-source.xml",
                "1-result.xml",
                2,
                1,
                "<example param1=\"123\"/>",
                null
        );
        when(conversionService.getConversion(1L)).thenReturn(dto);
        when(conversionService.getConversion(999L)).thenThrow(new NoSuchElementException("Conversion not found: 999"));

        mockMvc.perform(get("/request/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result").value("<example param1=\"123\"/>"));

        mockMvc.perform(get("/request/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conversion not found: 999"));
    }
}
