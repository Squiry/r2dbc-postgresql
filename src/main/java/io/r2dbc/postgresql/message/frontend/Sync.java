/*
 * Copyright 2017-2019 the original author or authors.
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

package io.r2dbc.postgresql.message.frontend;

import io.netty.buffer.ByteBuf;
import io.r2dbc.postgresql.util.Assert;

import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeByte;
import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeLengthPlaceholder;
import static io.r2dbc.postgresql.message.frontend.FrontendMessageUtils.writeSize;

/**
 * The Sync message.
 */
public final class Sync implements FrontendMessage {

    /**
     * A static singleton instance that should always be used.
     */
    public static final Sync INSTANCE = new Sync();

    private Sync() {
    }

    @Override
    public void encode(ByteBuf out) {
        Assert.requireNonNull(out, "out must not be null");

        writeByte(out, 'S');
        writeLengthPlaceholder(out);

        writeSize(out);
    }

    @Override
    public String toString() {
        return "Sync{}";
    }

}
