package xyz.dowob.stockweb.Repository.Crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoSubscriberChangeEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author yuan
 * 虛擬貨幣與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface CryptoRepository extends JpaRepository<CryptoTradingPair, Long> {
    /**
     * 透過交易對尋找虛擬貨幣
     *
     * @param tradingPair 交易對
     *
     * @return 虛擬貨幣
     */
    Optional<CryptoTradingPair> findByTradingPair(String tradingPair);

    /**
     * 查詢所有具有訂閱者的虛擬貨幣
     * @return 虛擬貨幣列表
     */
    Set<CryptoTradingPair> findAllByHasAnySubscribed(Boolean hasAnySubscribed);

    /**
     * 查詢所有虛擬貨幣的訂閱數量
     *
     * @return 訂閱數量
     */
    @Query("SELECT count(c.subscribers) FROM CryptoTradingPair c")
    int countAllSubscribeNumber();

    /**
     * 查詢所有虛擬貨幣的目標加密貨幣名稱列表,並依照名稱排序
     *
     * @return 交易對列表
     */
    @Query("SELECT c.baseAsset FROM CryptoTradingPair c ORDER BY c.baseAsset")
    List<String> findAllBaseAssetByOrderByBaseAssetAsc();

    /**
     * 查詢所有虛擬貨幣的交易對名稱列表,並依照名稱排序
     * @param pageable 分頁
     * @return 交易對列表
     */
    @Query("SELECT c.tradingPair FROM CryptoTradingPair c")
    Page<String> findAllTradingPairByPage(Pageable pageable);


    /**
     * 計算指定虛擬貨幣的訂閱數量
     * @return 交易對列表
     */
    @Query("SELECT COUNT(sub) FROM CryptoTradingPair c join c.subscribers sub WHERE c = :cryptoTradingPair")
    int countCryptoSubscribersNumber(
            @Param("cryptoTradingPair") CryptoTradingPair cryptoTradingPair);

    /**
     * 查詢所有虛擬貨幣的交易對名稱列表
     * @return 交易對列表
     */
    @Query("SELECT DISTINCT c.tradingPair FROM CryptoTradingPair c JOIN c.subscribers subscriber")
    Set<String> findAllTradingPairBySubscribers();










    Logger logger = LoggerFactory.getLogger(CryptoRepository.class);

    /**
     * 新增訂閱者並檢查是否需要獲取歷史資料
     * 當虛擬貨幣沒有任何訂閱者時，發布事件獲取歷史資料
     * @param cryptoTradingPair 虛擬貨幣
     * @param userId 用戶id
     * @param eventPublisher 事件發布者
     */
    @Transactional
    default void addAndCheckSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        boolean trackHistoryData = false;
        if (countCryptoSubscribersNumber(cryptoTradingPair) > 0) {
            logger.debug("已經有用戶訂閱過此資產，不須獲取此資產歷史資料");
        } else {
            logger.debug("此資產沒有用戶訂閱過或是歷史資料，獲取此資產歷史資料");
            trackHistoryData = true;
        }

        Set<Long> subscribers = cryptoTradingPair.getSubscribers();
        boolean successAdd = subscribers.add(userId);
        if (successAdd) {
            logger.debug("成功加入訂閱");
            save(cryptoTradingPair);
            boolean finalTrackHistoryData = trackHistoryData;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.debug("發布更新追蹤虛擬貨幣事件");
                    eventPublisher.publishEvent(new CryptoSubscriberChangeEvent(this, cryptoTradingPair));
                    if (finalTrackHistoryData) {
                        logger.debug("發布新增歷史資料事件");
                        eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair, "add"));
                    }
                }
            });
        }
    }


    /**
     * 刪除訂閱者並檢查是否需要刪除歷史資料
     * 當訂閱數量為0時，發布刪除歷史資料事件
     * @param cryptoTradingPair 虛擬貨幣
     * @param userId 用戶id
     * @param eventPublisher 事件發布者
     */
    @Transactional
    default void removeAndCheckSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        if (countCryptoSubscribersNumber(cryptoTradingPair) <= 1) {
            cryptoTradingPair.setHasAnySubscribed(false);
        }

        Set<Long> subscribers = cryptoTradingPair.getSubscribers();
        boolean successRemove = subscribers.remove(userId);
        if (successRemove) {
            logger.debug("成功刪除訂閱");
            save(cryptoTradingPair);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.debug("發布更新追蹤虛擬貨幣事件");
                    eventPublisher.publishEvent(new CryptoSubscriberChangeEvent(this, cryptoTradingPair));
                    if (!cryptoTradingPair.isHasAnySubscribed()) {
                        logger.debug("已經刪除所有訂閱，刪除歷史資料");
                        eventPublisher.publishEvent(new CryptoHistoryDataChangeEvent(this, cryptoTradingPair, "remove"));
                    }
                }
            });
        }
    }
}
