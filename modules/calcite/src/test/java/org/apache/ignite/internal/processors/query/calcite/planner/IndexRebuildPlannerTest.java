/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.calcite.planner;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteIndexScan;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteRel;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteTableScan;
import org.apache.ignite.internal.processors.query.calcite.schema.IgniteSchema;
import org.apache.ignite.internal.processors.query.calcite.trait.IgniteDistributions;
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Test;

/**
 * Planner test for index rebuild.
 */
public class IndexRebuildPlannerTest extends AbstractPlannerTest {
    /** */
    private IgniteSchema publicSchema;

    /** */
    private TestTable tbl;

    /** {@inheritDoc} */
    @Override public void setup() {
        super.setup();

        tbl = createTable("TBL", 100, IgniteDistributions.single(), "ID", Integer.class, "VAL", String.class)
            .addIndex("IDX", 0);

        publicSchema = createSchema(tbl);
    }

    /** */
    @Test
    public void testIndexRebuild() throws Exception {
        String sql = "SELECT * FROM TBL WHERE id = 0";

        assertPlan(sql, publicSchema, isInstanceOf(IgniteIndexScan.class));

        tbl.markIndexRebuildInProgress(true);

        assertPlan(sql, publicSchema, isInstanceOf(IgniteTableScan.class));

        tbl.markIndexRebuildInProgress(false);

        assertPlan(sql, publicSchema, isInstanceOf(IgniteIndexScan.class));
    }

    /** */
    @Test
    public void testConcurrentIndexRebuildStateChange() throws Exception {
        String sql = "SELECT * FROM TBL WHERE id = 0";

        AtomicBoolean stop = new AtomicBoolean();

        IgniteInternalFuture<?> fut = GridTestUtils.runAsync(() -> {
            while (!stop.get()) {
                tbl.markIndexRebuildInProgress(true);
                tbl.markIndexRebuildInProgress(false);
            }
        });

        try {
            for (int i = 0; i < 1000; i++) {
                IgniteRel rel = physicalPlan(sql, publicSchema);

                assertTrue(rel instanceof IgniteTableScan || rel instanceof IgniteIndexScan);
            }
        }
        finally {
            stop.set(true);
        }

        fut.get();
    }
}
