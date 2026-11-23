package com.example.xsltservice.service;

import com.example.xsltservice.service.ConversionService.ConversionMetricsCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversionMetricsCalculatorTest {

    private ConversionMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConversionMetricsCalculator(new ObjectMapper());
    }

    @Nested
    class CountXmlTags {

        @Test
        void countsNestedAndSelfClosingTags() {
            String xml = """
                    <order id="10">
                        <item>Book</item>
                        <item>Pen</item>
                        <note/>
                    </order>
                    """;

            assertEquals(4, calculator.countXmlTags(xml));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "<broken>"})
        void throwsWhenXmlIsBlankOrInvalid(String xml) {
            assertThrows(IllegalArgumentException.class, () -> calculator.countXmlTags(xml));
        }
    }

    @Nested
    class CountJsonKeys {

        @Test
        void countsKeysAcrossObjectsAndArrays() {
            String json = """
                    {
                      "order": {
                        "@id": "10",
                        "item": [
                          {"name": "Book"},
                          {"name": "Pen"}
                        ],
                        "note": null
                      }
                    }
                    """;

            assertEquals(6, calculator.countJsonKeys(json));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "{invalid"})
        void throwsWhenJsonIsBlankOrInvalid(String json) {
            assertThrows(IllegalArgumentException.class, () -> calculator.countJsonKeys(json));
        }
    }
}
