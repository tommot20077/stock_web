package xyz.dowob.stockweb.Component.Method.Kafka;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName KafkaConsumerMethod
 * @description
 * @create 2024-09-06 01:27
 * @Version 1.0
 **/
@Component
@Log4j2
@ConditionalOnProperty(name = "common.kafka.enable",
                       havingValue = "true")
public class KafkaConsumerMethod {
    @KafkaListener(topics = "crypto_kline", groupId = "#{'${spring.kafka.consumer.group-id}'}")
    public void consumeCryptoKlineDataByInfluxdb(Object klineData) {
        log.info("Received Crypto Kline Data: " + klineData);
    }

    @KafkaListener(topics = "stock_tw_kline", groupId = "#{'${spring.kafka.consumer.group-id}'}")
    public void consumeStockTwKlineDataByInfluxdb(Object klineData) {
        log.info("Received StockTw Kline Data: " + klineData);
    }
}

