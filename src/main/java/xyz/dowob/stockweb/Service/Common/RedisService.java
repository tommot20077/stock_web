package xyz.dowob.stockweb.Service.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class RedisService {
    private final RedisTemplate<String, String> redisTemplate;
    Logger logger = LoggerFactory.getLogger(RedisService.class);
    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;

    }

    public void saveToCache(String key, String value, int hours) {
        redisTemplate.opsForValue().set(key, value, hours, TimeUnit.HOURS);
    }

    public String getCacheFromKey(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 使用 scan 命令根据模式删除匹配的鍵
     *
     * @param pattern 模式字符串，例如："news_headline_page_*"
     */
    public void deleteByPattern(String pattern) {
        int deleteNum = 100;
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(deleteNum).build();
        try (Cursor<String> cursor = redisTemplate.opsForValue().getOperations().scan(options)) {
            List<String> keysToDelete = new ArrayList<>();
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
                if (keysToDelete.size() >= deleteNum) {
                    redisTemplate.delete(keysToDelete);
                    keysToDelete.clear();
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            logger.error("刪除redis時發生錯誤: "+ e.getMessage());
            throw new RuntimeException("刪除redis時發生錯誤: "+ e.getMessage());
        }


        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void updateCache(String key, String value) {
        redisTemplate.opsForValue().set(key, value, 8, TimeUnit.HOURS);
    }

}
