package xyz.dowob.stockweb.Service.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileService {
    @Value("${common.download.path:./}")
    private String downloadPath;
    Logger logger = LoggerFactory.getLogger(FileService.class);


    public List<String[]> downloadFileAndUnzipAndRead(String url, String fileName) {
        byte[] buffer = new byte[1024];
        List<String[]> result = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();

        Resource resource = restTemplate.getForObject(url, Resource.class);
        if (resource != null) {
            File dir = new File(downloadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.error("無法創建目錄: " + dir.getAbsolutePath());
                return null;
            }
            File zipFile = new File(dir, fileName);
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null, byte[].class);
                logger.debug("HTTP響應狀態碼: " + response.getStatusCode());
                logger.debug("檔案大小: " + response.getHeaders().getContentLength());

                if (response.getStatusCode() == HttpStatus.OK) {
                    byte[] fileBytes = response.getBody();
                    if (fileBytes == null) {
                        logger.error("檔案內容為空");
                        return null;
                    }

                    try (OutputStream outputStream = new FileOutputStream(zipFile)) {
                        outputStream.write(fileBytes);
                        logger.debug("檔案下載成功: " + zipFile.getAbsolutePath());
                    } catch (IOException e) {
                        logger.error("檔案保存失敗: " + e.getMessage());
                        return null;
                    }
                    if (zipFile.length() != response.getHeaders().getContentLength()) {
                        logger.error("檔案大小不一致");
                        return null;
                    }
                } else {
                    logger.error("HTTP響應狀態碼: " + response.getStatusCode());
                    return null;
                }
            } catch (RestClientException e) {
                logger.error("请求失败: " + e.getMessage());
                return null;
            }

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFile.getPath())))) {
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    File csvFile = new File(dir, zipEntry.getName());
                    logger.debug("解壓縮檔案: " + csvFile.getAbsolutePath());
                    try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(csvFile))) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer, 0, buffer.length)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    result.addAll(readCsvFile(csvFile.getPath()));
                    logger.debug("加入數據到結果");

                    if (!csvFile.delete()) {
                        logger.error("數據刪除失敗");
                        throw new RuntimeException("數據刪除失敗" + csvFile.getAbsolutePath());
                    }

                    zipEntry = zis.getNextEntry();
                    logger.debug("下一個檔案");
                }
                zis.closeEntry();
                logger.debug("沒有資料，關閉檔案");
            } catch (IOException e) {
                throw new RuntimeException("數據解壓縮失敗: " + e);
            }

            if (!zipFile.delete()) {
                logger.error("壓縮檔刪除失敗");
                throw new RuntimeException("壓縮檔刪除失敗" + zipFile.getAbsolutePath());
            }

        }
        return result;
    }


    public List<String[]> readCsvFile(String fileName) {
        List<String[]> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                result.add(values);
            }
        } catch (IOException e) {
            throw new RuntimeException("數據讀取失敗: " + e);
        }
        return result;
    }
}
