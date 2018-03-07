package ru.runa.wfe.service.impl;

import com.google.common.base.Objects;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.interceptor.Interceptors;
import javax.jms.JMSException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ejb.interceptor.SpringBeanAutowiringInterceptor;
import ru.runa.wfe.InternalApplicationException;
import ru.runa.wfe.audit.dao.ProcessLogDAO;
import ru.runa.wfe.commons.Utils;
import ru.runa.wfe.definition.dao.IProcessDefinitionLoader;
import ru.runa.wfe.execution.ExecutionContext;
import ru.runa.wfe.execution.Token;
import ru.runa.wfe.execution.dao.TokenDAO;
import ru.runa.wfe.lang.Node;
import ru.runa.wfe.lang.ProcessDefinition;

@Stateless(name = "NodeAsyncExecutorBean")
@TransactionManagement
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
@Interceptors({ SpringBeanAutowiringInterceptor.class })
public class NodeAsyncExecutorBean {
    private static final Log log = LogFactory.getLog(NodeAsyncExecutionBean.class);
    @Autowired
    private TokenDAO tokenDAO;
    @Autowired
    private IProcessDefinitionLoader processDefinitionLoader;
    @Autowired
    private ProcessLogDAO processLogDAO;

    public void handleMessage(Long processId, Long tokenId, String nodeId) throws JMSException {
        Token token = tokenDAO.getNotNull(tokenId);
        if (token.getProcess().hasEnded()) {
            log.debug("Ignored execution in ended " + token.getProcess());
            return;
        }
        if (!Objects.equal(nodeId, token.getNodeId())) {
            throw new InternalApplicationException(token + " expected to be in node " + nodeId);
        }
        ProcessDefinition processDefinition = processDefinitionLoader.getDefinition(token.getProcess());
        Node node = processDefinition.getNodeNotNull(token.getNodeId());
        try {
            ExecutionContext executionContext = new ExecutionContext(processDefinition, token);
            node.handle(executionContext);
        } catch (Throwable th) {
            log.error(processId + ":" + tokenId, th);
            Utils.sendNodeAsyncFailedExecutionMessage(tokenId, th);
        }
    }
}
