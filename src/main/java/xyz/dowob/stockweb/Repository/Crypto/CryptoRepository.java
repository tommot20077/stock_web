package xyz.dowob.stockweb.Repository.Crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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

public interface CryptoRepository extends JpaRepository<CryptoTradingPair, Long> {
    Optional<CryptoTradingPair> findByTradingPair(String tradingPair);

    @Query("SELECT distinct c FROM CryptoTradingPair c join c.subscribers")
    Set<CryptoTradingPair> findAllByHadSubscribed();

    @Query("SELECT count(c.subscribers) FROM CryptoTradingPair c")
    int countAllSubscribeNumber();

    @Query("SELECT c.baseAsset FROM CryptoTradingPair c ORDER BY c.baseAsset")
    List<String> findAllBaseAssetByOrderByBaseAssetAsc();


    @Query("SELECT COUNT(sub) FROM CryptoTradingPair c join c.subscribers sub WHERE c = :cryptoTradingPair")
    int countCryptoSubscribersNumber(@Param("cryptoTradingPair") CryptoTradingPair cryptoTradingPair);

    @Query("SELECT DISTINCT c.tradingPair FROM CryptoTradingPair c JOIN c.subscribers subscriber")
    Set<String> findAllTradingPairBySubscribers();






    Logger logger = LoggerFactory.getLogger(CryptoRepository.class);
    @Transactional
    default void addAndCheckSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        boolean trackHistoryData = false;
        if (countCryptoSubscribersNumber(cryptoTradingPair) > 0) {
            logger.debug("已經有用戶訂閱過此資產，不須獲取此資產歷史資料");
        } else {
            logger.debug("沒有用戶訂閱過此資產，獲取此資產歷史資料");
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

    @Transactional
    default void removeAndCheckSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        if (countCryptoSubscribersNumber(cryptoTradingPair) <= 1 ) {
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
