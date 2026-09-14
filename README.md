# Walmart Advanced Software Engineering — Job Simulation (Forage)

This repo contains my work from Walmart Global Tech's Advanced Software
Engineering job simulation on [Forage](https://www.theforage.com/), completed
September 2026. The simulation covered four tasks spanning data structure
design, system architecture, database design, and data engineering.

## Tasks

### 1. [Power-of-Two Max Heap](./01-power-of-two-max-heap)
Implemented a generalized d-ary max-heap in Java where the branching factor
is `2^x` and `x` is a constructor parameter, supporting `insert` and
`popMax`. Includes self-tests covering very small (`x=0`) and very large
(`x=62`) branching factors, plus a benchmark harness. Along the way, found
and fixed an integer-overflow bug that only appeared with extreme branching
factors.

### 2. [UML Class Diagram — Reconfigurable Data Processor](./02-uml-class-diagram)
Designed the architecture for a dynamically reconfigurable data processing
pipeline: a processor that can switch between operating modes (dump,
passthrough, validate) and target databases (Postgres, Redis, Elastic) at
runtime. Applied the Strategy and Factory patterns and SOLID principles so
new modes or databases can be added without modifying existing code.

### 3. [Entity-Relationship Diagram — Pet Department Database](./03-database-erd)
Designed a normalized relational schema to consolidate Walmart's pet
department data: products (food/toys/apparel) modeled via table
inheritance, many-to-many relationships for product↔animal, customer
transactions, and shipments between Walmart locations.

### 4. [Database Population Script](./04-database-population-script)
Python/pandas/SQLite script that reconciles three spreadsheets with
inconsistent schemas into a single shipment database — including
reconstructing shipment quantities by grouping repeated line-item rows and
joining shipment origin/destination from a separate lookup sheet.

## Skills demonstrated
Data Structures · Algorithms · Software Architecture · UML Modeling ·
Database Design · SQL · Java · Python · Data Processing / ETL
