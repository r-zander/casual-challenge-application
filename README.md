Application to deliver data to api.casualchallenge.gg and the Casual Challenge Checker.

See [Documentation api.casualchallenge.gg](https://docs.google.com/document/d/1fe1_pEHheCltBwVF__mqkcnVm2e6D-ElNi24bVWuJIg/edit?tab=t.0) for all details regardings this project.

## Local setup

- Docker Desktop, rest comes automatically within the Docker container
- `docker compose up` in this folder
- First start builds the jar and runs every migration, takes a few minutes
- API on http://localhost:8080, Documentation on http://localhost:8080/docs
- Postgres on localhost:5433, `postgres`/`postgres`
- `src/main/resources/static` and `templates` are mounted into the container - edit a page, reload the browser, done, no rebuild
- everything else (java, migrations) needs `docker compose up --build`
- `docker compose down -v` throws the database away and starts over
- The jwt key is made up on the first start and kept in `./secrets`, gitignored - overwrite the file in there if you want a specific one
- Admin token for the season wizard:

```
docker compose exec casual_challenge_application java -cp app.jar -Dloader.main=gg.casualchallenge.application.CliTools org.springframework.boot.loader.launch.PropertiesLauncher generate-admin-jwt ##YOUR_NAME##
```

The server runs `docker-compose.prod.yml`, not this one.
