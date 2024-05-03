(function ($) {
    'use strict';
    $(function () {
        var todoListItem = $('.todo-list');
        var todoListInput = $('.todo-list-input');
        $('.todo-list-add-btn').on("click", function (event) {
            event.preventDefault();

            var item = $(this).prevAll('.todo-list-input').val();

            if (item) {
                todoListItem.append("<li><div class='form-check'><label class='form-check-label'><input class='checkbox' type='checkbox'/>" + item + "<i class='input-helper'></i></label></div><i class='remove ti-close'></i></li>");
                todoListInput.val("");
            }

        });

        todoListItem.on('change', '.checkbox', function () {
            if ($(this).attr('checked')) {
                $(this).removeAttr('checked');
            } else {
                $(this).attr('checked', 'checked');
            }

            $(this).closest("li").toggleClass('completed');

        });

        todoListItem.on('click', '.remove', function () {
            $(this).parent().remove();
        });

    });
})(jQuery);

function addTodo() {
    const isRemindCheckbox = document.getElementById('isRemind');
    const remindTimeInput = document.getElementById('remindTime');
    const addButton = document.getElementById('addButton');
    remindTimeInput.disabled = !isRemindCheckbox.checked;
    isRemindCheckbox.addEventListener('change', (event) => {
        remindTimeInput.disabled = !event.target.checked;
    });

    let form = document.getElementById('addTodoForm');
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        addButton.disabled = true;
        hideSpinner();
        showSpinner(true);
        hideById("addTodoCard")
        const priority = document.getElementById('priority').value;
        const content = document.getElementById('content').value;
        const dueDate = document.getElementById('dueTime').value;
        const isRemind = document.getElementById('isRemind').checked;
        const reminderTime = document.getElementById('remindTime').value;

        let formData = {
            priority,
            content,
            dueDate,
            isReminder:isRemind.toString()
        };

        if (isRemind) {
            formData.reminderTime = reminderTime;
        }
        console.log(JSON.stringify(formData))

        fetch('/api/user/common/addTodoList', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(formData)
        }).then(response => {
            if (response.ok) {
                hideSpinner();
                showSpinner(false);
                showFlexById("addTodoCard");
                showFlexById("success_message");
                setTimeout(() => {
                    window.location.reload();
                }, 2000);
            } else {
                return response.text();
            }
        }).then(err => {
            if (err) {
                addButton.disabled = false;
                displayError(err, 'fail_message');
            }
        });
    });
}

function removeTodo () {
    showSpinner(true);
    hideById("addTodoCard");
    let checkedBoxes = document.querySelectorAll('#todoList input[type="checkbox"]:checked');
    let checkedIds = Array.from(checkedBoxes).map(box => box.getAttribute('data-todo-id'));
    if (checkedIds.length !== 0) {
        deleteTodo(checkedIds);
    } else {
        hideSpinner();
    }
}