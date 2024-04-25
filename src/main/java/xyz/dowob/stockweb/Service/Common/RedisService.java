package xyz.dowob.stockweb.Service.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    private final RedisTemplate<String, String> redisTemplate;
    Logger logger = LoggerFactory.getLogger(RedisService.class);
    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveValueToCache(String key, String value, int hours) {
        redisTemplate.opsForValue().set(key, value, hours, TimeUnit.HOURS);
    }

    public void saveHashToCache(String key, String innerKey, String value, int hours) {
        redisTemplate.opsForHash().put(key, innerKey, value);
        redisTemplate.expire(key, hours, TimeUnit.HOURS);
    }

    /**
     * 將批次資料增量插入到缓存列表中。
     * @param key             缓存的key值
     * @param value           要插入的資料，應為JSON格式的字符串
     * @param expirationTime  缓存過期時間（單位：小时）
     */
    public void rPushToCacheList(String key, String value, long expirationTime) {
        redisTemplate.opsForList().rightPush(key, value);
        redisTemplate.expire(key, expirationTime, TimeUnit.HOURS);
    }


    public String getCacheValueFromKey(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    public List<String> getCacheListValueFromKey(String key) {
        try {
            return redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            logger.error("讀取redis時發生錯誤: "+ e.getMessage());
            throw new RuntimeException("讀取redis時發生錯誤"+ e.getMessage());
        }
    }

    public String getHashValueFromKey(String key, String innerKey) {
        return (String) redisTemplate.opsForHash().get(key, innerKey);
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
}
