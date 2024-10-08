function generateStatisticsTable(title, value1, value2) {
    let display1 = value1;
    let display2 = value2;
    if (value2 === "數據不足") {
        display1 = "數據不足";
        display2 = "0";
        return `
        <div class="d-none d-md-block">
            <p class="statistics-title">${title}</p>
            <h3 class="rate-percentage">${display1}</h3>
            <p class="${parseFloat(display2) < 0 ? 'text-danger' : 'text-success'} d-flex"><i class="mdi ${parseFloat(display2) < 0 ? 'mdi-menu-down' : 'mdi-menu-up'}"></i><span>${display2}%</span></p>
        </div>
    `;
    } else if (value1 === "數據不足") {
        display1 = "0";
        display2 = "數據不足";
        return `
        <div class="d-none d-md-block">
            <p class="statistics-title">${title}</p>
            <h3 class="rate-percentage">${display2}</h3>
            <p class="${parseFloat(display1) < 0 ? 'text-danger' : 'text-success'} d-flex"><i class="mdi ${parseFloat(display1) < 0 ? 'mdi-menu-down' : 'mdi-menu-up'}"></i><span>${display1}%</span></p>
        </div>
    `;
    }

    return `
        <div class="d-none d-md-block">
            <p class="statistics-title">${title}</p>
            <h3 class="rate-percentage">${display1}</h3>
            <p class="${parseFloat(display2) < 0 ? 'text-danger' : 'text-success'} d-flex"><i class="mdi ${parseFloat(display2) < 0 ? 'mdi-menu-down' : 'mdi-menu-up'}"></i><span>${display2}%</span></p>
        </div>
    `;
}

function generateAssetListTable(asset) {
    let assetId = asset.assetId;
    let assetName = asset.assetName;
    let type = asset.type;
    let isSubscribed = asset.isSubscribed;
    let row = document.createElement("tr");
    let selectAsset = isSubscribed ?
        `<a href="/asset_info/${assetId}">${assetId}</a>` :
        `<span>${assetId}</span>`;

    row.innerHTML =
        `   
            <td>${selectAsset}</td>
            <td>${assetName}</td>
            <td>${getAssetType(type)}</td>
            <td>${isSubscribed === true ? "已有用戶訂閱" : "未有用戶訂閱"}</td>
        `;
    return row;
}

function generateRoiStatisticListTable(count, name, value) {
    let row = document.createElement("tr");
    row.innerHTML =
        `
            <td>${count}</td>
            <td>${name}</td>
            <td>${value}</td>
        `;
    count++;
    return row;
}

function formatValue(value, isPercentage = true) {
    if (typeof value === "number") {
        return isPercentage === true ? parseFloat(value).toFixed(3) + " %" : parseFloat(value).toFixed(3);
    } else {
        return value;
    }
}

function getAssetParamFromUrl() {
    const pathArray = window.location.pathname.split('/');
    return pathArray[pathArray.length - 1];
}

function thousands(value) {
    if (value) {
        value += "";
        let arr = value.split(".");
        let re = /(\d{1,3})(?=(\d{3})+$)/g;

        return arr[0].replace(re, "$1,") + (arr.length === 2 ? "." + arr[1] : "");
    } else {
        return ''
    }
}

function createAssetListPage(newPage, prevPageButton, nextPageButton, currentPageElement, category) {
    return function (e) {
        e.preventDefault();
        updateAssetsList(prevPageButton, nextPageButton, currentPageElement, newPage, category);
        scrollToElement('assetsListTableBody');
    };
}

function createNewsListPage(newPage, prevPageButton, nextPageButton, currentPageElement, category, asset) {
    return function (e) {
        e.preventDefault();
        updateNewsTable(prevPageButton, nextPageButton, currentPageElement, newPage, category, asset);
        scrollToElement('newsTableBody');
    };
}

function createTransactionListPage(newPage, prevPageButton, nextPageButton, currentPageElement) {
    return function (e) {
        e.preventDefault();
        updateTransactionTable(prevPageButton, nextPageButton, currentPageElement, newPage);
        scrollToElement('userTransactionList');
    };
}

function scrollToElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.scrollIntoView({behavior: 'smooth', block: 'start'});
    }
}

function capitalizeFirstLetter(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

function getColorsArray(dataLength) {
    var colors= [
        '#FF6384',
        '#36A2EB',
        '#FFCE56',
        '#4BC0C0',
        '#9966FF'
    ];
    for (var i = 0; i < dataLength; i++) {
        colors.push(colors[i % colors.length]);
    }
    return colors;
}

function searchFunction() {
    let searchTimeout;
    let searchResults = document.getElementById('searchResults');
    document.getElementById('searchInput').addEventListener('input', function() {
        clearTimeout(searchTimeout);
        let query = this.value.trim();
        if (query.length > 0) {
            searchTimeout = setTimeout(() => searchAsset(query), 300);
        } else {
            searchResults.style.display = 'none';
            searchResults.innerHTML = '';
        }
    })

    document.addEventListener('click', function(event) {
        let searchResults = document.getElementById('searchResults');
        let searchInput = document.getElementById('searchInput');
        if (!searchResults.contains(event.target) && event.target !== searchInput) {
            setTimeout(() => {
                searchResults.innerHTML = '';
            }, 100);
        }
    });
}