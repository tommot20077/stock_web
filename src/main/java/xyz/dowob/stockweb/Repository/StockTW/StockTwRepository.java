package xyz.dowob.stockweb.Repository.StockTW;

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

/**
 * @author yuan
 * 台灣股票與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface StockTwRepository extends JpaRepository<StockTw, Long> {
    /**
     * 透過股票代碼尋找台灣股票
     *
     * @param stockCode 股票代碼
     *
     * @return 台灣股票
     */
    Optional<StockTw> findByStockCode(String stockCode);

    /**
     * 獲取所有台灣股票的股票代碼,並依照股票代碼排序
     *
     * @return 股票代碼列表 Object[0]: 股票代碼, Object[1]: 股票名稱
     */
    @Query("SELECT s.stockCode, s.stockName FROM StockTw s ORDER BY s.stockCode")
    List<Object[]> findAllByOrderByStockCode();

    /**
     * 獲取所有台灣股票的股票代碼
     *
     * @param pageable 分頁
     *
     * @return 股票代碼列表
     */
    @Query("SELECT s.stockCode FROM StockTw s")
    Page<String> findAllStockCodeByPage(Pageable pageable);

    /**
     * 計算指定的台灣股票訂閱者人數
     *
     * @param stockTw 台灣股票
     *
     * @return 訂閱者人數
     */
    @Query("SELECT COUNT(sub) FROM StockTw s JOIN s.subscribers sub WHERE s = :stockTw")
    int countStockTwSubscribersNumber(
            @Param("stockTw") StockTw stockTw);

    /**
     * 查詢所有具有訂閱者的台灣股票,去除重複
     * 包含股票代碼和股票類型
     *
     * @return 台灣股票列表
     */
    @Query("SELECT DISTINCT s.stockCode, s.stockType FROM StockTw s JOIN s.subscribers subscriber")
    Set<Object[]> findAllStockCodeAndTypeBySubscribers();

    /**
     * 查詢所有具有訂閱者的台灣股票,去除重複
     * 僅包含股票代碼
     *
     * @return 台灣股票列表
     */
    @Query("SELECT DISTINCT s.stockCode FROM StockTw s JOIN s.subscribers subscriber WHERE s.stockType = :stockType")
    Set<String> findAllStockCodeBySubscribers(String stockType);

    /**
     * 查詢是否有訂閱者的台灣股票
     *
     * @param hasAnySubscribed 是否有任何訂閱者
     *
     * @return 台灣股票列表
     */
    Set<StockTw> findAllByHasAnySubscribed(boolean hasAnySubscribed);

    /**
     * 新增訂閱者並檢查是否需要獲取歷史資料
     *
     * @param stockTw        股票
     * @param userId         用戶id
     * @param eventPublisher 事件發布者
     */
    @Transactional
    default void addAndCheckSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        boolean trackHistoryData = false;
        if (countStockTwSubscribersNumber(stockTw) > 0) {
        } else {
            trackHistoryData = true;
        }
        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successAdd = subscribers.add(userId);
        if (successAdd) {
            save(stockTw);
            boolean finalTrackHistoryData = trackHistoryData;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new StockTwSubscriberChangeEvent(this, stockTw));
                    if (finalTrackHistoryData) {
                        eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "add"));
                    }
                }
            });
        }
    }

    /**
     * 刪除訂閱者並檢查是否需要刪除歷史資料
     *
     * @param stockTw        股票
     * @param userId         用戶id
     * @param eventPublisher 事件發布者
     */
    @Transactional
    default void removeAndCheckSubscriber(StockTw stockTw, Long userId, ApplicationEventPublisher eventPublisher) {
        if (countStockTwSubscribersNumber(stockTw) <= 1) {
            stockTw.setHasAnySubscribed(false);
        }
        Set<Long> subscribers = stockTw.getSubscribers();
        boolean successRemove = subscribers.remove(userId);
        if (successRemove) {
            save(stockTw);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new StockTwSubscriberChangeEvent(this, stockTw));
                    if (!stockTw.isHasAnySubscribed()) {
                        eventPublisher.publishEvent(new StockTwHistoryDataChangeEvent(this, stockTw, "remove"));
                    }
                }
            });
        }
    }
}
