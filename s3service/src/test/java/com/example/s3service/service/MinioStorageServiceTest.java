package com.example.s3service.service;

import com.example.s3service.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioStorageServiceTest {

    private MinioClient minioClient;
    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        service = new MinioStorageService(
                minioClient,
                new MinioProperties("http://localhost:9000", "minioadmin", "minioadmin", "xslt-storage")
        );
    }

    @Test
    void createsBucketAndUploads() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        byte[] payload = "<root/>".getBytes(StandardCharsets.UTF_8);
        String key = service.upload("test.xml", new ByteArrayInputStream(payload), payload.length, "application/xml");

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
        assertEquals("test.xml", key);
    }

    @Test
    void downloadsObject() throws Exception {
        byte[] payload = "<ok/>".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = new GetObjectResponse(
                Headers.of(), "xslt-storage", null, "doc.xml", new ByteArrayInputStream(payload)
        );
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        assertArrayEquals(payload, service.download("doc.xml"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsAndDeletesObjects() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("a.xml");
        Result<Item> result = mock(Result.class);
        when(result.get()).thenReturn(item);
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(result));

        assertEquals(List.of("a.xml"), service.list());

        service.delete("a.xml");
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
}
