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
    let assetHref;
    if (isSubscribed === true) {
        assetHref = "/asset_info/" + assetId;
    } else {
        assetHref = "#";
    }
    row.innerHTML =
        `   
            <td><a href=${assetHref}>${assetId}</a></td>
            <td>${assetName}</td>
            <td>${getAssetType(type)}</td>
            <td>${isSubscribed === true ? "已有用戶訂閱" : "未有用戶訂閱"}</td>
        `;
    return row;
}

function getAssetParamFromUrl() {
    const pathArray = window.location.pathname.split('/');
    return pathArray[pathArray.length - 1];
}
