package org.dromara.common.mybatis.handler;

import cn.hutool.core.util.ObjectUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.annotation.ScopeColumn;
import org.dromara.common.mybatis.helper.AbacDataScopeHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.api.permission.DataAuthorizationScope;
import org.dromara.system.api.permission.DataAuthorizationScopeProvider;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 将 ABAC 返回的结构化范围编译为当前 Mapper 声明的受信任 SQL 条件。 */
public class AbacDataScopeHandler {

    public Expression getSqlSegment(Expression where) {
        try {
            AbacDataScope declaration = requireScope();
            DataAuthorizationScope authorized = resolveScope(declaration);
            if (authorized.unrestricted()) {
                return where;
            }
            String filter = buildFilter(declaration, currentUser(), authorized);
            Expression expression = CCJSqlParserUtil.parseExpression(filter);
            ParenthesedExpressionList<Expression> parenthesis = new ParenthesedExpressionList<>(expression);
            return ObjectUtil.isNull(where) ? parenthesis : new AndExpression(where, parenthesis);
        } catch (JSQLParserException exception) {
            throw new ServiceException("ABAC 数据范围解析异常 => " + exception.getMessage());
        }
    }

    public boolean invalid() {
        return AbacDataScopeHelper.getScope() == null;
    }

    private DataAuthorizationScope resolveScope(AbacDataScope scope) {
        DataAuthorizationScopeProvider provider = SpringUtils.getBeanFactory()
            .getBeanProvider(DataAuthorizationScopeProvider.class).getIfAvailable();
        if (provider == null) {
            return DataAuthorizationScope.deniedScope();
        }
        Optional<DataAuthorizationScope> resolved = provider.resolve(new DataAuthorizationScopeRequest(
            scope.resourceType(), scope.action(), scope.purpose()));
        return resolved.orElseGet(DataAuthorizationScope::deniedScope);
    }

    private String buildFilter(AbacDataScope scope, LoginUser user, DataAuthorizationScope authorized) {
        if (authorized.denied()) {
            return "1 = 0";
        }

        Map<String, String> columns = columns(scope);
        List<String> conditions = new ArrayList<>();
        if (!authorized.departmentIds().isEmpty()) {
            String column = columns.get("ownerDeptId");
            if (StringUtils.isBlank(column)) {
                return "1 = 0";
            }
            String ids = authorized.departmentIds().stream().filter(Objects::nonNull).sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
            if (StringUtils.isNotBlank(ids)) {
                conditions.add(column + " IN (" + ids + ")");
            }
        }
        if (authorized.includeCurrentUser()) {
            String column = columns.get("ownerUserId");
            if (StringUtils.isBlank(column) || user.getUserId() == null) {
                return "1 = 0";
            }
            conditions.add(column + " = " + user.getUserId());
        }
        return conditions.isEmpty() ? "1 = 0" : String.join(" OR ", conditions);
    }

    private Map<String, String> columns(AbacDataScope scope) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ScopeColumn column : scope.columns()) {
            if (StringUtils.isBlank(column.attribute()) || StringUtils.isBlank(column.column())) {
                throw new ServiceException("ABAC 数据范围列映射不能为空");
            }
            result.putIfAbsent(column.attribute(), column.column());
        }
        return result;
    }

    private LoginUser currentUser() {
        LoginUser user = AbacDataScopeHelper.getVariable("user");
        if (user == null) {
            user = LoginHelper.getLoginUser();
            AbacDataScopeHelper.setVariable("user", user);
        }
        return user;
    }

    private AbacDataScope requireScope() {
        AbacDataScope scope = AbacDataScopeHelper.getScope();
        if (scope == null) {
            throw new ServiceException("ABAC 数据范围声明不存在");
        }
        return scope;
    }

}
