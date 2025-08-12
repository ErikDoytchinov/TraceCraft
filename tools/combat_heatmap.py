#!/usr/bin/env python3
import os

import matplotlib.pyplot as plt
import pandas as pd
from influxdb_client import InfluxDBClient

INFLUX_URL = ""
INFLUX_TOKEN = ""
INFLUX_ORG = "minecraft-tracecraft"
BUCKET = "minecraft_metrics"

START = "2025-06-27T02:05:55+02:00"
STOP = "2025-06-27T03:53:12+02:00"

client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
query_api = client.query_api()


def run_flux(flux: str) -> pd.DataFrame:
    result = query_api.query_data_frame(flux)
    if isinstance(result, list):
        df = pd.concat([d for d in result if not d.empty], ignore_index=True)
    else:
        df = result
    # drop duplicate columns from pivot warnings
    df = df.loc[:, ~df.columns.duplicated()]
    return df


# ─── PART A: Combat events per minute ───────────────────────────────────────────
flux_combat = f'''
from(bucket: "{BUCKET}")
  |> range(start: {START}, stop: {STOP})
  |> filter(fn: (r) => r["_measurement"] == "combat_event" and r["_field"] == "damage")
  |> pivot(
       rowKey:   ["_time"],
       columnKey: ["_field"],
       valueColumn: "_value"
     )
'''

df_combat = run_flux(flux_combat)
df_combat["_time"] = pd.to_datetime(df_combat["_time"])
df_combat.rename(columns={"_value": "damage"}, inplace=True)

# aggregate per‐minute
df_combat["minute"] = df_combat["_time"].dt.floor("T")
combat_stats = df_combat.groupby("minute").agg(
    event_count=("damage", "size"),
    total_damage=("damage", "sum")
).reset_index()

# Plot time series
fig, ax1 = plt.subplots(figsize=(10, 4))
ax1.plot(combat_stats["minute"], combat_stats["event_count"], label="Events/minute")
ax1.set_xlabel("Time")
ax1.set_ylabel("Combat Events per Minute")
ax1.grid(True)

ax2 = ax1.twinx()
ax2.plot(combat_stats["minute"], combat_stats["total_damage"], linestyle="--", label="Damage/minute")
ax2.set_ylabel("Damage per Minute")

# combine legends
lines, labels = ax1.get_lines() + ax2.get_lines(), [l.get_label() for l in ax1.get_lines() + ax2.get_lines()]
ax1.legend(lines, labels, loc="upper left")
plt.title("Combat Events & Damage Rate Over Time")
plt.tight_layout()

# ─── PART B: Death Locations Heatmap ────────────────────────────────────────────
flux_deaths = f'''
from(bucket: "{BUCKET}")
  |> range(start: {START}, stop: {STOP})
  |> filter(fn: (r) => r["_measurement"] == "player_death" and (r["_field"] == "x" or r["_field"] == "z"))
  |> pivot(
       rowKey:   ["_time"],
       columnKey: ["_field"],
       valueColumn: "_value"
     )
'''

df_deaths = run_flux(flux_deaths)
df_deaths["_time"] = pd.to_datetime(df_deaths["_time"])

# count deaths by integer block coordinates
# (if floats, they'll group narrowly; adjust rounding if needed)
death_counts = (
    df_deaths
    .assign(x=lambda d: d["x"].round(0), z=lambda d: d["z"].round(0))
    .groupby(["x", "z"])
    .size()
    .reset_index(name="count")
)

# scatter plot
fig, ax = plt.subplots(figsize=(6, 6))
sizes = death_counts["count"] * 20  # scale marker sizes as needed
ax.scatter(death_counts["x"], death_counts["z"], s=sizes, alpha=0.6)
ax.set_xlabel("X Coordinate")
ax.set_ylabel("Z Coordinate")
ax.set_title("Death Locations (size ∝ # of deaths)")
ax.set_aspect("equal", "box")
ax.grid(True)
plt.tight_layout()

plt.show()
