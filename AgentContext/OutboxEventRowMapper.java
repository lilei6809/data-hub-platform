package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.OutboxEventRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;


public class OutboxEventRowMapper implements RowMapper<OutboxEventRow> {

    @Nullable
    @Override
    public OutboxEventRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEventRow(
                rs.getString("event_id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("topic"),
                rs.getString("payload"),
                rs.getString("headers"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                toInstant(rs.getTimestamp("next_retry_at")),
                rs.getString("last_error"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("published_at"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
