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
                <td>$0</td>
                <td>${item.description}</td>
                <td><a href="#" style="color: blue" onclick="displayEditProperty(this)">編輯</a>&nbsp&nbsp&nbsp<a id="deleteButton" data-property-type="${item.assetType}" data-property-id="${item.propertyId}" href="#" style="color: red" onclick="deleteProperty(this, this)">刪除</a></td>
            </tr>`;
            tableBody.innerHTML += row;
        });
    } else {
        console.log("資料格式錯誤");
    }
}

function displayEditProperty(editButton) {
    var currentRow = editButton.closest('tr');

    var assetId = currentRow.cells[0].textContent;
    var assetType = currentRow.cells[1].textContent;
    var assetName = currentRow.cells[2].textContent;
    var quantity = currentRow.cells[3].textContent;
    var description = currentRow.cells[5].textContent;

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