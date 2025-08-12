import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

file_path = './data/pidstat_modded_cleaned.xlsx'
df = pd.read_excel(file_path, sheet_name='pidstat_modded_cleaned')

columns = {
    'Baseline': ('cpu base 1', 'rss base 1'),
    'Modded': ('cpu mod 1', 'rss mod 1'),
    'Prometheus Exporter': ('cpu exporter', 'rss exporter'),
    'Unified Metrics': ('cpu unified', 'rss unified'),
}

labels = list(columns.keys())

plt.rcParams['font.size'] = 10

# ---------- CPU: Boxplot ----------
cpu_data = [df[cpu_col].dropna().values for cpu_col, _ in columns.values()]

plt.figure(figsize=(8, 5))
plt.boxplot(
    cpu_data,
    labels=labels,
    notch=False,
    patch_artist=True,
    boxprops={'facecolor': 'white', 'edgecolor': 'black'},
    whiskerprops={'color': 'black'},
    capprops={'color': 'black'},
    medianprops={'color': 'red'},
    flierprops={'markeredgecolor': 'black'},
    showmeans=True,
    meanline=False,
    meanprops={
        'marker': 'o',
        'markerfacecolor': 'blue',
        'markeredgecolor': 'black',
        'markersize': 6
    }
)
plt.ylabel('CPU Usage (%)')
plt.xlabel('Server Configuration')
plt.grid(axis='y', linestyle='--', linewidth=0.5)

# Force y-axis to start at 0
ax = plt.gca()
ymin, ymax = ax.get_ylim()
if ymin > 0:
    ymin = 0
ax.set_ylim(ymin, ymax)

plt.tight_layout()
plt.savefig('./output/boxplot_cpu_1player.pdf')
plt.close()

# ---------- RSS: Bar Chart (Mean ± Std) ----------
rss_arrays = [df[rss_col].dropna().values for _, rss_col in columns.values()]
means = [arr.mean() if len(arr) else float('nan') for arr in rss_arrays]
stds  = [arr.std(ddof=1) if len(arr) > 1 else 0 for arr in rss_arrays]

x = np.arange(len(labels))
bar_width = 0.6

plt.figure(figsize=(8, 5))
ax = plt.gca()
bars = ax.bar(
    x,
    means,
    bar_width,
    yerr=stds,
    capsize=4,
    edgecolor='black'
)

ax.set_xticks(x)
ax.set_xticklabels(labels)
ax.set_ylabel('RSS Memory (MB)')
ax.set_xlabel('Server Configuration')
ax.set_ylim(bottom=0)  # already starts at 0

ax.yaxis.grid(True, linestyle='--', linewidth=0.5, alpha=0.7)

plt.tight_layout()
plt.savefig('./output/bar_rss_1player.pdf')
plt.close()

print('Saved boxplot_cpu_1player.pdf and bar_rss_1player.pdf')
