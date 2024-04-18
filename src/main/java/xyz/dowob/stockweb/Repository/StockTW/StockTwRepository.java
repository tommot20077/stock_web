package xyz.dowob.stockweb.Repository.StockTW;

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
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwHistoryDataChangeEvent;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwSubscriberChangeEvent;
import xyz.dowob.stockweb.Model.Stock.StockTw;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockTwRepository extends JpaRepository<StockTw, Long> {
    Optional<StockTw> findByStockCode(String stockCode);

    @Query("SELECT DISTINCT s.stockCode, s.stockName FROM StockTw s order by s.stockCode")
    List<Object[]> findDistinctStockCodeAndName();
    @Query("SELECT s.stockCode, s.stockName FROM StockTw s ORDER BY s.stockCode")
    List<Object[]> findAllByOrderByStockCode();

    @Query("SELECT s.stockCode FROM StockTw s")
    Page<String> findAllStockCodeByPage(Pageable pageable);

    @Query("SELECT COUNT(sub) FROM StockTw s JOIN s.subscribers sub WHERE s = :stockTw")
    int countStockTwSubscribersNumber(@Param("stockTw") StockTw stockTw);

    @Query("SELECT DISTINCT s.stockCode, s.stockType FROM StockTw s JOIN s.subscribers subscriber")
    Set<Object[]> findAllStockCodeAndTypeBySubscribers();

    @Query("SELECT DISTINCT s.stockCode FROM StockTw s JOIN s.subscribers subscriber")
    Set<String> findAllStockCodeBySubscribers();




    Logger logger = LoggerFactory.getLogger(StockTwRepository.class);

    @Transactional
    default void addAndCheckSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        boolean trackHistoryData = false;
        logger.debug("測試" + countStockTwSubscribersNumber(stockTw) + "用戶訂閱此資產");
        if (countStockTwSubscribersNumber(stockTw) > 0) {
            logger.debug("已經有用戶訂閱過此資產，不須獲取此資產歷史資料");
        } else {
            logger.debug("此資產沒有用戶訂閱過或是歷史資料，獲取此資產歷史資料");
            trackHistoryData = true;
        }

        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successAdd = subscribers.add(userId);
        if (successAdd) {
            logger.debug("成功加入訂閱");
            save(stockTw);
            boolean finalTrackHistoryData = trackHistoryData;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new StockTwSubscriberChangeEvent(this, stockTw));
                    logger.debug("發布更新追蹤股票事件");
                    if (finalTrackHistoryData) {
                        eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "add"));
                        logger.debug("發布更新歷史資料事件");
                    }
                }
            });
        }
    }

    @Transactional
    default void removeAndCheckSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        if (countStockTwSubscribersNumber(stockTw) <= 1) {
            logger.debug("沒有用戶訂閱此資產");
            stockTw.setHasAnySubscribed(false);
        }

        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successRemove = subscribers.remove(userId);
        if (successRemove) {
            logger.debug("成功刪除訂閱");
            save(stockTw);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new StockTwSubscriberChangeEvent(this, stockTw));
                    logger.debug("發布更新追蹤股票事件");
                    if (!stockTw.isHasAnySubscribed()) {
                        eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "remove"));
                        logger.debug("已經刪除所有訂閱，刪除歷史資料");
                    }
                }
            });
        }
    }











}
