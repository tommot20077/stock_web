package xyz.dowob.stockweb.Component.Method.AssetTrie;

import xyz.dowob.stockweb.Dto.Common.AssetListDto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yuan
 * 儲存字典樹的節點，因為需要將物件轉換成字串流傳輸，所以需要實作 Serializable
 * 1. children: 子節點
 * 2. isEndOfWord: 是否為單詞的結尾
 * 3. assets: 資產清單
 */
public class TrieNode implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    Map<Character, TrieNode> children;

    boolean isEndOfWord;

    List<AssetListDto> assets;

    /**
     * 字典樹節點的構造函數
     */

    public TrieNode() {
        children = new HashMap<>();
        isEndOfWord = false;
        assets = new ArrayList<>();
    }

    /**
     * 取得字典樹節點的字串表示
     *
     * @return 字典樹節點的字串表示
     */
    @Override
    public String toString() {
        return "TrieNode{" + "isEndOfWord=" + isEndOfWord + ", assets=" + assets + ", children=" + children.keySet() + '}';
    }
}
