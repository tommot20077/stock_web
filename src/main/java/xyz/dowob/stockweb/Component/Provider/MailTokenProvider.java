package xyz.dowob.stockweb.Component.Provider;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Model.User.Token;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.TokenRepository;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

/**
 * 這是一個用於生成和發送電子郵件驗證token的方法。
 *
 * @author yuan
 */
@Component
public class MailTokenProvider {
    private final TokenRepository tokenRepository;

    private final JavaMailSender javaMailSender;

    Logger logger = LoggerFactory.getLogger(UserService.class);

    /**
     * 這是一個構造函數，用於注入TokenRepository和JavaMailSender。
     *
     * @param tokenRepository 用戶token數據庫
     * @param javaMailSender  郵件發送器
     */
    @Autowired
    public MailTokenProvider(TokenRepository tokenRepository, JavaMailSender javaMailSender) {
        this.tokenRepository = tokenRepository;
        this.javaMailSender = javaMailSender;
    }

    @Value(value = "${security.email.receive.url:http://localhost:8080}")
    private String emailReceiveUrl;

    @Value(value = "${spring.mail.username}")
    private String emailSender;

    @Value(value = "${security.email.expiration:10}")
    private int expirationMinute;

    @Value(value = "${common.send_mail_times:3}")
    private int sendMailTimes;

    /**
     * 發送驗證電子郵件，並將token存入數據庫
     * 其中emailReceiveUrl是用於接收token的url，由application.properties中的security.email.receive.url設置
     *
     * @param user 用戶
     *
     * @throws RuntimeException 發送郵件失敗，或者已達到每小時發送限制
     */
    public void sendVerificationEmail(User user) throws RuntimeException {
        if (canSendEmail(user)) {
            String token = UUID.randomUUID().toString();
            String base64Token = Base64.getEncoder().encodeToString(token.getBytes());
            String base128Token = Base64.getEncoder().encodeToString(base64Token.getBytes());
            Token usertoken = user.getToken();
            usertoken.setEmailApiToken(base64Token);
            usertoken.setEmailApiTokenExpiryTime(OffsetDateTime.now().plusMinutes(expirationMinute));
            tokenRepository.save(usertoken);

            String verificationLink = emailReceiveUrl + "/api/user/common/verifyEmail?token=" + base128Token;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailSender);
            message.setTo(user.getEmail());
            message.setSubject("請驗證您的電子郵件");
            message.setText("請點擊以下鏈接完成驗證\n" + verificationLink + "\n(此鏈接在發送後" + expirationMinute + "分鐘內有效)");
            javaMailSender.send(message);
            logger.info("發送驗證信到用戶{}", user.getEmail());
        } else {
            throw new RuntimeException("目前已到達每小時發送電子郵件的限制，請稍後再試。");
        }
    }

    /**
     * 發送重設密碼電子郵件，並將token存入數據庫
     *
     * @param user 用戶
     *
     * @throws RuntimeException 發送郵件失敗
     */
    public void sendResetPasswordEmail(User user) throws RuntimeException {
        if (canSendEmail(user)) {
            String verificationCode = RandomStringUtils.randomNumeric(6);
            String base64Token = Base64.getEncoder().encodeToString(verificationCode.getBytes());
            Token usertoken = user.getToken();
            usertoken.setEmailApiToken(base64Token);
            usertoken.setEmailApiTokenExpiryTime(OffsetDateTime.now().plusMinutes(expirationMinute));
            tokenRepository.save(usertoken);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailSender);
            message.setTo(user.getEmail());
            message.setSubject("重設密碼驗證");
            message.setText("這是你的驗證碼\n" + verificationCode + "\n(此驗證碼在發送後" + expirationMinute + "分鐘內有效)");
            javaMailSender.send(message);
            logger.info("發送驗證信到用戶{}", user.getEmail());
        } else {
            throw new RuntimeException("目前已到達每小時發送電子郵件的限制，請稍後再試。");
        }
    }

    /**
     * 檢查用戶是否可以發送電子郵件
     *
     * @param user 用戶
     *
     * @return 是否可以發送電子郵件
     */
    private synchronized boolean canSendEmail(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        Token usertoken = user.getToken();
        if (now.isAfter(usertoken.getEmailApiTokenResetTime())) {
            usertoken.setEmailApiTokenResetTime(now.plusHours(1));
            usertoken.setEmailApiRequestCount(0);
        }
        boolean canSend = usertoken.getEmailApiRequestCount() < sendMailTimes;
        if (canSend) {
            usertoken.setEmailApiRequestCount(usertoken.getEmailApiRequestCount() + 1);
            tokenRepository.save(usertoken);
        }
        return canSend;
    }


    /**
     * 驗證token並返回用戶，如果token無效，將拋出異常
     *
     * @param base128Token token
     *
     * @return 用戶
     *
     * @throws RuntimeException 無效的token
     */
    public User validateTokenAndReturnUser(String base128Token) {
        byte[] decodedBytes = Base64.getDecoder().decode(base128Token);
        String base64Token = new String(decodedBytes);

        Token usertoken = tokenRepository.findByEmailApiToken(base64Token).orElse(null);
        if (usertoken == null) {
            throw new RuntimeException("無效的密鑰");
        } else if (usertoken.getEmailApiTokenExpiryTime().isBefore(OffsetDateTime.now(ZoneId.of((usertoken.getUser().getTimezone()))))) {
            throw new RuntimeException("密鑰已過期");
        } else if ((usertoken.getEmailApiToken()).equals(base64Token)) {
            return usertoken.getUser();
        } else {
            throw new RuntimeException("無效的密鑰");
        }
    }
}
