package com.example.xsltconverter.controller;

import com.example.xsltconverter.dto.AuthRequestDto;
import com.example.xsltconverter.dto.AuthTokenResponseDto;
import com.example.xsltconverter.dto.UserResponseDto;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.security.JwtService;
import com.example.xsltconverter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Регистрация нового пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь зарегистрирован"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные или username уже занят")
    })
    public ResponseEntity<UserResponseDto> register(@RequestBody AuthRequestDto request) {
        validateRequest(request);
        User created = userService.createUser(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponseDto(created.getId(), created.getUsername()));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Вход и получение JWT-токена")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWT-токен выдан"),
            @ApiResponse(responseCode = "400", description = "Пустой логин или пароль"),
            @ApiResponse(responseCode = "401", description = "Неверный логин или пароль")
    })
    public ResponseEntity<AuthTokenResponseDto> login(@RequestBody AuthRequestDto request) {
        validateRequest(request);
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        return ResponseEntity.ok(new AuthTokenResponseDto(jwtService.generateToken(auth.getName())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationError(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    private static void validateRequest(AuthRequestDto request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Username and password must not be blank");
        }
    }
}
