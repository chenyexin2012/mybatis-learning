/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.executor;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 能够执行批量修改操作的执行器
 *
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

    public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

    // Statement对象列表，和batchResultList一一对应，每个节点维护了一组相同的JDBC批处理任务
    // 也保证了最终执行结果严格的按插入顺序执行
    private final List<Statement> statementList = new ArrayList<>();
    private final List<BatchResult> batchResultList = new ArrayList<>();

    // 当前存储的sql，实际表示上一次执行的sql
    private String currentSql;
    // 同上，表示使用的MappedStatement对象
    private MappedStatement currentStatement;

    public BatchExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    @Override
    public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
        final Configuration configuration = ms.getConfiguration();
        final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
        final BoundSql boundSql = handler.getBoundSql();
        final String sql = boundSql.getSql();
        final Statement stmt;
        // 本次执行的sql和上次执行的sql相同 且 对应的MappedStatement也相同
        // 由此处可知，BatchExecutor的Statement按照相邻则复用的规则，如：
        // 1. 当执行顺序为sql1&ms1->sql1&ms1->sql2&ms2->sql2&ms2，则会生成两个statement对象，
        // 2. 当执行顺序为sql1&ms1->sql1&ms1->sql2&ms2->sql1&ms1，
        //    虽然第四个sql1&ms1与第一个和第二个相同，但是并不相邻，会重新生成一个statement对象，
        //    因此最终会生成三个statement对象。
        // 这种规格也保证了最终批量执行的顺序和插入顺序保持一致
        if (sql.equals(currentSql) && ms.equals(currentStatement)) {
            // 取出最近使用的Statement对象
            int last = statementList.size() - 1;
            stmt = statementList.get(last);
            applyTransactionTimeout(stmt);
            handler.parameterize(stmt);//fix Issues 322
            BatchResult batchResult = batchResultList.get(last);
            batchResult.addParameterObject(parameterObject);
        } else {
            // 不存在，则重新创建Statement对象
            Connection connection = getConnection(ms.getStatementLog());
            stmt = handler.prepare(connection, transaction.getTimeout());
            handler.parameterize(stmt);    //fix Issues 322
            // 记录当前执行的sql和MappedStatement对象
            currentSql = sql;
            currentStatement = ms;
            // statementList和batchResultList一一对应
            statementList.add(stmt);
            batchResultList.add(new BatchResult(ms, sql, parameterObject));
        }
        // handler.parameterize(stmt);
        handler.batch(stmt);
        return BATCH_UPDATE_RETURN_VALUE;
    }

    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        Statement stmt = null;
        try {
            flushStatements();
            Configuration configuration = ms.getConfiguration();
            StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
            Connection connection = getConnection(ms.getStatementLog());
            stmt = handler.prepare(connection, transaction.getTimeout());
            handler.parameterize(stmt);
            return handler.<E>query(stmt, resultHandler);
        } finally {
            closeStatement(stmt);
        }
    }

    @Override
    protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
        flushStatements();
        Configuration configuration = ms.getConfiguration();
        StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
        Connection connection = getConnection(ms.getStatementLog());
        Statement stmt = handler.prepare(connection, transaction.getTimeout());
        stmt.closeOnCompletion();
        handler.parameterize(stmt);
        return handler.<E>queryCursor(stmt);
    }

    @Override
    public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
        try {
            // 存储所有的执行情况
            List<BatchResult> results = new ArrayList<>();
            if (isRollback) {
                // 回滚，即啥也不干，返回一个emptyList
                return Collections.emptyList();
            }
            // 执行批量操作
            for (int i = 0, n = statementList.size(); i < n; i++) {
                Statement stmt = statementList.get(i);
                applyTransactionTimeout(stmt);
                BatchResult batchResult = batchResultList.get(i);
                try {
                    // stmt.executeBatch() 完成一组批处理任务
                    batchResult.setUpdateCounts(stmt.executeBatch());
                    MappedStatement ms = batchResult.getMappedStatement();
                    List<Object> parameterObjects = batchResult.getParameterObjects();
                    KeyGenerator keyGenerator = ms.getKeyGenerator();
                    if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
                        Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
                        jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
                    } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141
                        for (Object parameter : parameterObjects) {
                            keyGenerator.processAfter(this, ms, stmt, parameter);
                        }
                    }
                    // Close statement to close cursor #1109
                    closeStatement(stmt);
                } catch (BatchUpdateException e) {
                    StringBuilder message = new StringBuilder();
                    message.append(batchResult.getMappedStatement().getId())
                            .append(" (batch index #")
                            .append(i + 1)
                            .append(")")
                            .append(" failed.");
                    if (i > 0) {
                        message.append(" ")
                                .append(i)
                                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
                    }
                    throw new BatchExecutorException(message.toString(), e, results, batchResult);
                }
                results.add(batchResult);
            }
            return results;
        } finally {
            // 关闭所有的Statement对象，并清楚批量对象缓存
            for (Statement stmt : statementList) {
                closeStatement(stmt);
            }
            currentSql = null;
            statementList.clear();
            batchResultList.clear();
        }
    }

}
