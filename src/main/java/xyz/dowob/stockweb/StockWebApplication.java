package xyz.dowob.stockweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 啟動類
 * 啟用快取、非同步、定時任務
 *
 * @author yuan
 */
@SpringBootApplication
@EnableCaching(proxyTargetClass = true)
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
public class StockWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockWebApplication.class, args);
    }
}
