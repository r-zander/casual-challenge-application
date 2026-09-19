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
- The jwt key is made up on the first start and kept in `./secrets/jwt_private_key.txt`, gitignored - overwrite that file if you want a specific one
- Create an admin token for the season wizard (in PowerShell or CMD):

    ```
    docker compose exec casual_challenge_application java -cp app.jar "-Dloader.main=gg.casualchallenge.application.CliTools" org.springframework.boot.loader.launch.PropertiesLauncher generate-admin-jwt ##your_name##
    ```

- The wizard can push the three season migrations to GitHub itself. Needs a token with write access to contents and
  pull requests, pasted into step 3 - it is used for that one call and stored nowhere. Point
  `casual-challenge.season.github.repository` at a repository of your own while you try it out
- `casual-challenge.season.github.base-branch` is what the pull request goes against, `master` on the real one

The server runs `docker-compose.prod.yml`, not this one.
