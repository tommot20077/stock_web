package xyz.dowob.stockweb.Component.Method.Kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @author yuan
 * @program Stock-Web
 * @ClassName KafkaProducerMethod
 * @description
 * @create 2024-09-06 01:09
 * @Version 1.0
 **/
@Component
@ConditionalOnProperty(name = "common.kafka.enable", havingValue = "true")
public class KafkaProducerMethod {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaProducerMethod(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, Object message) {
        kafkaTemplate.send(topic, message);
    }

}
