package xyz.dowob.stockweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@EnableCaching
public class StockWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockWebApplication.class, args);
    }

}
