package org.apache.ibatis.executor.statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * StatementHandle工厂，用户实现，非mybatis源码
 *
 * 通过StatementType创建对应的StatementHandler
 *
 * @author Holmes
 */
public class DefaultStatementHandlerFactory implements StatementHandlerFactory {

    @Override
    public StatementHandler createStatementHandler(Executor executor, MappedStatement ms, Object parameter,
                                                   RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        StatementHandler statementHandler = null;

        switch (ms.getStatementType()) {
            case STATEMENT:
                statementHandler = new SimpleStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
                break;
            case PREPARED:
                statementHandler = new PreparedStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
                break;
            case CALLABLE:
                statementHandler = new CallableStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
                break;
            default:
                throw new ExecutorException("Unknown statement type: " + ms.getStatementType());
        }
        return statementHandler;
    }
}
