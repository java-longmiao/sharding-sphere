/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.transaction.base.manager.servicecomb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Saga definition builder.
 *
 * @author yangyi
 */
@RequiredArgsConstructor
public final class SagaDefinitionBuilder {
    
    private static final String TYPE = "sql";
    
    private static final String POLICY = "ForwardRecovery";
    
    private static final int DEFAULT_RETIRES = 5;
    
    private static final int DEFAULT_FAIL_RETRY_DELAY = 5000;
    
    private final int transactionRetires;
    
    private final int compensationRetires;
    
    private final int failRetryDelay;
    
    private final ConcurrentLinkedQueue<Object> requests = new ConcurrentLinkedQueue<>();
    
    private String[] parents = new String[]{};
    
    private ConcurrentLinkedQueue<Object> newRequestIds = new ConcurrentLinkedQueue<>();
    
    public SagaDefinitionBuilder() {
        this(DEFAULT_RETIRES, DEFAULT_RETIRES, DEFAULT_FAIL_RETRY_DELAY);
    }
    
    /**
     * Add child request node to definition graph.
     *
     * @param id                 request id
     * @param datasource         data source name
     * @param sql                transaction sql
     * @param params             transaction sql parameters
     * @param compensationSql    compensation sql
     * @param compensationParams compensation sql parameters
     */
    public void addChildRequest(final String id, final String datasource, final String sql, final List<List<Object>> params,
                                final String compensationSql, final List<Collection<Object>> compensationParams) {
        Transaction transaction = new Transaction(sql, params, transactionRetires);
        Compensation compensation = new Compensation(compensationSql, compensationParams, compensationRetires);
        requests.add(new SagaRequest(id, datasource, TYPE, transaction, compensation, parents, failRetryDelay));
        newRequestIds.add(id);
    }
    
    /**
     * switch to next logic sql.
     */
    public void switchParents() {
        parents = newRequestIds.toArray(new String[]{});
        newRequestIds = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Build saga definition json string.
     *
     * @return saga json string
     * @throws JsonProcessingException json process exception
     */
    public String build() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonRequest = objectMapper.writeValueAsString(requests);
        return "{\"policy\":\"" + POLICY + "\",\"requests\":" + jsonRequest + "}";
    }
    
    @RequiredArgsConstructor
    @Getter
    private static class SagaRequest {
        
        private final String id;
        
        private final String datasource;
        
        private final String type;
        
        private final Transaction transaction;
        
        private final Compensation compensation;
        
        private final String[] parents;
        
        private final int failRetryDelayMilliseconds;
    }
    
    @RequiredArgsConstructor
    @Getter
    private static class Transaction {
        
        private final String sql;
        
        private final List<List<Object>> params;
        
        private final int retries;
    }
    
    @RequiredArgsConstructor
    @Getter
    private static class Compensation {
        
        private final String sql;
        
        private final List<Collection<Object>> params;
        
        private final int retries;
    }
}