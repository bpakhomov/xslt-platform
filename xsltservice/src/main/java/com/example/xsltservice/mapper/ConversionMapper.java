package com.example.xsltservice.mapper;

import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ConversionMapper {

    public ConversionFilter toFilter(ConversionFilterDto dto) {
        if (dto == null) {
            return new ConversionFilter(20, 0, null, null, null, null, null, null, null, null);
        }
        return new ConversionFilter(
                dto.limit(),
                dto.offset(),
                dto.dateFrom(),
                dto.dateTo(),
                dto.conversionTimeFrom(),
                dto.conversionTimeTo(),
                dto.xmlTagsCountFrom(),
                dto.xmlTagsCountTo(),
                dto.jsonKeysCountFrom(),
                dto.jsonKeysCountTo()
        );
    }

    public Conversion toEntity(
            double conversionTime,
            LocalDateTime conversionDateTime,
            String jsonValue,
            int xmlTagsCount,
            int jsonKeysCount
    ) {
        return new Conversion(
                conversionTime,
                conversionDateTime,
                jsonValue,
                xmlTagsCount,
                jsonKeysCount
        );
    }

    public ConversionResponseDto toResponseDto(Conversion conversion) {
        if (conversion == null) {
            return null;
        }
        return new ConversionResponseDto(
                conversion.getId(),
                conversion.getConversionTime(),
                conversion.getConversionDateTime(),
                conversion.getJsonValue(),
                conversion.getXmlTagsCount(),
                conversion.getJsonKeysCount()
        );
    }

    public List<ConversionResponseDto> toResponseDtoList(List<Conversion> conversions) {
        if (conversions == null) {
            return List.of();
        }
        return conversions.stream()
                .map(this::toResponseDto)
                .toList();
    }
}
