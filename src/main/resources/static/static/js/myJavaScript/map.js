function getAssetType(type) {
    const map = {
        'STOCK_TW': "台灣股票",
        'STOCK_US': "美國股票",
        'CRYPTO': "加密貨幣",
        "CURRENCY": "貨幣",
        "貨幣": "CURRENCY",
        '美國股票': "STOCK_US",
        '台灣股票': "STOCK_TW",

    }
    return map[type] || type;
}

function getTransactionType(type) {
    const map = {
        'BUY': "買入",
        'SELL': "賣出",
        'DEPOSIT': "存款",
        'WITHDRAW': "提款"
    }
    return map[type] || type;
}


function getRole(role) {
    const map = {
        "ADMIN": "管理員",
        "UNVERIFIED_USER": "未驗證用戶",
        "VERIFIED_USER": "已驗證用戶"
    }
    return map[role] || "未知用戶";
}

function getPropertySummaryName (summaryName) {
    const map = {
        "total_sum": "總資產",
        "currency_sum": "貨幣資產",
        "crypto_sum": "加密貨幣資產",
        "stock_tw_sum": "台灣股票資產"
    }
    return map[summaryName] || summaryName;
}