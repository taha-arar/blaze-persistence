/*
 * Copyright 2014 - 2017 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.impl.transform;

import com.blazebit.persistence.impl.AbstractManager;
import com.blazebit.persistence.impl.expression.modifier.ExpressionModifier;

import java.util.Collections;
import java.util.Set;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class SimpleTransformerGroup implements ExpressionTransformerGroup<ExpressionModifier> {

    private final ExpressionModifierVisitor<ExpressionModifier> visitor;

    public SimpleTransformerGroup(ExpressionModifierVisitor<ExpressionModifier> visitor) {
        this.visitor = visitor;
    }

    @Override
    public void applyExpressionTransformer(AbstractManager<? extends ExpressionModifier> manager) {
        manager.apply(visitor);
    }

    @Override
    public void afterGlobalTransformation() {
    }

    @Override
    public Set<String> getRequiredGroupByClauses() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getOptionalGroupByClauses() {
        return Collections.emptySet();
    }
}
