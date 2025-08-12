import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

excel_file = './data/pidstat_modded_cleaned.xlsx'
sheet_name = 'pidstat_modded_cleaned'

metrics = ['cpu', 'rss']
scenarios = ['1', '5', '10']  # scenario identifiers in column names
scenario_labels = {'1': '1 Player', '5': '5 Players', '10': '10 Players'}
metric_labels = {
    'cpu': 'CPU Usage (%)',
    'rss': 'Memory Usage (RSS) (MB)'
}

def load_cleaned_data(path, sheet):
    if not os.path.isfile(path):
        raise FileNotFoundError(f"Excel file not found: {path}")
    return pd.read_excel(path, sheet_name=sheet)

df = load_cleaned_data(excel_file, sheet_name)

# -----------------------
# Reshape to Long Form
# -----------------------
records = []
for metric in metrics:
    for scenario in scenarios:
        base_col = f'{metric} base {scenario}'
        mod_col = f'{metric} mod {scenario}'
        if base_col not in df.columns or mod_col not in df.columns:
            print(f"Warning: Missing columns for {metric} scenario {scenario}")
            continue

        # Baseline values
        for val in df[base_col].dropna():
            records.append({
                'metric': metric,
                'scenario': scenario,
                'condition': 'Baseline',
                'value': val
            })

        # Modded values
        for val in df[mod_col].dropna():
            records.append({
                'metric': metric,
                'scenario': scenario,
                'condition': 'Modded',
                'value': val
            })

long_df = pd.DataFrame.from_records(records)

# -----------------------
# Summary Statistics Table
# -----------------------
summary = (
    long_df
    .groupby(['metric', 'scenario', 'condition'])['value']
    .agg(['mean', 'median', 'std', 'min', 'max', 'count'])
    .reset_index()
)

summary_table = summary.pivot_table(
    index=['metric', 'scenario'],
    columns='condition',
    values=['mean', 'median', 'std', 'min', 'max', 'count']
)

summary_table.columns = [f"{stat}_{cond.lower()}" for stat, cond in summary_table.columns]
summary_table = summary_table.reset_index()

# -----------------------
# Plotting
# -----------------------
plt.rcParams['font.size'] = 10

for metric in metrics:
    if metric != 'rss':
        # ---------- CPU: Horizontal Boxplot ----------
        data = []
        labels = []
        for scenario in scenarios:
            for cond in ['Baseline', 'Modded']:
                subset = long_df[
                    (long_df['metric'] == metric) &
                    (long_df['scenario'] == scenario) &
                    (long_df['condition'] == cond)
                    ]['value']
                data.append(subset.values)
                labels.append(f"{scenario_labels[scenario]} - {cond}")

        plt.figure(figsize=(10, 6))
        plt.boxplot(
            data,
            vert=False,
            labels=labels,
            notch=False,
            boxprops={'color': 'black'},
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

        ax = plt.gca()
        ax.xaxis.grid(True, linestyle='--', linewidth=0.5)
        ax.set_xlabel(metric_labels[metric], fontsize=12)
        ax.set_ylabel('Server Load Scenario', fontsize=12)
        ax.set_xlim(left=0)  # start axis at zero
        ax.tick_params(axis='x', labelsize=10)
        ax.tick_params(axis='y', labelsize=10)
        plt.tight_layout()

        plot_file = f'./output/boxplot_{metric}.pdf'
        plt.savefig(plot_file, format='pdf')
        print(f"Boxplot saved to {plot_file}")
        plt.close()

    else:
        rss_subset = long_df[long_df['metric'] == 'rss']

        rss_stats = (
            rss_subset
            .groupby(['scenario', 'condition'])['value']
            .agg(['mean', 'std', 'count'])
            .reindex(
                [(s, c) for s in scenarios for c in ['Baseline', 'Modded']]
            )
        )

        n_scenarios = len(scenarios)
        bar_width = 0.35
        x = np.arange(n_scenarios)

        means_baseline = [rss_stats.loc[(s, 'Baseline'), 'mean'] for s in scenarios]
        std_baseline = [rss_stats.loc[(s, 'Baseline'), 'std'] for s in scenarios]
        means_modded = [rss_stats.loc[(s, 'Modded'), 'mean'] for s in scenarios]
        std_modded = [rss_stats.loc[(s, 'Modded'), 'std'] for s in scenarios]

        plt.figure(figsize=(8, 5))
        ax = plt.gca()

        bars_b = ax.bar(x - bar_width/2, means_baseline, bar_width,
                        yerr=std_baseline, capsize=4, label='Baseline')
        bars_m = ax.bar(x + bar_width/2, means_modded, bar_width,
                        yerr=std_modded, capsize=4, label='Modded')

        ax.set_xlabel('Server Load Scenario', fontsize=12)
        ax.set_ylabel(metric_labels['rss'], fontsize=12)
        ax.set_xticks(x)
        ax.set_xticklabels([scenario_labels[s] for s in scenarios], fontsize=10)
        ax.set_ylim(bottom=0)

        # Headroom above highest (mean + std)
        max_with_error = max(
            max(m + (sd if not np.isnan(sd) else 0) for m, sd in zip(means_baseline, std_baseline)),
            max(m + (sd if not np.isnan(sd) else 0) for m, sd in zip(means_modded, std_modded))
        )
        ax.set_ylim(0, max_with_error * 1.05)

        ax.yaxis.grid(True, linestyle='--', linewidth=0.5, alpha=0.7)
        ax.legend(frameon=False, loc='upper left')

        plt.tight_layout()
        plot_file = './output/bar_rss.pdf'
        plt.savefig(plot_file, format='pdf')
        print(f"Bar chart saved to {plot_file}")
        plt.close()

print("Comparison script completed.")
