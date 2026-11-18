package com.example.xsltservice.mapper;

import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionMapperTest {

    private final ConversionMapper mapper = new ConversionMapper();

    @Test
    void mapsEntityToResponseDtoWithAndWithoutResult() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 11, 30);
        Conversion conversion = new Conversion(
                ConversionType.XML_TO_JSON,
                15.5,
                now,
                "key-source.xml",
                "key-result.json",
                2,
                1
        );

        ConversionResponseDto withResult = mapper.toResponseDto(conversion, "{\"a\":1}");
        ConversionResponseDto withoutResult = mapper.toResponseDto(conversion);

        assertNull(withResult.id());
        assertEquals(ConversionType.XML_TO_JSON, withResult.conversionType());
        assertEquals(15.5, withResult.conversionTime());
        assertEquals(now, withResult.conversionDateTime());
        assertEquals("key-source.xml", withResult.sourceFileKey());
        assertEquals("key-result.json", withResult.resultFileKey());
        assertEquals(2, withResult.inputElementsCount());
        assertEquals(1, withResult.outputElementsCount());
        assertEquals("{\"a\":1}", withResult.result());
        assertNull(withoutResult.result());
    }

    @Test
    void mapsFieldsToEntity() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 13, 20);
        Conversion conversion = mapper.toEntity(
                ConversionType.XML_TO_XML,
                15.5,
                now,
                "src.xml",
                "res.xml",
                5,
                2
        );

        assertEquals(ConversionType.XML_TO_XML, conversion.getConversionType());
        assertEquals(15.5, conversion.getConversionTime());
        assertEquals(now, conversion.getConversionDateTime());
        assertEquals("src.xml", conversion.getSourceFileKey());
        assertEquals("res.xml", conversion.getResultFileKey());
        assertEquals(5, conversion.getInputElementsCount());
        assertEquals(2, conversion.getOutputElementsCount());
    }

    @Test
    void handlesNullAndMapsList() {
        assertNull(mapper.toResponseDto(null));
        assertTrue(mapper.toResponseDtoList(null).isEmpty());

        Conversion first = new Conversion(
                ConversionType.XML_TO_JSON,
                10.0,
                LocalDateTime.now(),
                "src.xml",
                "res.json",
                1,
                0
        );
        List<ConversionResponseDto> result = mapper.toResponseDtoList(List.of(first));
        assertEquals(1, result.size());
        assertEquals(10.0, result.getFirst().conversionTime());
        assertNull(result.getFirst().result());
    }

    @Test
    void mapsFilterDtoToDaoFilter() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 17, 23, 59);
        ConversionFilterDto dto = new ConversionFilterDto(
                10,
                5,
                ConversionType.XML_TO_XML,
                from,
                to,
                1.0,
                20.0,
                2,
                10,
                3,
                15
        );

        ConversionFilter filter = mapper.toFilter(dto);

        assertEquals(10, filter.limit());
        assertEquals(5, filter.offset());
        assertEquals(ConversionType.XML_TO_XML, filter.conversionType());
        assertEquals(from, filter.dateFrom());
        assertEquals(to, filter.dateTo());
        assertEquals(1.0, filter.conversionTimeFrom());
        assertEquals(20.0, filter.conversionTimeTo());
        assertEquals(2, filter.inputElementsCountFrom());
        assertEquals(10, filter.inputElementsCountTo());
        assertEquals(3, filter.outputElementsCountFrom());
        assertEquals(15, filter.outputElementsCountTo());

        ConversionFilter fallback = mapper.toFilter(null);
        assertEquals(20, fallback.limit());
        assertEquals(0, fallback.offset());
    }
}
