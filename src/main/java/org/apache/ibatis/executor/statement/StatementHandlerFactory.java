package org.apache.ibatis.executor.statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * StatementHandle抽象工厂，用户实现，非mybatis源码
 *
 * @author Holmes
 */
public interface StatementHandlerFactory {

    public StatementHandler createStatementHandler(Executor executor, MappedStatement ms, Object parameter,
                                                   RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql);
}
