package com.trackflow.common.service;

import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 上传文件，返回存储路径
     */
    public String upload(String folder, String originalFilename, MultipartFile file) {
        try {
            ensureBucketExists();

            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = folder + "/" + UUID.randomUUID() + ext;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return objectName;
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除文件
     */
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO delete failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取文件流
     */
    public InputStream getObject(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO getObject failed: {}", e.getMessage(), e);
            throw new RuntimeException("文件获取失败", e);
        }
    }

    /**
     * 获取文件访问 URL（内部直接拼接，因为前端通过后端代理访问）
     */
    public String getUrl(String objectName) {
        return "/api/v1/files/" + objectName;
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
