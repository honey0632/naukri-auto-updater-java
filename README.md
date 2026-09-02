# Naukri Auto Updater — Java 21

Spring Boot + Playwright service that runs daily and re-saves the existing
profile/resume content.

## Safety/design notes

- It writes the existing value back unchanged.
- It does not attempt to bypass CAPTCHA, OTP, MFA, or anti-bot checks.
- Naukri can change its HTML/UI. Selectors are therefore configurable.
- Use your own account and comply with Naukri's terms.

## Run locally

1. Install Java 21 and Maven.
2. Create a `.env` file in the project root and fill in credentials.
3. Run:
   `mvn spring-boot:run`

Spring Boot loads the root `.env` file automatically for local runs. Docker
Compose loads the same file through `env_file`.

Playwright browsers may need installation on a new machine:
`mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`

## Run with Docker

1. Fill in `.env`.
2. Run:
   `docker compose up -d --build`

## GitHub Actions

- **CI** runs Maven verification and builds the Docker image for pushes and pull requests.
- **CD** publishes the Docker image to GitHub Container Registry and deploys it to the
  Oracle Cloud VM when a `v*.*.*` tag is pushed or the workflow is run manually.

Configure the application environment variables in the deployment platform; `.env`
is intentionally ignored and is never included in the workflows. For Oracle Cloud,
create these repository or environment secrets: `OCI_HOST`, `OCI_USERNAME`,
`OCI_SSH_KEY`, and `OCI_APP_DIR`. The target directory must contain
`docker-compose.yml` and the production `.env` file.

## Manual update

POST `/api/update`

Example:
`curl -X POST http://localhost:8080/api/update`

## History

GET `/api/history`

## Scheduling

Default: every day at 10:00 Asia/Kolkata.

Change `UPDATE_CRON` in `.env`.
