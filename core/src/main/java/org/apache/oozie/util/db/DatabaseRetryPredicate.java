/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.oozie.util.db;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public abstract class DatabaseRetryPredicate implements Predicate<Throwable> {

    @Override
    public abstract boolean test(Throwable input);

    /*
     * Helper method for subclasses to retrieve all exceptions in a set. "All exceptions" means the exception
     * hierarchy that can be walked by calling getCause() repeatedly.
     *
     * Subclasses either check if a particular exception was raised or not, or need the SQLException to extract the
     * error code. In both cases the exception has to be found.
     */
    protected Set<Class<?>> getAllExceptions(final Throwable t) {
        final Set<Class<?>> exceptions = new HashSet<>();

        exceptions.add(t.getClass());

        Throwable ex = t;
        while (ex.getCause() != null) {
            exceptions.add(ex.getCause().getClass());
            ex = ex.getCause();
        }

        return exceptions;
    }
}
