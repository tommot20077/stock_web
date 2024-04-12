let userPreferredCurrency;
function getUserDetails() {
    fetch("/api/user/common/getUserDetail")
        .then(response => {
            if (response.ok) return response.json();
            throw new Error("獲取用戶資料失敗");
        }).then(data => {
        firstName = data.firstName;
        lastName = data.lastName;
        email = data.email;
        userId = data.id;
        role = data.role;
        gender =data.gender;
        timeZone = data.timeZone;
        userPreferredCurrency = data.preferredCurrency;
        if (document.querySelector('.welcome-text > span')) {
            document.querySelector('.welcome-text > span').textContent = firstName;
        }
        if (document.querySelector('.welcome-text > a')) {
            document.querySelector('.welcome-text > a').textContent = " (" + getRole(role) + ")";
        }
        if (document.getElementById("userProfileForm")) {
            fetchPropertyName("preferred_currency", "CURRENCY")
            displayProfileForm();
        }
        if (document.getElementById("transaction_unit")) {
            fetchPropertyName("transaction_unit", "CURRENCY")
        }
    })
}


function logout(isRedirectImmediately, timeSet) {
    fetch("/logout?isRedirect=" + isRedirectImmediately)
        .then(response => {
            if (response.ok) {
                setTimeout(() => {
                    window.location.href = "/login";
                }, timeSet);

            } else {
                throw new Error("登出失敗");
            }
        }).catch(error => {
        console.error(error);
    })

}

function getGender(gender) {
    const map = {
        "MALE": "男性",
        "男性": "MALE",
        "FEMALE": "女性",
        "女性": "FEMALE",
        "OTHER": "其他",
        "其他": "OTHER"
    };
    return map[gender] || "其他";
}





function getTimeZoneList() {
    fetch("/api/user/common/getTimeZoneList")
        .then(response => {
            if (response.ok) return response.json();
            throw new Error("獲取時區列表失敗");
        }).then(data => {
        let timeZoneSelect = document.getElementById('timeZone');
        for (let i = 0; i < data.length; i++) {
            let option = document.createElement('option');
            option.text = data[i];
            timeZoneSelect.add(option);
        }
    })
}


function updateUserProfile() {
    let formSubmit = document.getElementById('userProfileFormSubmit')
    if (formSubmit) {
        formSubmit.addEventListener('click', (event) => {
            event.preventDefault();
            hideById('UpdateProfileSuccess');
            hideById('UpdateProfileFail');

            let newPassword = document.getElementById('newPassword').value;
            let newRePassword = document.getElementById('newRePassword').value;
            let firstName = document.getElementById('firstName').value;
            let lastName = document.getElementById('lastName').value;
            let email = document.getElementById('email').value;
            let gender = getGender(document.getElementById('gender').value);
            let timeZone = document.getElementById('timeZone').value;
            let preferredCurrency = document.getElementById('preferred_currency').value;


            if (newPassword.trim().length > 0 && newPassword.trim() !== newRePassword.trim()) {
                displayError('密碼不一致', 'UpdateProfileFail');
                return;
            }

            this.disabled = true;
            showSpinner(false);
            document.getElementById('cancelUpdateProfile').addEventListener('click', () => {
                this.disabled = true;
                hideSpinner();
            });
            document.getElementById('confirmUpdateProfile').addEventListener('click', (event) => {
                this.disabled = true;
                event.preventDefault();

                if (document.getElementById('originalPassword').value.trim().length === 0) {
                    displayError('請輸入密碼', 'UpdateProfileFail');
                    return;
                }
                hideById('confirmCard');
                showSpinner(true);
                let originalPassword = document.getElementById('originalPassword').value;

                let formData = {
                    originalPassword: originalPassword,
                    firstName: firstName,
                    lastName: lastName,
                    email: email,
                    gender: gender,
                    timeZone: timeZone,
                    preferredCurrency: preferredCurrency
                }

                if (newPassword.trim().length > 0) {
                    formData.newPassword = newPassword;
                }

                let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
                let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');

                fetch("/api/user/common/updateUserDetail", {
                    method: 'POST',
                    headers: {
                        [csrfHeader]: csrfToken,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(formData)
                }).then(response => {
                    if (response.ok) {
                        showSpinner(false);
                        hideById('UpdateProfileFail');
                        showFlexById('confirmCard');
                        showFlexById("UpdateProfileSuccess");
                        logout(false,3000);
                    } else {
                        return response.text().then(data => {
                            throw new Error(data);
                        });
                    }
                }).catch(data => {
                    showSpinner(false);
                    hideById('UpdateProfileSuccess');
                    showFlexById('confirmCard');
                    displayError(data, 'UpdateProfileFail')
                })
            });
        });
    }
}

function sendVerificationEmail() {
    if (document.getElementById('sendVerificationEmailButton')) {
        document.getElementById('sendVerificationEmailButton').addEventListener('click', (event) => {
            event.target.disabled = true;
            event.preventDefault()

            showSpinner(true);
            hideById('confirmCard');
            hideById("VerificationEmailFail")
            hideById('VerificationEmailSuccess');
            let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
            let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
            fetch("/api/user/common/sendVerificationEmail", {
                method: 'POST',
                headers: {
                    [csrfHeader]: csrfToken,
                    'Content-Type': 'application/json'
                }
            }).then(response => {
                    if (response.ok) {
                        hideSpinner();
                        hideById('VerificationEmailFail');
                        document.getElementById("verifyEmailSuccess").style.display = "block";
                    } else {
                        return response.text().then(data => {
                            throw new Error(data);
                        });
                    }
                }
            ).catch(data => {
                hideSpinner();
                hideById('VerificationEmailSuccess');
                displayError(data, 'VerificationEmailFail')
            })
        })
    }
}

async function getUserAllProperties() {
    const tableBody = document.getElementById('propertyTableBody');
    tableBody.innerHTML =
        `<td colspan="9">
            <div class="loadingio-spinner-dual-ball-l2u3038qtw8">
                <div class="ldio-4pqo44ipw4">
                    <div></div><div></div><div></div>
                </div>
            </div>
        </td>`;
    tableBody.style.cssText = "text-align: center; padding: 20px; font-size: 1.5em;";
    try {
        let response = await fetch("/api/user/property/getUserAllProperty",{
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return await response.json();
        } else {
            new Error('錯誤的請求: ' + response.status +'' + response.statusText);
        }
    } catch (error) {
        console.error('請求錯誤: ', error);
    }
}

function getPropertyType(type_id){
    if (document.getElementById(type_id)) {
        fetch('/api/user/property/getPropertyType')
            .then(response => response.json())
            .then(data => {
                let select = document.getElementById(type_id);
                select.innerHTML = '';
                let option = document.createElement('option');
                option.text = '請選擇';
                option.value = '';
                select.add(option);

                data.forEach(function (type) {
                    let option = document.createElement('option');
                    option.text = getAssetType(type);
                    option.value = type;
                    select.add(option);
                })
            })
            .catch(error => console.error(error));
    }
}

function detectionPropertyTypeChange(name_id, type_id) {
    if (document.getElementById(name_id)) {
        document.getElementById(type_id).addEventListener('change', (event) => {
            let selectType = event.target.value;
            fetchPropertyName(name_id,selectType);
        })
    }
}

function fetchPropertyName (name_id, type){
    let cacheKey = '/api/user/property/getAllNameByPropertyType?type=' + type;
    caches.match(cacheKey).then(result => {
        if (result) {
            return result.json()
        } else {
            return fetch(cacheKey)
                .then(response =>{
                    let cloneResponse = response.clone();
                    let jsonResponse = response.json();
                    caches.open("select-list-cache").then(cache => {
                        cache.put(cacheKey, cloneResponse);
                    })
                    return jsonResponse;
                })
        }
    }).then(data => {
        let select = document.getElementById(name_id);
        let foundPreferCurrency = false;
        select.innerHTML = '';
        Object.entries(data).forEach(function ([key, value]) {
            let option = document.createElement('option');
            if (key === value) {
                option.text = key;
            } else {
                option.text = key + "-" + value;
            }
            option.value = key;

            if (key === userPreferredCurrency) {
                option.selected = true;
                foundPreferCurrency = true;
            }

            select.add(option);
        })

        if (!foundPreferCurrency && select.options.length > 0) {
            select.options[0].selected = true;
        }
    })
}






function addOrUpdatePropertyForm(event) {
    if (document.getElementById('add_property_form') || document.getElementById('edit_property_form')) {
        event.preventDefault();
        showSpinner(true);
        hideById("confirmCard")
        hideById("success_edit_message");
        hideById("success_add_message");
        hideById("fail_edit_message");
        hideById("fail_add_message");
        let submitType = event.target.getAttribute('data-type');
        let formId = submitType === 'ADD' ? 'add_property_form' : 'edit_property_form';
        let formElement = document.getElementById(formId);


        if (formElement) {
            let formData = new FormData(formElement);
            let formDataJson = {};
            let type;

            if (submitType === 'ADD') {
                formDataJson = {
                    type: formData.get('add_property_type'),
                    symbol: formData.get('add_property_name'),
                    quantity: formData.get('add_property_quantity'),
                    description: formData.get('add_property_description'),
                    operationType: 'ADD'
                }
                type = formData.get('add_property_type').toLowerCase();
            } else if (submitType === 'UPDATE') {
                formDataJson = {
                    id: formData.get('edit_property_id'),
                    type: formData.get('add_property_type'),
                    symbol: formData.get('edit_property_name'),
                    quantity: formData.get('edit_property_quantity'),
                    description: formData.get('edit_property_description'),
                    operationType: 'UPDATE'
                }
                type = getAssetType(formData.get('edit_property_type')).toLowerCase();
            } else {
                hideSpinner();
                let errorId = submitType === 'ADD' ? 'fail_add_message' : 'fail_edit_message';
                displayError('無法識別的操作類型', errorId);
                return;
            }

            if (!validatePropertyForm(formData, submitType)) {
                return;
            }
            let propertyListDto = {
                propertyList: [formDataJson]
            };

            let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
            let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');

            fetch('/api/user/property/modify/' + type, {
                method: 'POST',
                headers: {
                    [csrfHeader]: csrfToken,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(propertyListDto)
            }).then(response => {
                if (response.ok) {
                    return response.text();
                } else {
                    return response.text().then(data => {
                        throw new Error(data);
                    });
                }
            }).then(response => {
                hideSpinner();
                if (submitType === 'ADD') {
                    showFlexById('success_add_message');
                    setTimeout(() => {
                        window.location.reload();
                    }, 2000);
                } else {
                    showFlexById('success_edit_message');
                    setTimeout(() => {
                        window.location.reload();
                    }, 2000);
                }
            }).catch(error => {
                hideSpinner();
                if (submitType === 'ADD') {
                    displayError(error,'fail_add_message');
                } else {
                    displayError(error,'fail_edit_message');
                }
            });
        }
    }
}

function deleteProperty(event, elementId) {
    this.disabled = true;
    showSpinner(false);
    let propertyId = elementId.getAttribute('data-property-id');
    let propertyType = elementId.getAttribute('data-property-type').toLowerCase();
    let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
    let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');

    document.getElementById('cancelDeleteProperty').addEventListener("click", ()=> {
        this.disabled = true;
        hideSpinner();
    });
    document.getElementById('confirmDeleteProperty').addEventListener("click", ()=> {
        this.disabled = true;
        hideById('deletePropertySuccess');
        hideById('deletePropertyFail');
        hideById('confirmCard');
        showSpinner(true);

        let formDataJson = {
            id: propertyId,
            operationType: 'REMOVE'
        }
        let propertyListDto = {
            propertyList: [formDataJson]
        };

        fetch("/api/user/property/modify/" + propertyType, {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(propertyListDto)
        }).then(response => {
            if (response.ok) {
                return response.text().then(data => {
                    hideSpinner();
                    window.location.reload();
                });
            } else {
                return response.text().then(data => {
                    throw new Error(data);
                });
            }
        }).catch(error => {
            showSpinner(false);
            showFlexById('confirmCard');
            hideById('deletePropertySuccess');
            displayError(error, 'deletePropertyFail');
        })
    });
}

async function getUserAllTransactions() {
    const tableBody = document.getElementById('TransactionTableBody');
    tableBody.innerHTML =
        `<td colspan="8"">
            <div class="loadingio-spinner-dual-ball-l2u3038qtw8">
                <div class="ldio-4pqo44ipw4">
                    <div></div><div></div><div></div>
                </div>
            </div>
        </td>`;
    tableBody.style.cssText = "text-align: center; padding: 20px; font-size: 1.5em;";

    try {
        let response = await fetch("/api/user/transaction/getUserAllTransaction",{
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return await response.json();
        } else {
            new Error('錯誤的請求: ' + response.status +'' + response.statusText);
        }
    } catch (error) {
        console.error('請求錯誤: ', error);
    }
}

function addTransaction(event) {
    let form = document.getElementById("add_transaction_form");
    if (form) {
        this.disabled = true;
        event.preventDefault();
        showSpinner(true);
        hideById("success_message");
        hideById("fail_message");

        let formData = new FormData(form);
        let formDataJson = {};
        if (!validateTransaction(formData)) {
            hideSpinner();
            this.disabled = false;
            return;
        }



        formDataJson = {
            type: formData.get('operation_type'),
            symbol: formData.get('transaction_name'),
            quantity: formData.get('transaction_quantity'),
            amount: formData.get('transaction_amount'),
            unit: formData.get('transaction_unit'),
            date: formData.get('transaction_time'),
            description: formData.get('transaction_description')
        }

        let transactionListDto = {
            transactionList: [formDataJson]
        };

        let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
        let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');

        fetch('/api/user/transaction/operation', {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(transactionListDto)
        }).then(response => {
            if (response.ok) {
                return response.text().then(data => {
                    hideSpinner();
                    showFlexById("success_message");
                    setTimeout(() => {
                        window.location.reload();
                    }, 2000);
                    });
            } else {
                return response.text().then(data => {
                    throw new Error(data);
                });
            }
        }).catch(error => {
            hideSpinner();
            displayError(error, "fail_message");
        })
    }

}













function hideById(id) {
    if (document.getElementById(id)) {
        document.getElementById(id).style.display = 'none';
    }
}
function showFlexById(id) {
    if (document.getElementById(id)) {
        document.getElementById(id).style.display = 'flex';
    }
}

function showSpinner(showRings) {
    document.getElementById('loading-spinner').style.display = 'flex';
    if (!showRings) {
        document.getElementById('lds-ring').style.display = 'none';
    } else {
        document.getElementById('lds-ring').style.display = 'flex';
    }
}
function hideSpinner() {
    document.getElementById('loading-spinner').style.display = 'none';
}

function displayError(error, elementId) {
    // Get the element by id
    let element = document.getElementById(elementId);

    // Set the error message and color
    element.innerText = error;
    element.style.color = 'red';
    element.style.display = "block"
}


function validatePropertyForm(formData, submitType) {
    let quantity = submitType === 'ADD' ? formData.get('add_property_quantity') : formData.get('edit_property_quantity');
    let errorMessage = submitType === 'ADD' ? 'fail_add_message' : 'fail_edit_message';

    if (quantity === '' || quantity === null || Number(quantity) <= 0) {
        displayError('數量必須大於0', errorMessage);
        hideSpinner();
        return false;
    }

    let propertyType = document.getElementById('add_property_type').value;
    if (!propertyType && errorMessage === 'fail_add_message') {
        displayError('必須選擇類型', errorMessage);
        hideSpinner();
        return false;
    }

    let propertyName = document.getElementById('add_property_name').value;
    if (!propertyName && errorMessage === 'fail_add_message') {
        displayError('必須選擇名稱', errorMessage);
        hideSpinner();
        return false;
    }
    return true;
}

function validateTransaction(formData) {
    let quantity = formData.get("transaction_quantity");
    let amount = formData.get("transaction_amount");
    let transactionType = formData.get("transaction_type");
    let transactionName = formData.get("transaction_name");
    let date = formData.get("transaction_time");
    let errorMessage = "fail_message";

    if (quantity === '' || quantity === null || Number(quantity) <= 0) {
        displayError('數量必須大於0', errorMessage);
        hideSpinner();
        return false;
    }

    if (amount === '' || amount === null || Number(amount) < 0) {
        displayError('金額必須大於0', errorMessage);
        hideSpinner();
        return false;
    }

    if (!transactionType) {
        displayError('必須選擇類型', errorMessage);
        hideSpinner();
        return false;
    }

    if (!transactionName) {
        displayError('必須選擇名稱', errorMessage);
        hideSpinner();
        return false;
    }

    if (!date) {
        displayError('必須選擇日期', errorMessage);
        hideSpinner();
        return false;
    }

    return true;
}