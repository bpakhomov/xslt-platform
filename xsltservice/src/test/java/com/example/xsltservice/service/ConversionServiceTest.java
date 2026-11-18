package com.example.xsltservice.service;

import com.example.xsltservice.dao.ConversionDao;
import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.mapper.ConversionMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversionServiceTest {

    @Test
    void convertsXmlToXmlUploadsBothFilesAndCountsOutputTags() throws Exception {
        Map<String, String> uploadedFiles = new LinkedHashMap<>();
        String inputXml = "<example><param1>123</param1><inner><param2>456</param2></inner></example>";
        String outputXml = "<example param1=\"123\"><inner param2=\"456\"/></example>";

        HttpServer converterServer = startConverterServer("XML_TO_XML", outputXml);
        HttpServer s3Server = startS3Server(uploadedFiles);
        try {
            ConversionService service = createService(mockDao(), converterServer, s3Server);

            ConversionResponseDto response = service.convertXml(
                    new ConversionRequestDto(inputXml, ConversionType.XML_TO_XML)
            );

            assertEquals(ConversionType.XML_TO_XML, response.conversionType());
            assertEquals(4, response.inputElementsCount());
            assertEquals(2, response.outputElementsCount());
            assertEquals(outputXml, response.result());
            assertTrue(response.sourceFileKey().endsWith("-source.xml"));
            assertTrue(response.resultFileKey().endsWith("-result.xml"));
            assertEquals(inputXml, uploadedFiles.get("/s3/" + response.sourceFileKey()));
            assertEquals(outputXml, uploadedFiles.get("/s3/" + response.resultFileKey()));
        } finally {
            converterServer.stop(0);
            s3Server.stop(0);
        }
    }

    @Test
    void convertsXmlToJsonAndDefaultsTypeWhenOmitted() throws Exception {
        Map<String, String> uploadedFiles = new LinkedHashMap<>();
        String inputXml = "<user><name>Alice</name></user>";
        String outputJson = "{\"user\":{\"name\":\"Alice\"}}";

        HttpServer converterServer = startConverterServer("XML_TO_JSON", outputJson);
        HttpServer s3Server = startS3Server(uploadedFiles);
        try {
            ConversionService service = createService(mockDao(), converterServer, s3Server);

            ConversionResponseDto response = service.convertXml(new ConversionRequestDto(inputXml, null));

            assertEquals(ConversionType.XML_TO_JSON, response.conversionType());
            assertEquals(2, response.inputElementsCount());
            assertEquals(2, response.outputElementsCount());
            assertEquals(outputJson, response.result());
            assertTrue(response.resultFileKey().endsWith("-result.json"));
            assertEquals(2, uploadedFiles.size());
        } finally {
            converterServer.stop(0);
            s3Server.stop(0);
        }
    }

    @Test
    void getsConversionByIdAndLoadsResultFromS3() throws Exception {
        Map<String, String> s3Files = new LinkedHashMap<>();
        s3Files.put("/s3/abc-result.xml", "<example param1=\"123\"/>");

        ConversionDao dao = mock(ConversionDao.class);
        Conversion entity = new Conversion(
                ConversionType.XML_TO_XML,
                5.2,
                LocalDateTime.of(2026, 11, 18, 14, 0),
                "abc-source.xml",
                "abc-result.xml",
                2,
                1
        );
        when(dao.findById(1L)).thenReturn(Optional.of(entity));
        when(dao.findById(99L)).thenReturn(Optional.empty());

        HttpServer s3Server = startS3Server(s3Files);
        try {
            ConversionService service = createService(dao, null, s3Server);

            ConversionResponseDto found = service.getConversion(1L);
            assertEquals("abc-result.xml", found.resultFileKey());
            assertEquals("<example param1=\"123\"/>", found.result());

            assertThrows(NoSuchElementException.class, () -> service.getConversion(99L));
        } finally {
            s3Server.stop(0);
        }
    }

    private ConversionDao mockDao() {
        ConversionDao dao = mock(ConversionDao.class);
        when(dao.save(any(Conversion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return dao;
    }

    private ConversionService createService(ConversionDao dao, HttpServer converterServer, HttpServer s3Server) {
        String converterUrl = converterServer != null
                ? "http://localhost:" + converterServer.getAddress().getPort()
                : "http://localhost:8081";
        return new ConversionService(
                dao,
                new ObjectMapper(),
                new ConversionMapper(),
                converterUrl,
                "http://localhost:" + s3Server.getAddress().getPort(),
                HttpClient.newHttpClient()
        );
    }

    private HttpServer startConverterServer(String expectedType, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/xml2format", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("type=" + expectedType, exchange.getRequestURI().getQuery());
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private HttpServer startS3Server(Map<String, String> storedFiles) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/s3/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("PUT".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                storedFiles.put(path, body);
                byte[] keyBytes = path.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, keyBytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(keyBytes);
                }
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                String content = storedFiles.get(path);
                int status = content != null ? 200 : 404;
                byte[] body = (content != null ? content : "Not found").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        return server;
    }
}
