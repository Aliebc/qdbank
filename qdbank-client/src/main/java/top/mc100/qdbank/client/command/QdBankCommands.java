package top.mc100.qdbank.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import top.mc100.qdbank.client.QdBankPlugin;

import java.util.List;

public final class QdBankCommands {
    private final QdBankPlugin plugin;
    public QdBankCommands(QdBankPlugin plugin){this.plugin=plugin;}
    public void register(){
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,event->
                event.registrar().register(build().build(),"起点银行",List.of("bank")));
    }
    private LiteralArgumentBuilder<CommandSourceStack> build(){
        var root=Commands.literal("qdbank").executes(ctx->{help(ctx.getSource().getSender());return 1;});
        root.then(accountCommand("open","qdbank.command.open",(sender,account)->plugin.create(sender,null,account)));
        root.then(accountCommand("close","qdbank.command.close",(sender,account)->plugin.delete(sender,null,account)));
        root.then(Commands.literal("see").requires(s->s.getSender().hasPermission("qdbank.command.see"))
                .then(Commands.argument("account",StringArgumentType.word())
                        .suggests((c,b)->suggestOwnAccounts(c.getSource(),b))
                        .executes(c->{plugin.open(c.getSource().getSender(),null,StringArgumentType.getString(c,"account"));return 1;})));
        root.then(Commands.literal("list").requires(s->s.getSender().hasPermission("qdbank.command.list"))
                .executes(ctx->{plugin.list(ctx.getSource().getSender(),null);return 1;}));
        root.then(Commands.literal("status").requires(s->s.getSender().hasPermission("qdbank.command.status"))
                .executes(ctx->{plugin.status(ctx.getSource().getSender());return 1;}));
        root.then(Commands.literal("reload").requires(s->s.getSender().hasPermission("qdbank.admin.reload"))
                .executes(ctx->{plugin.reloadBank(ctx.getSource().getSender());return 1;}));
        var admin=Commands.literal("admin");
        admin.then(adminAccount("open","qdbank.admin.open",(s,p,a)->plugin.create(s,p,a)));
        admin.then(adminAccount("close","qdbank.admin.close",(s,p,a)->plugin.delete(s,p,a)));
        admin.then(Commands.literal("see").requires(s->s.getSender().hasPermission("qdbank.admin.see"))
                .then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->suggestBankPlayers(b))
                        .then(Commands.argument("account",StringArgumentType.word())
                                .suggests((c,b)->suggestPlayerAccounts(StringArgumentType.getString(c,"player"),b))
                                .executes(c->{plugin.open(c.getSource().getSender(),StringArgumentType.getString(c,"player"),
                                        StringArgumentType.getString(c,"account"));return 1;}))));
        admin.then(Commands.literal("list").requires(s->s.getSender().hasPermission("qdbank.admin.list"))
                .then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->suggestPlayers(b))
                        .executes(c->{plugin.list(c.getSource().getSender(),StringArgumentType.getString(c,"player"));return 1;})));
        admin.then(Commands.literal("lookup").requires(s->s.getSender().hasPermission("qdbank.admin.lookup"))
                .then(Commands.argument("params",StringArgumentType.greedyString()).suggests((c,b)->suggestAuditParams(b))
                        .executes(c->{plugin.lookup(c.getSource().getSender(),StringArgumentType.getString(c,"params"));return 1;})));
        admin.then(Commands.literal("page").requires(s->s.getSender().hasPermission("qdbank.admin.lookup"))
                .then(Commands.argument("number",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(c->{plugin.page(c.getSource().getSender(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"number"));return 1;})));
        admin.then(Commands.literal("rollback").requires(s->s.getSender().hasPermission("qdbank.admin.rollback"))
                .then(Commands.argument("params",StringArgumentType.greedyString()).suggests((c,b)->suggestAuditParams(b))
                        .executes(c->{plugin.rollback(c.getSource().getSender(),StringArgumentType.getString(c,"params"));return 1;})));
        root.then(admin);return root;
    }
    private LiteralArgumentBuilder<CommandSourceStack> accountCommand(String name,String permission,AccountAction action){
        return Commands.literal(name).requires(s->s.getSender().hasPermission(permission))
                .then(Commands.argument("account",StringArgumentType.word()).executes(c->{action.run(c.getSource().getSender(),StringArgumentType.getString(c,"account"));return 1;}));
    }
    private LiteralArgumentBuilder<CommandSourceStack> adminAccount(String name,String permission,AdminAction action){
        return Commands.literal(name).requires(s->s.getSender().hasPermission(permission))
                .then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->suggestPlayers(b))
                        .then(Commands.argument("account",StringArgumentType.word()).executes(c->{action.run(c.getSource().getSender(),StringArgumentType.getString(c,"player"),StringArgumentType.getString(c,"account"));return 1;})));
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        plugin.getServer().getOnlinePlayers().forEach(p->builder.suggest(p.getName()));return builder.buildFuture();
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOwnAccounts(
            CommandSourceStack source,com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        if(!(source.getSender() instanceof org.bukkit.entity.Player player))return builder.buildFuture();
        var future=new java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>();
        plugin.directory(player.getUniqueId().toString(),players->{
            players.stream().flatMap(p->p.accounts().stream()).forEach(builder::suggest);
            future.complete(builder.build());
        });
        return future;
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBankPlayers(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        var future=new java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>();
        plugin.directory(null,players->{players.forEach(p->builder.suggest(p.playerName()));future.complete(builder.build());});
        return future;
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayerAccounts(
            String playerName,com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        var future=new java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>();
        plugin.directory(null,players->{
            players.stream().filter(p->p.playerName().equalsIgnoreCase(playerName))
                    .flatMap(p->p.accounts().stream()).forEach(builder::suggest);
            future.complete(builder.build());
        });
        return future;
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAuditParams(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder){
        String remaining=builder.getRemaining(),token=remaining.substring(remaining.lastIndexOf(' ')+1);
        int tokenStart=builder.getStart()+remaining.lastIndexOf(' ')+1;
        var current=builder.createOffset(tokenStart);
        if(token.startsWith("user:")||token.startsWith("u:")){
            String prefix=token.substring(0,token.indexOf(':')+1);
            var future=new java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>();
            plugin.directory(null,players->{players.forEach(p->current.suggest(prefix+p.playerName()));future.complete(current.build());});return future;
        }
        if(token.startsWith("action:")||token.startsWith("a:")){
            String prefix=token.substring(0,token.indexOf(':')+1);
            for(String action:java.util.List.of("inventory","+inventory","-inventory","open","close","create","delete","rollback"))current.suggest(prefix+action);
            return current.buildFuture();
        }
        if(token.startsWith("time:")||token.startsWith("t:")){
            String prefix=token.substring(0,token.indexOf(':')+1);
            for(String time:java.util.List.of("30m","1h","6h","1d","7d","2w"))current.suggest(prefix+time);
            return current.buildFuture();
        }
        if(token.startsWith("include:")||token.startsWith("i:")||token.startsWith("exclude:")||token.startsWith("e:")){
            String prefix=token.substring(0,token.indexOf(':')+1);
            org.bukkit.Registry.MATERIAL.stream().filter(org.bukkit.Material::isItem).limit(1000).forEach(m->current.suggest(prefix+m.getKey().getKey()));
            return current.buildFuture();
        }
        for(String parameter:java.util.List.of("user:","action:","time:","include:","exclude:"))current.suggest(parameter);
        return current.buildFuture();
    }
    private void help(CommandSender sender){sender.sendMessage("§6/qdbank open|close|list|see|status；管理员使用 /qdbank admin ...");}
    @FunctionalInterface private interface AccountAction{void run(CommandSender sender,String account);}
    @FunctionalInterface private interface AdminAction{void run(CommandSender sender,String player,String account);}
}
