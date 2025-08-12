import os
import sys
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

plt.rcParams.update({
    'figure.dpi': 300,
    'savefig.dpi': 300,
    'font.size': 11,
    'axes.labelsize': 12,
    'axes.titlesize': 14,
    'legend.fontsize': 9
})

DATA_CSV = "./output/player_path_sampling_validation.csv"
OUT_DIR = "./output"

def gt_pos_at_time(t, side=100.0, speed=4.3):
    L = 4 * side
    s = min(t * speed, L)
    if s <= side:
        return 0.0, s
    elif s <= 2 * side:
        return s - side, side
    elif s <= 3 * side:
        return side, 3 * side - s
    else:
        return 4 * side - s, 0.0

def compute_metrics(times, xs, zs, side=100.0, speed=4.3):
    x_true, z_true = zip(*(gt_pos_at_time(t, side=side, speed=speed) for t in times))
    x_true = np.array(x_true); z_true = np.array(z_true)
    rmse = np.sqrt(np.mean((xs - x_true)**2 + (zs - z_true)**2))
    dx = np.diff(xs); dz = np.diff(zs)
    path_length = np.sum(np.sqrt(dx**2 + dz**2))
    true_perimeter = 4 * side
    path_error = abs(path_length - true_perimeter)
    path_error_pct = path_error / true_perimeter * 100.0
    return {
        "rmse": rmse,
        "path_length": path_length,
        "path_error": path_error,
        "path_error_pct": path_error_pct,
    }

def load_data(path: str) -> pd.DataFrame:
    if not os.path.exists(path):
        print(f"ERROR: CSV not found at '{path}'.", file=sys.stderr)
        sys.exit(1)
    df = pd.read_csv(path)

    df["t_seconds"] = df["t_seconds"].astype(float)
    df["x"] = df["x"].astype(float)
    df["z"] = df["z"].astype(float)

    df = df.sort_values(["method", "t_seconds"]).reset_index(drop=True)
    return df

def make_side_by_side_pdf(df: pd.DataFrame, out_dir: str, side=100.0):
    fig, (ax_ts, ax_path) = plt.subplots(1, 2, figsize=(12, 5))

    methods = list(df["method"].unique())
    if "block_change" in methods:
        d = df[df["method"] == "block_change"]
        ax_ts.plot(d["t_seconds"], d["x"], linestyle="--", linewidth=1.8, label="Block-change X")
        ax_ts.plot(d["t_seconds"], d["z"], linestyle="--", linewidth=1.8, label="Block-change Z")
        methods.remove("block_change")

    for m in methods:
        d = df[df["method"] == m]
        ax_ts.plot(d["t_seconds"], d["x"], marker="o", linewidth=2.2, markersize=4, label=f"{m} X")
        ax_ts.plot(d["t_seconds"], d["z"], marker="s", linewidth=2.2, markersize=4, label=f"{m} Z")

    ax_ts.set_xlabel("Time (seconds)")
    ax_ts.set_ylabel("Coordinate value (blocks)")
    ax_ts.grid(True, alpha=0.35)
    ax_ts.legend(ncol=3, frameon=True, loc="upper center")

    # --- Right: 2D path ---
    methods_path = list(df["method"].unique())
    if "block_change" in methods_path:
        d = df[df["method"] == "block_change"]
        ax_path.plot(d["x"], d["z"], linestyle="-", linewidth=1.6, label="Block-change path")
        methods_path.remove("block_change")

    for m in methods_path:
        d = df[df["method"] == m]
        ax_path.plot(d["x"], d["z"], marker="o", linewidth=2.0, markersize=4, label=f"{m} path")

    square_x = [0, 0, side, side, 0]
    square_z = [0, side, side, 0, 0]
    ax_path.plot(square_x, square_z, linestyle="--", linewidth=2.0, label="Ground-truth path")

    ax_path.set_aspect("equal")
    ax_path.set_xlabel("X (blocks)")
    ax_path.set_ylabel("Z (blocks)")
    ax_path.grid(True, alpha=0.35)
    ax_path.legend(loc="upper center", ncol=2, frameon=True)

    fig.tight_layout()
    os.makedirs(out_dir, exist_ok=True)
    fig.savefig(os.path.join(out_dir, "player_path_validation_side_by_side.pdf"), bbox_inches="tight")
    plt.close(fig)

def write_metrics(df: pd.DataFrame, out_dir: str, side=100.0, speed=4.3):
    rows = []
    for method, g in df.groupby("method", sort=False):
        times = g["t_seconds"].to_numpy()
        xs = g["x"].to_numpy()
        zs = g["z"].to_numpy()
        m = compute_metrics(times, xs, zs, side=side, speed=speed)

        interval = g["interval_s"].dropna().astype(str).replace("", np.nan).dropna()
        interval_val = interval.iloc[0] if not interval.empty else ""
        rows.append({
            "method": method,
            "interval_s": interval_val,
            "n_samples": len(times),
            "rmse_blocks": m["rmse"],
            "path_length_blocks": m["path_length"],
            "path_error_blocks": m["path_error"],
            "path_error_pct": m["path_error_pct"],
        })
    metrics = pd.DataFrame(rows)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "player_path_validation_metrics.csv")
    metrics.to_csv(path, index=False)

def main():
    df = load_data(DATA_CSV)
    make_side_by_side_pdf(df, OUT_DIR, side=100.0)
    write_metrics(df, OUT_DIR, side=100.0, speed=4.3)
    print(f"Figures saved to: {OUT_DIR}")
    print(f"Read data from:  {DATA_CSV}")

if __name__ == "__main__":
    main()
