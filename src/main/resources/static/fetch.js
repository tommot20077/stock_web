function getUserDetails() {
    fetch("/api/user/getUserDetail")
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

        if (document.querySelector('.welcome-text > span')) {
            document.querySelector('.welcome-text > span').textContent = firstName;
        }
        if (document.querySelector('.welcome-text > a')) {
            document.querySelector('.welcome-text > a').textContent = " (" + getRole(role) + ")";
        }
        if (document.getElementById("userProfileForm")) {
            displayProfileForm();
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

function getRole(role) {
    const map = {
        "ADMIN": "管理員",
        "UNVERIFIED_USER": "未驗證用戶",
        "VERIFIED_USER": "已驗證用戶"
    }
    return map[role] || "未知用戶";
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

function getTimeZoneList() {
    fetch("/api/common/getTimeZoneList")
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
                    timeZone: timeZone
                }

                if (newPassword.trim().length > 0) {
                    formData.newPassword = newPassword;
                }

                let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
                let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');

                fetch("/api/user/updateUserDetail", {
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
    document.getElementById('sendVerificationEmailButton').addEventListener('click', (event) => {
        event.target.disabled = true;
        event.preventDefault()

        showSpinner(true);
        hideById('confirmCard');
        hideById("VerificationEmailFail")
        hideById('VerificationEmailSuccess');
        let csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
        let csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
        fetch("/api/user/sendVerificationEmail", {
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
            }}
        ).catch(data => {
            hideSpinner();
            hideById('VerificationEmailSuccess');
            displayError(data, 'VerificationEmailFail')
        })
    })
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