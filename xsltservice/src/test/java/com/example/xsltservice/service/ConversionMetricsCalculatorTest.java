package com.example.xsltservice.service;

import com.example.xsltservice.service.ConversionService.ConversionMetricsCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversionMetricsCalculatorTest {

    private ConversionMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConversionMetricsCalculator(new ObjectMapper());
    }

    @Test
    void countsXmlTagsIncludingNestedAndSelfClosing() {
        String xml = """
            <order id="10">
                <item>Book</item>
                <item>Pen</item>
                <note/>
            </order>
            """;

        assertEquals(4, calculator.countXmlTags(xml));
    }

    @Test
    void countsJsonKeysAcrossObjectsAndArrays() {
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

    @Test
    void throwsOnInvalidOrBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> calculator.countXmlTags("  "));
        assertThrows(IllegalArgumentException.class, () -> calculator.countXmlTags("<broken>"));
        assertThrows(IllegalArgumentException.class, () -> calculator.countJsonKeys("  "));
        assertThrows(IllegalArgumentException.class, () -> calculator.countJsonKeys("{invalid"));
    }
}
