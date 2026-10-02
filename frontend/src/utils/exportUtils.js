/**
 * Exports data to a CSV file and triggers a browser download.
 * @param {string} filename - The name of the downloaded file (e.g. 'products.csv')
 * @param {Array<{label: string, key: string, formatter?: Function}>} columns - Column configuration
 * @param {Array<object>} data - The dataset array to export
 */
export function exportToCsv(filename, columns, data) {
  if (!data || !data.length) {
    alert("No data available to export.");
    return;
  }

  // Header row
  const headers = columns.map((col) => `"${col.label.replace(/"/g, '""')}"`).join(",");

  // Data rows
  const rows = data.map((item) => {
    return columns
      .map((col) => {
        let val = item[col.key];
        if (col.formatter) {
          val = col.formatter(val, item);
        }
        if (val === null || val === undefined) {
          val = "";
        }
        val = String(val).replace(/"/g, '""');
        return `"${val}"`;
      })
      .join(",");
  });

  const csvContent = "\uFEFF" + [headers, ...rows].join("\r\n"); // UTF-8 BOM
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", filename.endsWith(".csv") ? filename : `${filename}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
