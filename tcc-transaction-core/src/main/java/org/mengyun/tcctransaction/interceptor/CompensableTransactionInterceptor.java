package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());

    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {

        CompensableMethodContext compensableMethodContext = new CompensableMethodContext(pjp);
        logger.info("上下文信息:" + JSON.toJSONString(compensableMethodContext, true));
        boolean isTransactionActive = transactionManager.isTransactionActive();

        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, compensableMethodContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method "
                + compensableMethodContext.getMethod().getName());
        }

        logger.info("当前方法" + JSON.toJSONString(compensableMethodContext.getMethod().getName(), true));
        switch (compensableMethodContext.getMethodRole(isTransactionActive)) {
            case ROOT:
                logger.info("当前方法是 ROOT 方法");
                return rootMethodProceed(compensableMethodContext);
            case PROVIDER:
                logger.info("当前方法是 PROVIDER 方法");
                return providerMethodProceed(compensableMethodContext);
            default:
                logger.info("当前方法是 default 方法");
                return pjp.proceed();
        }
    }

    /**
     * root方法逻辑
     * 
     * @param compensableMethodContext
     * @return
     * @throws Throwable
     */
    private Object rootMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Object returnValue = null;

        Transaction transaction = null;

        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        Set<Class<? extends Exception>> allDelayCancelExceptions = new HashSet<Class<? extends Exception>>();
        allDelayCancelExceptions.addAll(this.delayCancelExceptions);
        allDelayCancelExceptions
            .addAll(Arrays.asList(compensableMethodContext.getAnnotation().delayCancelExceptions()));

        try {
            transaction = transactionManager.begin(compensableMethodContext.getUniqueIdentity());
            logger.info("开始事务:" + JSON.toJSONString(transaction, true));
            try {
                returnValue = compensableMethodContext.proceed();
            } catch (Throwable tryingException) {

                if (!isDelayCancelException(tryingException, allDelayCancelExceptions)) {

                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s",
                        JSON.toJSONString(transaction)), tryingException);

                    transactionManager.rollback(asyncCancel);
                }

                throw tryingException;
            }
            logger.info("提交事务:" + JSON.toJSONString(transaction, true));
            transactionManager.commit(asyncConfirm);

        } catch (Throwable throwable) {
            logger.warn(JSON.toJSONString(throwable));
        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        return returnValue;
    }

    private Object providerMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Transaction transaction = null;

        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        try {

            switch (TransactionStatus.valueOf(compensableMethodContext.getTransactionContext().getStatus())) {
                case TRYING:
                    transaction =
                        transactionManager.propagationNewBegin(compensableMethodContext.getTransactionContext());
                    return compensableMethodContext.proceed();
                case CONFIRMING:
                    try {
                        transaction =
                            transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.commit(asyncConfirm);
                    } catch (NoExistedTransactionException excepton) {
                        // the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:

                    try {
                        transaction =
                            transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.rollback(asyncCancel);
                    } catch (NoExistedTransactionException exception) {
                        // the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        Method method = compensableMethodContext.getMethod();

        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable, Set<Class<? extends Exception>> delayCancelExceptions) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                    || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}
