package com.fitnessai.file.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Bucket is created lazily on first write, so the service boots even if MinIO is still starting. */
@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorage(@Value("${minio.endpoint}") String endpoint,
                              @Value("${minio.access-key}") String accessKey,
                              @Value("${minio.secret-key}") String secretKey,
                              @Value("${minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Could not store object " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new StorageException("Could not read object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Could not delete object " + key, e);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        }
    }
}
