function displayError(error, elementId) {
    // Get the element by id
    let element = document.getElementById(elementId);

    // Set the error message and color
    element.innerText = error;
    element.style.color = 'red';
}

function showSpinnerNone() {
    document.getElementById('loading-spinner').style.display = 'flex';
    document.getElementById('successModal').style.display = 'none';
}
function hideSpinnerNone() {
    document.getElementById('loading-spinner').style.display = 'none';
}
function hideRings() {
    document.getElementById('lds-ring').style.display = 'none';
    document.getElementById('successModal').style.display = 'flex';
}

