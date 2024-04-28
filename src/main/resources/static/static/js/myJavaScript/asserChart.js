let isCurrentFetching = false;
let isHistoryFetching = false;
let noData = false
let isCurrentCheck = true
let isHistoryCheck = true
let count = 0
let finalError;
async function getAssetKlineChart(assetId, type, method) {
    if (isHistoryFetching && type === 'history') {
        return false;
    } else if (isCurrentFetching && type === 'current') {
        return false;
    } else if (type === 'history') {
        isHistoryFetching = true;
    } else if (type === 'current') {
        isCurrentFetching = true;
    }

    try {
        let chartContainer;
        let data = await fetchKlineInfoData(assetId, type, method);
        if (method === 'get' && !noData) {
            let jsonData = JSON.parse(data);
            if (type === 'history') {
                chartContainer = document.getElementById('historyKlineChart');
            } else if (type === 'current') {
                chartContainer = document.getElementById('currentKlineChart');
            }
            chartContainer.innerHTML = '';
            let kData = jsonData.data.map(function(d) {
                return {
                    timestamp: new Date(d.timestamp).getTime(),
                    open: +d.open,
                    high: +d.high,
                    low: +d.low,
                    close: +d.close,
                    volume: Math.ceil(+d.volume),
                }
            });

            let chart = klinecharts.init(chartContainer);
            chart.applyNewData(kData);
            chart.createIndicator('VOL')

            return true;
        } else {
            return false;
        }
    } catch (error) {

        let loaderId;
        let errorTextId;
        if (type === 'history') {
            loaderId = document.getElementById('history-loader')
            errorTextId = document.getElementById('history-error-text')
        } else if (type === 'current') {
            loaderId = document.getElementById('current-loader')
            errorTextId = document.getElementById('current-error-text')
        }

        if (finalError) {
            loaderId.style.display = 'none';
            errorTextId.innerText = error;
            errorTextId.style.display = 'block';
            noData = true
            isHistoryFetching = true
            isCurrentFetching = true
            finalError = error
            return true
        }


        if (error.message === "資產資料已經在處理中") {
            loaderId.style.display = 'flex';
            errorTextId.style.display = 'none';
            return false;
        } else if (error.message ===  "此資產尚未有任何訂閱，請先訂閱後再做請求") {
            loaderId.style.display = 'none';
            errorTextId.innerText = error;
            errorTextId.style.display = 'block';
            noData = true
            isHistoryFetching = true
            isCurrentFetching = true
            finalError = error
            return true
        }else if (error.message === "找不到資產") {
            loaderId.style.display = 'none';
            errorTextId.innerText = error;
            errorTextId.style.display = 'block';
            noData = true
            isHistoryFetching = true
            isCurrentFetching = true
            return true
        } else if (error.message ===  "沒有請求過資產資料") {
            if (type === 'history' && isHistoryCheck) {
                isHistoryCheck = false
                loaderId.style.display = 'flex';
                errorTextId.style.display = 'none';
                await fetchKlineInfoData(assetId, "history", "handle");
                return false;
            } else if (type === 'current' && isCurrentCheck) {
                isCurrentCheck = false
                loaderId.style.display = 'flex';
                errorTextId.style.display = 'none';
                await fetchKlineInfoData(assetId, "current", "handle");
                return false;
            } else if (count < 10) {
                loaderId.style.display = 'flex';
                errorTextId.style.display = 'none';
                count += 1
                return false
            } else {
                loaderId.style.display = 'none';
                errorTextId.innerText = error;
                errorTextId.style.display = 'block';
                noData = true
                isHistoryFetching = true
                isCurrentFetching = true
                return true
            }
        }else {
            loaderId.style.display = 'none';
            errorTextId.innerText = error;
            errorTextId.style.display = 'block';
            return false;
        }
    } finally {
        if (type === 'history') {
            isHistoryFetching = false;
        } else if (type === 'current') {
            isCurrentFetching = false;
        }
    }
}

async function getAssetDetails(assetId) {
    let jsonData = await fetchAssetInfoData(assetId)
    try {
        let assetName = jsonData.assetName;
        let today = Number(jsonData.statistics[4]) ? (+jsonData.statistics[4]).toFixed(3) : "數據不足";
        if (today < 0) {
            today = "數據不足";
        }
        let day = Number(jsonData.statistics[3]) ? (+jsonData.statistics[3]).toFixed(3) : "數據不足";
        let week = Number(jsonData.statistics[2]) ? (+jsonData.statistics[2]).toFixed(3) : "數據不足";
        let month = Number(jsonData.statistics[1]) ? (+jsonData.statistics[1]).toFixed(3) : "數據不足";
        let year = Number(jsonData.statistics[0]) ? (+jsonData.statistics[0]).toFixed(3) : "數據不足";
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
            generateStatisticsTable("昨日價格", day, ((today - day) / day).toFixed(3)*100) +
            generateStatisticsTable("上周價格", week, ((today - week) / week).toFixed(3)*100) +
            generateStatisticsTable("上月價格", month, ((today - month) / month).toFixed(3)*100)+
            generateStatisticsTable("去年價格", year, ((today - year) / year).toFixed(3)*100);
    } catch (error) {
        console.error(error);
    }
}


