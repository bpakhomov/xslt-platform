package com.example.xsltservice.controller;

import com.example.xsltservice.dto.request.XmlRequestDto;
import com.example.xsltservice.dto.response.XmlResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class XmlRequestController {

    @PostMapping("/request")
    @Operation(summary = "Преобразование XML в JSON, с последующим получением количества xml-тегов, json-ключей, временем обработки и самого json")
    public ResponseEntity<XmlResponseDto> xmlToJsonRequest(@RequestBody XmlRequestDto xmlData) {


        return ;
    }
}
