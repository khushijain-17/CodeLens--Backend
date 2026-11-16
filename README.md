# CodeLens Backend

Spring Boot API behind **CodeLens**: paste a public GitHub repo URL, browse its file tree, open any file, and get an AI explanation of the code in plain English.

- **Live demo:** https://codelens-peach.vercel.app
- **Frontend repo:** https://github.com/khushijain-17/CodeLens--Frontend
- **API base URL:** https://codelens-backend-fygx.onrender.com

> The backend runs on Render's free tier, so the first request after a period of inactivity can take about a minute while it wakes up.

## Architecture

```
React (Vercel)  ->  Spring Boot API (Docker on Render)  ->  PostgreSQL (Neon)
                                  |-> JGit (shallow clone of GitHub repos)
                                  |-> Groq API (LLM explanations)
```

Code is split into controllers (HTTP), services (`GitService`, `AIService`), and a Spring Data JPA repository layer.

## Tech stack

Java 17, Spring Boot 3.5, Spring Data JPA, PostgreSQL (Neon), JGit, Groq API (`openai/gpt-oss-120b`), Docker (multi-stage build), Render.

## API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/repo/analyze` | Body `{"url": "https://github.com/owner/repo"}`. Validates the URL, shallow-clones the repo, saves it, returns the file tree |
| GET | `/api/repo/file?path=` | Returns the text of a file inside a cloned repo |
| POST | `/api/repo/explain` | Body is plain-text code. Returns an AI explanation |
| GET | `/api/repo/graph?repoPath=` | Returns a simple file dependency graph for a cloned repo |
| GET | `/api/saved` | Lists saved repos |
| POST | `/api/saved` | Saves a repo (URL is validated) |
| DELETE | `/api/saved/{id}` | Deletes one saved repo |
| DELETE | `/api/saved/all` | Deletes all saved repos (see limitations) |

## Security notes

While reviewing the project I found and fixed several issues:

- **Path traversal:** `/file` and `/graph` used to accept any server path. All paths now go through `GitService.resolveInsideRepos`, which uses `toRealPath()` (resolves `..` and symlinks) and checks the result starts with the app's `repos/` folder. Otherwise the API returns `403 Access denied`.
- **Arbitrary clone URLs:** `/analyze` only accepts `https://github.com/<owner>/<repo>`. Clones are shallow, stored under `repos/<uuid>`, and purged after 1 hour.
- **CORS:** the wildcard was replaced by an allow-list set through `CODELENS_ALLOWED_ORIGINS`.
- **Input validation** on the saved-repos endpoint.
- Secrets live in environment variables; the real `application.properties` is gitignored.
- `GitServiceTest` (JUnit 5 + AssertJ) covers URL validation, reading inside the repos folder, and blocking outside paths and `../` traversal.

## Run locally

Requirements: Java 17 and a PostgreSQL database (e.g. a free Neon project).

```bash
git clone https://github.com/khushijain-17/CodeLens--Backend.git
cd CodeLens--Backend
cp application.properties.example src/main/resources/application.properties
# fill in the placeholders: database URL/user/password and your Groq API key
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Run the unit tests with:

```bash
./mvnw -Dtest=GitServiceTest test
```

### Configuration

| Variable / property | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` (+ username/password) | PostgreSQL connection | none |
| Groq API key | LLM access (see the example properties file for the exact name) | none |
| `CODELENS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins | `http://localhost:3000` |
| `CODELENS_REPOS_DIR` | Folder for cloned repos | `repos` |

## Docker

```bash
docker build -t codelens-backend .
docker run -p 8080:8080 --env-file .env codelens-backend
```

The multi-stage Dockerfile builds the jar with Maven, then ships only the JRE and the jar.

## Known limitations and next steps

- No authentication, so `DELETE /api/saved/all` is open to anyone. Next: add auth and per-user saved lists.
- No rate limiting on `/analyze` and `/explain`.
- Clones are deleted after 1 hour, so older file links return 404.
- The full-context test (`contextLoads`) is disabled because it needs a real database and API key. Next: an integration test with a test database.
- Render's free tier has slow cold starts.
