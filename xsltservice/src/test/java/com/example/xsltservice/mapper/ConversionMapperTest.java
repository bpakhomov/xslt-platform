package com.example.xsltservice.mapper;

import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionMapperTest {

    private ConversionMapper mapper;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        mapper = new ConversionMapper();
        now = LocalDateTime.of(2026, 11, 17, 11, 30);
    }

    @Nested
    class ToResponseDto {

        @Test
        void mapsEntityWithResult() {
            Conversion conversion = new Conversion(
                    ConversionType.XML_TO_JSON, 15.5, now, "key-source.xml", "key-result.json", 2, 1
            );

            ConversionResponseDto dto = mapper.toResponseDto(conversion, "{\"a\":1}");

            assertNull(dto.id());
            assertEquals(ConversionType.XML_TO_JSON, dto.conversionType());
            assertEquals(15.5, dto.conversionTime());
            assertEquals(now, dto.conversionDateTime());
            assertEquals("key-source.xml", dto.sourceFileKey());
            assertEquals("key-result.json", dto.resultFileKey());
            assertEquals(2, dto.inputElementsCount());
            assertEquals(1, dto.outputElementsCount());
            assertEquals("{\"a\":1}", dto.result());
            assertNull(dto.username());
        }

        @Test
        void mapsEntityWithoutResult() {
            Conversion conversion = new Conversion(
                    ConversionType.XML_TO_JSON, 15.5, now, "key-source.xml", "key-result.json", 2, 1
            );

            ConversionResponseDto dto = mapper.toResponseDto(conversion);

            assertNull(dto.result());
        }

        @Test
        void mapsUsernameWhenUserPresent() {
            User user = new User("alice", "hash");
            Conversion conversion = new Conversion(
                    ConversionType.XML_TO_JSON, 10.0, now, null, null, 1, 1, "<a/>", "{\"a\":null}", user
            );

            ConversionResponseDto dto = mapper.toResponseDto(conversion, "{\"a\":null}");

            assertEquals("alice", dto.username());
        }

        @Test
        void returnsNullWhenEntityIsNull() {
            assertNull(mapper.toResponseDto(null));
        }
    }

    @Nested
    class ToResponseDtoList {

        @Test
        void returnsEmptyListWhenNull() {
            assertTrue(mapper.toResponseDtoList(null).isEmpty());
        }

        @Test
        void mapsEntityList() {
            Conversion first = new Conversion(
                    ConversionType.XML_TO_JSON, 10.0, now, "src.xml", "res.json", 1, 0
            );

            List<ConversionResponseDto> result = mapper.toResponseDtoList(List.of(first));

            assertEquals(1, result.size());
            assertEquals(10.0, result.getFirst().conversionTime());
            assertNull(result.getFirst().result());
        }
    }

    @Nested
    class ToEntity {

        @Test
        void mapsFieldsWithoutUser() {
            Conversion conversion = mapper.toEntity(
                    ConversionType.XML_TO_XML, 15.5, now, "src.xml", "res.xml", 5, 2, null, null
            );

            assertEquals(ConversionType.XML_TO_XML, conversion.getConversionType());
            assertEquals(15.5, conversion.getConversionTime());
            assertEquals(now, conversion.getConversionDateTime());
            assertEquals("src.xml", conversion.getSourceFileKey());
            assertEquals("res.xml", conversion.getResultFileKey());
            assertEquals(5, conversion.getInputElementsCount());
            assertEquals(2, conversion.getOutputElementsCount());
            assertNull(conversion.getUser());
        }

        @Test
        void mapsFieldsWithUser() {
            User user = new User("bob", "hash");

            Conversion pending = mapper.toEntity(
                    ConversionType.XML_TO_JSON, 8.0, now, null, null, 3, 2, "<a/>", "{\"a\":null}", user
            );

            assertNull(pending.getSourceFileKey());
            assertNull(pending.getResultFileKey());
            assertEquals("<a/>", pending.getSourceContent());
            assertEquals("{\"a\":null}", pending.getResultContent());
            assertEquals("bob", pending.getUser().getUsername());
        }
    }

    @Nested
    class ToFilter {

        @Test
        void mapsDtoFields() {
            LocalDateTime from = LocalDateTime.of(2026, 11, 16, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 11, 17, 23, 59);
            ConversionFilterDto dto = new ConversionFilterDto(
                    10, 5, ConversionType.XML_TO_XML, from, to, 1.0, 20.0, 2, 10, 3, 15
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
        }

        @Test
        void returnsDefaultFilterWhenDtoIsNull() {
            ConversionFilter fallback = mapper.toFilter(null);

            assertEquals(20, fallback.limit());
            assertEquals(0, fallback.offset());
        }
    }
}
