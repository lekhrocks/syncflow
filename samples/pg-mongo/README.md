# PostgreSQL → MongoDB Synchronization

This sample shows how to synchronize a PostgreSQL `users` table to a MongoDB `users` collection with transformations, CDC, and snapshot.

## Architecture

```
PostgreSQL  ──Snapshot──→  MongoDB
users           CDC        users
├─ id                     ├─ _id (mapped from id)
├─ email      ──lowercase→├─ email
├─ full_name  ──rename──→ ├─ name
├─ created_at             ├─ created_at
└─ deleted_at  ──filter──→ (excluded)
```

## Prerequisites

```bash
# Start databases
docker run -d --name pg -e POSTGRES_PASSWORD=pass -p 5432:5432 postgres:16
docker run -d --name mongo -p 27017:27017 mongo:7
```

## Setup

```bash
# 1. Create source table
psql -h localhost -U postgres -c "
  CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255),
    full_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP
  );
  INSERT INTO users (email, full_name) VALUES
    ('Alice@Example.COM', 'Alice Johnson'),
    ('Bob@Test.ORG', 'Bob Smith');
"

# 2. Create connections
syncflow connection create --name pg-source --type POSTGRESQL \
  --host localhost --port 5432 --database postgres --user postgres --password pass

syncflow connection create --name mongo-dest --type MONGODB \
  --host localhost --port 27017 --database admin

# 3. Create pipeline
syncflow pipeline create pg-to-mongo \
  --source-connection pg-source --source-schema public --source-table users \
  --dest-connection mongo-dest --dest-schema admin --dest-table users \
  --sync-mode CDC_SNAPSHOT_AND_INCREMENTAL \
  --batch-size 5000

# 4. Add column mappings
syncflow pipeline mapping add pg-to-mongo \
  --source id --destination _id

syncflow pipeline mapping add pg-to-mongo \
  --source email --destination email --transform lowercase

syncflow pipeline mapping add pg-to-mongo \
  --source full_name --destination name --transform "rename:name"

# 5. Deploy
syncflow pipeline deploy pg-to-mongo
```

## Verify

```bash
# Check destination
mongosh admin --eval "db.users.find().pretty()"

# Insert new row in source
psql -h localhost -U postgres -c "
  INSERT INTO users (email, full_name) VALUES ('Charlie@Example.COM', 'Charlie Brown');
"

# Verify CDC captured it
mongosh admin --eval "db.users.find().pretty()"
```

## Cleanup

```bash
syncflow pipeline delete pg-to-mongo
docker rm -f pg mongo
```
