package com.ees.eval.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공용 DB 환경에서 타 팀 테이블 접근을 원천 차단하는 MyBatis 인터셉터입니다.
 * _51 접미사가 없는 테이블에 대한 DML/DQL 실행 시 예외를 발생시킵니다.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }),
        @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class })
})
@Component
public class TableAccessGuardInterceptor implements Interceptor {

    // 테이블명 추출 패턴: from/join/into/update 뒤에 오는 테이블명
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?:from|join|into|update)\\s+([a-z_][a-z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    // 검사에서 제외할 시스템 뷰/테이블
    private static final Pattern SYSTEM_PATTERN = Pattern.compile(
            "sys\\.|information_schema\\.",
            Pattern.CASE_INSENSITIVE);

    /**
     * SQL 실행 전 테이블명을 검사하여 _51 접미사 여부를 확인합니다.
     *
     * @param invocation MyBatis 실행 컨텍스트
     * @return 검증 통과 시 원래 SQL 실행 결과
     * @throws IllegalAccessException _51 접미사 없는 테이블 접근 시 발생
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql().trim().toLowerCase();

        // 시스템 뷰 조회는 허용
        if (SYSTEM_PATTERN.matcher(sql).find()) {
            return invocation.proceed();
        }

        // 테이블명 추출 및 _51 접미사 검증
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            // 서브쿼리의 별칭(AS) 등 예약어 제외
            if (isReservedWord(tableName))
                continue;

            if (!tableName.endsWith("_51")) {
                throw new SecurityException(
                        String.format(
                                "[TableAccessGuard] 타 팀 테이블 접근 차단! " +
                                        "테이블명 '%s'은 '_51' 접미사가 없습니다. " +
                                        "Mapper ID: %s",
                                tableName, ms.getId()));
            }
        }

        return invocation.proceed();
    }

    /**
     * SQL 예약어 여부를 판단합니다. 예약어는 테이블명 검사에서 제외됩니다.
     *
     * @param word 검사할 단어
     * @return 예약어이면 true
     */
    private boolean isReservedWord(String word) {
        return switch (word.toLowerCase()) {
            case "select", "where", "and", "or", "not", "null",
                    "set", "values", "on", "inner", "outer", "left",
                    "right", "cross", "exists", "in", "as", "by",
                    "asc", "desc", "top", "with", "updlock", "nolock",
                    "rowlock", "holdlock", "readpast" ->
                true;
            default -> false;
        };
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
