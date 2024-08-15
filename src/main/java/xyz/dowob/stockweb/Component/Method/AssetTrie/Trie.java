package xyz.dowob.stockweb.Component.Method.AssetTrie;

import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Dto.Common.AssetListDto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yuan
 * 儲存字典樹的節點，因為需要將物件轉換成字串流傳輸，所以需要實作 Serializable
 * 1. root: 根節點
 */
@Component
public class Trie implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final TrieNode root;

    /**
     * 字典樹的構造函數
     */
    public Trie() {
        root = new TrieNode();
    }

    /**
     * 將資產插入到前綴樹中
     * @param asset 資產AssetListDto(資產ID, 資產名稱)
     */
    public void insert(AssetListDto asset) {
        TrieNode current = root;
        String name = asset.getAssetName();
        for (char c : name.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new TrieNode());
            current.assets.add(asset);
        }
        current.isEndOfWord = true;

    }

    /**
     * 搜尋前綴樹中的資產
     * @param prefix 搜尋的前綴
     * @return 資產列表
     */
    public List<AssetListDto> search(String prefix) {
        TrieNode node = searchNode(prefix);
        return node == null ? new ArrayList<>() : node.assets;
    }

    /**
     * 搜尋前綴樹中的節點
     * @param prefix 搜尋的前綴
     * @return 節點
     */
    private TrieNode searchNode(String prefix) {
        TrieNode current = root;
        for (char c : prefix.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return null;
            }
            current = current.children.get(c);
        }
        return current;
    }

    /**
     * 重寫toString方法，取得前綴樹的root
     * @return 根節點
     */
    @Override
    public String toString() {
        return "前綴樹目前: " + root;
    }
}
