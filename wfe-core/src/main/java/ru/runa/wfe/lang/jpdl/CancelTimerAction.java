/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package ru.runa.wfe.lang.jpdl;

import org.springframework.beans.factory.annotation.Autowired;

import ru.runa.wfe.audit.ActionLog;
import ru.runa.wfe.execution.ExecutionContext;
import ru.runa.wfe.job.dao.JobDAO;
import ru.runa.wfe.lang.Action;

public class CancelTimerAction extends Action {
    private static final long serialVersionUID = 1L;
    @Autowired
    private transient JobDAO jobDAO;

    @Override
    public void execute(ExecutionContext executionContext) {
        // remove timers created with NAME='node name' or NAME='node id'
        jobDAO.deleteTimersByName(executionContext.getToken(), getName());
        jobDAO.deleteTimersByName(executionContext.getToken(), getNodeId());
        executionContext.addLog(new ActionLog(this));
    }

}
