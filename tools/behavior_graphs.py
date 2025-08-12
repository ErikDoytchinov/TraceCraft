#!/usr/bin/env python3
# Unified TraceCraft Graphs
# Generates three PDFs with consistent styling from InfluxDB:
#   - walk_distances_ccdf_pareto_fit.pdf
#   - walk_durations_log_normal_fit.pdf
#   - pause_durations_pareto_fit.pdf

import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from influxdb_client import InfluxDBClient
from scipy.stats import lognorm

INFLUX_URL = ""
INFLUX_TOKEN = ""
INFLUX_ORG = "minecraft-tracecraft"
BUCKET = "minecraft_metrics"
START = "2025-06-27T02:05:55+02:00"
STOP = "2025-06-27T03:53:12+02:00"

# ----------------------------
# Plotting style (unified)
# ----------------------------
plt.rcParams.update({
    "figure.dpi": 300,
    "savefig.dpi": 300,
    "font.size": 9,
    "axes.labelsize": 9,
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "legend.fontsize": 8,
    "figure.constrained_layout.use": True,
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.02,
})

FIGSIZE = (3.2, 2.3)

SPEED_THRESHOLD = 0.1  # blocks/sec: lower bound for "walking"
MAX_SPEED = 10.0       # blocks/sec: upper bound to reject teleports/artifacts


def query_player_path() -> pd.DataFrame:
    '''Query player movement from InfluxDB and return a tidy DataFrame sorted by player and time.'''
    client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
    query_api = client.query_api()
    flux = f'''
        from(bucket: "{BUCKET}")
          |> range(start: {START}, stop: {STOP})
          |> filter(fn: (r) => r._measurement == "player_path")
          |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
          |> sort(columns: ["_time"])
    '''
    df = query_api.query_data_frame(flux)
    if isinstance(df, list):
        df = pd.concat(df, ignore_index=True)
    # Ensure proper types/sorting
    df["_time"] = pd.to_datetime(df["_time"])
    df = df.sort_values(by=["player", "_time"]).reset_index(drop=True)
    return df


def compute_walk_distances(df: pd.DataFrame) -> np.ndarray:
    '''Accumulate contiguous walking distances per player, splitting on pauses/teleports.'''
    out = []
    for _, group in df.groupby("player"):
        group = group.reset_index(drop=True)
        current = 0.0
        for i in range(1, len(group)):
            x1, y1, z1 = group.loc[i-1, ["x", "y", "z"]]
            x2, y2, z2 = group.loc[i,   ["x", "y", "z"]]
            dist = float(np.sqrt((x2 - x1)**2 + (y2 - y1)**2 + (z2 - z1)**2))
            t1, t2 = group.loc[i-1, "_time"], group.loc[i, "_time"]
            dt = (t2 - t1).total_seconds()
            if dt <= 0:
                continue
            speed = dist / dt
            if speed > MAX_SPEED:
                if current > 0:
                    out.append(current)
                    current = 0.0
                continue
            if speed > SPEED_THRESHOLD:
                current += dist
            else:
                if current > 0:
                    out.append(current)
                    current = 0.0
        if current > 0:
            out.append(current)
    arr = np.asarray(out, dtype=float)
    return arr[arr > 0]


def compute_walk_durations(df: pd.DataFrame) -> np.ndarray:
    '''Accumulate contiguous walking durations (seconds) per player, splitting on pauses/teleports.'''
    out = []
    for _, group in df.groupby("player"):
        group = group.reset_index(drop=True)
        current = 0.0
        for i in range(1, len(group)):
            x1, y1, z1 = group.loc[i-1, ["x", "y", "z"]]
            x2, y2, z2 = group.loc[i,   ["x", "y", "z"]]
            dist = float(np.sqrt((x2 - x1)**2 + (y2 - y1)**2 + (z2 - z1)**2))
            t1, t2 = group.loc[i-1, "_time"], group.loc[i, "_time"]
            dt = (t2 - t1).total_seconds()
            if dt <= 0:
                continue
            speed = dist / dt
            if speed > MAX_SPEED:
                if current > 0:
                    out.append(current)
                    current = 0.0
                continue
            if speed > SPEED_THRESHOLD:
                current += dt
            else:
                if current > 0:
                    out.append(current)
                    current = 0.0
        if current > 0:
            out.append(current)
    arr = np.asarray(out, dtype=float)
    return arr[arr > 0]


def compute_pause_durations(df: pd.DataFrame) -> np.ndarray:
    '''Compute contiguous pause durations (seconds) per player where speed <= threshold and not teleport.'''
    out = []
    for _, group in df.groupby("player"):
        group = group.reset_index(drop=True)
        pause_start = None
        for i in range(1, len(group)):
            x1, y1, z1 = group.loc[i-1, ["x", "y", "z"]]
            x2, y2, z2 = group.loc[i,   ["x", "y", "z"]]
            dist = float(np.sqrt((x2 - x1)**2 + (y2 - y1)**2 + (z2 - z1)**2))
            t1, t2 = group.loc[i-1, "_time"], group.loc[i, "_time"]
            dt = (t2 - t1).total_seconds()
            if dt <= 0:
                continue
            speed = dist / dt
            if (speed <= SPEED_THRESHOLD) and (speed <= MAX_SPEED):
                if pause_start is None:
                    pause_start = t1
            else:
                if pause_start is not None:
                    dur = (t1 - pause_start).total_seconds()
                    if dur > 0:
                        out.append(dur)
                    pause_start = None
        # trailing pause
        if pause_start is not None:
            dur = (group.loc[len(group) - 1, "_time"] - pause_start).total_seconds()
            if dur > 0:
                out.append(dur)
    arr = np.asarray(out, dtype=float)
    return arr[arr > 0]


def plot_ccdf_pareto(distances: np.ndarray, outfile: str) -> None:
    '''Plot empirical CCDF and Pareto fit on log-log axes.'''
    if len(distances) == 0:
        raise ValueError("No walking distances computed; nothing to plot.")
    x = np.sort(distances)
    n = len(x)
    emp_ccdf = 1.0 - np.arange(1, n + 1) / n

    # Pareto MLE with x_min = min(x)
    x_min = x.min()
    alpha = 1.0 + n / np.sum(np.log(x / x_min))
    pareto_ccdf = (x / x_min) ** (-alpha)

    fig = plt.figure(figsize=FIGSIZE)
    plt.plot(x, emp_ccdf, drawstyle="steps-post", label="Empirical CCDF")
    plt.plot(x, pareto_ccdf, "--", label=f"Pareto fit (α={alpha:.2f})")
    plt.xscale("log")
    plt.yscale("log")
    plt.xlabel("Walk distance (blocks)")
    plt.ylabel("CCDF")
    plt.grid(True, which="both", linestyle="--", linewidth=0.5)
    plt.legend(frameon=False)
    fig.savefig(outfile, bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)


def plot_walk_duration_lognormal(durations: np.ndarray, outfile: str) -> None:
    '''Plot histogram of walking durations with a Log-Normal fit (x on log scale).'''
    if len(durations) == 0:
        raise ValueError("No walking durations computed; nothing to plot.")
    # Fit log-normal via log transform
    logd = np.log(durations)
    mu = float(logd.mean())
    sigma = float(logd.std())

    x_plot = np.linspace(logd.min(), logd.max(), 200)
    pdf_vals = lognorm.pdf(np.exp(x_plot), s=sigma, scale=np.exp(mu))

    fig = plt.figure(figsize=FIGSIZE)
    plt.hist(durations, bins=50, density=True, alpha=0.6, label="Empirical PDF")
    plt.plot(np.exp(x_plot), pdf_vals, "-", linewidth=1.75, label="Log-normal fit")
    plt.xscale("log")
    plt.xlabel("Walk duration (seconds)")
    plt.ylabel("Density")
    plt.grid(True, which="both", linestyle="--", linewidth=0.5)
    plt.legend(frameon=False)
    fig.savefig(outfile, bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)


def plot_pause_duration_pareto(durations: np.ndarray, outfile: str) -> None:
    '''Plot histogram of pause durations with Pareto PDF overlay.'''
    if len(durations) == 0:
        raise ValueError("No pause durations computed; nothing to plot.")
    x_min = durations.min()
    alpha = 1.0 + len(durations) / np.sum(np.log(durations / x_min))

    x_vals = np.linspace(x_min, durations.max(), 200)
    pareto_pdf = alpha * (x_min ** alpha) / (x_vals ** (alpha + 1))

    fig = plt.figure(figsize=FIGSIZE)
    plt.hist(durations, bins=50, density=True, alpha=0.6, label="Empirical PDF")
    plt.plot(x_vals, pareto_pdf, "--", linewidth=1.75, label=f"Pareto fit (α={alpha:.2f})")
    plt.xlabel("Pause duration (seconds)")
    plt.ylabel("PDF")
    plt.grid(True, linestyle="--", linewidth=0.5)
    plt.legend(frameon=False)
    fig.savefig(outfile, bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)


def main():
    df = query_player_path()

    walk_distances = compute_walk_distances(df)
    walk_durations = compute_walk_durations(df)
    pause_durations = compute_pause_durations(df)

    plot_ccdf_pareto(
        walk_distances, "./output/walk_distances_ccdf_pareto_fit.pdf"
    )
    plot_walk_duration_lognormal(
        walk_durations, "./output/walk_durations_log_normal_fit.pdf"
    )
    plot_pause_duration_pareto(
        pause_durations, "./output/pause_durations_pareto_fit.pdf"
    )


if __name__ == "__main__":
    main()
