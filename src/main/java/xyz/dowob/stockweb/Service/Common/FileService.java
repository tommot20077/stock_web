package xyz.dowob.stockweb.Service.Common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * @author yuan
 * 檔案相關業務邏輯
 */
@Service
public class FileService {
    @Value("${common.download.path:./}")
    private String downloadPath;

    /**
     * 下載檔案並解壓縮並讀取csv檔案
     *
     * @param url      下載的URL
     * @param fileName 下載的檔案名稱
     *
     * @return List<String [ ]> csv檔案的數據
     *
     * @throws RuntimeException 當檔案下載失敗或解壓縮失敗時拋出
     */
    public List<String[]> downloadFileAndUnzipAndRead(String url, String fileName) {
        byte[] buffer = new byte[1024];
        List<String[]> result = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        Resource resource = restTemplate.getForObject(url, Resource.class);
        if (resource != null) {
            File dir = new File(downloadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File zipFile = new File(dir, fileName);
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    byte[] fileBytes = response.getBody();
                    if (fileBytes == null) {
                        return null;
                    }
                    try (OutputStream outputStream = new FileOutputStream(zipFile)) {
                        outputStream.write(fileBytes);
                    } catch (IOException e) {
                        return null;
                    }
                    if (zipFile.length() != response.getHeaders().getContentLength()) {
                        return null;
                    }
                } else {
                    return null;
                }
            } catch (RestClientException e) {
                return null;
            }
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFile.getPath())))) {
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    File csvFile = new File(dir, zipEntry.getName());
                    try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(csvFile))) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer, 0, buffer.length)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    result.addAll(readCsvFile(csvFile.getPath()));
                    if (!csvFile.delete()) {
                        throw new IOException("數據刪除失敗" + csvFile.getAbsolutePath());
                    }
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException("數據解壓縮失敗: " + e);
            }
            if (!zipFile.delete()) {
                throw new RuntimeException("壓縮檔刪除失敗" + zipFile.getAbsolutePath());
            }
        }
        return result;
    }

    /**
     * 讀取csv檔案
     *
     * @param fileName 檔案名稱
     *
     * @return List<String [ ]> csv檔案的數據
     *
     * @throws RuntimeException 當檔案讀取失敗時拋出
     */
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
