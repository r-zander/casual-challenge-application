Application to deliver data to api.casualchallenge.gg and the Casual Challenge Checker.

See [Documentation api.casualchallenge.gg](https://docs.google.com/document/d/1fe1_pEHheCltBwVF__mqkcnVm2e6D-ElNi24bVWuJIg/edit?tab=t.0) for all details regardings this project.

## Local setup

- Docker Desktop, rest comes automatically within the Docker container
- `docker compose up` in this folder
- First start builds the jar and runs every migration, takes a few minutes
- API on http://localhost:8080, Documentation on http://localhost:8080/docs
- Postgres on localhost:5433, `postgres`/`postgres`
- `docker compose down -v` throws the database away and starts over

The server runs `docker-compose.prod.yml`, not this one.
