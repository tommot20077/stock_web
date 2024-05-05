async function getAssetKlineChart(assetId, type, method) {
    let isLoadingDisplay = method === 'get';
    toggleLoadingDisplay(type, isLoadingDisplay); // 控制加載動畫顯示或隱藏

    try {
        let data = await fetchKlineInfoData(assetId, type, method);
        let jsonData = JSON.parse(data);

        console.log("jsonData" + jsonData.data)
        if (jsonData && jsonData.data && jsonData.data.length > 0) {
            updateKlineChart(type, jsonData.data); // 更新K線圖
            toggleLoadingDisplay(type, false); // 資料加載完畢，隱藏加載動畫
            return true;
        } else {
            toggleLoadingDisplay(type, true); // 資料處理中，顯示加載動畫
            return false;
        }
    } catch (error) {
        let errorMessage = typeof error === 'string' ? error : error.message;
        if (errorMessage.includes("開始處理資產資料，稍後用/getAssetInfo/assetId取得結果") || errorMessage.includes("資產資料已經在處理中") || errorMessage.includes("沒有請求過資產資料")) {
            toggleLoadingDisplay(type, true); // 資料處理中，顯示加載動畫
            return false;
        }
        displayError(type, error); // 顯示錯誤資訊
        toggleLoadingDisplay(type, false);  // 出錯後，隱藏加載動畫
        return false;
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
    loaderElement.style.display = shouldDisplay ? 'flex' : 'none';
}

function updateKlineChart(type, data) {
    let chartContainerId = type === 'history' ? 'historyKlineChart' : 'currentKlineChart';
    let chartContainer = document.getElementById(chartContainerId);
    chartContainer.innerHTML = '';  // 清空當前圖表
    let kData = data.map(d => ({
        timestamp: new Date(d.timestamp).getTime(),
        open: +d.open,
        high: +d.high,
        low: +d.low,
        close: +d.close,
        volume: Math.ceil(+d.volume),
    }));

    let chart = klinecharts.init(chartContainer);
    chart.applyNewData(kData);
    chart.createIndicator('VOL');
}

async function getAssetDetails(assetId) {
    let jsonData = await fetchAssetInfoData(assetId);
    try {
        let assetName = jsonData.assetName;
        let todayValue = Number(jsonData.statistics[4]);
        let today = !isNaN(todayValue) && todayValue >= 0 ? todayValue.toFixed(3) : "數據不足";
        let dayValue = Number(jsonData.statistics[3]);
        let day = !isNaN(dayValue) && dayValue >= 0 ? dayValue.toFixed(3) : "數據不足";
        let weekValue = Number(jsonData.statistics[2]);
        let week = !isNaN(weekValue) && weekValue >= 0 ? weekValue.toFixed(3) : "數據不足";
        let monthValue = Number(jsonData.statistics[1]);
        let month = !isNaN(monthValue) && monthValue >= 0 ? monthValue.toFixed(3) : "數據不足";
        let yearValue = Number(jsonData.statistics[0]);
        let year = !isNaN(yearValue) && yearValue >= 0 ? yearValue.toFixed(3) : "數據不足";

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
                    <h3 class="rate-percentage">${today}</h3>
                    <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
                </div>
                ` +
            generateStatisticsTable("昨日價格", day, ((((+today) - (+day)) / (+day)) * 100).toFixed(2)) +
            generateStatisticsTable("上周價格", week, ((((+today) - (+week)) / (+week)) * 100).toFixed(2)) +
            generateStatisticsTable("上月價格", month, ((((+today) - (+month)) / (+month)) * 100).toFixed(2)) +
            generateStatisticsTable("去年價格", year, ((((+today) - (+year)) / (+year)) * 100).toFixed(2));
    } catch (error) {
        console.error(error);
    }
}


