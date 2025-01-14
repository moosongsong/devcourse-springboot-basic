package com.prgms.management.voucher.repository;

import com.prgms.management.common.exception.DeleteFailException;
import com.prgms.management.common.exception.FindFailException;
import com.prgms.management.common.exception.SaveFailException;
import com.prgms.management.voucher.model.FixedAmountVoucher;
import com.prgms.management.voucher.model.PercentDiscountVoucher;
import com.prgms.management.voucher.model.Voucher;
import com.prgms.management.voucher.model.VoucherType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Repository
@Profile({"default"})
public class JdbcVoucherRepository implements VoucherRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcVoucherRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Voucher findById(UUID voucherId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * from voucher WHERE id = UNHEX(REPLACE(:id, '-', ''))",
                Collections.singletonMap("id", voucherId.toString()),
                (rs, rowNum) -> mapToVoucher(rs));
        } catch (EmptyResultDataAccessException e) {
            throw new FindFailException("찾는 ID에 대한 바우처가 없습니다.");
        }
    }

    @Override
    public List<Voucher> findByType(VoucherType type) {
        return jdbcTemplate.query("SELECT * from voucher WHERE type = :type ORDER BY created_at DESC",
            Collections.singletonMap("type", type.toString()),
            (rs, rowNum) -> mapToVoucher(rs));
    }

    @Override
    public List<Voucher> findByDate(Timestamp start, Timestamp end) {
        Map<String, Object> paramMap = new HashMap<>() {{
            put("start", start);
            put("end", end);
        }};
        return jdbcTemplate.query("SELECT * from voucher WHERE created_at BETWEEN :start AND :end " +
                "ORDER BY created_at DESC",
            paramMap,
            (rs, rowNum) -> mapToVoucher(rs));
    }

    @Override
    public List<Voucher> findByTypeAndDate(VoucherType type, Timestamp start, Timestamp end) {
        Map<String, Object> paramMap = new HashMap<>() {{
            put("type", type.toString());
            put("start", start);
            put("end", end);
        }};
        return jdbcTemplate.query("SELECT * from voucher WHERE type = :type AND created_at BETWEEN :start AND :end " +
                "ORDER BY created_at DESC",
            paramMap,
            (rs, rowNum) -> mapToVoucher(rs));
    }

    @Override
    public List<Voucher> findAll() {
        return jdbcTemplate.query("SELECT * from voucher ORDER BY created_at DESC", (rs, rowNum) -> mapToVoucher(rs));
    }

    @Override
    public Voucher save(Voucher voucher) {
        Map<String, Object> paramMap = new HashMap<>() {{
            put("id", voucher.getId().toString());
            put("name", voucher.getName());
            put("type", voucher.getType().toString());
            put("figure", voucher.getFigure());
            put("created_at", voucher.getCreatedAt());
        }};
        int result = jdbcTemplate.update("INSERT INTO voucher(id, name, type, figure, created_at) " +
                "VALUES (UNHEX(REPLACE(:id, '-', '')), :name, :type, :figure, :created_at)",
            paramMap);
        if (result == 1) {
            return voucher;
        }
        throw new SaveFailException();
    }

    @Override
    public void removeById(UUID voucherId) {
        int result = jdbcTemplate.update("DELETE FROM voucher WHERE id = (UNHEX(REPLACE(:id, '-', '')))",
            Collections.singletonMap("id", voucherId.toString()));
        if (result != 1) {
            throw new DeleteFailException();
        }
    }

    private UUID toUUID(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private Voucher mapToVoucher(ResultSet set) throws SQLException {
        UUID id = toUUID(set.getBytes("id"));
        String type = set.getString("type");
        String name = set.getString("name");
        int figure = set.getInt("figure");
        Timestamp createdAt = set.getTimestamp("created_at");

        if (type.equals(VoucherType.FIXED.toString())) {
            return new FixedAmountVoucher(id, name, figure, createdAt);
        } else {
            return new PercentDiscountVoucher(id, name, figure, createdAt);
        }
    }
}
