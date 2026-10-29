# Local presentation run

Open the javac-campus folder in VS Code. Run `npm install`, then `npm run dev`, or use the existing Linux Start/Stop tasks. Website: http://localhost:5173/.

Java and MySQL are no longer needed for today's build. Profiles and campus requests use a local SQLite database in `frontend/.local-data/`. On Vercel, connect a shared Neon Postgres database through Storage. See the root README for exact settings and a two-account walkthrough.

The earlier user-local Java/MySQL installations were preserved, and their services remain stopped. Local database credentials from the earlier implementation stay in ignored files; they are not uploaded to GitHub or sent to the frontend.
