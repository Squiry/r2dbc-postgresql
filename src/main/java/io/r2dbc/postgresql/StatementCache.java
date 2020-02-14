/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.postgresql;

import io.r2dbc.postgresql.client.Binding;
import io.r2dbc.postgresql.client.ProtocolConnection;
import reactor.core.publisher.Mono;

interface StatementCache {

    Mono<String> getName(Binding binding, String sql);

    static StatementCache fromPreparedStatementCacheQueries(ProtocolConnection protocolConnection, int preparedStatementCacheQueries) {
        if (preparedStatementCacheQueries < 0) {
            return new IndefiniteStatementCache(protocolConnection);
        }
        if (preparedStatementCacheQueries == 0) {
            return new DisabledStatementCache(protocolConnection);
        }
        return new BoundedStatementCache(protocolConnection, preparedStatementCacheQueries);
    }
}
