package com.example.xsltservice.mapper;

import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ConversionMapper {

    public ConversionFilter toFilter(ConversionFilterDto dto) {
        if (dto == null) {
            return new ConversionFilter(20, 0, null, null, null, null, null, null, null, null, null);
        }
        return new ConversionFilter(
                dto.limit(),
                dto.offset(),
                dto.conversionType(),
                dto.dateFrom(),
                dto.dateTo(),
                dto.conversionTimeFrom(),
                dto.conversionTimeTo(),
                dto.inputElementsCountFrom(),
                dto.inputElementsCountTo(),
                dto.outputElementsCountFrom(),
                dto.outputElementsCountTo()
        );
    }

    public Conversion toEntity(
            ConversionType conversionType,
            double conversionTime,
            LocalDateTime conversionDateTime,
            String sourceFileKey,
            String resultFileKey,
            Integer inputElementsCount,
            Integer outputElementsCount
    ) {
        return new Conversion(
                conversionType,
                conversionTime,
                conversionDateTime,
                sourceFileKey,
                resultFileKey,
                inputElementsCount,
                outputElementsCount
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

    public ConversionResponseDto toResponseDto(Conversion conversion) {
        return toResponseDto(conversion, null);
    }

    public ConversionResponseDto toResponseDto(Conversion conversion, String result) {
        if (conversion == null) {
            return null;
        }
        return new ConversionResponseDto(
                conversion.getId(),
                conversion.getConversionType(),
                conversion.getConversionTime(),
                conversion.getConversionDateTime(),
                conversion.getSourceFileKey(),
                conversion.getResultFileKey(),
                conversion.getInputElementsCount(),
                conversion.getOutputElementsCount(),
                result
        );
    }
}
