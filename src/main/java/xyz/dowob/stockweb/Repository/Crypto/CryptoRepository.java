package xyz.dowob.stockweb.Repository.Crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.dowob.stockweb.Component.Event.Crypto.CryptoChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwChangeEvent;
import xyz.dowob.stockweb.Model.Crypto.CryptoTradingPair;
import xyz.dowob.stockweb.Model.Stock.StockTw;
import xyz.dowob.stockweb.Model.User.Transaction;

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


    @Query("SELECT COUNT(c.subscribers) FROM CryptoTradingPair c WHERE c = :cryptoTradingPair")
    int countCryptoSubscribersNumber(CryptoTradingPair cryptoTradingPair);

    @Query("SELECT DISTINCT c.tradingPair FROM CryptoTradingPair c JOIN c.subscribers subscriber")
    Set<String> findAllTradingPairBySubscribers();






    Logger logger = LoggerFactory.getLogger(CryptoRepository.class);
    @Transactional
    default void addSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        Set<Long> subscribers = cryptoTradingPair.getSubscribers();
        boolean successAdd = subscribers.add(userId);
        if (successAdd) {
            logger.debug("成功加入訂閱");
            save(cryptoTradingPair);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.debug("發布更新追蹤虛擬貨幣事件");
                    eventPublisher.publishEvent(new CryptoChangeEvent(this, cryptoTradingPair));
                }
            });
        } else {
            logger.debug("已經加入訂閱");
        }
    }

    @Transactional
    default void removeSubscriber(CryptoTradingPair cryptoTradingPair, Long userId, ApplicationEventPublisher eventPublisher) {
        Set<Long> subscribers = cryptoTradingPair.getSubscribers();
        boolean successRemove = subscribers.remove(userId);
        if (successRemove) {
            logger.debug("成功刪除訂閱");
            save(cryptoTradingPair);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.debug("發布更新追蹤虛擬貨幣事件");
                    eventPublisher.publishEvent(new CryptoChangeEvent(this, cryptoTradingPair));
                }
            });
        } else {
            logger.debug("已經刪除訂閱");
        }
    }


}
