/*
 * This file is part of the RUNA WFE project.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; version 2.1
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */
package ru.runa.wfe.service.impl;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.runa.wfe.commons.SystemProperties;
import ru.runa.wfe.service.impl.ReceiveMessageBean.ReceiveMessageData;

public class ProcessTokenSynchronization {
    private static Log log = LogFactory.getLog(ProcessTokenSynchronization.class);
    private static final Set<Long> lockedProcessIds = Sets.newHashSet();
    // this cache is required due to locking inside transaction
    // locking outside transaction is impossible due to CMT requirements for rollback (exception is not treated as normal behaviour)
    private static final Cache<Long, Long> trackedProcessIds = CacheBuilder.newBuilder()
            .expireAfterWrite(SystemProperties.getProcessExecutionTrackingTimeoutInSeconds(), TimeUnit.SECONDS).build();

    public static boolean lock(Long processId, Long tokenId) {
        log.debug("Locking process " + processId + " with token " + tokenId);
        synchronized (ProcessTokenSynchronization.class) {
            if (lockedProcessIds.contains(processId)) {
                log.debug("Deferring execution request due to lock");
                return false;
            }
            Long trackedTokenId = trackedProcessIds.getIfPresent(processId);
            if (trackedTokenId != null && !Objects.equal(trackedTokenId, tokenId)) {
                log.debug("Deferring execution request due to track");
                return false;
            }
            lockedProcessIds.add(processId);
            log.debug("Locked");
            return true;
        }
    }

    public static void unlock(Long processId, Long tokenId) {
        log.debug("Unlocking process " + processId + " with token " + tokenId);
        synchronized (ProcessTokenSynchronization.class) {
            lockedProcessIds.remove(processId);
            trackedProcessIds.put(processId, tokenId);
            log.debug("Unlocked");
        }
    }

    public static boolean lock(List<ReceiveMessageData> receiveMessageDatas) {
        log.debug("Locking processes " + receiveMessageDatas);
        synchronized (ProcessTokenSynchronization.class) {
            for (ReceiveMessageData receiveMessageData : receiveMessageDatas) {
                if (lockedProcessIds.contains(receiveMessageData.processId)) {
                    log.debug("Deferring execution request due to lock");
                    return false;
                }
                Long trackedTokenId = trackedProcessIds.getIfPresent(receiveMessageData.processId);
                if (trackedTokenId != null && !Objects.equal(trackedTokenId, receiveMessageData.tokenId)) {
                    log.debug("Deferring execution request due to track");
                    return false;
                }
            }
            for (ReceiveMessageData data : receiveMessageDatas) {
                lockedProcessIds.add(data.processId);
            }
            log.debug("Locked");
            return true;
        }
    }

    public static void unlock(List<ReceiveMessageData> receiveMessageDatas) {
        log.debug("Unlocking processes " + receiveMessageDatas);
        synchronized (ProcessTokenSynchronization.class) {
            for (ReceiveMessageData receiveMessageData : receiveMessageDatas) {
                lockedProcessIds.remove(receiveMessageData.processId);
                trackedProcessIds.put(receiveMessageData.processId, receiveMessageData.processId);
            }
            log.debug("Unlocked");
        }
    }

}
