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
                <td>${item.quantity}</td>
                <td style="text-align: right">${item.currentPrice}</td>
                <td style="text-align: right">${numTotal.toFixed(3).replace(/\.?0+$/, "")}</td>
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

async function displayTransactionTable() {
    let data = await getUserAllTransactions();
    if (data && Array.isArray(data)) {
        let tableBody = document.getElementById("TransactionTableBody");
        tableBody.innerHTML = "";
        data.forEach(function (item) {
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
    } else {
        console.log("資料格式錯誤");
    }
}

async function displayStatisticsOverview() {
    let tableBody = document.getElementById('statistics_overview');
    try {
        const summaryData = await fetchUserPropertySummary();
        const propertyOverviewData = await fetchStatisticsOverview();
        const latestTotalSum = summaryData.latest.filter(dataPoint => dataPoint.field === "total_sum")[0].value;
        const latestTotalSumFloat = parseFloat(latestTotalSum).toFixed(2);
        tableBody.innerHTML =
            `
            <div class="d-none d-md-block">
                <p class="statistics-title">目前總資產</p>
                <h3 class="rate-percentage">${latestTotalSum}</h3>
                <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
            </div>
            <div class="d-none d-md-block">
                <p class="statistics-title">資金淨流量</p>
                <h3 class="rate-percentage">${propertyOverviewData.cash_flow}</h3>
                <p class="text-success d-flex"><i class="mdi mdi-menu-up"></i><span>0%</span></p>
            </div>
            ` +
            generateStatisticsTable("日收益", (parseFloat(propertyOverviewData.day) * latestTotalSumFloat / 100).toFixed(3), propertyOverviewData.day) +
            generateStatisticsTable("周收益", (parseFloat(propertyOverviewData.week) * latestTotalSumFloat / 100).toFixed(3), propertyOverviewData.week) +
            generateStatisticsTable("月收益", (parseFloat(propertyOverviewData.month) * latestTotalSumFloat / 100).toFixed(3), propertyOverviewData.month) +
            generateStatisticsTable("年收益", (parseFloat(propertyOverviewData.year) * latestTotalSumFloat / 100).toFixed(3), propertyOverviewData.year);

    } catch (error) {
        console.error(error);
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
        nextPageButton.nextHandler = createNewsHandleNext(currentPage + 1, prevPageButton, nextPageButton, currentPageElement, category, asset);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createNewsHandlePrev(currentPage - 1, prevPageButton, nextPageButton, currentPageElement, category, asset);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }
}


function createNewsHandlePrev(newPage, prevPageButton, nextPageButton, currentPageElement, category, asset) {
    return function (e) {
        e.preventDefault();
        updateNewsTable(prevPageButton, nextPageButton, currentPageElement, newPage, category, asset);
        scrollToElement('newsTableBody');
    };
}

function createNewsHandleNext(newPage, prevPageButton, nextPageButton, currentPageElement, category, asset) {
    return function (e) {
        e.preventDefault();
        updateNewsTable(prevPageButton, nextPageButton, currentPageElement, newPage, category, asset);
        scrollToElement('newsTableBody');
    };
}

function scrollToElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.scrollIntoView({behavior: 'smooth', block: 'start'});
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
    nextPageButton.href = `#newsTableBody`;
    prevPageButton.classList.toggle('disabled', currentPage <= 1);
    prevPageButton.style.display = currentPage <= 1 ? 'none' : '';
    prevPageButton.href = currentPage > 1 ? `#newsTableBody` : '#';

    prevPageButton.removeEventListener('click', prevPageButton.prevHandler);
    nextPageButton.removeEventListener('click', nextPageButton.nextHandler);

    if (!isLastPage) {
        nextPageButton.nextHandler = createAssetListHandleNext(currentPage + 1, prevPageButton, nextPageButton, currentPageElement, category);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createAssetListHandlePrev(currentPage - 1, prevPageButton, nextPageButton, currentPageElement, category);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }
}

function createAssetListHandlePrev(newPage, prevPageButton, nextPageButton, currentPageElement, category) {
    return function (e) {
        e.preventDefault();
        updateAssetsList(prevPageButton, nextPageButton, currentPageElement, newPage, category);
        scrollToElement('newsTableBody');
    };
}

function createAssetListHandleNext(newPage, prevPageButton, nextPageButton, currentPageElement, category) {
    return function (e) {
        e.preventDefault();
        updateAssetsList(prevPageButton, nextPageButton, currentPageElement, newPage, category);
        scrollToElement('newsTableBody');
    };
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

