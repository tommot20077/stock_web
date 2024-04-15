function generateTimeLabels(days, numPerDay) {
    let labels = [];
    let now = new Date();
    now.setMinutes(0);
    now.setSeconds(0);
    now.setMilliseconds(0);
    const hours = now.getHours();
    now.setHours(hours - (hours % (24 / numPerDay)));


    for (let i = 1; i < days * numPerDay; i++) {
        let label = new Date(now.getTime() - i * (24 / numPerDay) * 60 * 60 * 1000);
        labels.unshift(dateFns.format(label, "yyyy-MM-dd HH:mm:ss"));
    }
    return labels;
}

function generatePriceLabels(maxValue) {
    let step;
    if (maxValue > Math.pow(10, Math.floor(Math.log10(maxValue)))) {
        step = Math.pow(10, Math.floor(Math.log10(maxValue)));
    } else {
        step = 5 * Math.pow(10, Math.floor(Math.log10(maxValue)) - 1);
    }
    return step
}

function fillData(datasets, labels, summaryData, numPerDay) {
    const dataGroups = [summaryData.total_sum, summaryData.currency_sum, summaryData.crypto_sum, summaryData.stock_tw_sum];
    let maxValue = 0;

    datasets.forEach((set, index) => {
        const newData = [];
        labels.forEach(label => {
            let found = false;
            for (const dataPoint of dataGroups[index]) {
                if (dateFns.isWithinInterval(new Date(dataPoint.date_Format), {
                    start: new Date(label),
                    end: new Date(new Date(label).getTime() + (24 / numPerDay) * 60 * 60 * 1000)
                })) {
                    newData.push(dataPoint.value);
                    found = true;
                    if (dataPoint.value > maxValue) {
                        maxValue = dataPoint.value;
                    }
                    break;
                }
            }
            if (!found) {
                newData.push(null);
            }
        });
        set.data = newData;
    });
    return maxValue;
}
async function userPropertySummaryLineChart () {
    try {
        const summaryData = await INDEX_NAMESPACE.fetchUserPropertySummary();
        const datasets = [
            {
                label: '總資產',
                data: summaryData.total_sum.map(dataPoint => dataPoint.value),
                fill: false,
                borderColor: 'rgb(255, 99, 132)',
                tension: 0.1,
                spanGaps: true
            },
            {
                label: '貨幣資產',
                data: summaryData.currency_sum.map(dataPoint => dataPoint.value),
                fill: false,
                borderColor: 'rgb(54, 162, 235)',
                tension: 0.1,
                spanGaps: true
            },
            {
                label: '加密貨幣資產',
                data: summaryData.crypto_sum.map(dataPoint => dataPoint.value),
                fill: false,
                borderColor: 'rgb(255, 205, 86)',
                tension: 0.1,
                spanGaps: true
            },
            {
                label: '台灣股票資產',
                data: summaryData.stock_tw_sum.map(dataPoint => dataPoint.value),
                fill: false,
                borderColor: 'rgb(255, 99, 132)',
                tension: 0.1,
                spanGaps: true
            }
        ];
        let labelDays = 7;
        let numPerDay = 4;
        const labels = generateTimeLabels(labelDays, numPerDay);
        const maxValue = fillData(datasets, labels, summaryData, numPerDay);
        const ctx = document.getElementById('userPropertySummaryLineChart');
        if (ctx && ctx.getContext('2d')) {

            const config = {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: datasets
                },

                options: {
                    scales: {
                        x: {
                            type: 'time',
                            time: {
                                unit: 'day',
                            },
                            ticks: {
                                callback: function(value, index, values) {
                                    try {
                                        const timestamp = Number(value);
                                        return dateFns.format(new Date(timestamp), "yyyy-MM-dd");
                                    } catch (e) {
                                        console.error('日期解析錯誤:', e);
                                        return "";
                                    }
                                }
                            },
                            display: true,
                            title: {
                                display: true,
                                text: '日期'
                            }
                        },
                        y: {
                            display: true,
                            title: {
                                display: true,
                                text: '金額'
                            },
                            ticks: {
                                stepSize: generatePriceLabels(maxValue),
                            },
                            beginAtZero: true
                        }
                    },
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        tooltip: {
                            mode: 'index',
                            intersect: false
                        }
                    }
                }
            };
            new Chart(ctx, config);
        }
    } catch (error) {
        console.error("在生成圖表時出現錯誤", error);
    }
}

async function userPropertySummaryPieChart(){
    try {
        const summaryData = await INDEX_NAMESPACE.fetchUserPropertySummary();
        const latestData = summaryData.latest.filter(dataPoint => dataPoint.field !== "total_sum");
        const labels = latestData.map(dataPoint => getPropertySummaryName(dataPoint.field));
        const data = latestData.map(dataPoint => dataPoint.value);

        const ctx2 = document.getElementById('propertySummaryPieChart');
        if (ctx2 && ctx2.getContext("2d")) {
            const config = {
                type: 'pie',
                data: {
                    labels: labels,
                    datasets: [{
                        data: data
                    }]
                },
                responsive: true,
                maintainAspectRatio: false
            }
            new Chart(ctx2, config);
        }
    } catch (error) {
        console.error("在生成圖表時出現錯誤", error);
    }
}

