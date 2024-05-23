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

function remindMaxSpace(maxValue) {
    let power = Math.floor(Math.log10(maxValue));
    let remainder = Math.ceil(maxValue / Math.pow(10, power)) + 1;
    return Math.pow(10, power) * remainder;
}
function generatePriceLabels(maxValue) {
    let formatMax = remindMaxSpace(maxValue);
    return formatMax / 5;
}

function fillData(datasets, labels, summaryData, numPerDay, dataGroups) {
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

async function userLineChart() {
    try {
        const summaryData = await fetchUserPropertySummary();
        const summaryDatasets = [
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

        let roiDatasets = [
            {
                label: 'ROI',
                data: summaryData.daily_roi.map(dataPoint => dataPoint.value),
                fill: false,
                borderColor: 'rgb(154,51,233)',
                tension: 0.1,
                spanGaps: true
            }
        ];
        let summaryDataGroups = [summaryData.total_sum, summaryData.currency_sum, summaryData.crypto_sum, summaryData.stock_tw_sum];
        let roiDataGroups = [summaryData.daily_roi];

        const summaryLabels = generateTimeLabels(7, 4);
        const roiLabels = generateTimeLabels(7, 6);
        const summaryMaxValue = fillData(summaryDatasets, summaryLabels, summaryData, 4, summaryDataGroups);
        const roiMaxValue = fillData(roiDatasets, roiLabels, summaryData, 6, roiDataGroups);
        const summaryLineChart = document.getElementById('userPropertySummaryLineChart');
        const roiLineChart = document.getElementById('userROILineChart');

        if (summaryLineChart && summaryLineChart.getContext('2d') && roiLineChart && roiLineChart.getContext('2d')) {
            const summaryConfig= getLineConfig(summaryDatasets, summaryLabels, '金額', summaryMaxValue);
            const roiConfig= getLineConfig(roiDatasets, roiLabels, '百分比', roiMaxValue);

            new Chart(summaryLineChart, summaryConfig);
            new Chart(roiLineChart, roiConfig);
        }
    } catch (error) {
        console.error("在生成圖表時出現錯誤", error);
    }
}

async function userPropertySummaryPieChart() {
    try {
        const summaryData = await fetchUserPropertySummary();
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

function getLineConfig(datasets, labels, yLabel, maxValue){
    return {
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
                        callback: function (value, index, values) {
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
                        text: yLabel
                    },
                    ticks: {
                        stepSize: generatePriceLabels(maxValue),
                    },
                    beginAtZero: true,
                    max: remindMaxSpace(maxValue)

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
    }

}