# Jenkins pipeline

[`Jenkinsfile`](Jenkinsfile) (also symlinked at the repository root) defines the
promotion pipeline.

**You do not need Jenkins to work on this project.** `mvn clean verify` and
`docker compose up --build` do everything the pipeline does. The pipeline that actually
runs on every push is [`../.github/workflows/ci.yml`](../.github/workflows/ci.yml).

## Stages

| Stage | Command | Purpose |
|---|---|---|
| Checkout | `checkout scm` | Fetch the revision |
| Compile | `mvn clean compile` | Fail fast on compilation errors |
| Unit tests | `mvn test` | Fast feedback, no Docker required |
| Integration tests | `mvn verify -DskipUTs` | Testcontainers against real PostgreSQL |
| Checkstyle / SpotBugs | parallel, `-Pquality` | Static analysis |
| Coverage | `mvn jacoco:report` | Publishes the HTML report |
| SonarQube | `mvn sonar:sonar` | Skipped unless `SONAR_HOST_URL` is set |
| Package | `mvn package -DskipTests` | Builds and archives the jars |
| Docker build | `docker build` per service | Nine images tagged with the build number |
| Image validation | `docker compose config` | Catches compose drift |

## Agent requirements

- JDK 21 configured in Jenkins as `jdk-21`
- Maven 3.9 configured as `maven-3.9`
- A reachable Docker daemon (Testcontainers and the image build both need it)
- Optional: a SonarQube server configured as `sonarqube`

## Design notes

- **Unit and integration tests are separate stages.** Surefire runs `*Test`, Failsafe
  runs `*IT`. A compilation or unit-test failure should not wait on containers starting.
- **Static analysis does not fail the build by default.** It is behind the `quality`
  profile so a style violation cannot block a deployment while the team is adopting the
  rules. Flip `failOnViolation` in the parent POM once the baseline is clean.
- **SonarQube is conditional.** The stage is skipped when `SONAR_HOST_URL` is unset, so
  the pipeline works without a Sonar server.
- **The workspace is cleaned in `post`**, because nine Docker images per build fills a
  disk quickly.
