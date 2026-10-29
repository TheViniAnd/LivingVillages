package ru.livingvillages.persistence;

import ru.livingvillages.i18n.Messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.livingvillages.domain.Village;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** One SQLite connection, owned exclusively by a dedicated executor. */
public final class Database implements AutoCloseable {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1024),r->new Thread(r,"LivingVillages-SQL"),new ThreadPoolExecutor.AbortPolicy());
    public int queued(){return worker.getQueue().size();}
    private Connection connection;
    public record Operation(UUID id, UUID player, UUID village, String before, String after, String aggregate, String state, String note) { }
    public static Village copy(Village v) { Village copy=decode(encode(v));copy.edition=v.edition;return copy; }
    public static String encode(Village v) { return JSON.toJson(v); }
    public static Village decode(String json){FreeSaveGuard.check(json);Village v=JSON.fromJson(json,Village.class);if(v.format!=15)throw new IllegalStateException(Messages.text("Неизвестный формат деревни ")+v.format);return v;}
    public CompletableFuture<List<Village>> open(Path file) {
        return submit(()->{
            Files.createDirectories(file.getParent());
            Class.forName("org.sqlite.JDBC");
            connection=DriverManager.getConnection("jdbc:sqlite:"+file);
            try(Statement s=connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL"); s.execute("PRAGMA busy_timeout=5000");
                int version;
                try(ResultSet r=s.executeQuery("PRAGMA user_version")) { if(!r.next())throw new SQLException(Messages.text("Не читается версия базы"));version=r.getInt(1); }
                if(version>3) throw new SQLException(Messages.text("База новее плагина; требуется обновление, не понижение версии"));
                connection.setAutoCommit(false);
                try {
                    if(version<1) {
                        s.execute("CREATE TABLE villages(id TEXT PRIMARY KEY, data TEXT NOT NULL)");
                        s.execute("CREATE TABLE operations(id TEXT PRIMARY KEY, player TEXT NOT NULL, village TEXT, before_inventory TEXT NOT NULL, after_inventory TEXT NOT NULL, aggregate TEXT, state TEXT NOT NULL, note TEXT NOT NULL)");
                    }
                    if(version<2) {
                        s.execute("CREATE INDEX operations_player_state ON operations(player,state)");
                        s.execute("CREATE TABLE audit(id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT DEFAULT CURRENT_TIMESTAMP, actor TEXT, village TEXT, message TEXT NOT NULL)");
                    }
                    if(version<3){
                        s.execute("UPDATE operations SET state='QUARANTINE' WHERE state='PREPARED' AND (player IN (SELECT player FROM operations WHERE state='PREPARED' GROUP BY player HAVING COUNT(*)>1) OR village IN (SELECT village FROM operations WHERE state='PREPARED' AND village IS NOT NULL GROUP BY village HAVING COUNT(*)>1))");
                        s.execute("CREATE UNIQUE INDEX one_prepared_player ON operations(player) WHERE state='PREPARED'");
                        s.execute("CREATE UNIQUE INDEX one_prepared_village ON operations(village) WHERE state='PREPARED' AND village IS NOT NULL");
                    }
                    s.execute("PRAGMA user_version=3"); connection.commit();
                } catch(Exception e) { connection.rollback(); throw e; }
                finally { connection.setAutoCommit(true); }
            }
            List<Village> list=new ArrayList<>();
            try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT data FROM villages")) { while(r.next()) list.add(decode(r.getString(1))); }
            return list;
        });
    }
    public CompletableFuture<Void> save(String json) { return submit(()->{upsert(json);return null;}); }
    public CompletableFuture<Void> saveAudited(String json,UUID actor,UUID village,String note){return submit(()->{connection.setAutoCommit(false);try{upsert(json);auditNow(actor,village,note);connection.commit();}catch(Exception error){connection.rollback();throw error;}finally{connection.setAutoCommit(true);}return null;});}
    
    private void upsert(String json) throws SQLException {
        Village v=decode(json);
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO villages(id,data) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data")) {
            s.setString(1,v.id.toString()); s.setString(2,json); s.executeUpdate();
        }
    }
    public CompletableFuture<Void> prepare(Operation op) { return submit(()->{
        try(PreparedStatement check=connection.prepareStatement("SELECT 1 FROM operations WHERE state IN ('PREPARED','QUARANTINE') AND (player=? OR village=?) LIMIT 1")){
            check.setString(1,op.player.toString());check.setString(2,op.village==null?null:op.village.toString());try(ResultSet r=check.executeQuery()){if(r.next())throw new SQLException(Messages.text("Игрок или деревня уже участвуют в незавершённой операции"));}
        }
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO operations VALUES(?,?,?,?,?,?,?,?)")) {
            s.setString(1,op.id.toString()); s.setString(2,op.player.toString()); s.setString(3,op.village==null?null:op.village.toString());
            s.setString(4,op.before);s.setString(5,op.after);s.setString(6,op.aggregate);s.setString(7,"PREPARED");s.setString(8,op.note);s.executeUpdate();
        } return null;
    }); }
    public CompletableFuture<Boolean> finish(Operation op, boolean commit) { return finish(op,commit,null); }
    public CompletableFuture<Boolean> finish(Operation op, boolean commit,UUID resolver) { return submit(()->{
        connection.setAutoCommit(false);
        try {
            String state;
            try(PreparedStatement s=connection.prepareStatement("SELECT state FROM operations WHERE id=?")) {
                s.setString(1,op.id.toString());try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new SQLException(Messages.text("Операция отсутствует")); state=r.getString(1); }
            }
            if(!state.equals("PREPARED") && !state.equals("QUARANTINE")) { connection.rollback();return state.equals("COMMITTED"); }
            if(commit && op.aggregate!=null) upsert(op.aggregate);
            try(PreparedStatement s=connection.prepareStatement("UPDATE operations SET state=? WHERE id=?")) {
                s.setString(1,commit?"COMMITTED":"CANCELLED");s.setString(2,op.id.toString());s.executeUpdate();
            }
            auditNow(resolver==null?op.player:resolver,op.village,(resolver==null?"":"MANUAL player="+op.player+" ")+(commit?"COMMIT ":"CANCEL ")+op.id+" "+op.note);
            connection.commit();
        } catch(Exception e) { connection.rollback();throw e; }
        finally { connection.setAutoCommit(true); }
        return commit;
    }); }
    public CompletableFuture<List<Operation>> pending(UUID player) { return submit(()->{
        List<Operation> out=new ArrayList<>();
        String sql="SELECT * FROM operations WHERE state IN ('PREPARED','QUARANTINE')"+(player==null?"":" AND player=?");
        try(PreparedStatement s=connection.prepareStatement(sql)) {
            if(player!=null) s.setString(1,player.toString());
            try(ResultSet r=s.executeQuery()) { while(r.next()) out.add(new Operation(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("player")),r.getString("village")==null?null:UUID.fromString(r.getString("village")),r.getString("before_inventory"),r.getString("after_inventory"),r.getString("aggregate"),r.getString("state"),r.getString("note"))); }
        } return out;
    }); }
    public CompletableFuture<Void> quarantine(UUID id) { return submit(()->{
        try(PreparedStatement s=connection.prepareStatement("UPDATE operations SET state='QUARANTINE' WHERE id=?")) { s.setString(1,id.toString());s.executeUpdate(); }return null;
    }); }
    public CompletableFuture<Void> audit(UUID player,UUID village,String message) { return submit(()->{auditNow(player,village,message);return null;}); }
    private void auditNow(UUID actor,UUID village,String message) throws SQLException {
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO audit(actor,village,message) VALUES(?,?,?)")) {
            s.setString(1,Objects.toString(actor,null));s.setString(2,Objects.toString(village,null));s.setString(3,message);s.executeUpdate();
        }
    }
    public record AuditEntry(String at,String actor,String message){}
    public CompletableFuture<List<AuditEntry>> recentAudit(UUID village,int limit){return submit(()->{
        var out=new ArrayList<AuditEntry>();try(var q=connection.prepareStatement("SELECT at,actor,message FROM audit WHERE village=? ORDER BY id DESC LIMIT ?")){q.setString(1,village.toString());q.setInt(2,Math.clamp(limit,1,45));try(var r=q.executeQuery()){while(r.next())out.add(new AuditEntry(r.getString(1),r.getString(2),r.getString(3)));}}return out;
    });}
    private <T> CompletableFuture<T> submit(Callable<T> job) {
        try{return CompletableFuture.supplyAsync(()->{try{return job.call();}catch(Exception e){throw new CompletionException(e);}},worker);}catch(RejectedExecutionException full){return CompletableFuture.failedFuture(new IllegalStateException(Messages.text("Очередь SQLite заполнена или закрыта; операция не принята"),full));}
    }
    public CompletableFuture<Void> barrier() { return submit(()->null); }
    @Override public void close() {
        worker.shutdown();
        try{if(!worker.awaitTermination(30,TimeUnit.SECONDS))throw new IllegalStateException(Messages.text("SQLite ещё завершает операции; блокировка папки сохранена до остановки JVM"));if(connection!=null)connection.close();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(Messages.text("Ожидание SQLite прервано"),e);}catch(SQLException e){throw new IllegalStateException(Messages.text("Ошибка закрытия SQLite"),e);}
    }
}
