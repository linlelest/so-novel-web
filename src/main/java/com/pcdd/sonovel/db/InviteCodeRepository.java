package com.pcdd.sonovel.db;

import com.pcdd.sonovel.model.InviteCode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InviteCodeRepository {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public InviteCode create(String code, int maxUses, String createdBy) {
        long now = System.currentTimeMillis();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO invite_codes (code,max_uses,used_count,created_at,created_by) VALUES(?,?,0,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code); ps.setInt(2, maxUses); ps.setLong(3, now); ps.setString(4, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return InviteCode.builder().id(rs.getInt(1)).code(code).maxUses(maxUses).usedCount(0).createdAt(now).createdBy(createdBy).build();
        } catch (Exception e) { throw new RuntimeException("创建邀请码失败", e); }
        return null;
    }

    public List<InviteCode> findAll() {
        List<InviteCode> list = new ArrayList<>();
        try (Connection c = db.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM invite_codes WHERE max_uses=0 OR used_count<max_uses ORDER BY id")) {
            while (rs.next()) list.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return list;
    }

    public InviteCode findByCode(String code) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM invite_codes WHERE code=?")) {
            ps.setString(1, code); ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        } catch (Exception e) { throw new RuntimeException(e); }
        return null;
    }

    /** 使用邀请码（递增使用次数），返回 true 表示成功 */
    public boolean useCode(String code) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE invite_codes SET used_count=used_count+1 WHERE code=? AND (max_uses=0 OR used_count<max_uses)")) {
            ps.setString(1, code); return ps.executeUpdate() > 0;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void delete(int id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM invite_codes WHERE id=?")) {
            ps.setInt(1, id); ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void batchDelete(List<Integer> ids) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            for (int id : ids) s.addBatch("DELETE FROM invite_codes WHERE id=" + id);
            s.executeBatch();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public int countAvailable() { // count not-exhausted codes
        try (Connection c = db.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM invite_codes WHERE max_uses=0 OR used_count<max_uses")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    private InviteCode map(ResultSet rs) throws SQLException {
        return InviteCode.builder().id(rs.getInt("id")).code(rs.getString("code"))
                .maxUses(rs.getInt("max_uses")).usedCount(rs.getInt("used_count"))
                .createdAt(rs.getLong("created_at")).createdBy(rs.getString("created_by")).build();
    }
}
