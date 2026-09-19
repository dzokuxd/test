package pl.dzoku.sectorsystem.model;

public enum TransferState {
    IDLE("Gracz nie jest w trakcie transferu"),
    PENDING("Oczekiwanie na potwierdzenie transferu"),
    SAVING_STATE("Zapisywanie stanu gracza"),
    DISCONNECTING("Odłączanie od obecnego serwera"),
    TRANSFERRING("Przekierowywanie przez proxy"),
    LOADING_STATE("Ładowanie stanu na nowym serwerze"),
    COMPLETE("Transfer zakończony sukcesem"),
    FAILED("Transfer nieudany - rollback");

    private final String description;

    TransferState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(TransferState next) {
        return switch (this) {
            case IDLE -> next == PENDING;
            case PENDING -> next == SAVING_STATE || next == FAILED;
            case SAVING_STATE -> next == DISCONNECTING || next == FAILED;
            case DISCONNECTING -> next == TRANSFERRING || next == FAILED;
            case TRANSFERRING -> next == LOADING_STATE || next == FAILED;
            case LOADING_STATE -> next == COMPLETE || next == FAILED;
            case COMPLETE, FAILED -> next == IDLE;
        };
    }

    public long getMaxDurationMs() {
        return switch (this) {
            case PENDING -> 5_000;
            case SAVING_STATE -> 10_000;
            case DISCONNECTING -> 5_000;
            case TRANSFERRING -> 15_000;
            case LOADING_STATE -> 10_000;
            default -> Long.MAX_VALUE;
        };
    }
}
