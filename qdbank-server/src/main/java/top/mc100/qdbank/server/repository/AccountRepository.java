package top.mc100.qdbank.server.repository;

import top.mc100.qdbank.server.database.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.mc100.qdbank.common.dto.PlayerAccounts;

public final class AccountRepository {
    public record Account(byte[] content, long revision) {}
    private final Database database;
    public AccountRepository(Database database) { this.database = database; }

    public boolean create(String uuid, String playerName, String name) throws SQLException {
        try (Connection c = database.connection(); PreparedStatement s = c.prepareStatement(
                "INSERT OR IGNORE INTO accounts(uuid,player_name,account_name,content) VALUES(?,?,?,?)")) {
            s.setString(1,uuid);s.setString(2,playerName);s.setString(3,name);s.setBytes(4,new byte[0]);
            boolean created=s.executeUpdate()==1;
            if(!created)try(PreparedStatement update=c.prepareStatement("UPDATE accounts SET player_name=? WHERE uuid=?")){
                update.setString(1,playerName);update.setString(2,uuid);update.executeUpdate();
            }
            return created;
        }
    }
    public boolean delete(String uuid, String name) throws SQLException {
        try (Connection c=database.connection(); PreparedStatement s=c.prepareStatement("DELETE FROM accounts WHERE uuid=? AND account_name=?")) {
            s.setString(1,uuid); s.setString(2,name); return s.executeUpdate()==1;
        }
    }
    public List<String> list(String uuid) throws SQLException {
        try (Connection c=database.connection(); PreparedStatement s=c.prepareStatement("SELECT account_name FROM accounts WHERE uuid=? ORDER BY account_name")) {
            s.setString(1,uuid); List<String> result=new ArrayList<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next())result.add(rs.getString(1));} return result;
        }
    }
    public Optional<Account> find(String uuid, String name) throws SQLException {
        try (Connection c=database.connection(); PreparedStatement s=c.prepareStatement("SELECT content,revision FROM accounts WHERE uuid=? AND account_name=?")) {
            s.setString(1,uuid); s.setString(2,name);
            try(ResultSet rs=s.executeQuery()){return rs.next()?Optional.of(new Account(rs.getBytes(1),rs.getLong(2))):Optional.empty();}
        }
    }
    public boolean update(String uuid,String name,long expected,byte[] content) throws SQLException {
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement(
                "UPDATE accounts SET content=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE uuid=? AND account_name=? AND revision=?")){
            s.setBytes(1,content);s.setString(2,uuid);s.setString(3,name);s.setLong(4,expected);return s.executeUpdate()==1;
        }
    }
    public long forceUpdate(String uuid,String name,byte[] content)throws SQLException{
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement(
                "UPDATE accounts SET content=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE uuid=? AND account_name=?")){
            s.setBytes(1,content);s.setString(2,uuid);s.setString(3,name);s.executeUpdate();
        }
        return find(uuid,name).orElseThrow().revision();
    }
    public List<PlayerAccounts> directory(String uuid)throws SQLException{
        String sql=uuid==null||uuid.isBlank()
                ?"SELECT uuid,player_name,account_name FROM accounts ORDER BY player_name COLLATE NOCASE,account_name"
                :"SELECT uuid,player_name,account_name FROM accounts WHERE uuid=? ORDER BY account_name";
        try(Connection c=database.connection();PreparedStatement s=c.prepareStatement(sql)){
            if(uuid!=null&&!uuid.isBlank())s.setString(1,uuid);
            java.util.LinkedHashMap<String,PlayerAccounts> result=new java.util.LinkedHashMap<>();
            try(ResultSet rs=s.executeQuery()){while(rs.next()){
                String id=rs.getString(1),player=rs.getString(2),account=rs.getString(3);
                PlayerAccounts existing=result.get(id);
                java.util.ArrayList<String> names=existing==null?new java.util.ArrayList<>():new java.util.ArrayList<>(existing.accounts());
                names.add(account);result.put(id,new PlayerAccounts(id,player,names));
            }}
            return new ArrayList<>(result.values());
        }
    }
}
