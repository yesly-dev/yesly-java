# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The official Java SDK, `io.yesly:yesly-java` on Maven Central. Java 11 (`maven.compiler.release`), Maven. Usage is in `README.md`.

## Commands

```bash
mvn verify
mvn test -Dtest=<Class>#<method>             # one test
rm -rf src/main/java/io/yesly/model src/main/java/io/yesly/service && node ../yesly-openapi/sdk/generate.mjs java .
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
YESLY_API_KEY=ysk_test_… java -cp target/classes:target/test-classes:$(cat target/cp.txt) io.yesly.smoke.Smoke
```

Smoke tests run against staging with a `ysk_test_` key and `YESLY_BASE_URL=https://api.dev.yesly.dev/v1`; they create and clean up one object per resource.

## How it is built

The API-shaped part is generated from `yesly-openapi/openapi.yaml` by `yesly-openapi/sdk/generate.mjs` (`node ../yesly-openapi/sdk/generate.mjs java .`, after deleting the generated folders so removed classes go away): `src/main/java/io/yesly/model/`, `src/main/java/io/yesly/service/`, `Yesly.java` and `ApiVersion.java`. Never edit it by hand: change the spec in `yesly-openapi`, lint it, and regenerate with both repos checked out side by side. Everything else is hand-written: `Client.java`, `ClientOptions.java`, `RequestOptions.java`, `http/`, `exception/`, `Page.java`, `Webhook.java`, `Json.java`, `Version.java`. The hand-written client sends `Authorization: Bearer <key>` and `Yesly-Version`, retries with backoff honouring `Retry-After`, adds an automatic `Idempotency-Key` to writes, pages with `starting_after`, and verifies webhook signatures. The procedure for an API change across all SDKs is `yesly-openapi/docs/sdks.md`; package names must match `yesly-web/src/lib/sdks.ts`.

## Releasing

Bump `<version>` in `pom.xml` and `Version.VERSION`, then push a tag `v<version>`.
