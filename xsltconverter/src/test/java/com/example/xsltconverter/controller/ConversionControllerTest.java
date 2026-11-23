package com.example.xsltconverter.controller;

import com.example.xsltconverter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

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

    @Autowired
    private JwtService jwtService;

    @Value("${service-auth.username}")
    private String serviceUsername;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtService.generateToken(serviceUsername);
    }

    @Nested
    class Xml2Format {

        @Test
        void returnsUnauthorizedWithoutToken() throws Exception {
            mockMvc.perform(post("/xml2format")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<person><name>Bob</name></person>"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void convertsRawXmlAndWritesLogFile() throws Exception {
            mockMvc.perform(post("/xml2format")
                            .header("Authorization", bearerToken)
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
        void convertsXmlWithXsltWhenTypeIsXmlToXml() throws Exception {
            mockMvc.perform(post("/xml2format")
                            .header("Authorization", bearerToken)
                            .param("type", "XML_TO_XML")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<example><param1>123</param1></example>"))
                    .andExpect(status().isOk())
                    .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(MockMvcResultMatchers.xpath("/example/@param1").string("123"));
        }

        @Test
        void returnsBadRequestOnInvalidXml() throws Exception {
            mockMvc.perform(post("/xml2format")
                            .header("Authorization", bearerToken)
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<broken>"))
                    .andExpect(status().isBadRequest());
        }
    }
}
