package top.mc100.qdbank.common.dto;

import java.util.List;

public record PlayerAccounts(String uuid, String playerName, List<String> accounts) {
    public PlayerAccounts { accounts = accounts == null ? List.of() : List.copyOf(accounts); }
}
