/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.vault.metadata;

import java.util.Objects;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;

import com.google.common.base.Preconditions;

public class StorageInformation {

    public static class Builder {
        @FunctionalInterface
        public interface RequireBucketName {
            RequireBlobId bucketName(BucketName bucketName);
        }

        @FunctionalInterface
        public interface RequireBlobId {
            StorageInformation blobId(BlobId blobId);
        }
    }

    public static Builder.RequireBucketName builder() {
        return bucketName -> blobId -> new StorageInformation(bucketName, blobId);
    }

    private final BucketName bucketName;
    private final BlobId blobId;

    private StorageInformation(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);

        this.bucketName = bucketName;
        this.blobId = blobId;
    }

    public BucketName getBucketName() {
        return bucketName;
    }

    public BlobId getBlobId() {
        return blobId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof StorageInformation) {
            StorageInformation that = (StorageInformation) o;

            return Objects.equals(this.bucketName, that.bucketName)
                && Objects.equals(this.blobId, that.blobId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(bucketName, blobId);
    }
}
