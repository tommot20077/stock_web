package xyz.dowob.stockweb.Service.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Provider.EmailReminderTask;
import xyz.dowob.stockweb.Dto.User.TodoDto;
import xyz.dowob.stockweb.Exception.FormatExceptions;
import xyz.dowob.stockweb.Exception.UserExceptions;
import xyz.dowob.stockweb.Model.User.Todo;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.TodoListRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * @author yuan
 * 待辦事項相關服務
 * 1. 新增待辦事項
 * 2. 取得使用者所有待辦事項
 * 3. 刪除待辦事項
 * 4. 轉換待辦事項Dto為Json
 * 5. 初始化待辦事項提醒任務
 */
@Log4j2
@Service
public class TodoService {
    private final TodoListRepository todoListRepository;

    private final JavaMailSender javaMailSender;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();

    @Value(value = "${mail.sender.name}")
    private String emailSender;

    /**
     * TodoService構造函數
     *
     * @param todoListRepository 待辦事項數據庫
     * @param javaMailSender     郵件發送器
     * @param taskScheduler      任務調度器
     */
    public TodoService(TodoListRepository todoListRepository, JavaMailSender javaMailSender, TaskScheduler taskScheduler) {
        this.todoListRepository = todoListRepository;
        this.javaMailSender = javaMailSender;
        this.taskScheduler = taskScheduler;
    }

    /**
     * 初始化待辦事項提醒任務
     * 1. 取得所有設定提醒的待辦事項
     * 2. 檢查是否過期
     * 3. 設定提醒任務
     */
    @PostConstruct
    public void init() {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
            List<Todo> todoList = todoListRepository.findAllByReminderAndReminderTimeIsAfter(true, now);
            todoList.forEach(todo -> {
                if (todo.getDueDate().isBefore(now)) {
                    return;
                }
                scheduleEmailReminderTask(todo);
            });
        } catch (Exception e) {
            log.error("初始化錯誤: " + e);
        }
    }

    /**
     * 新增待辦事項
     *
     * @param todoDto 待辦事項資料
     * @param user    使用者
     */
    public void addTodo(TodoDto todoDto, User user) throws UserExceptions {
        Todo todo = todoDto.toEntity(todoDto, user);
        todoListRepository.save(todo);
        if (Objects.equals(todoDto.getIsReminder(), "true")) {
            scheduleEmailReminderTask(todo);
        }
    }

    /**
     * 取得使用者所有待辦事項, 並轉換成Dto
     *
     * @param user 使用者
     *
     * @return List<TodoDto>
     */
    public List<TodoDto> findAllByUser(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String timeZone = user.getTimezone();
        return todoListRepository.findAllByUserId(user.getId()).stream().map(todoList -> {
            TodoDto todoDto = new TodoDto();
            todoDto.setId(todoList.getId());
            todoDto.setContent(todoList.getContent());
            todoDto.setPriority(String.valueOf(todoList.getPriority()));
            LocalDateTime localDateTime = todoList.getDueDate().atZoneSameInstant(ZoneId.of(timeZone)).toLocalDateTime();
            todoDto.setDueDate(localDateTime.format(formatter));
            return todoDto;
        }).collect(Collectors.toList());
    }

    /**
     * 刪除待辦事項，如果有設置提醒則取消提醒任務
     *
     * @param id 待辦事項ID
     */
    public void delete(Long id) {
        ScheduledFuture<?> scheduledFuture = scheduledFutureMap.get(id);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFutureMap.remove(id);
        }
        todoListRepository.deleteById(id);
    }

    /**
     * 轉換待辦事項Dto為Json
     *
     * @param todoListDto 待辦事項Dto
     *
     * @return String
     */
    public String formatToJson(List<TodoDto> todoListDto) throws FormatExceptions {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(todoListDto);
        } catch (Exception e) {
            throw new FormatExceptions(FormatExceptions.ErrorEnum.JSON_FORMAT_ERROR, e.getMessage());
        }
    }

    /**
     * 設定提醒任務, 並發送Email
     *
     * @param todo 待辦事項
     */
    private void scheduleEmailReminderTask(Todo todo) {
        EmailReminderTask emailReminderTask = new EmailReminderTask(todo, javaMailSender, emailSender);
        ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(emailReminderTask,
                                                                    todo.getReminderTime().atZoneSameInstant(ZoneId.of("UTC")).toInstant());
        scheduledFutureMap.put(todo.getId(), scheduledFuture);
    }
}
