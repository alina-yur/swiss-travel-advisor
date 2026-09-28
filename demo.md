# Demo Runbook

Assumes the app is built and credentials are already loaded.

## Linux: ADB + Podman Phoenix

Podman runs natively on Linux. Do **not** use `podman machine`.

```bash
podman-compose up -d phoenix
./target/swiss-travel-advisor
```

Open:

- App: <http://localhost:8080>
- Phoenix: <http://localhost:6006>

If Linux is remote, forward ports `8080` and `6006` over SSH or VS Code.

Optional ADB proof:

```bash
./scripts/explore-db-list-tables.sh
./scripts/explore-db-hotels.sh
./scripts/explore-db-wishlist.sh
```

## macOS: local Podman Oracle + Phoenix

Start the complete demo:

```bash
podman machine start
podman-compose up -d phoenix
./scripts/run-with-local-oracle.sh
```

The script starts Oracle, waits until it is healthy, and runs the app with the
local `TRAVEL` database user. Phoenix remains independently managed. Open the
same app and Phoenix URLs shown above.

## Showcase

Enter these prompts in the app, in order:

1. `Find a quiet lakeside hotel near Lucerne under CHF 250.`
2. `Save the first hotel to my wishlist.`
3. `What is currently on my wishlist?`
4. `Show scenic activities within 40 km of Interlaken.`

Then open Phoenix, select `swiss-travel-advisor`, and show the `AGENT`, `LLM`,
`TOOL`, `EMBEDDING`, and `RETRIEVER` spans.
