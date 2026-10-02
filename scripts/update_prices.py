#!/usr/bin/env python3
"""Refresh MacroDime's sourced food prices from public US datasets.

Writes MacroDime/Engine/SourcedPrices.swift for iOS and
android/core/src/main/kotlin/com/lungelo/macrodime/engine/SourcedPrices.kt for
Android, from the same table, so the two apps always quote the same prices. The
apps never fetch a price: this script runs at build time (by hand, or monthly in
CI), and the result is compiled in. That is what keeps the App Store answer
"Data Not Collected" and the Play Store answer "No data collected" true.

Sources
    BLS Average Price Data. Monthly U.S. city average retail prices, collected
    by the Bureau of Labor Statistics for the CPI. Read through the BLS Public
    Data API. With no key it uses API v1 (no registration, a small daily quota
    per IP address). Set BLS_API_KEY to use v2, which has a larger quota and
    also returns series titles, which this script then checks against the
    titles recorded below.

    USDA ERS Fruit and Vegetable Prices. Average retail prices per pound from
    retail scanner data, with a preparation yield factor, published for one
    year at a time. Carried forward to the latest BLS month with the CPI for
    fruits and vegetables (CUUR0000SAF113).

Only foods with a genuine match are listed. Everything else keeps the hand-set
estimate in FoodCatalog.swift, and the app says how many are estimates.

When ERS publishes a new year: update ERS_YEAR and the two ERS_FILES URLs from
https://www.ers.usda.gov/data-products/fruit-and-vegetable-prices and rerun.

Usage
    python3 scripts/update_prices.py                 # fetch and write both files
    python3 scripts/update_prices.py --dry-run       # fetch and print, write nothing
    python3 scripts/update_prices.py --sync-kotlin   # offline: Kotlin from the Swift file
    python3 scripts/update_prices.py --check-kotlin  # offline: fail if the two differ
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUTPUT = REPO / "MacroDime" / "Engine" / "SourcedPrices.swift"
KOTLIN_OUTPUT = (REPO / "android" / "core" / "src" / "main" / "kotlin" / "com" / "lungelo"
                 / "macrodime" / "engine" / "SourcedPrices.kt")
CATALOG = REPO / "MacroDime" / "Engine" / "FoodCatalog.swift"

BLS_V1 = "https://api.bls.gov/publicAPI/v1/timeseries/data/"
BLS_V2 = "https://api.bls.gov/publicAPI/v2/timeseries/data/"
CPI_FRUITS_AND_VEGETABLES = "CUUR0000SAF113"
USER_AGENT = "MacroDime-price-script/1.0 (+https://github.com/lungeloncanana324-blip/macrodime)"

ERS_YEAR = 2023
ERS_FILES = {
    "fruit": "https://www.ers.usda.gov/media/6210/all-fruits-average-prices-csv-format.csv",
    "vegetable": "https://www.ers.usda.gov/media/6240/all-vegetables-average-prices-csv-format.csv",
}

# A price older than this many months is treated as a discontinued series.
STALE_AFTER_MONTHS = 9

# Yield: the share of what is bought that ends up in the catalogue serving.
# AS_BOUGHT means the serving is weighed in the state it is sold in (dry rice,
# raw meat, frozen vegetables, potatoes with the skin on). ("ers", product,
# form) takes the preparation yield factor from that ERS row, so a banana pays
# for its peel.
AS_BOUGHT = 1.0

# (food id, BLS series, series title as BLS publishes it, unit, yield)
BLS_ITEMS = [
    ("eggs-large", "APU0000708111", "Eggs, grade A, large, per doz.", "dozen", AS_BOUGHT),
    ("ground-beef-80-20", "APU0000703112", "Ground beef, 100% beef, per lb.", "pound", AS_BOUGHT),
    ("chicken-breast", "APU0000FF1101", "Chicken breast, boneless, per lb.", "pound", AS_BOUGHT),
    ("sirloin-steak", "APU0000703613", "Steak, sirloin, USDA Choice, boneless, per lb.", "pound", AS_BOUGHT),
    ("white-rice", "APU0000701312", "Rice, white, long grain, uncooked, per lb.", "pound", AS_BOUGHT),
    ("dried-pasta", "APU0000701322", "Spaghetti and macaroni, per lb.", "pound", AS_BOUGHT),
    ("whole-wheat-bread", "APU0000702212", "Bread, whole wheat, pan, per lb.", "pound", AS_BOUGHT),
    ("potatoes", "APU0000712112", "Potatoes, white, per lb.", "pound", AS_BOUGHT),
    ("banana", "APU0000711211", "Bananas, per lb.", "pound", ("ers", "Bananas", "Fresh")),
    ("whole-milk", "APU0000709112", "Milk, fresh, whole, fortified, per gal.", "gallon", AS_BOUGHT),
    ("cheddar-block", "APU0000710212", "Cheddar cheese, natural, per lb.", "pound", AS_BOUGHT),
]

# (food id, ERS file, product, form, yield). ERS prices are all per pound.
ERS_ITEMS = [
    ("dried-lentils", "vegetable", "Lentils", "Dried", AS_BOUGHT),
    ("canned-black-beans", "vegetable", "Black beans", "Canned", "ers"),
    ("sweet-potato", "vegetable", "Sweet potatoes", "Fresh", AS_BOUGHT),
    ("avocado", "vegetable", "Avocados", "Fresh", "ers"),
    ("frozen-mixed-vegetables", "vegetable",
     "Mixed vegetables, carrots, peas, corn, and green beans", "Frozen", AS_BOUGHT),
    ("frozen-broccoli", "vegetable", "Broccoli", "Frozen", AS_BOUGHT),
    ("cabbage", "vegetable", "Cabbage, green", "Fresh", "ers"),
    ("carrots", "vegetable", "Carrots, raw whole", "Fresh", "ers"),
    ("onion", "vegetable", "Onions", "Fresh", "ers"),
    ("fresh-broccoli", "vegetable", "Broccoli heads", "Fresh", "ers"),
    ("baby-spinach", "vegetable", "Spinach, eaten raw", "Fresh", "ers"),
    ("bell-pepper", "vegetable", "Green peppers", "Fresh", "ers"),
    ("asparagus", "vegetable", "Asparagus", "Fresh", "ers"),
    ("apple", "fruit", "Apples", "Fresh", "ers"),
    ("frozen-berries", "fruit", "Berries, mixed", "Frozen", AS_BOUGHT),
    ("fresh-blueberries", "fruit", "Blueberries", "Fresh", "ers"),
]

GRAMS_PER_POUND = 453.59237
ML_PER_GALLON = 3785.411784


class PriceError(RuntimeError):
    """Anything that should stop the refresh rather than write a partial table."""


# ---------------------------------------------------------------- fetching

def http(url: str, body: dict | None = None) -> bytes:
    data = json.dumps(body).encode() if body is not None else None
    headers = {"User-Agent": USER_AGENT}
    if data is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers)
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def bls_series(ids: list[str], start_year: int, end_year: int) -> dict[str, dict]:
    """Returns {series id: series json}. Fails loudly on any BLS message."""
    key = os.environ.get("BLS_API_KEY", "").strip()
    url, batch = (BLS_V2, 50) if key else (BLS_V1, 25)
    found: dict[str, dict] = {}
    for start in range(0, len(ids), batch):
        body = {"seriesid": ids[start:start + batch],
                "startyear": str(start_year), "endyear": str(end_year)}
        if key:
            body.update(registrationkey=key, catalog=True)
        reply = json.loads(http(url, body))
        if reply.get("status") != "REQUEST_SUCCEEDED":
            raise PriceError(f"BLS refused the request: {reply.get('message')}")
        for message in reply.get("message", []):
            # "No Data Available for Series X Year: Y" is expected for a CPI
            # year range; anything about quotas or keys is not.
            if "threshold" in message.lower() or "key" in message.lower():
                raise PriceError(f"BLS: {message}. Set BLS_API_KEY (free) to raise the quota.")
        for series in reply["Results"]["series"]:
            found[series["seriesID"]] = series
    return found


def monthly(series: dict) -> list[dict]:
    """Monthly observations with a value, newest first."""
    rows = [row for row in series.get("data", [])
            if re.fullmatch(r"M(0[1-9]|1[0-2])", row["period"]) and row["value"] not in ("-", "")]
    return sorted(rows, key=lambda row: (row["year"], row["period"]), reverse=True)


def period_name(row: dict) -> str:
    return f"{row['periodName']} {row['year']}"


def months_ago(row: dict, today: dt.date) -> int:
    return (today.year - int(row["year"])) * 12 + today.month - int(row["period"][1:])


def ers_rows() -> dict[tuple[str, str, str], dict]:
    """{(file, product, form): row} from the two ERS CSV files."""
    table = {}
    for name, url in ERS_FILES.items():
        text = http(url).decode("utf-8-sig")
        reader = csv.reader(io.StringIO(text))
        header = [cell.strip() for cell in next(reader)]
        for cells in reader:
            if not cells or not cells[0].strip():
                continue
            row = dict(zip(header, (cell.strip() for cell in cells)))
            product = row.get("Fruit") or row.get("Vegetable")
            table[(name, product, row["Form"])] = row
    return table


# ---------------------------------------------------------------- building

def build(today: dt.date) -> dict:
    ers = ers_rows()

    def ers_row(file: str, product: str, form: str) -> dict:
        row = ers.get((file, product, form))
        if row is None:
            raise PriceError(f"ERS has no row for {product} ({form}) in the {file} file")
        if row["AverageRetailPriceUnitOfMeasure"] != "per pound":
            raise PriceError(f"ERS {product} ({form}) is priced "
                             f"{row['AverageRetailPriceUnitOfMeasure']}, not per pound")
        return row

    def resolve_yield(spec, row: dict | None) -> float:
        if spec == "ers":
            return float(row["PreparationYieldFactor"])
        if isinstance(spec, tuple):
            return float(ers_row("fruit", spec[1], spec[2])["PreparationYieldFactor"])
        return float(spec)

    # BLS: one request for the items, one for the CPI (which needs ERS_YEAR).
    item_ids = [series for _, series, _, _, _ in BLS_ITEMS]
    items = bls_series(item_ids, today.year - 1, today.year)
    cpi = bls_series([CPI_FRUITS_AND_VEGETABLES], ERS_YEAR, today.year)[CPI_FRUITS_AND_VEGETABLES]

    cpi_rows = monthly(cpi)
    base_months = [float(row["value"]) for row in cpi_rows if row["year"] == str(ERS_YEAR)]
    if len(base_months) != 12:
        raise PriceError(f"CPI {CPI_FRUITS_AND_VEGETABLES} has {len(base_months)} months for {ERS_YEAR}, need 12")
    cpi_base = sum(base_months) / 12
    cpi_latest = cpi_rows[0]
    cpi_factor = float(cpi_latest["value"]) / cpi_base
    latest_period = period_name(cpi_latest)

    entries = []
    for food_id, series_id, title, unit, yield_spec in BLS_ITEMS:
        series = items.get(series_id)
        rows = monthly(series) if series else []
        if not rows:
            raise PriceError(f"BLS returned no monthly data for {series_id} ({food_id})")
        latest = rows[0]
        if months_ago(latest, today) > STALE_AFTER_MONTHS:
            raise PriceError(f"{series_id} ({food_id}) last reported {period_name(latest)}; "
                             "the series looks discontinued, remove or remap it")
        # v2 only: the catalog carries the published title. A series id that
        # BLS has reassigned to a different item must not slip through.
        catalog = series.get("catalog") or {}
        published = " ".join(str(value) for value in catalog.values())
        if published and title not in published:
            raise PriceError(f"{series_id} is published as '{published[:120]}', expected '{title}'")
        entries.append({
            "food_id": food_id,
            "source": {"kind": "bls", "series": series_id, "item": title},
            "period": period_name(latest),
            "dollars": float(latest["value"]),
            "unit": unit,
            "yield": resolve_yield(yield_spec, None),
            "note": f"BLS {series_id}, {period_name(latest)}: ${latest['value']} per {unit}",
        })

    for food_id, file, product, form, yield_spec in ERS_ITEMS:
        row = ers_row(file, product, form)
        raw = float(row["AverageRetailPrice"])
        entries.append({
            "food_id": food_id,
            "source": {"kind": "ers", "product": product, "form": form, "year": ERS_YEAR},
            "period": latest_period,
            "dollars": raw * cpi_factor,
            "unit": "pound",
            "yield": resolve_yield(yield_spec, row),
            "note": f"ERS {ERS_YEAR} {product} ({form}): ${raw:.4f}/lb x CPI {cpi_factor:.4f}",
        })

    ids = [entry["food_id"] for entry in entries]
    duplicates = {food_id for food_id in ids if ids.count(food_id) > 1}
    if duplicates:
        raise PriceError(f"food listed twice: {sorted(duplicates)}")

    return {
        "entries": sorted(entries, key=lambda entry: entry["food_id"]),
        "latest_period": latest_period,
        "cpi": {"series": CPI_FRUITS_AND_VEGETABLES, "base": cpi_base,
                "latest": float(cpi_latest["value"]), "factor": cpi_factor},
        "generated_on": today.isoformat(),
        "ers_year": ERS_YEAR,
    }


# ---------------------------------------------------------------- output

def swift(table: dict) -> str:
    lines = [
        "//",
        "//  SourcedPrices.swift",
        "//  MacroDime",
        "//",
        f"//  GENERATED by scripts/update_prices.py on {table['generated_on']}. Do not edit:",
        "//  rerun the script. See PriceTable.swift for what the fields mean and how a",
        "//  per unit price becomes the cost of one serving.",
        "//",
        f"//  ERS prices are {ERS_YEAR} averages carried to {table['latest_period']} with CPI",
        f"//  {table['cpi']['series']} ({table['cpi']['base']:.3f} for {ERS_YEAR}, "
        f"{table['cpi']['latest']:.3f} now, x{table['cpi']['factor']:.4f}).",
        "//",
        "",
        "import Foundation",
        "",
        "extension PriceTable {",
        "",
        f'    static let generatedOn = "{table["generated_on"]}"',
        "",
        "    /// The newest month any price in the table describes.",
        f'    static let latestPeriod = "{table["latest_period"]}"',
        "",
        "    /// The year of the USDA ERS fruit and vegetable prices.",
        f"    static let ersYear = {ERS_YEAR}",
        "",
        "    static let entries: [SourcedPrice] = [",
    ]
    for entry in table["entries"]:
        lines += [
            f"        // {entry['note']}",
            "        SourcedPrice(",
            f'            foodID: "{entry["food_id"]}",',
            f"            source: {swift_source(entry['source'])},",
            f'            period: "{entry["period"]}",',
            f"            dollarsPerUnit: {entry['dollars']:.4f},",
            f"            unit: .{entry['unit']},",
            f"            yield: {entry['yield']:.4f}",
            "        ),",
        ]
    lines += ["    ]", "}", ""]
    return "\n".join(lines)


def swift_source(source: dict) -> str:
    if source["kind"] == "bls":
        return f'.bls(series: "{source["series"]}", item: "{source["item"]}")'
    return f'.ers(product: "{source["product"]}", form: "{source["form"]}", year: {source["year"]})'


def kotlin_source(source: dict) -> str:
    if source["kind"] == "bls":
        return f'PriceSource.Bls(series = "{source["series"]}", item = "{source["item"]}")'
    return (f'PriceSource.Ers(product = "{source["product"]}", form = "{source["form"]}", '
            f'year = {source["year"]})')


def kotlin(table: dict) -> str:
    lines = [
        "/*",
        " * SourcedPrices.kt",
        " * MacroDime",
        " *",
        f" * GENERATED by scripts/update_prices.py on {table['generated_on']}. Do not edit:",
        " * rerun the script. See PriceTable.kt for what the fields mean and how a per",
        " * unit price becomes the cost of one serving. The iOS app compiles the same",
        " * table from MacroDime/Engine/SourcedPrices.swift.",
        " *",
        f" * ERS prices are {table['ers_year']} averages carried to {table['latest_period']} with CPI",
        f" * {table['cpi']['series']} ({table['cpi']['base']:.3f} for {table['ers_year']}, "
        f"{table['cpi']['latest']:.3f} now, x{table['cpi']['factor']:.4f}).",
        " */",
        "package com.lungelo.macrodime.engine",
        "",
        "internal object SourcedPrices {",
        "",
        f'    const val GENERATED_ON = "{table["generated_on"]}"',
        "",
        "    /** The newest month any price in the table describes. */",
        f'    const val LATEST_PERIOD = "{table["latest_period"]}"',
        "",
        "    /** The year of the USDA ERS fruit and vegetable prices. */",
        f"    const val ERS_YEAR = {table['ers_year']}",
        "",
        "    val ENTRIES: List<SourcedPrice> = listOf(",
    ]
    for entry in table["entries"]:
        lines += [
            f"        // {entry['note']}",
            "        SourcedPrice(",
            f'            foodId = "{entry["food_id"]}",',
            f"            source = {kotlin_source(entry['source'])},",
            f'            period = "{entry["period"]}",',
            f"            dollarsPerUnit = {entry['dollars']:.4f},",
            f"            unit = PriceUnit.{entry['unit'].capitalize()},",
            f"            servingYield = {entry['yield']:.4f},",
            "        ),",
        ]
    lines += ["    )", "}", ""]
    return "\n".join(lines)


def table_from_swift(path: Path = OUTPUT) -> dict:
    """The committed Swift table, read back, so the Kotlin one can be rebuilt offline."""
    text = path.read_text(encoding="utf-8")

    def one(pattern: str) -> str:
        match = re.search(pattern, text)
        if match is None:
            raise PriceError(f"{path.name}: cannot find {pattern!r}")
        return match.group(1)

    cpi = re.search(r"CPI\n//\s+(\S+) \(([\d.]+) for (\d+), ([\d.]+) now, x([\d.]+)\)", text)
    if cpi is None:
        raise PriceError(f"{path.name}: cannot find the CPI line")
    entries = []
    pattern = r"        // (.*?)\n        SourcedPrice\((.*?)\n        \),"
    for note, body in re.findall(pattern, text, re.S):
        fields = dict(re.findall(r"(\w+): (.*?),?\n", body + "\n"))
        source = fields["source"]
        bls = re.fullmatch(r'\.bls\(series: "(.*?)", item: "(.*?)"\)', source)
        ers = re.fullmatch(r'\.ers\(product: "(.*?)", form: "(.*?)", year: (\d+)\)', source)
        if bls:
            parsed = {"kind": "bls", "series": bls.group(1), "item": bls.group(2)}
        elif ers:
            parsed = {"kind": "ers", "product": ers.group(1), "form": ers.group(2), "year": int(ers.group(3))}
        else:
            raise PriceError(f"{path.name}: unreadable source {source}")
        entries.append({
            "food_id": fields["foodID"].strip('"'),
            "source": parsed,
            "period": fields["period"].strip('"'),
            "dollars": float(fields["dollarsPerUnit"]),
            "unit": fields["unit"].lstrip("."),
            "yield": float(fields["yield"]),
            "note": note,
        })
    if not entries:
        raise PriceError(f"{path.name}: no entries found")
    return {
        "entries": entries,
        "latest_period": one(r'static let latestPeriod = "(.*?)"'),
        "generated_on": one(r'static let generatedOn = "(.*?)"'),
        "ers_year": int(one(r"static let ersYear = (\d+)")),
        "cpi": {"series": cpi.group(1), "base": float(cpi.group(2)),
                "latest": float(cpi.group(4)), "factor": float(cpi.group(5))},
    }


def catalogue_preview(table: dict) -> list[str]:
    """Per-serving cost next to the hand-set estimate, for a human to review."""
    source = CATALOG.read_text(encoding="utf-8")
    foods = {}
    for block in re.findall(r"FoodSnapshot\((.*?)\n        \)", source, re.S):
        fields = dict(re.findall(r'(\w+):\s*("[^"]*"|[\d.]+)', block))
        if "id" in fields:
            foods[fields["id"].strip('"')] = fields
    report = [f"{'food':26} {'estimate':>8} {'sourced':>8}  ratio  source"]
    for entry in table["entries"]:
        food = foods.get(entry["food_id"])
        if food is None:
            raise PriceError(f"{entry['food_id']} is not in FoodCatalog.swift")
        grams = float(food["servingGrams"])
        text = food["servingDescription"].strip('"')
        if entry["unit"] == "pound":
            quantity = grams / entry["yield"] / GRAMS_PER_POUND
        elif entry["unit"] == "gallon":
            quantity = float(text.split()[0]) / entry["yield"] / ML_PER_GALLON
        else:
            quantity = float(text.split()[0]) / entry["yield"] / 12
        cost = entry["dollars"] * quantity
        estimate = float(food["costPerServing"])
        report.append(f"{entry['food_id']:26} {estimate:8.2f} {cost:8.2f}  {cost / estimate:5.2f}  {entry['note']}")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--dry-run", action="store_true", help="print the result, write nothing")
    parser.add_argument("--sync-kotlin", action="store_true",
                        help="rebuild the Kotlin table from the committed Swift one, offline")
    parser.add_argument("--check-kotlin", action="store_true",
                        help="fail if the Kotlin table differs from the Swift one, offline")
    args = parser.parse_args()

    if args.sync_kotlin or args.check_kotlin:
        try:
            expected = kotlin(table_from_swift())
        except (PriceError, OSError, KeyError, ValueError) as error:
            print(f"error: {error}", file=sys.stderr)
            return 1
        if args.check_kotlin:
            current = KOTLIN_OUTPUT.read_text(encoding="utf-8") if KOTLIN_OUTPUT.exists() else ""
            if current != expected:
                print(f"error: {KOTLIN_OUTPUT.relative_to(REPO)} has drifted from "
                      f"{OUTPUT.relative_to(REPO)}; run scripts/update_prices.py --sync-kotlin",
                      file=sys.stderr)
                return 1
            print("Kotlin and Swift price tables match.")
            return 0
        KOTLIN_OUTPUT.write_text(expected, encoding="utf-8", newline="\n")
        print(f"wrote {KOTLIN_OUTPUT.relative_to(REPO)}")
        return 0

    try:
        table = build(dt.date.today())
        preview = catalogue_preview(table)
    except (PriceError, OSError, KeyError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    print("\n".join(preview))
    print(f"\n{len(table['entries'])} sourced prices, latest {table['latest_period']}")
    if args.dry_run:
        return 0
    OUTPUT.write_text(swift(table), encoding="utf-8", newline="\n")
    print(f"wrote {OUTPUT.relative_to(REPO)}")
    KOTLIN_OUTPUT.write_text(kotlin(table), encoding="utf-8", newline="\n")
    print(f"wrote {KOTLIN_OUTPUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
