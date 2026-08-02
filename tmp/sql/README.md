# tmp/sql — ad-hoc query scratch dir

Throwaway `.sql` files for **read-only inspection / verification / debugging** of
the database. Gitignored except this README.

## The database-access model (protective + enabling)

1. **Schema changes ALWAYS go through Flyway migrations** in
   `backend/src/main/resources/db/migration/V##__*.sql` — reviewed, committed,
   and applied automatically on deploy. **Never hand-run DDL against prod.** The
   DB structure only ever changes through a reviewed commit. If a schema change
   is needed, the answer is "add `V19__…​.sql`", not a query in this folder.

2. **Ad-hoc reads live here.** Claude drops a `.sql` file with an intent comment;
   you review it and run it — easiest via **Neon's dashboard SQL Editor** (paste
   and click), or with `psql` / the read-only role below.

## Running a query file

Read-only role `claude` (created in Neon). Keep its password in the OS keychain
(same pattern as other secrets), not in a dotfile:

```bash
# one-time store:
#   secret-tool store --label='neon/pollsystem-readonly' service neon account claude
PGPASSWORD="$(secret-tool lookup service neon account claude)" \
  psql "host=<direct-endpoint-host> dbname=neondb user=claude sslmode=require" \
  -f tmp/sql/<file>.sql
```

(Or just paste the file's contents into the Neon SQL Editor.)

## Handy verification snippets

```sql
-- Confirm Flyway ran and how far:
select version, description, success from flyway_schema_history order by installed_rank desc limit 5;

-- Confirm the read-only role really is read-only (should ERROR):
create table _writetest(x int);
```
