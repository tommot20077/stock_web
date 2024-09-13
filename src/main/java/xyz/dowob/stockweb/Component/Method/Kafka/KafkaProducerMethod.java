package xyz.dowob.stockweb.Component.Method.Kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 用於Kafka生產者的方法。
 * 當設定檔中的common.kafka.enable為true時，此類別將被實例化。
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName KafkaProducerMethod
 * @description
 * @create 2024-09-06 01:09
 * @Version 1.0
 **/
@Component
@ConditionalOnProperty(name = "common.kafka.enable",
                       havingValue = "true")
public class KafkaProducerMethod {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerMethod(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 發送消息到指定的主題。
     *
     * @param topic   主題名稱
     * @param message 消息對象
     */
    public void sendMessage(String topic, Object message) {
        kafkaTemplate.send(topic, message);
    }
}
