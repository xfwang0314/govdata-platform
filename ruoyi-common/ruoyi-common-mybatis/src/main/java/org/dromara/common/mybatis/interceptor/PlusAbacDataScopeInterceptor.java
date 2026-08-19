package org.dromara.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.BaseMultiTableInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.dromara.common.mybatis.handler.AbacDataScopeHandler;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** 仅处理 {@code @AbacDataScope} 的 MyBatis-Plus 拦截器。 */
public class PlusAbacDataScopeInterceptor extends BaseMultiTableInnerInterceptor implements InnerInterceptor {

    private final AbacDataScopeHandler handler = new AbacDataScopeHandler();

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId()) || handler.invalid()) {
            return;
        }
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(parserSingle(mpBoundSql.sql(), ms.getId()));
    }

    @Override
    public void beforePrepare(StatementHandler statementHandler, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpStatement = PluginUtils.mpStatementHandler(statementHandler);
        MappedStatement mappedStatement = mpStatement.mappedStatement();
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        if ((commandType == SqlCommandType.UPDATE || commandType == SqlCommandType.DELETE)
            && !InterceptorIgnoreHelper.willIgnoreDataPermission(mappedStatement.getId()) && !handler.invalid()) {
            PluginUtils.MPBoundSql mpBoundSql = mpStatement.mPBoundSql();
            mpBoundSql.sql(parserMulti(mpBoundSql.sql(), mappedStatement.getId()));
        }
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (select instanceof PlainSelect plainSelect) {
            setWhere(plainSelect);
        } else if (select instanceof SetOperationList operations) {
            List<Select> selects = operations.getSelects();
            selects.forEach(value -> setWhere((PlainSelect) value));
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        update.setWhere(handler.getSqlSegment(update.getWhere()));
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        delete.setWhere(handler.getSqlSegment(delete.getWhere()));
    }

    private void setWhere(PlainSelect select) {
        select.setWhere(handler.getSqlSegment(select.getWhere()));
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        return where;
    }
}
