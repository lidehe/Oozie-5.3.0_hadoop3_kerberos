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

package org.apache.oozie.executor.jpa;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.oozie.ErrorCode;

import java.util.Objects;

/**
 * Deletes the coordinator action if its in WAITING or READY state.
 */
public class CoordActionRemoveJPAExecutor implements JPAExecutor<Void> {

    // private CoordinatorActionBean coordAction = null;
    private String coordActionId = null;

    /**
     * Constructor which records coordinator action id.
     *
     * @param coordActionId id of coordinator action
     */
    public CoordActionRemoveJPAExecutor(String coordActionId) {
        Objects.requireNonNull(coordActionId, "coordActionId cannot be null");
        this.coordActionId = coordActionId;
    }

    @Override
    public Void execute(EntityManager em) throws JPAExecutorException {
        Query g = em.createNamedQuery("DELETE_UNSCHEDULED_ACTION");
        g.setParameter("id", coordActionId);
        int actionsDeleted;
        try {
            actionsDeleted = g.executeUpdate();
        } catch (Exception e) {
            throw new JPAExecutorException(ErrorCode.E0603, e.getMessage(), e);
        }

        if (actionsDeleted == 0)
            throw new JPAExecutorException(ErrorCode.E1022, coordActionId);

        return null;
    }

    @Override
    public String getName() {
        return "CoordActionRemoveJPAExecutor";
    }
}
