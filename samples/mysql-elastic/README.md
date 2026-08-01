# MySQL → Elasticsearch Synchronization

This sample shows how to synchronize a MySQL `products` table to Elasticsearch for full-text search.

## Prerequisites

```bash
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=pass -p 3306:3306 mysql:8
docker run -d --name elastic -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8
```

## Setup

```bash
# Create source data
mysql -h localhost -u root -p pass -e "
  CREATE DATABASE shop;
  USE shop;
  CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    price DECIMAL(10,2),
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  INSERT INTO products (name, description, price, category) VALUES
    ('Wireless Mouse', 'Ergonomic wireless mouse', 29.99, 'Electronics'),
    ('Mechanical Keyboard', 'RGB mechanical keyboard', 89.99, 'Electronics');
"

# Create connections
syncflow connection create --name mysql-source --type MYSQL \
  --host localhost --port 3306 --database shop --user root --password pass

# Create pipeline
syncflow pipeline create mysql-to-es \
  --source-connection mysql-source --source-schema shop --source-table products

# Deploy
syncflow pipeline deploy mysql-to-es
```

## Verify

```bash
curl -s http://localhost:9200/products/_search?q=wireless | jq '.hits.hits[]._source'
```
