"""
populate_database.py

Loads the Walmart shipping department's disparate spreadsheet data into
shipment_database.db.

Spreadsheet 0 (shipping_data_0.csv) is self-contained: each row already
represents one full shipment of a single product, complete with its own
quantity, origin, and destination.

Spreadsheets 1 and 2 are two halves of the same data:
  - shipping_data_1.csv has one row per unit of product in a shipment
    (repeated rows, no explicit quantity, no origin/destination), keyed by
    shipment_identifier.
  - shipping_data_2.csv has one row per shipment_identifier giving that
    shipment's origin and destination.

They are joined on shipment_identifier, and the quantity of each product in
a shipment is derived by counting how many rows in spreadsheet 1 share the
same (shipment_identifier, product) pair.

Usage:
    python populate_database.py [--db shipment_database.db] [--data-dir data]
"""

import argparse
import sqlite3
from pathlib import Path

import pandas as pd


def get_or_create_product_id(cursor: sqlite3.Cursor, cache: dict, name: str) -> int:
    """Return product.id for `name`, inserting a new product row the first
    time it's seen. `cache` avoids a redundant SELECT for repeated names."""
    if name in cache:
        return cache[name]

    cursor.execute("SELECT id FROM product WHERE name = ?", (name,))
    row = cursor.fetchone()
    if row is not None:
        product_id = row[0]
    else:
        cursor.execute("INSERT INTO product (name) VALUES (?)", (name,))
        product_id = cursor.lastrowid

    cache[name] = product_id
    return product_id


def insert_shipment(
    cursor: sqlite3.Cursor,
    product_id_cache: dict,
    product_name: str,
    quantity: int,
    origin: str,
    destination: str,
) -> None:
    """Munge one logical shipment record into the schema and insert it."""
    product_id = get_or_create_product_id(cursor, product_id_cache, product_name)
    cursor.execute(
        """
        INSERT INTO shipment (product_id, quantity, origin, destination)
        VALUES (?, ?, ?, ?)
        """,
        (product_id, int(quantity), origin, destination),
    )


def load_self_contained_shipments(
    cursor: sqlite3.Cursor, product_id_cache: dict, path: Path
) -> int:
    """Spreadsheet 0: one row is already one complete shipment record."""
    df = pd.read_csv(path)
    for row in df.itertuples(index=False):
        insert_shipment(
            cursor,
            product_id_cache,
            product_name=row.product,
            quantity=row.product_quantity,
            origin=row.origin_warehouse,
            destination=row.destination_store,
        )
    return len(df)


def load_split_shipments(
    cursor: sqlite3.Cursor,
    product_id_cache: dict,
    products_path: Path,
    locations_path: Path,
) -> int:
    """Spreadsheets 1 & 2: reassemble one shipment record per
    (shipment_identifier, product) pair. Quantity is the number of matching
    rows in spreadsheet 1; origin/destination are looked up from spreadsheet 2."""
    products_df = pd.read_csv(products_path)
    locations_df = pd.read_csv(locations_path)

    # shipment_identifier -> (origin_warehouse, destination_store)
    locations = locations_df.set_index("shipment_identifier")[
        ["origin_warehouse", "destination_store"]
    ]

    # Collapse repeated (shipment_identifier, product) rows into a quantity.
    grouped = (
        products_df.groupby(["shipment_identifier", "product"])
        .size()
        .reset_index(name="quantity")
    )

    for row in grouped.itertuples(index=False):
        origin, destination = locations.loc[row.shipment_identifier]
        insert_shipment(
            cursor,
            product_id_cache,
            product_name=row.product,
            quantity=row.quantity,
            origin=origin,
            destination=destination,
        )
    return len(grouped)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Populate shipment_database.db from the shipping CSVs."
    )
    parser.add_argument("--db", default="shipment_database.db", type=Path)
    parser.add_argument("--data-dir", default="data", type=Path)
    args = parser.parse_args()

    connection = sqlite3.connect(args.db)
    cursor = connection.cursor()
    product_id_cache: dict = {}

    try:
        count_0 = load_self_contained_shipments(
            cursor, product_id_cache, args.data_dir / "shipping_data_0.csv"
        )
        count_1 = load_split_shipments(
            cursor,
            product_id_cache,
            args.data_dir / "shipping_data_1.csv",
            args.data_dir / "shipping_data_2.csv",
        )
        connection.commit()
        print(f"Inserted {count_0} shipments from shipping_data_0.csv")
        print(
            f"Inserted {count_1} shipments from "
            "shipping_data_1.csv + shipping_data_2.csv"
        )
        print(f"Total distinct products: {len(product_id_cache)}")
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()
