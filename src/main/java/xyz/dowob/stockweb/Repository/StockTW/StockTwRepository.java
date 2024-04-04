package xyz.dowob.stockweb.Repository.StockTW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import xyz.dowob.stockweb.Component.Event.StockTw.StockTwChangeEvent;
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

    @Query("SELECT COUNT(s.subscribers) FROM StockTw s WHERE s = :stockTw")
    int countStockTwSubscribersNumber(StockTw stockTw);

    @Query("SELECT DISTINCT s.stockCode, s.stockType FROM StockTw s JOIN s.subscribers subscriber")
    Set<Object[]> findAllAssetIdsWithSubscribers();




    Logger logger = LoggerFactory.getLogger(StockTwRepository.class);

    @Transactional
    default void addSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successAdd = subscribers.add(userId);
        if (successAdd) {
            logger.debug("成功加入訂閱");
            save(stockTw);

            eventPublisher.publishEvent(new StockTwChangeEvent(this, stockTw));
            logger.debug("發布更新追蹤股票事件");
        } else {
            logger.debug("已經加入訂閱");
        }
    }

    @Transactional
    default void removeSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successRemove = subscribers.remove(userId);
        if (successRemove) {
            logger.debug("成功刪除訂閱");
            save(stockTw);
            eventPublisher.publishEvent(new StockTwChangeEvent(this, stockTw));
            logger.debug("發布更新追蹤股票事件");
        } else {
            logger.debug("已經刪除訂閱");
        }
    }











}
