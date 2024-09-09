const charts = {};


function disableLoading(type) {
    let elementId = type === "current" ? "current-loader" : "history-loader"
    document.getElementById(elementId).style.display = 'none'
}

function displayError(type, message) {
    let errorTextId = type === 'history' ? 'history-error-text' : 'current-error-text';
    let errorTextElement = document.getElementById(errorTextId);
    errorTextElement.innerText = message;
    errorTextElement.style.display = 'block';
    disableLoading(type)


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
    let Value = Number(input);
    if (!isNaN(Value) && Value >= 0) {
        return Value < 1 ? Value.toFixed(6) : Value.toFixed(2);
    } else {
        return "數據不足";
    }
}


function handleIncomingData(rawData) {
    try {
        const jsonData = JSON.parse(rawData);
        const {data, preferCurrencyExrate, type} = jsonData;

        if (data && data.length > 0) {
            const formattedData = formatKlineData(data, preferCurrencyExrate);
            updateKlineChart(type, formattedData);
        } else {
            console.warn("接收到的資料為空");
        }
    } catch (error) {
        console.error("處理接收到的資料時出錯：", error);
        if (error instanceof SyntaxError && typeof rawData === 'string') {
            console.log("rawData" + rawData)
            displayError("history", rawData)
            displayError("current", rawData)
        }
    }
}

function formatKlineData(data, exrate = 1) {
    const formattedData = [];

    data.forEach(kline => {
        formattedData.push({
            timestamp: new Date(kline.timestamp).getTime(),
            open: parseFloat(kline.open) * exrate,
            high: parseFloat(kline.high) * exrate,
            low: parseFloat(kline.low) * exrate,
            close: parseFloat(kline.close) * exrate,
            volume: parseFloat(kline.volume)
        });
    });

    formattedData.sort((a, b) => a.timestamp - b.timestamp);

    return formattedData;
}

function initKlineChart(type) {
    const chartContainerId = type === 'history' ? 'historyKlineChart' : 'currentKlineChart';
    const chartContainer = document.getElementById(chartContainerId);

    if (!chartContainer) {
        console.error(`未找到ID為${chartContainerId}的元素`);
        return;
    }
    const chart = klinecharts.init(chartContainer);
    chart.createIndicator('VOL');
    charts[type] = chart;


}
function updateKlineChart(type, data) {
    const chart = charts[type];
    if (!chart) {
        console.error(`類型為${type}的圖表尚未初始化`);
        return;
    }
    const existingData = chart.getDataList();
    if (existingData.length === 0) {
        disableLoading(type)
        chart.applyNewData(data);
        if (type === "current") {
            socket.send(JSON.stringify({
                "action": 'chartInitializedDone',
                "assetId": assetId
            }));
        }
    } else {
        data.forEach(item => {
            chart.updateData(item);
        });
    }
}
