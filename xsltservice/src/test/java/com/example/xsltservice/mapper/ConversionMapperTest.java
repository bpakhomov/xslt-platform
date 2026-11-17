package com.example.xsltservice.mapper;

import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionMapperTest {

    private final ConversionMapper mapper = new ConversionMapper();

    @Test
    void mapsEntityToResponseDto() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 11, 30);
        Conversion conversion = new Conversion(15.5, now, "{\"a\":1}", 2, 1);

        ConversionResponseDto dto = mapper.toResponseDto(conversion);

        assertNull(dto.id());
        assertEquals(15.5, dto.conversionTime());
        assertEquals(now, dto.conversionDateTime());
        assertEquals("{\"a\":1}", dto.jsonValue());
        assertEquals(2, dto.xmlTagsCount());
        assertEquals(1, dto.jsonKeysCount());
    }

    @Test
    void mapsFieldsToEntity() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 13, 20);
        Conversion conversion = mapper.toEntity(15.5, now, "{\"a\":1}", 2, 1);

        assertEquals(15.5, conversion.getConversionTime());
        assertEquals(now, conversion.getConversionDateTime());
        assertEquals("{\"a\":1}", conversion.getJsonValue());
        assertEquals(2, conversion.getXmlTagsCount());
        assertEquals(1, conversion.getJsonKeysCount());
    }

    @Test
    void handlesNullAndMapsList() {
        assertNull(mapper.toResponseDto(null));
        assertTrue(mapper.toResponseDtoList(null).isEmpty());

        Conversion first = new Conversion(10.0, LocalDateTime.now(), "{}", 1, 0);
        List<ConversionResponseDto> result = mapper.toResponseDtoList(List.of(first));
        assertEquals(1, result.size());
        assertEquals(10.0, result.getFirst().conversionTime());
    }

    @Test
    void mapsFilterDtoToDaoFilter() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 17, 23, 59);
        ConversionFilterDto dto = new ConversionFilterDto(10, 5, from, to, 1.0, 20.0, 2, 10, 3, 15);

        ConversionFilter filter = mapper.toFilter(dto);

        assertEquals(10, filter.limit());
        assertEquals(5, filter.offset());
        assertEquals(from, filter.dateFrom());
        assertEquals(to, filter.dateTo());
        assertEquals(1.0, filter.conversionTimeFrom());
        assertEquals(20.0, filter.conversionTimeTo());
        assertEquals(2, filter.xmlTagsCountFrom());
        assertEquals(10, filter.xmlTagsCountTo());
        assertEquals(3, filter.jsonKeysCountFrom());
        assertEquals(15, filter.jsonKeysCountTo());

        ConversionFilter fallback = mapper.toFilter(null);
        assertEquals(20, fallback.limit());
        assertEquals(0, fallback.offset());
    }
}
