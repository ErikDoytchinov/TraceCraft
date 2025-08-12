#!/usr/bin/env python3
"""
Inspect InfluxDB schema for a given bucket:
  - list measurements
  - list field keys per measurement
  - list tag keys per measurement

Requires:
    pip install influxdb-client pandas
"""

import os
import warnings
from influxdb_client import InfluxDBClient
from influxdb_client.client.warnings import MissingPivotFunction
import pandas as pd

warnings.simplefilter("ignore", MissingPivotFunction)

INFLUX_URL = ""
INFLUX_TOKEN = ""
INFLUX_ORG = "minecraft-tracecraft"
BUCKET = "minecraft_metrics"

client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
query_api = client.query_api()


def run_schema_query(flux: str) -> pd.DataFrame:
    df = query_api.query_data_frame(flux)
    if isinstance(df, list):
        df = pd.concat(df, ignore_index=True)
    return df


print(f"Schema for bucket: {BUCKET}\n{'=' * 40}\n")

# 1) list measurements
flux_meas = f'''
import "influxdata/influxdb/schema"
schema.measurements(bucket: "{BUCKET}")
'''
meas_df = run_schema_query(flux_meas)
measurements = meas_df["_value"].unique().tolist()

print("Measurements:")
for m in measurements:
    print(f"  • {m}")
print()

# 2) for each measurement, list field keys
for m in measurements:
    flux_fields = f'''
import "influxdata/influxdb/schema"
schema.fieldKeys(
  bucket: "{BUCKET}",
  predicate: (r) => r["_measurement"] == "{m}"
)
'''
    fields_df = run_schema_query(flux_fields)
    fields = fields_df["_value"].unique().tolist()
    print(f"Fields in '{m}':")
    for f in fields:
        print(f"  - {f}")
    print()

# 3) for each measurement, list tag keys
for m in measurements:
    flux_tags = f'''
import "influxdata/influxdb/schema"
schema.tagKeys(
  bucket: "{BUCKET}",
  predicate: (r) => r["_measurement"] == "{m}"
)
'''
    tags_df = run_schema_query(flux_tags)
    tags = tags_df["_value"].unique().tolist()
    print(f"Tags in '{m}':")
    for t in tags:
        print(f"  - {t}")
    print()
