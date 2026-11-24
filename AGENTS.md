# Repository Guidelines

## Project Structure

- `src/main/java/com/digitallife/` contains the Spring Boot backend. Use `web` and `websocket` for client interfaces, `agent` and `scheduler` for turn processing, and `sensor`, `memory`, and `repository` for perception and storage.
- `src/main/resources/static/` contains the shared Web and desktop UI in plain HTML, CSS, and JavaScript. Spring Boot loads `src/main/resources/schema.sql` at startup.
- `src/test/java/` mirrors the Java packages. `src-tauri/` contains the Windows desktop shell, tray integration, and icons. Product, API, and acceptance references live in `docs/`.

## Build and Run

Run these commands from the repository root with Java 17+ and Maven.

- `mvn -B test` runs the Java test suite.
- `mvn -B package` runs the tests and builds the runnable JAR.
- `java -jar target/digital-life-companion-0.1.0-SNAPSHOT.jar` starts the Web UI and API at `http://127.0.0.1:8080/`.
- `npx.cmd --yes --package @tauri-apps/cli@2.12.6 tauri build --ci -- --locked` builds the Windows executable and NSIS installer. It requires Node.js, Rust MSVC, C++ Build Tools, and WebView2. Start the backend before opening the desktop app.

## Coding Style

Use four-space indentation in Java and Rust and two spaces in the Web files. Use `PascalCase` for Java types, `camelCase` for Java and JavaScript methods, and `snake_case` for Rust functions. Reuse the existing API client and CSS variables. Check edited JavaScript with `node --check <file>` and format Rust with `cargo fmt --manifest-path src-tauri/Cargo.toml`.

## Testing and Privacy

Name Java tests `*Test.java`. Use JUnit 5 and Mockito for unit tests, and Spring Boot tests for integration coverage. Run a focused regression with `mvn -B -Dtest=EventDispatcherTest test`. Use isolated SQLite databases, synthetic inputs, and a stub model for automated tests. Keep raw screenshots, audio, and keystroke contents out of logs and stored test data.

## Commits and Pull Requests

Use short, action-oriented commit subjects. Recent commits favor Chinese, and Git history also includes `feat(frontend): ...`. In pull requests, summarize behavior changes, list verification commands and results, link relevant issues, and include synthetic screenshots for visible UI changes. Update `docs/api.md` for REST or WebSocket changes and `docs/tasks.md` with acceptance results. Ask before editing `docs/PRD.md` or `docs/architecture.md`. Prefer PlantUML and the installed PlantUML skill for UML diagrams.
