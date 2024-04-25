let isFetching = false;
async function getAssetKlineChart(assetId, method) {
    if (isFetching) {
        return false;
    }
    isFetching = true;
    try {
        let data = await fetchAssetInfoData(assetId, 'history', method);
        if (method === 'get') {
            let jsonData = JSON.parse(data);
            let chartContainer = document.getElementById('klineChart');
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
        console.log("error" + error);
        if (error === "資產資料已經在處理中") {
            document.getElementById('loader').style.display = 'flex';
            document.getElementById('error-text').style.display = 'none';
            return false;
        } else {
            console.error(error);
            document.getElementById('loader').style.display = 'none';
            document.getElementById('error-text').innerText = error;
            document.getElementById('error-text').style.display = 'block';
            return false;
        }


    } finally {
        isFetching = false;
    }
}





function getAssetIdFromUrl() {
    const pathArray = window.location.pathname.split('/');
    return pathArray[pathArray.length - 1];
}




