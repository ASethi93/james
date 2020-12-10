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

package org.apache.james.json;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class JsonGenericSerializer<T, U extends DTO> {

    public static <T, U extends DTO> RequireNestedConfiguration<T, U> forModules(Set<? extends DTOModule<? extends T, ? extends U>> modules) {
        return nestedTypesModules -> {
            ImmutableSet<DTOModule<? extends T, ? extends U>> dtoModules = ImmutableSet.copyOf(modules);
            return new JsonGenericSerializer<>(dtoModules, ImmutableSet.copyOf(nestedTypesModules), new DTOConverter<>(dtoModules));
        };
    }

    @SafeVarargs
    public static <T, U extends DTO> RequireNestedConfiguration<T, U> forModules(DTOModule<? extends T, ? extends U>... modules) {
        return forModules(ImmutableSet.copyOf(modules));
    }

    public interface RequireNestedConfiguration<T, U extends DTO> {
        JsonGenericSerializer<T, U> withNestedTypeModules(Set<DTOModule<?, ?>> modules);

        default JsonGenericSerializer<T, U> withMultipleNestedTypeModules(DTOModule<?, ?>... modules) {
            return withNestedTypeModules(ImmutableSet.copyOf(modules));
        }

        default JsonGenericSerializer<T, U> withMultipleNestedTypeModules(Set<DTOModule<?, ?>>... modules) {
            return withNestedTypeModules(Arrays.stream(modules).flatMap(Collection::stream).collect(Guavate.toImmutableSet()));
        }

        default JsonGenericSerializer<T, U> withoutNestedType() {
            return withNestedTypeModules(ImmutableSet.of());
        }
    }

    public static class InvalidTypeException extends RuntimeException {
        public InvalidTypeException(String message) {
            super(message);
        }

        public InvalidTypeException(String message, MismatchedInputException exception) {
            super(message, exception);
        }
    }

    public static class UnknownTypeException extends RuntimeException {
        public UnknownTypeException(String message) {
            super(message);
        }
    }

    private final ObjectMapper objectMapper;
    private final DTOConverter<T, U> dtoConverter;

    private JsonGenericSerializer(Set<? extends DTOModule<? extends T, ? extends U>> modules, Set<? extends DTOModule<?, ?>> nestedTypesModules, DTOConverter<T, U> converter) {
        Preconditions.checkArgument(!hasDuplicateTypeIds(modules, nestedTypesModules));
        this.dtoConverter = converter;
        this.objectMapper = buildObjectMapper(Sets.union(modules, nestedTypesModules));
    }

    private boolean hasDuplicateTypeIds(Set<? extends DTOModule<?, ?>> modules, Set<? extends DTOModule<?, ?>> nestedTypesModules) {
        return Sets.intersection(
                modules.stream().map(DTOModule::getDomainObjectType).collect(Collectors.toSet()),
                nestedTypesModules.stream().map(DTOModule::getDomainObjectType).collect(Collectors.toSet()))
            .size() > 0;
    }

    private ObjectMapper buildObjectMapper(Set<? extends DTOModule<?, ?>> modules) {
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new GuavaModule())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        NamedType[] namedTypes = modules.stream()
            .map(module -> new NamedType(module.getDTOClass(), module.getDomainObjectType()))
            .toArray(NamedType[]::new);
        objectMapper.registerSubtypes(namedTypes);
        return objectMapper;
    }

    public String serialize(T domainObject) throws JsonProcessingException {
        U dto = dtoConverter.toDTO(domainObject)
            .orElseThrow(() -> new UnknownTypeException("unknown type " + domainObject.getClass()));
        return objectMapper.writeValueAsString(dto);
    }


    public T deserialize(String value) throws IOException {
        U dto = jsonToDTO(value);
        return dtoConverter.toDomainObject(dto)
            .orElseThrow(() -> new UnknownTypeException("unknown type " + dto.getType()));
    }

    private U jsonToDTO(String value) throws IOException {
        try {
            JsonNode jsonTree = detectDuplicateProperty(value);
            return parseAsPolymorphicDTO(jsonTree);
        } catch (InvalidTypeIdException e) {
            String typeId = e.getTypeId();
            if (typeId == null) {
                throw new InvalidTypeException("Unable to deserialize the json document", e);
            } else {
                throw new UnknownTypeException("unknown type " + typeId);
            }
        } catch (MismatchedInputException e) {
            throw new InvalidTypeException("Unable to deserialize the json document", e);
        }
    }

    private JsonNode detectDuplicateProperty(String value) throws IOException {
        return objectMapper.readTree(value);
    }

    @SuppressWarnings("rawtypes")
    private U parseAsPolymorphicDTO(JsonNode jsonTree) throws IOException {
        return (U) objectMapper.readValue(objectMapper.treeAsTokens(jsonTree), DTO.class);
    }

}
