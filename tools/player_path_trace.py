import numpy as np
import matplotlib.pyplot as plt

plt.rcParams.update({
    'figure.dpi': 300,
    'savefig.dpi': 300,
    'font.size': 11,
    'axes.labelsize': 12,
    'axes.titlesize': 14,
    'legend.fontsize': 9
})

def generate_realistic_path_data(seed=42):
    rng = np.random.default_rng(seed)
    side = 100
    speed = 4.3  # blocks/sec

    # Build ground-truth path
    gt_x, gt_z, gt_t = [], [], []
    for i in range(side + 1):
        gt_x.append(0); gt_z.append(i); gt_t.append(i / speed)
    for i in range(1, side + 1):
        gt_x.append(i); gt_z.append(side); gt_t.append((side + i) / speed)
    for i in range(side - 1, -1, -1):
        gt_x.append(side); gt_z.append(i); gt_t.append((2 * side + (side - i)) / speed)
    for i in range(side - 1, 0, -1):
        gt_x.append(i); gt_z.append(0); gt_t.append((3 * side + (side - i)) / speed)

    ground_truth = {'x': gt_x, 'z': gt_z, 'time': gt_t}
    total_time = gt_t[-1]
    noise = 0.05

    # Fixed interval sampling
    base_times = np.arange(0, total_time, 5.0)
    if total_time - base_times[-1] > 1e-6:
        interval_times = np.append(base_times, total_time)
    else:
        interval_times = base_times

    gt_time_array = np.array(gt_t)
    idxs = np.searchsorted(gt_time_array, interval_times, side='left')
    closest_idxs = []
    for t, i_right in zip(interval_times, idxs):
        if i_right == 0:
            closest_idxs.append(0)
        elif i_right >= len(gt_time_array):
            closest_idxs.append(len(gt_time_array) - 1)
        else:
            i_left = i_right - 1
            if abs(gt_time_array[i_left] - t) <= abs(gt_time_array[i_right] - t):
                closest_idxs.append(i_left)
            else:
                closest_idxs.append(i_right)

    interval_x = [gt_x[i] + rng.normal(0, noise) for i in closest_idxs]
    interval_z = [gt_z[i] + rng.normal(0, noise) for i in closest_idxs]
    interval = {'x': interval_x, 'z': interval_z, 'time': interval_times}

    # Block-change sampling
    block_x = [x + rng.normal(0, noise) for x in gt_x]
    block_z = [z + rng.normal(0, noise) for z in gt_z]
    block = {'x': block_x, 'z': block_z, 'time': gt_t}

    return {'ground_truth': ground_truth, 'interval': interval, 'block': block}

def calculate_metrics(sampled, ground_truth):
    n = len(sampled['x'])
    gx = np.array(ground_truth['x'][:n])
    gz = np.array(ground_truth['z'][:n])
    sx = np.array(sampled['x'])
    sz = np.array(sampled['z'])
    rmse = np.sqrt(np.mean((sx - gx)**2 + (sz - gz)**2))

    dx = np.diff(sx); dz = np.diff(sz)
    path_length = np.sum(np.sqrt(dx**2 + dz**2))
    true_perimeter = 4 * 100.0
    path_error = abs(path_length - true_perimeter)
    path_error_pct = path_error / true_perimeter * 100.0
    return {
        'rmse': rmse,
        'path_length': path_length,
        'path_error': path_error,
        'path_error_pct': path_error_pct
    }

def create_validation_figure(seed=42):
    data = generate_realistic_path_data(seed=seed)
    m_interval = calculate_metrics(data['interval'], data['ground_truth'])
    m_block = calculate_metrics(data['block'], data['ground_truth'])
    rmse_improv = (m_interval['rmse'] - m_block['rmse']) / m_interval['rmse'] * 100

    # Create side-by-side subplots
    fig, (ax_ts, ax_path) = plt.subplots(1, 2, figsize=(12, 5))

    # Time Series Plot
    ax_ts.plot(data['block']['time'], data['block']['x'], '--', linewidth=2.2, label='Block X')
    ax_ts.plot(data['block']['time'], data['block']['z'], '--', linewidth=2.2, label='Block Z')
    ax_ts.plot(data['interval']['time'], data['interval']['x'], 'o-', linewidth=2.6,
               markersize=5, markerfacecolor='white', label=f"Interval X (RMSE {m_interval['rmse']:.2f})")
    ax_ts.plot(data['interval']['time'], data['interval']['z'], 's-', linewidth=2.6,
               markersize=5, markerfacecolor='white', label='Interval Z')
    ax_ts.set_xlabel('Time (seconds)')
    ax_ts.set_ylabel('Coordinate Value (blocks)')
    ax_ts.grid(True, alpha=0.35)
    ax_ts.legend(ncol=2, frameon=True, loc='upper center')
    ax_ts.set_title('Time Series Comparison')

    # 2D Path Plot
    ax_path.plot(data['interval']['x'], data['interval']['z'], 'b-o', linewidth=2.2,
                 markersize=5, markerfacecolor='white', label='Recorded Path')
    square_x = [0, 0, 100, 100, 0]
    square_z = [0, 100, 100, 0, 0]
    ax_path.plot(square_x, square_z, 'r--', linewidth=2.2, alpha=0.8, label='Ground Truth')
    ax_path.set_xlabel('X Coordinate (blocks)')
    ax_path.set_ylabel('Z Coordinate (blocks)')
    ax_path.set_aspect('equal')
    ax_path.grid(True, alpha=0.35)
    ax_path.legend(loc='upper center')
    ax_path.set_title('2D Path Plot')

    fig.tight_layout()
    fig.savefig('./output/player_path_validation_side_by_side.pdf', bbox_inches='tight')
    plt.show()

    # Print summary
    print("=== Player Path Trace Validation (Side-by-Side) ===")
    print(f"Interval Sampling RMSE: {m_interval['rmse']:.3f} blocks")
    print(f"Block Sampling RMSE: {m_block['rmse']:.3f} blocks")
    print(f"RMSE Improvement: {rmse_improv:.1f}%")

if __name__ == '__main__':
    create_validation_figure(seed=42)
