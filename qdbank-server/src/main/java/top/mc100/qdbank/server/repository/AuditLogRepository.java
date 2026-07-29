package top.mc100.qdbank.server.repository;

import top.mc100.qdbank.common.dto.*;
import top.mc100.qdbank.common.util.AuditItemCodec;
import top.mc100.qdbank.server.database.Database;

import java.sql.*;
import java.util.*;

public final class AuditLogRepository {
    private final Database database;
    public AuditLogRepository(Database database){this.database=database;}

    public void append(String uuid,String account,ActionType action,String detail,String clientId,
                       OperationContext context,byte[] before,byte[] after)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                INSERT INTO audit_logs(uuid,account_name,action_type,item_detail,client_id,world,x,y,z,server_id,before_content,after_content)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""")){
            s.setString(1,uuid);s.setString(2,account);s.setString(3,action.name());s.setString(4,detail);s.setString(5,clientId);
            s.setString(6,context.world());s.setDouble(7,context.x());s.setDouble(8,context.y());s.setDouble(9,context.z());s.setString(10,context.serverId());
            s.setBytes(11,before);s.setBytes(12,after);s.executeUpdate();
        }
    }
    public List<AuditEntry> lookup(String uuid,long since)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                SELECT timestamp,account_name,action_type,item_detail,world,x,y,z,server_id
                FROM audit_logs WHERE uuid=? AND timestamp>=datetime(?/1000,'unixepoch') ORDER BY id DESC LIMIT 200""")){
            s.setString(1,uuid);s.setLong(2,since);List<AuditEntry> rows=new ArrayList<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next())rows.add(new AuditEntry(rs.getString(1),rs.getString(2),
                    ActionType.valueOf(rs.getString(3)),AuditItemCodec.decode(rs.getString(4)),
                    new OperationContext(rs.getString(5),rs.getDouble(6),rs.getDouble(7),rs.getDouble(8),rs.getString(9))));}
            return rows;
        }
    }
    public Map<String,byte[]> rollbackSnapshots(String uuid,long since)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                SELECT account_name,before_content FROM audit_logs
                WHERE uuid=? AND timestamp>=datetime(?/1000,'unixepoch') AND before_content IS NOT NULL
                ORDER BY id ASC""")){
            s.setString(1,uuid);s.setLong(2,since);Map<String,byte[]> snapshots=new LinkedHashMap<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next())snapshots.putIfAbsent(rs.getString(1),rs.getBytes(2));}
            return snapshots;
        }
    }
}
