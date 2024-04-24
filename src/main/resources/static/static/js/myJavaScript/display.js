async function displayPropertyTable() {
    let data = await fetchUserAllProperties();
    if(data && Array.isArray(data)){
        let tableBody = document.getElementById("propertyTableBody");
        tableBody.innerHTML = "";

        data.forEach(function (item) {
            let row = `
            <tr>
                <td>${item.propertyId}</td>
                <td>${getAssetType(item.assetType)}</td>
                <td><a href="/asset_info/${item.assetId}">${item.assetName}</a></td>
                <td>${item.quantity}</td>
                <td style="text-align: right">${item.currentPrice}</td>
                <td style="text-align: right">${item.currentTotalPrice}</td>
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
    if(data && Array.isArray(data)){
        let tableBody = document.getElementById("subscribeTableBody");
        tableBody.innerHTML = "";
        data.forEach(function (item) {
            if (item.removeAble === true) {
                item.removeAble = '可以取消訂閱';
            } else if (item.removeAble === false) {
                item.removeAble = '此為用戶資產，由伺服器訂閱';
            }


            let row = `
            <tr>
                <td>${item.assetId}</td>
                <td>${getAssetType(item.assetType)}</td>
                <td>${item.subscribeName}</td>
                <td>${item.removeAble}</td>
                <td><a id="deleteButton" data-subscribe-name="${item.subscribeName}" data-subscribe-type="${item.assetType}" href="#" style="color: red" onclick="deleteSubscription(this, this)">刪除</a></td>
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
    if(data && Array.isArray(data)){
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

async function displayStatisticsOverview () {
    let tableBody = document.getElementById('statistics_overview');
    try {
        const summaryData = await INDEX_NAMESPACE.fetchUserPropertySummary();
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
            generateStatisticsTable("日收益",(parseFloat(propertyOverviewData.day) * latestTotalSumFloat / 100).toFixed(3) , propertyOverviewData.day) +
            generateStatisticsTable("周收益",(parseFloat(propertyOverviewData.week) * latestTotalSumFloat / 100).toFixed(3) , propertyOverviewData.week) +
            generateStatisticsTable("月收益",(parseFloat(propertyOverviewData.month) * latestTotalSumFloat / 100).toFixed(3) , propertyOverviewData.month) +
            generateStatisticsTable("年收益",(parseFloat(propertyOverviewData.year) * latestTotalSumFloat / 100).toFixed(3) , propertyOverviewData.year);

    } catch (error) {
        console.error(error);
    }
}

function generateStatisticsTable(title, value, percentage) {
    let displayValue = value;
    let displayPercentage = percentage;
    if (percentage === "數據不足") {
        displayValue = "數據不足";
        displayPercentage = "0";
    }

    return `
        <div class="d-none d-md-block">
            <p class="statistics-title">${title}</p>
            <h3 class="rate-percentage">${displayValue}</h3>
            <p class="${parseFloat(displayPercentage) < 0 ? 'text-danger' : 'text-success'} d-flex"><i class="mdi ${parseFloat(displayPercentage) < 0 ? 'mdi-menu-down' : 'mdi-menu-up'}"></i><span>${displayPercentage}%</span></p>
        </div>
    `;
}

async function displayNewsTable(pageNumber) {
    let tableBody = document.getElementById("newsTableBody");
    let newsData = await fetchIndexNewsData(pageNumber);
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
            <td>${news.newsType}</td>
            <td>${new Date(news.publishedAt).toLocaleString()}</td>
            <td>${news.sourceName}</td>
        `;
        tbody.appendChild(row);
    })

    return newsData.last;
}


async function updateNewsTable(prevPageButton, nextPageButton, currentPageElement, page) {
    let currentPage = page;
    currentPageElement.textContent = currentPage;
    let isLastPage = await displayNewsTable(currentPage);

    nextPageButton.classList.toggle('disabled',isLastPage);
    nextPageButton.style.display = isLastPage ? 'none' : '';
    nextPageButton.href = `#newsTableBody`;
    prevPageButton.classList.toggle('disabled', currentPage <= 1);
    prevPageButton.style.display = currentPage <= 1 ? 'none' : '';
    prevPageButton.href = currentPage > 1 ? `#newsTableBody` : '#';

    prevPageButton.removeEventListener('click', prevPageButton.prevHandler);
    nextPageButton.removeEventListener('click', nextPageButton.nextHandler);

    if (!isLastPage) {
        nextPageButton.nextHandler = createHandleNext(currentPage + 1, prevPageButton, nextPageButton, currentPageElement);
        nextPageButton.addEventListener('click', nextPageButton.nextHandler);
    }

    if (currentPage > 1) {
        prevPageButton.prevHandler = createHandlePrev(currentPage - 1, prevPageButton, nextPageButton, currentPageElement);
        prevPageButton.addEventListener('click', prevPageButton.prevHandler);
    }

}


function createHandlePrev(newPage, prevPageButton, nextPageButton, currentPageElement) {
    return function(e) {
        e.preventDefault();
        updateNewsTable(prevPageButton, nextPageButton, currentPageElement, newPage);
        scrollToElement('newsTableBody');
    };
}

function createHandleNext(newPage, prevPageButton, nextPageButton, currentPageElement) {
    return function(e) {
        e.preventDefault();
        updateNewsTable(prevPageButton, nextPageButton, currentPageElement, newPage);
        scrollToElement('newsTableBody');
    };
}
function scrollToElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}