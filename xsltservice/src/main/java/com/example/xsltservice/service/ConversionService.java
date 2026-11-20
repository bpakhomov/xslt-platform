package com.example.xsltservice.service;

import com.example.xsltservice.dao.ConversionDao;
import com.example.xsltservice.dao.UploadedConversion;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.mapper.ConversionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ConversionService {

    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);

    private final ConversionDao conversionDao;
    private final ConversionMapper conversionMapper;
    private final ConversionMetricsCalculator metricsCalculator;
    private final TransactionTemplate transactionTemplate;
    private final ConversionCache conversionCache;
    private final String xsltconverterUrl;
    private final String s3serviceUrl;
    private final HttpClient httpClient;
    private final int migrationRowsLimit;

    @Autowired
    public ConversionService(
            ConversionDao conversionDao,
            ObjectMapper objectMapper,
            ConversionMapper conversionMapper,
            TransactionTemplate transactionTemplate,
            ConversionCache conversionCache,
            @Value("${xsltconverter.url}") String xsltconverterUrl,
            @Value("${s3service.url}") String s3serviceUrl,
            @Value("${migration.rows-limit}") int migrationRowsLimit
    ) {
        this(
                conversionDao,
                objectMapper,
                conversionMapper,
                transactionTemplate,
                conversionCache,
                xsltconverterUrl,
                s3serviceUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                migrationRowsLimit
        );
    }

    ConversionService(
            ConversionDao conversionDao,
            ObjectMapper objectMapper,
            ConversionMapper conversionMapper,
            TransactionTemplate transactionTemplate,
            ConversionCache conversionCache,
            String xsltconverterUrl,
            String s3serviceUrl,
            HttpClient httpClient,
            int migrationRowsLimit
    ) {
        this.conversionDao = conversionDao;
        this.metricsCalculator = new ConversionMetricsCalculator(objectMapper);
        this.conversionMapper = conversionMapper;
        this.transactionTemplate = transactionTemplate;
        this.conversionCache = conversionCache;
        this.xsltconverterUrl = stripTrailingSlash(xsltconverterUrl);
        this.s3serviceUrl = stripTrailingSlash(s3serviceUrl);
        this.httpClient = httpClient;
        this.migrationRowsLimit = migrationRowsLimit;
    }

    public void migrateConversionsToS3() {
        List<Conversion> conversions = transactionTemplate.execute(
                _ -> conversionDao.getConversionsForMigration(migrationRowsLimit)
        );
        if (conversions == null || conversions.isEmpty()) {
            return;
        }

        List<UploadedConversion> uploadedConversions = new ArrayList<>();
        for (Conversion conversion : conversions) {
            String filePrefix = String.valueOf(conversion.getId());
            String sourceFileKey = filePrefix + "-source.xml";
            String resultFileKey = filePrefix + "-result" + conversion.getConversionType().getFileExtension();

            try {
                uploadToS3(sourceFileKey, conversion.getSourceContent(), MediaType.APPLICATION_XML_VALUE);
                uploadToS3(resultFileKey, conversion.getResultContent(), conversion.getConversionType().getContentType());
                uploadedConversions.add(new UploadedConversion(conversion.getId(), sourceFileKey, resultFileKey));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Migration to S3 interrupted at conversion id={}", conversion.getId(), e);
                break;
            } catch (Exception e) {
                log.warn("Failed to upload conversion id={} to S3", conversion.getId(), e);
            }
        }

        if (!uploadedConversions.isEmpty()) {
            transactionTemplate.executeWithoutResult(
                    _ -> conversionDao.updateConversionsAfterMigration(uploadedConversions)
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ConversionResponseDto> findByFilter(ConversionFilterDto filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter must not be null");
        }
        if (filter.limit() <= 0 || filter.offset() < 0
                || isInvalidRange(filter.dateFrom(), filter.dateTo())
                || isInvalidRange(filter.conversionTimeFrom(), filter.conversionTimeTo())
                || isInvalidRange(filter.inputElementsCountFrom(), filter.inputElementsCountTo())
                || isInvalidRange(filter.outputElementsCountFrom(), filter.outputElementsCountTo())) {
            throw new IllegalArgumentException("Uncorrected request params");
        }

        List<Conversion> conversions = conversionDao.findByFilter(conversionMapper.toFilter(filter));
        return conversionMapper.toResponseDtoList(conversions);
    }

    public ConversionResponseDto getConversion(Long id) throws IOException, InterruptedException {
        Conversion conversion = transactionTemplate.execute(_ -> conversionDao.findById(id).orElse(null));
        if (conversion == null) {
            throw new NoSuchElementException("Conversion not found: " + id);
        }
        String resultKey = conversion.getResultFileKey();
        String result = (resultKey != null && !resultKey.isBlank())
                ? downloadFromS3(resultKey)
                : conversion.getResultContent();
        return conversionMapper.toResponseDto(conversion, result);
    }

    public ConversionResponseDto convertXml(ConversionRequestDto request) throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        String xmlContent = request.xmlContent();
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("XML content must not be empty");
        }
        ConversionType conversionType = request.conversionType();
        Optional<ConversionResponseDto> cached = conversionCache.get(xmlContent, conversionType);
        if (cached.isPresent()) {
            return cached.get();
        }

        int inputElementsCount = metricsCalculator.countXmlTags(xmlContent);

        long start = System.nanoTime();
        String resultContent = requestConversion(xmlContent, conversionType);
        double duration = (System.nanoTime() - start) / 1_000_000.0;

        int outputElementsCount = countOutputElements(conversionType, resultContent);

        Conversion conversion = conversionMapper.toEntity(
                conversionType,
                duration,
                LocalDateTime.now(),
                null,
                null,
                inputElementsCount,
                outputElementsCount,
                xmlContent,
                resultContent
        );
        Conversion saved = transactionTemplate.execute(_ -> conversionDao.save(conversion));

        ConversionResponseDto response = conversionMapper.toResponseDto(saved, resultContent);
        conversionCache.put(xmlContent, conversionType, response);
        return response;
    }

    private <T extends Comparable<? super T>> boolean isInvalidRange(T from, T to) {
        return from != null && to != null && from.compareTo(to) > 0;
    }

    private String requestConversion(String xmlContent, ConversionType conversionType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(xsltconverterUrl + "/xml2format?type=" + conversionType.name()))
                .header("Content-Type", MediaType.APPLICATION_XML_VALUE)
                .header("Accept", conversionType.getContentType())
                .POST(HttpRequest.BodyPublishers.ofString(xmlContent, StandardCharsets.UTF_8))
                .build();

        return sendAndCheck(request, "xsltconverter");
    }

    private int countOutputElements(ConversionType conversionType, String resultContent) {
        return switch (conversionType) {
            case XML_TO_JSON -> metricsCalculator.countJsonKeys(resultContent);
            case XML_TO_XML -> metricsCalculator.countXmlTags(resultContent);
        };
    }

    private void uploadToS3(String key, String content, String contentType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(s3serviceUrl + "/s3/" + key))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8))
                .build();

        sendAndCheck(request, "s3service");
    }

    private String downloadFromS3(String key) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(s3serviceUrl + "/s3/" + key))
                .GET()
                .build();

        return sendAndCheck(request, "s3service");
    }

    private String sendAndCheck(HttpRequest request, String serviceName) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    serviceName + " returned HTTP " + response.statusCode() + ": " + response.body()
            );
        }
        return response.body();
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
