package com.xfwang.govdata.datasource.infrastructure.connector.mysql;

import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import org.dromara.common.core.exception.ServiceException;

import java.util.concurrent.Semaphore;

/** 固定条带避免随数据源数量增长的锁缓存；碰撞只会收紧并发，不会放宽上限。 */
public final class QueryConcurrencyLimiter {
    private static final Semaphore GLOBAL = new Semaphore(32);
    private static final Semaphore[] TENANTS = stripes(64, 16);
    private static final Semaphore[] SOURCES = stripes(256, 4);

    private QueryConcurrencyLimiter() { }

    public static Permit acquire(String tenantId, Long datasourceId) {
        Semaphore tenant = TENANTS[Math.floorMod(tenantId.hashCode(), TENANTS.length)];
        Semaphore source = SOURCES[Math.floorMod(java.util.Objects.hash(tenantId, datasourceId), SOURCES.length)];
        if (!GLOBAL.tryAcquire()) throw busy();
        if (!tenant.tryAcquire()) { GLOBAL.release(); throw busy(); }
        if (!source.tryAcquire()) { tenant.release(); GLOBAL.release(); throw busy(); }
        return new Permit(tenant, source);
    }

    private static Semaphore[] stripes(int count, int permits) {
        Semaphore[] values = new Semaphore[count];
        for (int index = 0; index < count; index++) values[index] = new Semaphore(permits);
        return values;
    }

    private static ServiceException busy() { return new ServiceException(DataSourceErrorCode.QUERY_BUSY); }

    public static final class Permit implements AutoCloseable {
        private final Semaphore tenant;
        private final Semaphore source;
        private boolean closed;
        private Permit(Semaphore tenant, Semaphore source) { this.tenant = tenant; this.source = source; }
        @Override public void close() {
            if (!closed) { closed = true; source.release(); tenant.release(); GLOBAL.release(); }
        }
    }
}
