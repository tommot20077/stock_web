package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author yuan
 * 儲存資產清單的資料傳輸物件，因為需要將物件轉換成字串流傳輸，所以需要實作 Serializable
 * 1. assetId: 資產ID
 * 2. assetName: 資產名稱
 * 3. isSubscribe: 是否訂閱
 */
@Data
public class AssetListDto implements Serializable {
    @Serial
    private final static long serialVersionUID = 1L;

    private Long assetId;

    private String assetName;

    private boolean isSubscribe;

    /**
     * 資產清單的構造函數
     *
     * @param assetId   資產ID
     * @param assetName 資產名稱
     * @param isSubscribe 是否訂閱
     */
    public AssetListDto(Long assetId, String assetName, boolean isSubscribe) {
        this.assetId = assetId;
        this.assetName = assetName;
        this.isSubscribe = isSubscribe;
    }
}
