---
name: write-java-code
description: This skill should be used when writing Java code, implementing Java classes or methods, handling Java exceptions, or generating any Java source code.
version: 1.1.0
---

# Write Java Code

This skill provides coding conventions to follow when writing Java code.

## Error Message Conventions

- For "... not found" errors, use `"Missing ..."` instead.

  **Avoid:**
  ```java
  throw new RuntimeException("User not found");
  throw new IllegalArgumentException("Config key not found");
  ```

  **Prefer:**
  ```java
  throw new RuntimeException("Missing user");
  throw new IllegalArgumentException("Missing config key");
  ```

## Preferred Dependencies

When implementing functionality that these libraries cover, prefer them over custom implementations or alternatives:

- **Google Guava** — collections, caching, string utilities, preconditions, I/O helpers, etc.
- **Apache Commons** (Lang, Collections, IO, etc.) — string/array utilities, file I/O, reflection helpers, etc.

## Braces

Always use curly braces for `if`, `else`, and `for` bodies, even when the body is a single statement.

  **Avoid:**
  ```java
  if (condition) doSomething();
  for (int i = 0; i < n; i++) process(i);
  ```

  **Prefer:**
  ```java
  if (condition) {
      doSomething();
  }
  for (int i = 0; i < n; i++) {
      process(i);
  }
  ```

## Argument Validation

Use **Guava `Preconditions`** for method argument checks. Never write manual `if` + `throw` for validation.

  **Avoid:**
  ```java
  if (windowSizeNanos <= 0) throw new IllegalArgumentException("Missing valid windowSizeNanos");
  if (name == null) throw new NullPointerException("name");
  ```

  **Prefer:**
  ```java
  import static com.google.common.base.Preconditions.checkArgument;
  import static com.google.common.base.Preconditions.checkNotNull;

  checkArgument(windowSizeNanos > 0, "Missing valid windowSizeNanos");
  checkNotNull(name, "Missing name");
  ```

## Dependency Injection

Use **Google Guice** for dependency injection. Avoid Spring DI, manual factory wiring, or `new` for injectable types.

  **Prefer:**
  ```java
  public class UserService {
      private final UserRepository repository;

      @Inject
      public UserService(UserRepository repository) {
          this.repository = repository;
      }
  }

  public class AppModule extends AbstractModule {
      @Override
      protected void configure() {
          bind(UserRepository.class).to(UserRepositoryImpl.class);
      }
  }
  ```
