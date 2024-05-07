let count = 0;

function generateAdminCommandTable(tbodyId, name, paramDescription, url, method, csrfToken) {
    count++;
    let tbody = document.getElementById(tbodyId)
    let row = document.createElement("tr");
    const commandButton = `execute-${count}`;
    row.innerHTML =
        `   
            <td>${name}</td}">
            <td>${paramDescription}</td>
            <td><input type="text" class="admin-command-param-${count} form-control"></td>
            <td><button class="btn btn-primary btn-rounded btn-fw" id="${commandButton}" onclick="executeAdminCommand('${method}', '${url}', '${csrfToken}')">執行</button></td>
        `;
    tbody.appendChild(row);

    document.getElementById(commandButton).addEventListener('click', function () {
        showSpinner(false);
        executeAdminCommand(method, url, csrfToken, `.admin-command-param-${count}`);
        document.getElementById("close").addEventListener('click', function () {
            window.location.reload();
        })
    })
}

function executeAdminCommand(method, baseUrl, csrfToken, inputSelector) {
    let commandParam = document.querySelector(inputSelector).value;

    fetch(baseUrl, {
        method: method,
        redirect: 'manual',
        headers: {
            'Content-Type': 'application/json',
            "X-CSRF-TOKEN": csrfToken
        }
    })
        .then(response => {
            if (response.status === 0) {
                return "錯誤的請求參數或者路徑: " + response.url;
            } else if (response.status !== 200) {
                throw new Error('請求失敗，回應碼: ' + response.status);
            }
            return response.text();
        })
        .then(data => {
            document.getElementById("responseMessage").innerText = data;
            document.getElementById("current-loader").style.display = "none";
        })
        .catch(error => {
            document.getElementById("responseMessage").innerText = error;
            document.getElementById("current-loader").style.display = "none";
        });
}