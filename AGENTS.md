# Правила проекта

## Unit-тесты (Java / JUnit 5 / Mockito)

При написании и рефакторинге unit-тестов во всех сервисах (`xsltservice`, `xsltconverter`, `s3service`) ориентируйся на `xsltservice/src/test/java/com/example/xsltservice/service/UserServiceTest.java` как на эталон:

- **Инициализация (`@BeforeEach`):** создавай общие Mockito-моки и тестируемый экземпляр (SUT) в `@BeforeEach void setUp()`.
- **Структура (`@Nested`):** группируй тесты в `@Nested`-классы (1 уровень вложенности) по тестируемым методам, когда у метода несколько сценариев; один `@Test`-метод — один конкретный сценарий (Arrange → Act → Assert).
- **Граничные значения (`@ParameterizedTest`):** для проверки `null`, пустых и пробельных строк (`@NullAndEmptySource`, `@ValueSource`) и однотипных граничных кейсов используй `@ParameterizedTest` вместо дублирующих `@Test`.
- **Моки без `HttpServer`:** подменяй внешние зависимости, DAO и HTTP-клиенты (`HttpClient` и т.п.) через Mockito (`mock`, `when`, `verify`, `verifyNoInteractions`, `never`); не поднимай встроенный `HttpServer`, сокеты или Spring-контекст в unit-тестах.
- **Граница применимости:** правило относится к unit-тестам; интеграционные `@SpringBootTest`-тесты допустимы только там, где проверяется именно конфигурация/контекст Spring или интеграция слоёв.
