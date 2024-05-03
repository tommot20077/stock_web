package xyz.dowob.stockweb.Component.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import xyz.dowob.stockweb.Model.User.Todo;

/**
 * @author yuan
 */
public class EmailReminderTask implements Runnable {
    private final Todo todo;
    private final JavaMailSender javaMailSender;
    private final String emailSender;
    Logger logger = LoggerFactory.getLogger(EmailReminderTask.class);

    public EmailReminderTask(Todo todo, JavaMailSender javaMailSender, String emailSender) {
        this.todo = todo;
        this.javaMailSender = javaMailSender;
        this.emailSender = emailSender;
    }

    @Override
    public void run() {
        logger.info("發布待辦事項提醒任務: " + todo.getId());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailSender);
        message.setTo(todo.getUser().getEmail());
        message.setSubject("待辦事項提醒");
        message.setText("您的待辦事項：" + todo.getContent() + " 已經到期，請盡快處理。");
        try {
            javaMailSender.send(message);
            logger.info("提醒郵件: {} 已發送", todo.getId());
        } catch (Exception e) {
            logger.error("發生錯誤: " + e.getMessage());
        }
    }
}
