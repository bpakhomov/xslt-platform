package com.example.xsltservice.service;

import com.example.xsltservice.dao.ConversionDao;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.mapper.ConversionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversionService {

    @Value("${xsltconverter.url}")
    private String xsltconverterUrl;

    private final ConversionDao conversionDao;
    private final ConversionMapper conversionMapper;
    private final ConversionMetricsCalculator metricsCalculator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public ConversionService(
            ConversionDao conversionDao,
            ObjectMapper objectMapper,
            ConversionMapper conversionMapper
    ) {
        this.conversionDao = conversionDao;
        this.metricsCalculator = new ConversionMetricsCalculator(objectMapper);
        this.conversionMapper = conversionMapper;
    }

    @Transactional(readOnly = true)
    public List<ConversionResponseDto> getConversions(ConversionFilterDto filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter must not be null");
        }
        if (filter.limit() <= 0 || filter.offset() < 0
                || isInvalidRange(filter.dateFrom(), filter.dateTo())
                || isInvalidRange(filter.conversionTimeFrom(), filter.conversionTimeTo())
                || isInvalidRange(filter.xmlTagsCountFrom(), filter.xmlTagsCountTo())
                || isInvalidRange(filter.jsonKeysCountFrom(), filter.jsonKeysCountTo())) {
            throw new IllegalArgumentException("Uncorrected request params");
        }

        List<Conversion> conversions = conversionDao.findByFilter(conversionMapper.toFilter(filter));
        return conversionMapper.toResponseDtoList(conversions);
    }

    private <T extends Comparable<? super T>> boolean isInvalidRange(T from, T to) {
        return from != null && to != null && from.compareTo(to) > 0;
    }

    @Transactional
    public ConversionResponseDto convertXml(ConversionRequestDto request) throws IOException, InterruptedException {
        String xmlContent = request != null ? request.xmlContent() : null;
        int xmlTagsCount = metricsCalculator.countXmlTags(xmlContent);

        long start = System.nanoTime();
        String jsonContent = requestJsonConversion(xmlContent);
        double duration = (System.nanoTime() - start) / 1_000_000.0;

        int jsonKeysCount = metricsCalculator.countJsonKeys(jsonContent);

        Conversion conversion = conversionMapper.toEntity(
                duration,
                LocalDateTime.now(),
                jsonContent,
                xmlTagsCount,
                jsonKeysCount
        );
        Conversion saved = conversionDao.save(conversion);

        return conversionMapper.toResponseDto(saved);
    }

    private String requestJsonConversion(String xmlContent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(xsltconverterUrl + "/xml2format"))
                .header("Content-Type", "application/xml")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(xmlContent))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "xsltconverter returned HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        return response.body();
    }

    static class ConversionMetricsCalculator {
        private final ObjectMapper objectMapper;
        private final XMLInputFactory xmlInputFactory;

        ConversionMetricsCalculator(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.xmlInputFactory = XMLInputFactory.newInstance();
            this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        }

        int countXmlTags(String xmlContent) {
            if (xmlContent == null || xmlContent.isBlank()) {
                throw new IllegalArgumentException("XML content must not be empty");
            }

            XMLStreamReader reader = null;
            try {
                reader = xmlInputFactory.createXMLStreamReader(new StringReader(xmlContent.trim()));
                int count = 0;
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        count++;
                    }
                }
                return count;
            } catch (XMLStreamException e) {
                throw new IllegalArgumentException("Invalid XML content", e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (XMLStreamException ignored) {
                    }
                }
            }
        }

        int countJsonKeys(String jsonContent) {
            if (jsonContent == null || jsonContent.isBlank()) {
                throw new IllegalArgumentException("JSON content must not be empty");
            }

            try {
                return countKeys(objectMapper.readTree(jsonContent));
            } catch (JacksonException e) {
                throw new IllegalArgumentException("Invalid JSON content", e);
            }
        }

        private int countKeys(JsonNode node) {
            if (node == null || node.isNull()) {
                return 0;
            }
            int count = node.isObject() ? node.size() : 0;
            if (node.isObject() || node.isArray()) {
                for (JsonNode child : node.values()) {
                    count += countKeys(child);
                }
            }
            return count;
        }
    }
}
