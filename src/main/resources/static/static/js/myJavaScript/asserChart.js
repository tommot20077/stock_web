let isCurrentFetching = false;
let isHistoryFetching = false;
let hasStatistic = false;
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
        let data = await fetchAssetInfoData(assetId, type, method);
        if (method === 'get') {
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


            if (hasStatistic === false) {
                try {
                    console.log("data.statistics: " + jsonData.statistics);
                    hasStatistic = true;
                    let today = Number(jsonData.statistics[4]) ? +jsonData.statistics[4] : "數據不足";
                    let day = Number(jsonData.statistics[3]) ? +jsonData.statistics[3] : "數據不足";
                    let week = Number(jsonData.statistics[2]) ? +jsonData.statistics[2] : "數據不足";
                    let month = Number(jsonData.statistics[1]) ? +jsonData.statistics[1] : "數據不足";
                    let year = Number(jsonData.statistics[0]) ? +jsonData.statistics[0] : "數據不足";
                    let body = document.getElementById("statistics_overview");
                    body.innerHTML =
                        `
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

        if (error.message === "資產資料已經在處理中" || error.message ===  "沒有請求過資產資料") {
            loaderId.style.display = 'flex';
            errorTextId.style.display = 'none';
            return false;
        } else {
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


function getAssetIdFromUrl() {
    const pathArray = window.location.pathname.split('/');
    return pathArray[pathArray.length - 1];
}