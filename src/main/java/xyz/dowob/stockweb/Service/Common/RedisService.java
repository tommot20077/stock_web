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

/**
 * @author yuan
 * 有關redis緩存的業務邏輯
 */
@Service
public class RedisService {
    private final RedisTemplate<String, String> redisTemplate;

    Logger logger = LoggerFactory.getLogger(RedisService.class);

    /**
     * RedisService構造函數
     *
     * @param redisTemplate redis模板
     */
    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 將數據存入緩存
     *
     * @param key   緩存的key值
     * @param value 要存入的數據
     * @param hours 緩存過期時間（單位：小時）
     */
    public void saveValueToCache(String key, String value, int hours) {
        redisTemplate.opsForValue().set(key, value, hours, TimeUnit.HOURS);
    }

    /**
     * 將數據存入哈希表緩存
     *
     * @param key      緩存的key值
     * @param innerKey 哈希表的key值
     * @param value    要存入的數據
     * @param hours    緩存過期時間（單位：小時）
     */
    public void saveHashToCache(String key, String innerKey, String value, int hours) {
        redisTemplate.opsForHash().put(key, innerKey, value);
        redisTemplate.expire(key, hours, TimeUnit.HOURS);
    }

    /**
     * 使用新数据完全替换列表
     *
     * @param key            列表的键
     * @param values         新的数据值
     * @param expirationTime 过期时间，以小時为单位
     */
    public void saveListToCache(String key, List<String> values, long expirationTime) {
        redisTemplate.delete(key);
        if (!values.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, values);
            redisTemplate.expire(key, expirationTime, TimeUnit.HOURS);
        }
    }

    /**
     * 將批次資料增量插入到缓存列表中。
     *
     * @param key            缓存的key值
     * @param value          要插入的資料，應為JSON格式的字符串
     * @param expirationTime 缓存過期時間（單位：小时）
     */
    public void rPushToCacheList(String key, String value, long expirationTime) {
        redisTemplate.opsForList().rightPush(key, value);
        redisTemplate.expire(key, expirationTime, TimeUnit.HOURS);
    }


    /**
     * 取得緩存中的數據值
     *
     * @param key 緩存的key值
     *
     * @return 緩存中的數據值
     */
    public String getCacheValueFromKey(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 取得緩存列表中的數據值
     *
     * @param key 緩存的key值
     *
     * @return 緩存列表中的數據值
     */
    public List<String> getCacheListValueFromKey(String key) {
        try {
            return redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            logger.error("讀取redis時發生錯誤: " + e.getMessage());
            throw new RuntimeException("讀取redis時發生錯誤" + e.getMessage());
        }
    }

    /**
     * 取得哈希表緩存中的數據值
     *
     * @param key      緩存的key值
     * @param innerKey 哈希表的key值
     *
     * @return 哈希表緩存中的數據值
     */
    public String getHashValueFromKey(String key, String innerKey) {
        return (String) redisTemplate.opsForHash().get(key, innerKey);
    }


    /**
     * 使用 scan 命令根据模式删除匹配的鍵
     *
     * @param pattern 模式字符串，例如："news_headline_page_*"
     *                這將刪除所有以"news_headline_page_"開頭的鍵
     *
     * @throws RuntimeException 當刪除redis時發生錯誤時拋出
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
            logger.error("刪除redis時發生錯誤: " + e.getMessage());
            throw new RuntimeException("刪除redis時發生錯誤: " + e.getMessage());
        }


        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
