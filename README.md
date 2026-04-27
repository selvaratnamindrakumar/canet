# CaNet

Spring Boot application that fetches JSON over HTTPS and displays it in a
server-rendered UI.  The `spring-boot-app/` directory contains all new code;
the original `quickstart/` Maven project is left untouched.

---

## Quick start

```bash
cd spring-boot-app
mvn spring-boot:run          # http://localhost:8080
mvn test                     # run all three test layers
docker build -t canet-app .
docker run -p 8080:8080 canet-app
```

Change the target HTTPS endpoint in `src/main/resources/application.yml`:

```yaml
external:
  api:
    base-url: https://your-api.example.com/endpoint
```

---

## Spring Boot vs a JavaScript-based HTTPS data fetcher

The table below compares this system against a typical client-side JavaScript
approach (React / Vue / vanilla `fetch`) that calls an HTTPS API directly from
the browser.

| Concern | JavaScript (browser fetch / Node proxy) | This system (Spring Boot + Thymeleaf) |
|---|---|---|
| **HTTPS & CORS** | Browser enforces CORS on every cross-origin call. Requires the API to set `Access-Control-Allow-Origin`, or you add a proxy — extra infrastructure. | JVM makes the request server-to-server. CORS does not apply. No proxy needed. |
| **API key safety** | Keys embedded in client JS are visible in DevTools and bundle dumps. | Credentials stay in `application.yml` / env vars on the server, never sent to the browser. |
| **Dependency surface** | A React/Vue project pulls in 500–1 500+ transitive npm packages, each a potential vulnerability. | Four Maven starters. Spring Boot's BOM ensures compatible versions across all of them. |
| **Build toolchain** | Node.js + npm/yarn + bundler (Webpack/Vite) + Babel/tsc + linters — breakage when versions diverge. | `mvn package` — one command, reproducible on any machine with Java 17. |
| **Docker image size** | Node runtime + possibly `node_modules` ≈ 400 MB+ images. | Multi-stage build discards Maven; final image is JRE Alpine + fat JAR ≈ 180 MB. |
| **Type safety** | Dynamic typing means a JSON shape change silently produces `undefined` in the UI. | `DataItem` is a typed Java class; shape mismatches produce a clear deserialization error, not a silent glitch. |
| **Error handling** | Two separate error boundaries: one for fetch, one for rendering — in different files. | Single `try/catch` in the service layer; controller always passes a (possibly empty) list to the template. |
| **Testing the HTTPS call** | Mocking `fetch`/`axios` in Jest requires manual stubs or `msw`. Testing real SSL from a browser is impractical. | Three layers out of the box: **MockMvc** (slice), **MockRestServiceServer** (JVM-level intercept), **WireMock** (real embedded HTTP server). |
| **Server-side rendering** | SPA delivers an empty `<div>` until JS loads and fetch completes. Needs Next.js/Nuxt to fix — more complexity. | Thymeleaf renders full HTML before the first byte reaches the browser. Works without JS. Fully crawlable. |
| **Observability** | Requires third-party APM or custom logging. Browser network logs are client-only. | Actuator exposes `/actuator/health` and `/actuator/info`; SLF4J logs every outbound call automatically. |
| **Long-term maintenance** | Ecosystem moves fast — 12 months untouched can mean dozens of breaking changes and security advisories. | A single `<parent>` version bump in `pom.xml` updates all compatible dependencies. Java LTS gives multi-year stability. |

### When a JavaScript frontend is still the right choice

This is not a blanket rejection of JavaScript. A client-side approach is
preferable when:

- The UI requires **rich stateful interactivity** (drag-and-drop, real-time
  collaboration, canvas/WebGL).
- The app is a **public consumer SPA** where offline capability and
  time-to-interactive are critical product requirements.
- The remote API is **under your control** and CORS headers can be set correctly.
- The team's existing expertise is primarily in a JS framework and the switch
  cost outweighs the benefits.

For the CaNet use-case — fetching structured JSON from a third-party HTTPS
endpoint and rendering it in a table — the Spring Boot approach eliminates an
entire category of infrastructure complexity with no meaningful trade-off.

---

## Project layout

```
spring-boot-app/
├── Dockerfile                          # multi-stage: Maven build → JRE Alpine
├── pom.xml                             # Spring Boot 3.2, Java 17
└── src/
    ├── main/java/com/canet/app/
    │   ├── CaNetApplication.java
    │   ├── config/HttpClientConfig.java        # RestTemplate with timeouts
    │   ├── controller/DataController.java      # UI + REST endpoints
    │   ├── model/DataItem.java
    │   └── service/
    │       ├── HttpsDataService.java           # interface
    │       └── HttpsDataServiceImpl.java       # RestTemplate + error handling
    ├── main/resources/
    │   ├── application.yml
    │   ├── templates/
    │   │   ├── index.html                      # data table + filter bar
    │   │   └── about.html                      # this comparison, rendered in-app
    │   └── static/css/style.css
    └── test/java/com/canet/app/
        ├── CaNetApplicationTests.java          # context smoke test
        ├── controller/DataControllerTest.java  # @WebMvcTest + MockMvc
        ├── service/HttpsDataServiceTest.java   # @RestClientTest + MockRestServiceServer
        └── mock/MockApiServerTest.java         # WireMock embedded server
```
