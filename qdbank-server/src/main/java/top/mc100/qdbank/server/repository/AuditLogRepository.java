package top.mc100.qdbank.server.repository;

import top.mc100.qdbank.common.dto.*;
import top.mc100.qdbank.common.util.AuditItemCodec;
import top.mc100.qdbank.server.database.Database;

import java.sql.*;
import java.util.*;

public final class AuditLogRepository {
    public record Page(int page,int totalPages,List<AuditEntry> entries){}
    private final Database database;
    public AuditLogRepository(Database database){this.database=database;}

    public void append(String uuid,String account,ActionType action,String detail,String clientId,
                       OperationContext context)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                INSERT INTO audit_logs(uuid,account_name,action_type,item_detail,client_id,actor_uuid,actor_name,world,x,y,z,server_id,timestamp)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""")){
            s.setString(1,uuid);s.setString(2,account);s.setString(3,action.name());s.setString(4,detail);s.setString(5,clientId);
            s.setString(6,context.actorUuid());s.setString(7,context.actorName());s.setString(8,context.world());
            s.setDouble(9,context.x());s.setDouble(10,context.y());s.setDouble(11,context.z());s.setString(12,context.serverId());
            s.setLong(13,System.currentTimeMillis());
            s.executeUpdate();
        }
    }
    public Page lookup(String uuid,long since,String action,String include,String exclude,int page,int pageSize)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                SELECT timestamp,account_name,action_type,item_detail,actor_uuid,actor_name,world,x,y,z,server_id
                FROM audit_logs WHERE uuid=? AND timestamp>=? ORDER BY id DESC LIMIT 5000""")){
            s.setString(1,uuid);s.setLong(2,since);List<AuditEntry> rows=new ArrayList<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next())rows.add(new AuditEntry(rs.getString(1),rs.getString(2),
                    ActionType.valueOf(rs.getString(3)),AuditItemCodec.decode(rs.getString(4)),
                    new OperationContext(rs.getString(5),rs.getString(6),rs.getString(7),rs.getDouble(8),rs.getDouble(9),rs.getDouble(10),rs.getString(11))));}
            java.util.Set<String> includes=csv(include),excludes=csv(exclude);
            rows.removeIf(entry->{
                if((action.isBlank()||action.equals("inventory")||action.equals("+inventory")||action.equals("-inventory"))
                        &&entry.changes().isEmpty())return true;
                boolean positive=entry.changes().stream().anyMatch(cg->cg.amount()>0);
                boolean negative=entry.changes().stream().anyMatch(cg->cg.amount()<0);
                if(action.equals("+inventory")&&!positive)return true;
                if(action.equals("-inventory")&&!negative)return true;
                if(!action.isBlank()&&!action.equals("inventory")&&!action.equals("+inventory")&&!action.equals("-inventory")
                        &&!entry.action().name().equalsIgnoreCase(action))return true;
                java.util.Set<String> materials=entry.changes().stream().map(cg->cg.material().replace("minecraft:","").toLowerCase()).collect(java.util.stream.Collectors.toSet());
                return (!includes.isEmpty()&&java.util.Collections.disjoint(materials,includes))
                        ||(!excludes.isEmpty()&&!java.util.Collections.disjoint(materials,excludes));
            });
            int safeSize=Math.max(1,Math.min(50,pageSize)),total=Math.max(1,(rows.size()+safeSize-1)/safeSize),safePage=Math.max(1,Math.min(page,total));
            int from=Math.min(rows.size(),(safePage-1)*safeSize),to=Math.min(rows.size(),from+safeSize);
            return new Page(safePage,total,List.copyOf(rows.subList(from,to)));
        }
    }
    private static java.util.Set<String> csv(String value){
        if(value==null||value.isBlank())return java.util.Set.of();
        return java.util.Arrays.stream(value.toLowerCase().split(",")).map(v->v.replace("minecraft:","")).collect(java.util.stream.Collectors.toSet());
    }
    public record RollbackChange(String account,List<AuditItemChange> changes,OperationContext context){}
    public List<RollbackChange> rollbackChanges(String uuid,long since)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement("""
                SELECT account_name,item_detail,actor_uuid,actor_name,world,x,y,z,server_id FROM audit_logs
                WHERE uuid=? AND timestamp>=? AND item_detail<>''
                ORDER BY id DESC""")){
            s.setString(1,uuid);s.setLong(2,since);List<RollbackChange> result=new ArrayList<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next())result.add(new RollbackChange(rs.getString(1),AuditItemCodec.decode(rs.getString(2)),
                    new OperationContext(rs.getString(3),rs.getString(4),rs.getString(5),rs.getDouble(6),rs.getDouble(7),rs.getDouble(8),rs.getString(9))));}
            return result;
        }
    }
}
