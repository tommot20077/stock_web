package xyz.dowob.stockweb.Service.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Component.Provider.EmailReminderTask;
import xyz.dowob.stockweb.Dto.User.TodoDto;
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
@Service
public class TodoService {
    private final TodoListRepository todoListRepository;
    private final JavaMailSender javaMailSender;
    private final TaskScheduler taskScheduler;
    private Map<Long, ScheduledFuture<?>> scheduledFutureMap = new HashMap<>();

    @Value(value = "${spring.mail.username}")
    private String emailSender;
    private final
    Logger logger = LoggerFactory.getLogger(TodoService.class);

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
        logger.info("開始為設定提醒的待辦事項設定提醒任務");
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
        logger.debug("現在時間: " + now);
        List<Todo> todoList = todoListRepository.findAllByReminderAndReminderTimeIsAfter(true, now);
        todoList.forEach(todo -> {
            if (todo.getDueDate().isBefore(now)) {
                logger.info("待辦事項已過期: " + todo.getId() + "，不發送提醒");
                return;
            }
            scheduleEmailReminderTask(todo);
        });

    }

    /**
     * 新增待辦事項
     * @param todoDto 待辦事項資料
     * @param user 使用者
     */
    public void addTodo(TodoDto todoDto, User user) {
        logger.info("addTodo: " + todoDto.toString());
        Todo todo = todoDto.toEntity(todoDto, user);
        todoListRepository.save(todo);
        if (Objects.equals(todoDto.getIsReminder(), "true")) {
            scheduleEmailReminderTask(todo);
        }
    }

    /**
     * 取得使用者所有待辦事項, 並轉換成Dto
     * @param user 使用者
     * @return List<TodoDto>
     */
    public List<TodoDto> findAllByUser(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String timeZone = user.getTimezone();
        return todoListRepository.findAllByUserId(user.getId())
                                 .stream()
                                 .map(todoList-> {
                                        TodoDto todoDto = new TodoDto();
                                        todoDto.setId(todoList.getId());
                                        todoDto.setContent(todoList.getContent());
                                        todoDto.setPriority(String.valueOf(todoList.getPriority()));
                                        LocalDateTime localDateTime = todoList.getDueDate().atZoneSameInstant(ZoneId.of(timeZone)).toLocalDateTime();
                                        todoDto.setDueDate(localDateTime.format(formatter));
                                        return todoDto;
                                 })
                                 .collect(Collectors.toList());
    }

    /**
     * 刪除待辦事項，如果有設置提醒則取消提醒任務
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
     * @param todoListDto 待辦事項Dto
     * @return String
     */
    public String formatToJson(List<TodoDto> todoListDto) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(todoListDto);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new RuntimeException("轉換Json失敗: " + e.getMessage());
        }
    }

    /**
     * 設定提醒任務, 並發送Email
     *
     * @param todo 待辦事項
     */
    private void scheduleEmailReminderTask(Todo todo) {
        logger.debug("設定提醒任務: " + todo.getId());
        EmailReminderTask emailReminderTask = new EmailReminderTask(todo, javaMailSender, emailSender);
        ScheduledFuture<?> scheduledFuture =  taskScheduler.schedule(emailReminderTask, todo.getReminderTime().atZoneSameInstant(ZoneId.of("UTC")).toInstant());
        logger.debug("提醒時間: " + todo.getReminderTime().atZoneSameInstant(ZoneId.of("UTC")).toInstant());
        scheduledFutureMap.put(todo.getId(), scheduledFuture);
    }
}
