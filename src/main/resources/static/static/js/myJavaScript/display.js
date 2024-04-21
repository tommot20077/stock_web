async function displayPropertyTable() {
    let data = await getUserAllProperties();
    if(data && Array.isArray(data)){
        let tableBody = document.getElementById("propertyTableBody");
        tableBody.innerHTML = "";

        data.forEach(function (item) {
            let row = `
            <tr>
                <td>${item.propertyId}</td>
                <td>${getAssetType(item.assetType)}</td>
                <td>${item.assetName}</td>
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
            generateStatisticsTable("日收益",parseFloat(propertyOverviewData.day) * latestTotalSumFloat , propertyOverviewData.day) +
            generateStatisticsTable("周收益",parseFloat(propertyOverviewData.week) * latestTotalSumFloat , propertyOverviewData.week) +
            generateStatisticsTable("月收益",parseFloat(propertyOverviewData.month) * latestTotalSumFloat , propertyOverviewData.month) +
            generateStatisticsTable("年收益",parseFloat(propertyOverviewData.year) * latestTotalSumFloat , propertyOverviewData.year);

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