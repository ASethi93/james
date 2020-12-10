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

package org.apache.james.mailbox.store.quota;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;

/**
 * This quota manager is intended to be used when you want to deactivate the Quota feature
 */
public class NoQuotaManager implements QuotaManager {

    @Override
    public Quota<QuotaCountLimit, QuotaCountUsage> getMessageQuota(QuotaRoot quotaRoot) {
        return Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(0))
            .computedLimit(QuotaCountLimit.unlimited())
            .build();
    }

    @Override
    public Quota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(QuotaRoot quotaRoot) {
        return Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(0))
            .computedLimit(QuotaSizeLimit.unlimited())
            .build();
    }

    @Override
    public Quotas getQuotas(QuotaRoot quotaRoot) {
        return new Quotas(getMessageQuota(quotaRoot), getStorageQuota(quotaRoot));
    }
}
