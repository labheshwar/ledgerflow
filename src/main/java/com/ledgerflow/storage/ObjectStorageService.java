package com.ledgerflow.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Wraps the S3 client behind three verbs. Nothing here is MinIO-specific --
 * pointing this at a real S3 bucket in production is a config change to
 * {@link StorageProperties}, not a code change.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final S3Client s3Client;
    private final StorageProperties properties;

    public ObjectStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * Both the web and worker processes reach this on startup, so the race
     * to create a missing bucket is a real one -- caught and ignored rather
     * than prevented, since letting Postgres-style "one migrator" ownership
     * apply here would mean a new environment's worker has to wait on its
     * web replica for no reason.
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
                log.info("Created object storage bucket {}", properties.bucket());
            } catch (BucketAlreadyOwnedByYouException raced) {
                // Another process won the race between our headBucket and
                // this createBucket; the bucket exists either way.
            }
        }
    }

    public void put(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public byte[] get(String key) {
        return s3Client
                .getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .asByteArray();
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }
}
