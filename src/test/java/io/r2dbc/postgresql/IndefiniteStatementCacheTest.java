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
import io.r2dbc.postgresql.client.Parameter;
import io.r2dbc.postgresql.client.TestProtocolConnection;
import io.r2dbc.postgresql.message.backend.ErrorResponse;
import io.r2dbc.postgresql.message.backend.ParseComplete;
import io.r2dbc.postgresql.message.frontend.Flush;
import io.r2dbc.postgresql.message.frontend.Parse;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;

import static io.r2dbc.postgresql.client.TestProtocolConnection.NO_OP;
import static io.r2dbc.postgresql.message.Format.FORMAT_BINARY;
import static io.r2dbc.postgresql.util.TestByteBufAllocator.TEST;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class IndefiniteStatementCacheTest {

    @Test
    void constructorNoClient() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IndefiniteStatementCache(null))
            .withMessage("client must not be null");
    }

    @Test
    void getName() {
        // @formatter:off
        ProtocolConnection protocolConnection = TestProtocolConnection.builder()
            .expectRequest(new Parse("S_0", new int[]{100}, "test-query"), Flush.INSTANCE)
                .thenRespond(ParseComplete.INSTANCE)
            .expectRequest(new Parse("S_1", new int[]{200}, "test-query"), Flush.INSTANCE)
                .thenRespond(ParseComplete.INSTANCE)
            .expectRequest(new Parse("S_2", new int[]{200}, "test-query-2"), Flush.INSTANCE)
                .thenRespond(ParseComplete.INSTANCE)
            .build();
        // @formatter:on

        IndefiniteStatementCache statementCache = new IndefiniteStatementCache(protocolConnection);

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(100)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("S_0")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(200)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("S_0")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 200, Flux.just(TEST.buffer(2).writeShort(300)))), "test-query")
            .as(StepVerifier::create)
            .expectNext("S_1")
            .verifyComplete();

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 200, Flux.just(TEST.buffer(4).writeShort(300)))), "test-query-2")
            .as(StepVerifier::create)
            .expectNext("S_2")
            .verifyComplete();
    }

    @Test
    void getNameErrorResponse() {
        // @formatter:off
        ProtocolConnection protocolConnection = TestProtocolConnection.builder()
            .expectRequest(new Parse("S_0", new int[]{100}, "test-query"), Flush.INSTANCE)
                .thenRespond(new ErrorResponse(Collections.emptyList()))
            .build();
        // @formatter:on

        IndefiniteStatementCache statementCache = new IndefiniteStatementCache(protocolConnection);

        statementCache.getName(new Binding(1).add(0, new Parameter(FORMAT_BINARY, 100, Flux.just(TEST.buffer(4).writeInt(200)))), "test-query")
            .as(StepVerifier::create)
            .verifyError(R2dbcNonTransientResourceException.class);
    }

    @Test
    void getNameNoBinding() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IndefiniteStatementCache(NO_OP).getName(null, "test-query"))
            .withMessage("binding must not be null");
    }

    @Test
    void getNameNoSql() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IndefiniteStatementCache(NO_OP).getName(new Binding(0), null))
            .withMessage("sql must not be null");
    }

}
