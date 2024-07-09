async function displayPropertyTable() {
    let data = await fetchUserAllProperties();
    if (data && Array.isArray(data)) {
        let tableBody = document.getElementById("propertyTableBody");
        tableBody.innerHTML = "";
        data.forEach(function (item) {
            let numTotal = +item.currentTotalPrice;
            let row = `
            <tr>
                <td>${item.propertyId}</td>
                <td>${getAssetType(item.assetType)}</td>
                <td><a href="/asset_info/${item.assetId}">${item.assetName}</a></td>
                <td>${thousands(item.quantity)}</td>
                <td style="text-align: right">${thousands(item.currentPrice.toFixed(3))}</td>
                <td style="text-align: right">${thousands(numTotal.toFixed(3).replace(/\.?0+$/, ""))}</td>
                <td>${item.description}</td>
                <td><a href="#" style="color: blue" onclick="displayEditProperty(this)">編輯</a>&nbsp&nbsp&nbsp<a id="deleteButton" data-property-type="${item.assetType}" data-property-id="${item.propertyId}" href="#" style="color: red" onclick="deleteProperty(this, this)">刪除</a></td>
            </tr>`;
            tableBody.innerHTML += row;
        });
    } else {
        console.log("資料格式錯誤");
    }
}

async function displaySubscribeTable() {
    let data = await getUserAllSubscribes();
    if (data && Array.isArray(data)) {
        let tableBody = document.getElementById("subscribeTableBody");
        tableBody.innerHTML = "";
        data.forEach(function (item) {
            let removeAbleText = item.removeAble ? '可以取消訂閱' : '此為用戶資產，由伺服器管理訂閱，不可取消';
            let deleteButton = item.removeAble ?
                `<a class="deleteButton" data-subscribe-name="${item.subscribeName}" data-subscribe-type="${item.assetType}" href="#" style="color: red" onclick="deleteSubscription(this)">刪除</a>` :
                `<span style="color: grey; cursor: not-allowed;">不可刪除</span>`;


            let row = `
            <tr>
                <td><a href="/asset_info/${item.assetId}">${item.assetId}</a></td>
                <td>${getAssetType(item.assetType)}</td>
                <td>${item.subscribeName}</td>
                <td>${removeAbleText}</td>
                <td>${deleteButton}</td>
            </tr>`;
            tableBody.innerHTML += row;
        })
    }
}

async function displayDebtTable() {
    let table = document.getElementById("debt_sheet")
    let jsonData = await fetchDebtsData();
    let htmlContent = '';
    for (let country in jsonData) {
        let tableHtml =
            `
                <h2>${capitalizeFirstLetter(country)}</h2>
                <div class="table-responsive pt-3">
                    <table class="table table-bordered">
                        <thead>
                            <tr>
                                <th>債券期限</th>
                                <th>當前利率</th>
                            </tr>
                        </thead>
                        <tbody>
            `;
        let bonds = jsonData[country];

        for (let term in bonds) {
            let rate = bonds[term];
            let formatTerm = term.replace("year", "年").replace("month", "個月").replace("week", "週").replace("-", "");
            tableHtml += `<tr><td>${formatTerm}</td><td>${rate} %</td></tr>\n`;
        }

        tableHtml += `</tbody></table></div><br><br>`;
        htmlContent += tableHtml;
    }
    table.innerHTML = htmlContent;
}

async function displayDebtChart() {
    let jsonData = await fetchDebtsData(true);
    let table = document.getElementById("debt_chart");
    let htmlContent = '<div class="row">';
    let count = 0
    for (let period in jsonData) {
        const periodDataSize = Object.keys(jsonData[period]).length;
        let canvasId = `debt-${period}`;
        let classWidth = periodDataSize >= 10 ? 'col-lg-12' : 'col-lg-6';
        let name = period.replace("year", "年").replace("month", "個月").replace("week", "週").replace("-", "");
        let chartTable =
            `
                <div class="${classWidth} grid-margin stretch-card">
                    <div class="card">
                        <div class="card-body">
                            <h4 class="card-title">${name}</h4>
                            <canvas id="${canvasId}"></canvas>
                        </div>
                    </div>
                </div>`;

        if (periodDataSize >= 10) {
            if (count % 2 !== 0) {
                htmlContent += '</div><div class="row">';
            }
            htmlContent += chartTable;
            htmlContent += '</div><div class="row">';
            count=0
        } else {
            htmlContent += chartTable;
            count++;
            if (count % 2 === 0) {
                htmlContent += '</div><div class="row">';
            }
        }
        if (count % 2 === 0) {
            htmlContent += '</div><div class="row">';
        }
    }
    if (count % 2 !== 0) {
        htmlContent += '</div>';
    }
    table.innerHTML = htmlContent;
    generationDebtChart(jsonData);
}

function generationDebtChart(jsonData) {

    for (let period in jsonData) {
        let canvasId = `debt-${period}`;
        let data = jsonData[period];
        let labels = Object.keys(data);
        let values = Object.values(data);
        let ctx = document.getElementById(canvasId).getContext('2d');
        let colors = getColorsArray(values.length);
        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: '利率 (%)',
                    data: values,
                    backgroundColor: colors,
                    borderColor: colors,
                    borderWidth: 1
                }]
            },
            options: {
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    }
}


function displayEditProperty(editButton) {
    var currentRow = editButton.closest('tr');

    var assetId = currentRow.cells[0].textContent;
    var assetType = currentRow.cells[1].textContent;
    var assetName = currentRow.cells[2].textContent;
    var quantity = currentRow.cells[3].textContent;
    var description = currentRow.cells[6].textContent;

    document.getElementById('edit_property_id').value = assetId;
    document.getElementById('edit_property_type').value = assetType;
    document.getElementById('edit_property_name').value = assetName;
    document.getElementById('edit_property_quantity').value = quantity;
    document.getElementById('edit_property_description').value = description;

    document.getElementById('edit_property').click();
}

function displayProfileForm() {
    document.getElementById('firstName').value = firstName;
    document.getElementById('lastName').value = lastName;
    document.getElementById('email').value = email;
    document.getElementById('verifyEmail').value = email;


    let timeZoneSelect = document.getElementById('timeZone');
    for (let i = 0; i < timeZoneSelect.options.length; i++) {
        if (timeZoneSelect.options[i].text === timeZone) {
            timeZoneSelect.selectedIndex = i;
            break;
        }
    }

    let genderSelect = document.getElementById('gender');
    for (let i = 0; i < genderSelect.options.length; i++) {
        if (genderSelect.options[i].text.toUpperCase() === getGender(gender)) {
            genderSelect.selectedIndex = i;
            break;
        }
    }
}

async function displayTransactionTable(page) {
    let data = await getUserTransactions(page);
    if (data && Array.isArray(data.transactions)) {
        let tableBody = document.getElementById("TransactionTableBody");
        tableBody.innerHTML = "";
        data.transactions.forEach(function (item) {
            item.date = item.date.replace(" ", "\u00A0\u00A0\u00A0");
            let row = `
            <tr>
                <td>${item.id}</td>
                <td>${getTransactionType(item.type)}</td>
                <td>${item.symbol}</td>
                <td>${item.quantity}</td>
                <td>${item.amount}</td>
                <td>${item.unit}</td>
                <td>${item.date}</td>    
                <td style="text-align: left">${item.description}</td>
            </tr>`;
            tableBody.innerHTML += row;
        });
        return await data.totalPages;
    } else {
        console.log("資料格式錯誤");
    }
}

let statisticsOverviewData = null
async function displayStatisticsOverview() {
    let tableBody = document.getElementById('statistics_overview');
    try {
        const summaryData = await fetchUserPropertySummary();
        const propertyOverviewData = await fetchStatisticsOverview();
        const latestTotalSum = summaryData.latest.filter(dataPoint => dataPoint.field === "total_sum")[0].value;
        const latestTotalSumFloat = parseFloat(latestTotalSum).toFixed(3);
        tableBody.innerHTML =
            `
            <div class="d-none d-md-block">
                <p class="statistics-title">目前總資產</p>
                <h3 class="rate-percentage">${thousands(latestTotalSum)}</h3>
                <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
            </div>
            <div class="d-none d-md-block">
                <p class="statistics-title">資金淨流量</p>
                <h3 class="rate-percentage">${thousands(parseFloat(propertyOverviewData.cash_flow).toFixed(3))}</h3>
                <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
            </div>
            ` +
            generateStatisticsTable("日收益", thousands((parseFloat(propertyOverviewData.day) * latestTotalSumFloat / 100).toFixed(3)), propertyOverviewData.day) +
            generateStatisticsTable("周收益", thousands((parseFloat(propertyOverviewData.week) * latestTotalSumFloat / 100).toFixed(3)), propertyOverviewData.week) +
            generateStatisticsTable("月收益", thousands((parseFloat(propertyOverviewData.month) * latestTotalSumFloat / 100).toFixed(3)), propertyOverviewData.month) +
            generateStatisticsTable("年收益", thousands((parseFloat(propertyOverviewData.year) * latestTotalSumFloat / 100).toFixed(3)), propertyOverviewData.year);

        statisticsOverviewData =  await propertyOverviewData;
        return statisticsOverviewData;
    } catch (error) {
        console.error(error);
    }
}

async function displayRoiStatistics(roiData) {

    let monthRoi = statisticsOverviewData.month;
    let yearRoi = statisticsOverviewData.year;
    let monthCalmarRatio= "數據不足"
    let yearCalmarRatio = "數據不足"

    if (monthRoi && monthRoi !== "數據不足" && roiData.draw_down.month.total.rate !== "數據不足"){
        let formatDrawRate = parseFloat(roiData.draw_down.month.total.rate)
        if (formatDrawRate !== 0) {
            monthCalmarRatio = parseFloat(monthRoi) / formatDrawRate;
        } else {
            monthCalmarRatio = "無限大"
        }
    }
    if (yearRoi && yearRoi !== "數據不足" && roiData.draw_down.year.total.rate !== "數據不足"){
        let formatDrawRate = parseFloat(roiData.draw_down.year.total.rate)
        if (formatDrawRate !== 0) {
            yearCalmarRatio = parseFloat(yearRoi) / formatDrawRate;
        } else {
            yearCalmarRatio = "無限大"
        }
    }

    let tableBody = document.getElementById('roiStatisticTableBody');
    let count = 1;
    if (roiData) {
        tableBody.innerHTML = "";
        tableBody.appendChild(generateRoiStatisticListTable(count++, "收益波動率", formatValue(roiData.sigma)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "平均收益率", formatValue(roiData.average)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "計算基準國家", formatValue(roiData.sharp_ratio.base_country, false)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "本月夏普比率", formatValue(roiData.sharp_ratio.month, false)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "本年夏普比率", formatValue(roiData.sharp_ratio.year, false)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "本月卡瑪比率", formatValue(monthCalmarRatio, false)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "本年卡瑪比率", formatValue(yearCalmarRatio, false)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "資產本周最大回撤", formatValue(roiData.draw_down.week.total.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "資產本月最大回撤", formatValue(roiData.draw_down.month.total.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "資產本年最大回撤", formatValue(roiData.draw_down.year.total.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "虛擬貨幣本周最大回撤", formatValue(roiData.draw_down.week.crypto.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "虛擬貨幣本月最大回撤", formatValue(roiData.draw_down.month.crypto.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "虛擬貨幣本年最大回撤", formatValue(roiData.draw_down.year.crypto.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "貨幣本周最大回撤", formatValue(roiData.draw_down.week.currency.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "貨幣本月最大回撤", formatValue(roiData.draw_down.month.currency.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "貨幣本年最大回撤", formatValue(roiData.draw_down.year.currency.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "台灣股市本周最大回撤", formatValue(roiData.draw_down.week.stock_tw.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "台灣股市本月最大回撤", formatValue(roiData.draw_down.month.stock_tw.rate)));
        tableBody.appendChild(generateRoiStatisticListTable(count++, "台灣股市本年最大回撤", formatValue(roiData.draw_down.year.stock_tw.rate)));

    } else {
        console.log("資料格式錯誤");
    }
}

async function displayNewsTable(pageNumber, category, asset) {
    let tableBody = document.getElementById("newsTableBody");
    let newsData = await fetchIndexNewsData(pageNumber, category, asset);
    if (!newsData) {
        console.log("請求新聞時發生錯誤");
        return
    }

    tableBody.innerHTML = `
        <thead>
            <tr>
            <th>標題</th>
            <th>類型</th>
            <th>發布時間</th>
            <th>來源</th>
            </tr>
        </thead>
        <tbody>
        </tbody>
    `;

    let tbody = tableBody.querySelector("tbody");
    newsData.content.forEach(news => {
        let row = document.createElement("tr");

        row.innerHTML = `
            <td><a href="${news.url}" target="_blank">${news.title}</a></td}">
            <td>${getAssetType(news.newsType)}</td>
            <td>${new Date(news.publishedAt).toLocaleString()}</td>
            <td>${news.sourceName}</td>
        `;
        tbody.appendChild(row);
    })

    return newsData.last;
}


async function updateNewsTable(prevPageButton, nextPageButton, currentPageElement, page, category, asset) {
    let currentPage = page;
    currentPageElement.textContent = currentPage;
    let isLastPage = await displayNewsTable(currentPage, category, asset);

    nextPageButton.classList.toggle('disabled', isLastPage);
    nextPageButton.style.display = isLastPage ? 'none' : '';
    nextPageButton.href = `#newsTableBody`;
    prevPageButton.classList.toggle('disabled', currentPage <= 1);
    prevPageButton.style.display = currentPage <= 1 ? 'none' : '';
    prevPageButton.href = currentPage > 1 ? `#newsTableBody` : '#';

    prevPageButton.removeEventListener('click', prevPageButton.prevHandler);
    nextPageButton.removeEventListener('click', nextPageButton.nextHandler);

    if (!isLastPage) {
        nextPageButton.nextHandler = createNewsListPage(currentPage + 1, prevPageButton, nextPageButton, currentPageElement, category, asset);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createNewsListPage(currentPage - 1, prevPageButton, nextPageButton, currentPageElement, category, asset);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }
}

async function updateTransactionTable(prevPageButton, nextPageButton, currentPageElement, page) {
    let currentPage = page;
    currentPageElement.textContent = currentPage;
    let totalPage = await displayTransactionTable(currentPage);
    let isLastPage = currentPage >= totalPage;
    nextPageButton.classList.toggle('disabled', isLastPage);
    nextPageButton.style.display = isLastPage ? 'none' : '';
    nextPageButton.href = `#userTransactionList`;
    prevPageButton.classList.toggle('disabled', currentPage <= 1);
    prevPageButton.style.display = currentPage <= 1 ? 'none' : '';
    prevPageButton.href = currentPage > 1 ? `#userTransactionList` : '#';

    prevPageButton.removeEventListener('click', prevPageButton.prevHandler);
    nextPageButton.removeEventListener('click', nextPageButton.nextHandler);

    if (!isLastPage) {
        nextPageButton.nextHandler = createTransactionListPage(currentPage + 1, prevPageButton, nextPageButton, currentPageElement);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createTransactionListPage(currentPage - 1, prevPageButton, nextPageButton, currentPageElement);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }
}

async function displayAssetsList(pageNumber, category) {
    let tableBody = document.getElementById("assetsListTableBody");
    let assetListData = await fetchAssetListData(pageNumber, category);
    if (assetListData) {
        tableBody.innerHTML = `
            <thead>
                <tr>
                <th>ID</th>
                <th>名稱</th>
                <th>類型</th>
                <th>資產訂閱狀態</th>
                </tr>
            </thead>
            <tbody>
            </tbody>
        `;
        let tbody = tableBody.querySelector("tbody");
        assetListData.forEach(asset => {
            let row = generateAssetListTable(asset);
            tbody.appendChild(row);
        })
    } else {
        console.log("請求資產列表時發生錯誤");
    }
}

async function updateAssetsList(prevPageButton, nextPageButton, currentPageElement, page, category) {
    let currentPage = page;
    currentPageElement.textContent = currentPage;
    let isLastPage = await displayAssetsList(currentPage, category);

    nextPageButton.classList.toggle('disabled', isLastPage);
    nextPageButton.style.display = isLastPage ? 'none' : '';
    nextPageButton.href = `#assetsListTableBody`;
    prevPageButton.classList.toggle('disabled', currentPage <= 1);
    prevPageButton.style.display = currentPage <= 1 ? 'none' : '';
    prevPageButton.href = currentPage > 1 ? `#assetsListTableBody` : '#';

    prevPageButton.removeEventListener('click', prevPageButton.prevHandler);
    nextPageButton.removeEventListener('click', nextPageButton.nextHandler);

    if (!isLastPage) {
        nextPageButton.nextHandler = createAssetListPage(currentPage + 1, prevPageButton, nextPageButton, currentPageElement, category);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createAssetListPage(currentPage - 1, prevPageButton, nextPageButton, currentPageElement, category);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }
}

async function displayServerStatus() {
    let serverStatus = await fetchServerStatus();
    let isCryptoOpen = document.getElementById("isCryptoOpen");
    let isStockTwOpen = document.getElementById("isStockTwOpen");
    if (serverStatus.isCryptoOpen) {
        isCryptoOpen.innerHTML = `<label class="badge badge-success text-white" style="background-color: #4DA761">開啟</label>`;
    } else {
        isCryptoOpen.innerHTML = `<label class="badge badge-danger text-white" style="background-color: #F95F53">關閉</label>`;
    }

    if (serverStatus.isStockTwOpen) {
        isStockTwOpen.innerHTML = `<label class="badge badge-success text-white" style="background-color: #4DA761">開啟</label>`;
    } else {
        isStockTwOpen.innerHTML = `<label class="badge badge-danger text-white" style="background-color: #F95F53">關閉</label>`;
    }
}

async function displayTodoList() {
    let todoList = document.getElementById('todoList');
    let todoListData = await fetchTodoList();

    todoListData.forEach(todo => {
        let priorityBadge;
        if (todo.priority === "HIGH") {
            priorityBadge = `<div class="badge badge-opacity-danger me-3" style="background-color: #ffbfb1">${getPriority(todo.priority)}</div>`;
        } else if (todo.priority === "MEDIUM") {
            priorityBadge = `<div class="badge badge-opacity-warning me-3">${getPriority(todo.priority)}</div>`;
        } else {
            priorityBadge = `<div class="badge badge-opacity-success me-3">${getPriority(todo.priority)}</div>`;
        }


        let todoItem = `
            <li class="d-block">
                <div class="form-check w-100">
                    <label class="form-check-label" style="white-space: normal; overflow-wrap: break-word; word-wrap: break-word">
                        <input class="checkbox" type="checkbox" data-todo-id="${todo.id}"> ${todo.content}
                        <i class="input-helper rounded"></i>
                    </label>
                    <div class="d-flex mt-2">
                        <div class="ps-4 text-small me-3">${todo.dueDate}</div>
                        ${priorityBadge}
                    </div>
                </div>
            </li>
        `;
        todoList.innerHTML += todoItem;
    });

}

