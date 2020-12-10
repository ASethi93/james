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
package org.apache.james.rrt.cassandra;

import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class CassandraRecipientRewriteTable extends AbstractRecipientRewriteTable {
    private static final SchemaVersion MAPPINGS_SOURCES_SUPPORTED_VERSION = new SchemaVersion(7);

    private final CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO;
    private final CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;
    private final CassandraSchemaVersionManager versionManager;

    @Inject
    CassandraRecipientRewriteTable(CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO,
                                   CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO,
                                   CassandraSchemaVersionManager versionManager) {
        this.cassandraRecipientRewriteTableDAO = cassandraRecipientRewriteTableDAO;
        this.cassandraMappingsSourcesDAO = cassandraMappingsSourcesDAO;
        this.versionManager = versionManager;
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) {
        cassandraRecipientRewriteTableDAO.addMapping(source, mapping)
            .then(cassandraMappingsSourcesDAO.addMapping(mapping, source))
            .block();
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) {
        cassandraRecipientRewriteTableDAO.removeMapping(source, mapping)
            .then(cassandraMappingsSourcesDAO.removeMapping(mapping, source))
            .block();
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return cassandraRecipientRewriteTableDAO.retrieveMappings(source)
            .blockOptional()
            .orElse(MappingsImpl.empty());
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        return cassandraRecipientRewriteTableDAO.getAllMappings()
            .collect(Guavate.toImmutableMap(
                pair -> pair.getLeft(),
                pair -> MappingsImpl.fromMappings(pair.getRight()),
                Mappings::union))
            .block();
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) {
        return cassandraRecipientRewriteTableDAO.retrieveMappings(MappingSource.fromUser(user, domain)).blockOptional()
            .or(() -> cassandraRecipientRewriteTableDAO.retrieveMappings(MappingSource.fromDomain(domain)).blockOptional())
            .orElse(MappingsImpl.empty());
    }

    @Override
    public Stream<MappingSource> listSources(Mapping mapping) throws RecipientRewriteTableException {
        Preconditions.checkArgument(listSourcesSupportedType.contains(mapping.getType()),
            "Not supported mapping of type %s", mapping.getType());

        if (versionManager.isBefore(MAPPINGS_SOURCES_SUPPORTED_VERSION).block()) {
            return super.listSources(mapping);
        }

        return cassandraMappingsSourcesDAO.retrieveSources(mapping).toStream();
    }
}
