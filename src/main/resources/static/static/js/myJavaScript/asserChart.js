let lastUpdateTimestamp = {};
let HistoryIsUpdate = false;
let CurrentIsUpdate = false;

async function getAssetKlineChart(assetId, type, method) {
    toggleLoadingDisplay(type, true);

    if (method === 'handle') {
        if (((type === 'history') && !HistoryIsUpdate) || (type === 'current' && !CurrentIsUpdate)) {
            await fetchKlineInfoData(assetId, type, method);
        }
        toggleLoadingDisplay(type, true);
        return;
    }

    try {
        let data = await fetchKlineInfoData(assetId, type, method);
        let jsonData = JSON.parse(data);
        if (jsonData && jsonData.data && jsonData.data.length > 0) {
            let newTimestamp = new Date(jsonData.data[jsonData.data.length - 1].timestamp).getTime();
            if (newTimestamp > (lastUpdateTimestamp[type] || 0)) {
                updateKlineChart(type, jsonData.data, jsonData.preferCurrencyExrate);
                toggleLoadingDisplay(type, false);
                return true;
            } else {
                toggleLoadingDisplay(type, true);
                return false;
            }
        }
    } catch (error) {
        let errorMessage = typeof error === 'string' ? error : error.message;
        if (errorMessage.includes("開始處理資產資料") || errorMessage.includes("沒有請求過資產資料")) {
            toggleLoadingDisplay(type, true);
            return false;
        } else if (errorMessage.includes("沒有足夠的資料")) {
            if (type === 'history') {
                HistoryIsUpdate = true;
            } else if (type === 'current') {
                CurrentIsUpdate = true;
            }
        } else {
            displayError(type, error);
            toggleLoadingDisplay(type, false);
            return false;
        }

    }
}

function displayError(type, error) {
    let errorTextId = type === 'history' ? 'history-error-text' : 'current-error-text';
    let errorTextElement = document.getElementById(errorTextId);
    errorTextElement.innerText = error.message;
    errorTextElement.style.display = 'block';
}

function toggleLoadingDisplay(type, shouldDisplay) {
    let loaderId = type === 'history' ? 'history-loader' : 'current-loader';
    let loaderElement = document.getElementById(loaderId);
    if (loaderElement) {
        loaderElement.style.display = shouldDisplay ? 'flex' : 'none';
    } else {
        console.error('找不到元素: ' + loaderId);
    }
}

async function klineChart(type) {
    await getAssetKlineChart(assetId, type, "get")
        .then(async () => {
            return getAssetKlineChart(assetId, type, "handle");
        })
        .then(() => {
            async function updateChart() {
                let updated = await getAssetKlineChart(assetId, type, "get");
                if (updated) {
                    clearInterval(interval);
                } else {
                    let loader = type === "history" ? 'history-loader' : 'current-loader';
                    showFlexById(loader);
                }
            }

            let count = 0;
            let interval = setInterval(async function () {
                count++;
                if (count > 5) {
                    clearInterval(interval);
                    return;
                }
                await updateChart();
            }, 10000);
        });
}

function updateKlineChart(type, data, preferCurrencyExrate) {
    let chartContainerId = type === 'history' ? 'historyKlineChart' : 'currentKlineChart';
    let chartContainer = document.getElementById(chartContainerId);
    chartContainer.innerHTML = '';
    let exrate = parseFloat(preferCurrencyExrate)

    let kData = data.map(d => ({
        timestamp: new Date(d.timestamp).getTime(),
        open: +d.open * exrate,
        high: +d.high * exrate,
        low: +d.low * exrate,
        close: +d.close * exrate,
        volume: Math.ceil(+d.volume),
    }));

    let chart = klinecharts.init(chartContainer);
    chart.applyNewData(kData);
    chart.createIndicator('VOL');

    let lastData = kData[kData.length - 1];
    lastUpdateTimestamp[type] = new Date(lastData.timestamp).getTime();
}

async function getAssetDetails(assetId) {
    let jsonData = await fetchAssetInfoData(assetId);
    try {
        let assetName = jsonData.assetName;
        let today = formatDetailToFloatString(jsonData.statistics[4]);
        let day = formatDetailToFloatString(jsonData.statistics[3]);
        let week = formatDetailToFloatString(jsonData.statistics[2]);
        let month = formatDetailToFloatString(jsonData.statistics[1]);
        let year = formatDetailToFloatString(jsonData.statistics[0]);

        let body = document.getElementById("statistics_overview");
        body.innerHTML =
            `
                <div class="d-none d-md-block">
                    <p class="statistics-title">資產名稱</p>
                    <h3 class="rate-percentage">${assetName}</h3>
                    <p class="d-flex"><i class="mdi mdi-menu-up"></i><span></span></p>
                </div>
                <div class="d-none d-md-block">
                    <p class="statistics-title">目前資產價格</p>
                    <h3 class="rate-percentage">${thousands(today)}</h3>
                    <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
                </div>
                ` +
            generateStatisticsTable("昨日價格", thousands(day), ((((+today) - (+day)) / (+day)) * 100).toFixed(3)) +
            generateStatisticsTable("上周價格", thousands(week), ((((+today) - (+week)) / (+week)) * 100).toFixed(3)) +
            generateStatisticsTable("上月價格", thousands(month), ((((+today) - (+month)) / (+month)) * 100).toFixed(3)) +
            generateStatisticsTable("去年價格", thousands(year), ((((+today) - (+year)) / (+year)) * 100).toFixed(3));
    } catch (error) {
        console.error(error);
    }
}

function formatDetailToFloatString(input) {
    console.log("Input: " + input);
    let Value = Number(input);
    console.log("Value: " + Value);
    if (!isNaN(Value) && Value >= 0) {
        return Value < 1 ? Value.toFixed(6) : Value.toFixed(2);
    } else {
        return "數據不足";
    }
}

