# Single-VM deployment

This package targets one Linux VM with 2 vCPU / 4 GB RAM, Docker Engine and Compose
2.24.4+. It does not provision the VM. Use [environment operations](../infrastructure/README.md)
for the command matrix, retention, resource limits and reset boundaries.

Check `timedatectl status` on the VM and keep its clock synchronized through the
operating system's time service. Small clock corrections are tolerated for JWT
issuance timestamps (up to 60 seconds); token expiry stays strict. Investigate
large clock steps rather than increasing that allowance. Docker Desktop clocks
can also step after sleep/resume; check host/VM time before performance comparisons.

## Build and transfer

Build on a development/CI machine, not the small VM. Use the same image for
Simulation and Deployment, and keep its source revision with backups.

```bash
revision=$(git rev-parse --short=12 HEAD)
docker build -t "service-advertiser:$revision" .
docker save "service-advertiser:$revision" -o service-advertiser.tar
# Transfer the tar and this checkout's infrastructure definitions to the VM.
docker load -i service-advertiser.tar
```

Inspect `docker image inspect ... --format '{{.Id}} {{json .RepoDigests}}'` and
record the image ID; locally built images may have no registry digest. Tags are
explicit, but patch tags remain mutable: retain the verified image ID/digest for
reproducibility. Registry publishing is optional and not configured by CI.

## Protected configuration

Create `/etc/service-advertiser/deployment.env` from `.env.example` in this folder.
Set the image tag, public DNS hostname, database settings and absolute secrets
directory. No genuine secret belongs in that env file or in the checkout.

Create three files without a trailing newline in the secrets directory:
`database_password`, `jwt_key`, `grafana_password`. Generate distinct random
passwords and a Base64 encoding of at least 32 random bytes for the JWT key.
On first setup, these commands create the protected files directly without
printing values or placing them in shell history. Noclobber refuses to overwrite
existing secrets; do not delete an existing key just to repeat setup.

```bash
sudo install -d -m 0700 /etc/service-advertiser/secrets
sudo install -m 0600 deployment/.env.example /etc/service-advertiser/deployment.env
sudoedit /etc/service-advertiser/deployment.env
sudo sh -ec '
  set -C
  umask 077
  openssl rand -base64 32 | tr -d "\n" > /etc/service-advertiser/secrets/database_password
  openssl rand -base64 32 | tr -d "\n" > /etc/service-advertiser/secrets/jwt_key
  openssl rand -base64 32 | tr -d "\n" > /etc/service-advertiser/secrets/grafana_password
'
```

The host secret directory must be root-owned and mode 0700. On Linux, use
root-owned files mode 0440, group 10001 for database/JWT files, and group 472 for
the Grafana password. The API runs as 10001; Alloy uses its image user plus supplemental group 10001; Grafana uses its image's 472
identity. Verify that identity when updating images. PostgreSQL's root entrypoint
can read the database secret during initialization. File-backed Compose secrets
do not reliably remap uid/gid/mode: verify actual read access, do not loosen to 0644.

```bash
sudo chmod 0700 /etc/service-advertiser/secrets
sudo chown root:10001 /etc/service-advertiser/secrets/database_password /etc/service-advertiser/secrets/jwt_key
sudo chown root:472 /etc/service-advertiser/secrets/grafana_password
sudo chmod 0440 /etc/service-advertiser/secrets/database_password /etc/service-advertiser/secrets/jwt_key /etc/service-advertiser/secrets/grafana_password
sudo chmod 0600 /etc/service-advertiser/deployment.env
```

Run Compose with an account able to read this protected configuration and access
Docker. Docker access itself is administrative access. Compose secrets are
protected file injection, not an encrypted secret-management server. Never print
secret contents or pass values as process arguments.

The deployment command examples assume a root Bash shell (`sudo -s`) with the
checkout as the working directory. Keep the env file root-only rather than
loosening permissions to make an ordinary user's Compose command work.

Spring imports required `/run/secrets/` configtree files and has no development
datasource/JWT fallback. Missing files fail mounting/startup; invalid signing keys
fail validation. API secrets mount as `SPRING_DATASOURCE_PASSWORD` and
`JWT_SECRET_BASE64`; the database and Grafana receive only their own password file.

## Validate privately, then enable HTTPS

```bash
deploy=(--env-file /etc/service-advertiser/deployment.env -f deployment/compose.yaml --profile database)
docker compose "${deploy[@]}" config --quiet
docker compose "${deploy[@]}" -f deployment/compose.local.yaml up -d --wait
curl --fail http://127.0.0.1:8082/readyz
docker compose "${deploy[@]}" -f deployment/compose.local.yaml down
```

Local validation publishes only loopback 8082 and disables public TLS through an
HTTP site address. It does not verify certificate issuance. Check secrets are
readable with `run --rm --no-deps --entrypoint sh api -ec 'test -r /run/secrets/JWT_SECRET_BASE64 && test -r /run/secrets/SPRING_DATASOURCE_PASSWORD'`.

For public deployment, point DNS A/AAAA records to the correct VM, remove stale
AAAA records, and allow inbound TCP 80/443 through host and cloud firewalls.
Restrict SSH to authorized administrators. Do not open PostgreSQL, management,
Prometheus, Loki, or Grafana. Inspect Docker's actual published ports as well as
firewall rules; do not assume a host firewall automatically blocks Docker NAT.

```bash
docker compose "${deploy[@]}" up -d --wait
curl --fail https://api.example.com/readyz
curl -I http://api.example.com/readyz
docker compose "${deploy[@]}" logs --tail 100 caddy
```

Verify HTTP redirects to HTTPS, certificate validity, and denial of `/actuator`
and `/actuator/prometheus` externally. Never use the local override publicly.
Certificate state persists in project-scoped Caddy volumes. There is one proxy,
no extra load balancer. Grafana access uses a tunnel:

```bash
ssh -L 3002:127.0.0.1:3002 administrator@your-vm
```

Open localhost:3002 and sign in as `admin`. On first startup the supplied file
initializes the administrator password. Later file changes do not rotate an
existing Grafana account; use Grafana's supported password-change procedure.

## Proxy trust

Caddy has a fixed address only on the dedicated ingress network. API and Caddy
have separate outbound networks so Docker cannot select an untrusted alternate
shared route. The API trusts exactly that proxy address, not every private IP.
Caddy replaces forwarded identity with its socket peer and drops conflicting
headers. Both application rate-limit callers continue using `getRemoteAddr()`.

If subnet 10.203.80.0/24 conflicts, change `DEPLOYMENT_INGRESS_SUBNET`,
`CADDY_INGRESS_ADDRESS`, and `TRUSTED_PROXY_REGEX` together. Do not put a CDN or
another proxy in front without revisiting client-IP trust and acceptance tests.

## Backup and verified restore

Take a manual backup before updates and at a frequency appropriate for tolerated
data loss (initial default: daily). Copy completed backups off the VM and retain
seven daily copies; periodically perform the restore drill. Scheduling is manual.

```bash
sh deployment/backup.sh /etc/service-advertiser/deployment.env /srv/service-advertiser/backups
```

The script uses PostgreSQL's local container socket, a custom-format dump, mode
0600 output, and atomic rename after success. The `.tmp` file is removed on error.
Backups contain private application data. Protect transport/storage. JWT and
other secret recovery is a separate protected operation; a database dump alone
does not contain deployment secrets.

Restore into a new explicitly disposable project; never overwrite the source to
test a backup. Keep the same application image/configuration. Avoid the running
deployment ingress subnet by setting separate restore values in the shell:

```bash
export DEPLOYMENT_INGRESS_SUBNET=10.203.82.0/24
export CADDY_INGRESS_ADDRESS=10.203.82.254
export TRUSTED_PROXY_REGEX='10\.203\.82\.254'
restore=(-p service-advertiser-restore-check --env-file /etc/service-advertiser/deployment.env -f deployment/compose.yaml --profile database)
docker compose "${restore[@]}" up -d --wait postgres
docker compose "${restore[@]}" exec -T postgres sh -ec 'pg_restore --exit-on-error -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /srv/service-advertiser/backups/chosen.dump
docker compose "${restore[@]}" up -d --wait api
docker compose "${restore[@]}" exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;"'
docker compose "${restore[@]}" exec -T api curl --fail http://localhost:8080/readyz
```

Inspect representative row counts, then perform login and a read/write flow with
a known restored test account through the restore API (use container-local HTTP,
or an explicitly temporary loopback-only mapping). Confirm source record counts
did not change. The runnable acceptance check performs this drill with synthetic
data. Stop and remove only the disposable restore project's volumes:

```bash
docker compose "${restore[@]}" down --volumes
unset DEPLOYMENT_INGRESS_SUBNET CADDY_INGRESS_ADDRESS TRUSTED_PROXY_REGEX
```

For managed PostgreSQL, omit `--profile database`, supply its TLS-verified JDBC
URL/username/password file, and use that provider's backup/restore tools. The local
backup script deliberately targets the included PostgreSQL service only.

## Updates, shutdown and troubleshooting

Back up, retain the old image ID/configuration, load the new image, update
`APPLICATION_IMAGE`, then `up -d --wait`. Never remove volumes during an update.
Flyway applies migrations at startup. If schema compatibility changes, rolling
back an image alone is insufficient: use a verified compatible restore and plan
for writes since the backup. JWT key rotation invalidates existing access tokens.
Database initialization secrets do not rotate an existing PostgreSQL password:
change the database role password and application file in a coordinated operation.

API shutdown allows 30 seconds to drain, with 45 seconds before container kill.
Readiness checks query the API connector and database; monitoring is not an API
startup dependency. `docker stats --no-stream` and `docker compose ... ps` help
diagnose OOM/readiness failures. Inspect file ownership for log/secret errors,
resolved project volumes for apparent empty data, disk usage for ingestion
failures, and Caddy logs/DNS/firewall for certificate errors. Do not disable
security or reset data to fix an unexplained startup failure.
