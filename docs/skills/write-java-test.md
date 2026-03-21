---
name: write-java-test
description: This skill should be used when writing Java tests, generating test classes, adding unit or integration tests, or implementing test coverage for Java code.
version: 1.4.0
---

# Write Java Test

This skill provides testing conventions to follow when writing Java tests.

## Frameworks & Libraries

- **JUnit 5** (`org.junit.jupiter`) — test framework; never use JUnit 4 annotations (`@Test` from `org.junit`, `@RunWith`, etc.)
- **Mockito** — mocking and stubbing; when `@Mock` fields are present, call `openMocks(this)` (statically imported) in `beforeEach` — do **not** rely on `@ExtendWith(MockitoExtension.class)` for mock injection
- **AssertJ** — all assertions; never use JUnit's `Assertions.assertEquals` or Hamcrest matchers

## Static Imports

Always use static imports for the following — never qualify them with the class name:

```java
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.MockitoAnnotations.openMocks;
```

Use `arguments(...)` (statically imported) instead of `arguments(...)`.

## Language Style

- Use `var` for local variable declarations whenever the type is obvious from the right-hand side.

  **Avoid:**
  ```java
  UserService userService = new UserService(repository);
  List<String> names = List.of("Alice", "Bob");
  ```

  **Prefer:**
  ```java
  var userService = new UserService(repository);
  var names = List.of("Alice", "Bob");
  ```

## Test Structure

### Common setup in `@BeforeEach`

- Name the `@BeforeEach` method `beforeEach` and the `@AfterEach` method `afterEach`.
- Extract shared fixture construction (creating the class under test, setting up stubs that apply to all tests) into `beforeEach`. Only put test-specific stubs inside individual test methods.
- When `@Mock` fields are declared, call `MockitoAnnotations.openMocks(this)` as the first statement of `beforeEach` to inject them.

```java
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    private OrderService orderService;

    @BeforeEach
    void beforeEach() {
        openMocks(this);
        orderService = new OrderService(repository);
    }
}
```

If teardown is needed:

```java
    @AfterEach
    void afterEach() {
        // teardown
    }
```

### Prefer parameterized tests over many similar tests

When multiple test cases share the same logic and differ only in inputs/outputs, use `@ParameterizedTest` instead of separate `@Test` methods.

**Allowed sources (only these two):**
- `@MethodSource` — when arguments are objects, tuples, or require construction
- `@ValueSource` — when there is a single primitive/String argument

**Never use:** `@CsvSource`, `@EnumSource`, or any other source annotation.

#### Structure: `@Nested` class with `_class` suffix

Group each `@ParameterizedTest` together with its source method inside a `@Nested` class named after the scenario with a `_class` suffix. The test method and the source method **must have the same name**.

**Avoid (top-level, mismatched names):**
```java
@ParameterizedTest
@MethodSource("discountCases")
void WHEN_discount_called_THEN_returns_correct_value(CustomerTier tier, double expected) {
    assertThat(service.discount(tier)).isEqualTo(expected);
}

static Stream<Arguments> discountCases() { ... }
```

**Prefer (`@Nested` class, same method name, `_class` suffix):**
```java
@Nested
class WHEN_discount_called_THEN_returns_correct_value_class {

    static Stream<Arguments> WHEN_discount_called_THEN_returns_correct_value() {
        return Stream.of(
            arguments(CustomerTier.GOLD,   0.20),
            arguments(CustomerTier.SILVER, 0.10),
            arguments(CustomerTier.BRONZE, 0.05)
        );
    }

    @ParameterizedTest
    @MethodSource
    void WHEN_discount_called_THEN_returns_correct_value(CustomerTier tier, double expected) {
        assertThat(service.discount(tier)).isEqualTo(expected);
    }
}
```

For `@ValueSource` (single primitive argument):
```java
@Nested
class GIVEN_blank_name_WHEN_create_called_THEN_throws_class {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void GIVEN_blank_name_WHEN_create_called_THEN_throws(String name) {
        assertThatThrownBy(() -> service.create(name))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

> Note: when `@MethodSource` has no argument, JUnit 5 resolves the source by matching the test method name — this is why both methods must share the same name.

## Assertions with AssertJ

Always import `org.assertj.core.api.Assertions.assertThat` (or `assertThatThrownBy`, `assertThatCode`).

```java
// values
assertThat(result).isEqualTo(expected);
assertThat(result).isNotNull();
assertThat(flag).isTrue();

// collections
assertThat(list).hasSize(3).containsExactly("a", "b", "c");
assertThat(map).containsKey("id").containsEntry("name", "Alice");

// exceptions
assertThatThrownBy(() -> service.process(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Missing");
```

## Mockito Usage

```java
// stubbing
when(repository.findById(1L)).thenReturn(Optional.of(entity));

// void methods
doThrow(new RuntimeException("Missing connection")).when(repository).save(any());

// verification
verify(repository).save(argThat(order -> order.getStatus() == Status.CONFIRMED));
verifyNoMoreInteractions(repository);
```

## Naming Convention

All test methods must follow one of these two patterns (uppercase keywords, `snake_case` segments):

- `GIVEN_given_WHEN_when_THEN_then` — when a precondition is relevant
- `WHEN_when_THEN_then` — when there is no meaningful precondition

The same name is used for the `@Nested` class (with `_class` appended), the `@ParameterizedTest` method, and the `@MethodSource` method.

```java
// Simple test — no precondition
@Test
void WHEN_order_id_is_null_THEN_throws() { ... }

// Test with precondition
@Test
void GIVEN_empty_cart_WHEN_checkout_called_THEN_throws() { ... }

// Parameterized — @Nested class mirrors the method name + _class suffix
@Nested
class GIVEN_valid_tier_WHEN_discount_called_THEN_returns_correct_value_class {

    static Stream<Arguments> GIVEN_valid_tier_WHEN_discount_called_THEN_returns_correct_value() {
        return Stream.of(
            arguments(CustomerTier.GOLD,   0.20),
            arguments(CustomerTier.SILVER, 0.10),
            arguments(CustomerTier.BRONZE, 0.05)
        );
    }

    @ParameterizedTest
    @MethodSource
    void GIVEN_valid_tier_WHEN_discount_called_THEN_returns_correct_value(
            CustomerTier tier, double expected) {
        assertThat(service.discount(tier)).isEqualTo(expected);
    }
}
```
