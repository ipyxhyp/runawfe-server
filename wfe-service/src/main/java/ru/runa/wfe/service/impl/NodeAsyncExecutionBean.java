package ru.runa.wfe.service.impl;

import javax.ejb.EJB;

import java.util.Set;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.interceptor.Interceptors;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ejb.interceptor.SpringBeanAutowiringInterceptor;
import ru.runa.wfe.InternalApplicationException;
import ru.runa.wfe.audit.dao.ProcessLogDAO;
import ru.runa.wfe.commons.ITransactionListener;
import ru.runa.wfe.commons.TransactionListeners;
import ru.runa.wfe.commons.Utils;
import ru.runa.wfe.definition.dao.IProcessDefinitionLoader;
import ru.runa.wfe.execution.ExecutionContext;
import ru.runa.wfe.execution.Token;
import ru.runa.wfe.execution.dao.TokenDAO;
import ru.runa.wfe.lang.Node;
import ru.runa.wfe.lang.ProcessDefinition;
import ru.runa.wfe.service.interceptors.EjbExceptionSupport;
import ru.runa.wfe.service.interceptors.PerformanceObserver;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

/**
 * @since 4.3.0
 * @author Alex Chernyshev
 */
@MessageDriven(activationConfig = { @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/nodeAsyncExecution"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue") })
@TransactionManagement(TransactionManagementType.CONTAINER)
@Interceptors({ EjbExceptionSupport.class, PerformanceObserver.class, SpringBeanAutowiringInterceptor.class })
public class NodeAsyncExecutionBean implements MessageListener {
    private static final Log log = LogFactory.getLog(NodeAsyncExecutionBean.class);
    private static final Set<Long> lockedProcessIds = Sets.newHashSet();
    @Resource
    private MessageDrivenContext context;
    @EJB
    private NodeAsyncExecutorBean nodeAsyncExecutorBean;

    @Override
    public void onMessage(Message jmsMessage) {
        Long processId = null;
        try {
            ObjectMessage message = (ObjectMessage) jmsMessage;
            processId = message.getLongProperty("processId");
            Long tokenId = message.getLongProperty("tokenId");
            String nodeId = message.getStringProperty("nodeId");
            log.debug("handling node async execution request: {processId=" + processId + ", tokenId=" + tokenId + ", nodeId=" + nodeId + "}");
            boolean retry = message.getBooleanProperty("retry");
            if (message.getJMSRedelivered() && !retry) {
                log.debug("rejected due to redelivering");
                return;
            }
            synchronized (lockedProcessIds) {
                if (lockedProcessIds.contains(processId)) {
                    log.debug("deferring execution request due to lock on " + processId);
                    context.setRollbackOnly();
                    return;
                }
                lockedProcessIds.add(processId);
            }
            nodeAsyncExecutorBean.handleMessage(processId, tokenId, nodeId);
            for (ITransactionListener listener : TransactionListeners.get()) {
                try {
                    // TODO transaction in progress, so timeout must be long enough
                    listener.onTransactionComplete(null);
                } catch (Throwable th) {
                    log.error(th);
                }
            }
        } catch (Exception e) {
            log.error(jmsMessage, e);
            context.setRollbackOnly();
        } finally {
            synchronized (lockedProcessIds) {
                lockedProcessIds.remove(processId);
            }
        }
    }

}