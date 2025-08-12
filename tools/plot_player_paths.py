import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from influxdb_client import InfluxDBClient

INFLUX_URL = ""
INFLUX_TOKEN = ""
INFLUX_ORG = "minecraft-tracecraft"
BUCKET = "minecraft_metrics"

START = "2025-06-27T00:05:55Z"
STOP = "2025-06-27T01:53:12Z"
TELEPORT_THRESHOLD = 300.0

flux_query = f'''from(bucket:"{BUCKET}")
  |> range(start: {START}, stop: {STOP})
  |> filter(fn: (r) => r["_measurement"] == "player_path")
  |> filter(fn: (r) => r["_field"] == "x" or r["_field"] == "z")
  |> aggregateWindow(every:1s, fn:mean, createEmpty:false)
  |> pivot(
       rowKey:["_time"],
       columnKey:["_field"],
       valueColumn:"_value"
     )
'''

client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
query_api = client.query_api()
result = query_api.query_data_frame(flux_query)

df = pd.concat([d for d in result if not d.empty], ignore_index=True) if isinstance(result, list) else result

if df.index.name == "_time":
    df = df.reset_index(drop=True)

df = df.loc[:, ~df.columns.duplicated()]

if "_time" not in df.columns:
    raise RuntimeError("Flux query did not yield a `_time` column!")

df = df.sort_values("_time")

unique_players = df["player"].unique()
anonym_map = {pid: f"Player {i+1}" for i, pid in enumerate(unique_players)}

plt.figure(figsize=(12, 9))

plt.rcParams['font.size'] = 11

for player_id, sub in df.groupby("player", sort=False):
    sub = sub.sort_values("_time")
    dx = sub["x"].diff().fillna(0)
    dz = sub["z"].diff().fillna(0)
    dist = np.hypot(dx, dz)
    breaks = (dist > TELEPORT_THRESHOLD).astype(int)
    sub = sub.assign(segment=breaks.cumsum())

    label = anonym_map.get(player_id, player_id)
    for (_, seg), color in zip(sub.groupby("segment"), plt.cm.tab10.colors):
        if len(seg) < 2:
            continue
        plt.plot(seg["x"], seg["z"], linewidth=1, label=label)

handles, labels = plt.gca().get_legend_handles_labels()
by_label = dict(zip(labels, handles))

plt.xlabel("X Coordinate")
plt.ylabel("Z Coordinate")
plt.legend(by_label.values(), by_label.keys(), title="Player", bbox_to_anchor=(1.05, 1), loc="upper left")
plt.axis("equal")
plt.grid(True)
plt.tight_layout()

output_file = "./output/minecraft_player_paths.pdf"
plt.savefig(output_file, format='pdf')
plt.close()

print(f"Graph exported to {output_file}")
